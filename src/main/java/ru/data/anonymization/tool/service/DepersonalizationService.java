package ru.data.anonymization.tool.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;
import ru.data.anonymization.tool.methods.options.MaskItem;
import ru.data.anonymization.tool.dto.SyntheticConfigDto;
import ru.data.anonymization.tool.dto.TableData;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DepersonalizationService {

    private final DatabaseConnectionService controllerDB;
    private final DataPreparationService dataPreparationService;
    private final StatisticService statisticService;
    private final TableInfoService tableInfoService;
    private final SelectionService selectionService;
    private final SyntheticMethodService syntheticMethodService;

    @Getter
    @Setter
    private Map<String, MaskItem> methodsMap = new HashMap<>();

    private final Map<String, List<String>> riskConfigMap = new HashMap<>();
    private final Map<String, List<String>> assessmentConfigMap = new HashMap<>();

    private List<MaskItem> methods = new ArrayList<>();
    private String oldConnection = null;

    public void addRiskConfig(String key, List<String> columns) {
        riskConfigMap.put(key, columns);
    }

    public List<String> getRiskConfig(String key) {
        return riskConfigMap.get(key);
    }

    public void removeRiskConfig(String key) {
        riskConfigMap.remove(key);
    }

    public void addAssessmentConfig(String key, List<String> columns) {
        assessmentConfigMap.put(key, columns);
    }

    public void removeAssessmentConfig(String key) {
        assessmentConfigMap.remove(key);
    }

    public List<String> getAssessmentConfig(String key) {
        return assessmentConfigMap.get(key);
    }

    public void addMethod(String name, MaskItem method) {
        methodsMap.put(name, method);
    }

    public void removeMethod(String name) {
        methodsMap.remove(name);
    }

    public boolean isContainsKey(String name) {
        return methodsMap.containsKey(name);
    }

    public MaskItem getMethod(String name) {
        return methodsMap.get(name);
    }

    public List<String> getConfig() {
        List<String> tableMethods = new ArrayList<>();
        methodsMap.forEach((key, method) -> tableMethods.add(key));
        return tableMethods;
    }

    public String start() {
        methods = new ArrayList<>(methodsMap.values());
        List<SyntheticConfigDto> syntheticConfigs = syntheticMethodService.getConfigs();

        if (methods.isEmpty() && syntheticConfigs.isEmpty()) {
            return null;
        }
        String time = "0";
        try {
            if (tableInfoService.isCsvSourceActive()) {
                if (!syntheticConfigs.isEmpty()) {
                    time = syntheticCsv(syntheticConfigs);
                } else {
                    time = maskingCsv();
                }
            } else {
                init();
                if (!syntheticConfigs.isEmpty()) {
                    time = maskingSynthetic(syntheticConfigs);
                } else {
                    time = masking();
                }
            }
        } catch (Exception e) {
            System.out.println(e.getLocalizedMessage());
            System.out.println(e.getMessage());
            e.printStackTrace();
            controllerDB.disconnect();
        }
        return time;
    }

    private void init() throws Exception {
        oldConnection = controllerDB.getDatabase();
        String maskDB = "mask_" + controllerDB.getDatabase();
        var someREs = controllerDB.executeQuery(
                "SELECT pid, usename, datname, application_name, state \n"
                + "FROM pg_stat_activity \n"
                + "WHERE datname = 'mad';");
        while (someREs.next()) {
            System.out.println("PID: " + someREs.getInt("pid"));
            System.out.println("User: " + someREs.getString("usename"));
            System.out.println("Database: " + someREs.getString("datname"));
            System.out.println("Application: " + someREs.getString("application_name"));
            System.out.println("State: " + someREs.getString("state"));
            System.out.println("----------------------------");
        }
        controllerDB.execute("DROP DATABASE IF EXISTS " + maskDB + ";");
        controllerDB.execute(
                "CREATE DATABASE " + maskDB
                + " WITH TEMPLATE " + controllerDB.getDatabase()
                + " OWNER " + controllerDB.getUsername() + ";");
        controllerDB.setNameDB(maskDB);

        controllerDB.disconnect();
        controllerDB.connect();
    }

    public void backToOriginDatabase() {
        if (oldConnection == null) {
            return;
        }
        controllerDB.setNameDB(oldConnection);
        controllerDB.disconnect();
        controllerDB.connect();
    }

    private String masking() throws Exception {
        statisticService.resetStatistic();
        statisticService.setNotMaskStatistic(assessmentConfigMap);

        dataPreparationService.start();
        applySelectionFilter();

        long start = System.currentTimeMillis();

        for (MaskItem method : methods) {
            method.start(controllerDB);
        }

        long end = System.currentTimeMillis();

        statisticService.calculateRisk(riskConfigMap);
        statisticService.setMaskStatistic(assessmentConfigMap);

        return formatSeconds(start, end);
    }

    private String maskingSynthetic(List<SyntheticConfigDto> syntheticConfigs)
            throws Exception {
        statisticService.resetStatistic();
        statisticService.setNotMaskStatistic(assessmentConfigMap);

        dataPreparationService.start();
        applySelectionFilter();

        long start = System.currentTimeMillis();
        List<TableData> syntheticTables = syntheticMethodService.generateSyntheticTables(syntheticConfigs);
        for (TableData syntheticTable : syntheticTables) {
            tableInfoService.importCsvTable(syntheticTable);
        }

        long end = System.currentTimeMillis();

        statisticService.calculateRisk(riskConfigMap);
        statisticService.setMaskStatistic(assessmentConfigMap);

        return formatSeconds(start, end);
    }

    private void applySelectionFilter() throws Exception {
        for (String tableName : tableInfoService.getTables()) {
            if (!selectionService.hasCustomSelection(tableName)) {
                continue;
            }

            Set<Integer> selectedRows = selectionService.getSelectedRows(tableName);
            if (selectedRows.isEmpty()) {
                controllerDB.execute("DELETE FROM " + tableName + ";");
                continue;
            }

            List<String> columnNames = tableInfoService.getColumnNames(tableName);
            if (columnNames.isEmpty()) {
                continue;
            }

            String orderColumn = columnNames.get(0);
            String selectedRowNumbers = selectedRows.stream()
                                                    .map(index -> index + 1)
                                                    .sorted()
                                                    .map(String::valueOf)
                                                    .collect(Collectors.joining(", "));

            String filterSql = """
                    WITH ordered AS (
                        SELECT ctid, ROW_NUMBER() OVER (ORDER BY %s) AS rn
                        FROM %s
                    )
                    DELETE FROM %s
                    USING ordered
                    WHERE %s.ctid = ordered.ctid AND ordered.rn NOT IN (%s);
                    """.formatted(orderColumn, tableName, tableName, tableName, selectedRowNumbers);

            controllerDB.execute(filterSql);
        }
    }

    private String maskingCsv() throws IOException {
        long start = System.currentTimeMillis();
        List<TableData> maskedTables = new ArrayList<>();

        for (String tableName : tableInfoService.getTables()) {
            var tableOptional = tableInfoService.getCsvTable(tableName);
            if (tableOptional.isEmpty()) {
                continue;
            }

            TableData sourceTable = tableOptional.get();
            List<List<String>> filteredRows = new ArrayList<>();
            List<List<String>> sourceRows = sourceTable.getRows();

            for (int i = 0; i < sourceRows.size(); i++) {
                if (!selectionService.hasCustomSelection(tableName)
                        || selectionService.isRowSelected(tableName, i)) {
                    filteredRows.add(new ArrayList<>(sourceRows.get(i)));
                }
            }

            TableData maskedTable = new TableData(
                    tableName + "_masked",
                    sourceTable.getColumnNames(),
                    filteredRows
            );

            maskedTables.add(maskedTable);
            saveTempCsv(maskedTable);
        }

        if (!maskedTables.isEmpty()) {
            tableInfoService.loadCsvData(maskedTables);
        }

        long end = System.currentTimeMillis();
        return formatSeconds(start, end);
    }

    private String syntheticCsv(List<SyntheticConfigDto> syntheticConfigs)
            throws Exception {
        long start = System.currentTimeMillis();
        List<TableData> syntheticTables = syntheticMethodService.generateSyntheticTables(syntheticConfigs);
        if (!syntheticTables.isEmpty()) {
            tableInfoService.loadCsvData(syntheticTables);
        }
        long end = System.currentTimeMillis();
        return formatSeconds(start, end);
    }

    private String formatSeconds(long start, long end) {
        NumberFormat formatter = new DecimalFormat("#0.00");
        return formatter.format((end - start) / 1000d).replace(",", ".");
    }

    private void saveTempCsv(TableData tableData) throws IOException {
        String fileName = tableData.getName() + ".csv";
        Path tempFile = Files.createTempFile(fileName, "");

        List<String> lines = new ArrayList<>();
        lines.add(String.join(";", tableData.getColumnNames()));
        for (List<String> row : tableData.getRows()) {
            lines.add(String.join(";", row));
        }

        Files.write(tempFile, lines, StandardOpenOption.TRUNCATE_EXISTING);
    }

}
