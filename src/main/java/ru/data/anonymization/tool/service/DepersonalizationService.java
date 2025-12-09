package ru.data.anonymization.tool.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;
import ru.data.anonymization.tool.methods.options.MaskItem;
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
import java.util.Objects;
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

    @Getter
    @Setter
    private Map<String, MaskItem> methodsMap = new HashMap<>();

    private final Map<String, List<String>> riskConfigMap = new HashMap<>();
    private final Map<String, List<String>> assessmentConfigMap = new HashMap<>();

    private List<MaskItem> methods = new ArrayList<>();
    private String oldConnection = null;
    private static final String INDEX_COLUMN_NAME = "masking_row_index";

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

        if (methods.isEmpty()) {
            return null;
        }
        String time = "0";
        try {
            if (tableInfoService.isCsvSourceActive()) {
                time = maskingCsv();
            } else {
                init();
                time = masking();
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

        Map<String, String> backupTables = prepareSelectionBackups();

        try {
            dataPreparationService.start();

            long start = System.currentTimeMillis();

            for (MaskItem method : methods) {
                method.start(controllerDB);
            }

            long end = System.currentTimeMillis();

            statisticService.calculateRisk(riskConfigMap);
            statisticService.setMaskStatistic(assessmentConfigMap);

            NumberFormat formatter = new DecimalFormat("#0.00");
            return formatter.format((end - start) / 1000d).replace(",", ".");
        } finally {
            restoreUnselectedRows(backupTables);
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
                List<String> rowCopy = new ArrayList<>(sourceRows.get(i));
                filteredRows.add(rowCopy);
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

    private Map<String, String> prepareSelectionBackups() throws Exception {
        Map<String, String> backups = new HashMap<>();

        for (String tableName : tableInfoService.getTables()) {
            if (!selectionService.hasCustomSelection(tableName)) {
                continue;
            }

            Set<Integer> selectedRows = selectionService.getSelectedRows(tableName);
            if (selectedRows.isEmpty() && selectionService.getSelectionPercent(tableName) == 100) {
                continue;
            }

            List<String> columnNames = tableInfoService.getColumnNames(tableName);
            if (columnNames.isEmpty()) {
                continue;
            }

            String orderedColumn = "\"" + columnNames.get(0) + "\"";
            String backupTableName = tableName + "_mask_backup";

            controllerDB.execute("ALTER TABLE " + tableName + " ADD COLUMN IF NOT EXISTS "
                    + INDEX_COLUMN_NAME + " BIGINT;");

            String indexingSql = "WITH ordered AS (" +
                    "SELECT ctid, row_number() OVER (ORDER BY " + orderedColumn + ") - 1 AS rn " +
                    "FROM " + tableName +
                    ") UPDATE " + tableName + " t " +
                    "SET " + INDEX_COLUMN_NAME + " = ordered.rn " +
                    "FROM ordered " +
                    "WHERE t.ctid = ordered.ctid;";

            controllerDB.execute(indexingSql);

            controllerDB.execute("DROP TABLE IF EXISTS " + backupTableName + ";");
            controllerDB.execute("CREATE TEMP TABLE " + backupTableName + " AS SELECT * FROM "
                    + tableName + " WHERE " + buildNotSelectedCondition(selectedRows) + ";");

            backups.put(tableName, backupTableName);
        }

        return backups;
    }

    private void restoreUnselectedRows(Map<String, String> backups) throws Exception {
        for (Map.Entry<String, String> entry : backups.entrySet()) {
            String tableName = entry.getKey();
            String backupTableName = entry.getValue();
            Set<Integer> selectedRows = selectionService.getSelectedRows(tableName);

            controllerDB.execute("DELETE FROM " + tableName + " WHERE "
                    + buildNotSelectedCondition(selectedRows) + ";");

            controllerDB.execute("INSERT INTO " + tableName + " SELECT * FROM "
                    + backupTableName + ";");

            controllerDB.execute("DROP TABLE IF EXISTS " + backupTableName + ";");
            controllerDB.execute("ALTER TABLE " + tableName + " DROP COLUMN IF EXISTS "
                    + INDEX_COLUMN_NAME + ";");
        }
    }

    private String buildNotSelectedCondition(Set<Integer> selectedRows) {
        if (selectedRows.isEmpty()) {
            return "TRUE";
        }

        String selectedIndexes = selectedRows.stream()
                                             .filter(Objects::nonNull)
                                             .map(index -> "CAST(" + index + " AS BIGINT)")
                                             .collect(Collectors.joining(","));
        return INDEX_COLUMN_NAME + " NOT IN (" + selectedIndexes + ")";
    }

}
