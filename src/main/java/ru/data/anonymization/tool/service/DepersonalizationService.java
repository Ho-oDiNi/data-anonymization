package ru.data.anonymization.tool.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;
import ru.data.anonymization.tool.methods.options.MaskItem;
import ru.data.anonymization.tool.dto.TableData;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    }

    private String maskingCsv() throws Exception {
        long start = System.currentTimeMillis();

        List<TableData> sourceTables = new ArrayList<>();
        for (String tableName : tableInfoService.getTables()) {
            tableInfoService.getCsvTable(tableName).ifPresent(sourceTables::add);
        }
        if (sourceTables.isEmpty()) {
            return "0";
        }

        prepareTemporaryDatabase(sourceTables);

        statisticService.resetStatistic();
        statisticService.setNotMaskStatistic(assessmentConfigMap);

        dataPreparationService.start();

        for (MaskItem method : methods) {
            method.start(controllerDB);
        }

        List<TableData> maskedTables = exportTables(sourceTables);
        if (!maskedTables.isEmpty()) {
            tableInfoService.loadCsvData(maskedTables);
        }

        backToOriginDatabase();

        long end = System.currentTimeMillis();
        NumberFormat formatter = new DecimalFormat("#0.00");
        return formatter.format((end - start) / 1000d).replace(",", ".");
    }

    private void prepareTemporaryDatabase(List<TableData> sourceTables) throws Exception {
        oldConnection = controllerDB.getDatabase();
        String tempDatabase = "csv_mask_" + System.currentTimeMillis();

        controllerDB.execute("DROP DATABASE IF EXISTS " + tempDatabase + ";");
        controllerDB.execute(
                "CREATE DATABASE " + tempDatabase
                        + " OWNER " + controllerDB.getUsername() + ";");

        controllerDB.setNameDB(tempDatabase);
        controllerDB.disconnect();
        controllerDB.connect();
        tableInfoService.useDatabaseSource();

        for (TableData tableData : sourceTables) {
            createTableFromCsv(tableData);
        }
    }

    private void createTableFromCsv(TableData tableData) throws Exception {
        StringBuilder createSql = new StringBuilder("CREATE TABLE ")
                .append(tableData.getName())
                .append(" (");

        List<String> columns = tableData.getColumnNames();
        for (int i = 0; i < columns.size(); i++) {
            createSql.append(columns.get(i)).append(" TEXT");
            if (i < columns.size() - 1) {
                createSql.append(", ");
            }
        }
        createSql.append(");");
        controllerDB.execute(createSql.toString());

        String placeholders = String.join(", ", columns.stream().map(col -> "?").toList());
        String insertSql = "INSERT INTO " + tableData.getName() + " ("
                + String.join(", ", columns) + ") VALUES (" + placeholders + ");";

        for (int rowIndex = 0; rowIndex < tableData.getRows().size(); rowIndex++) {
            if (selectionService.hasCustomSelection(tableData.getName())
                    && !selectionService.isRowSelected(tableData.getName(), rowIndex)) {
                continue;
            }

            List<String> row = tableData.getRows().get(rowIndex);
            try (var statement = controllerDB.getPrepareStatement(insertSql)) {
                for (int i = 0; i < columns.size(); i++) {
                    statement.setString(i + 1, row.get(i));
                }
                statement.execute();
            }
        }
    }

    private List<TableData> exportTables(List<TableData> sourceTables) throws Exception {
        List<TableData> maskedTables = new ArrayList<>();
        for (TableData sourceTable : sourceTables) {
            String tableName = sourceTable.getName();
            List<String> columns = tableInfoService.getColumnNames(tableName);
            List<List<String>> rows = new ArrayList<>();

            String query = "SELECT * FROM " + tableName + ";";
            try (var rs = controllerDB.executeQuery(query)) {
                while (rs.next()) {
                    List<String> row = new ArrayList<>();
                    for (String column : columns) {
                        Object value = rs.getObject(column);
                        row.add(value != null ? value.toString() : null);
                    }
                    rows.add(row);
                }
            }

            TableData maskedTable = new TableData(tableName + "_masked", columns, rows);
            maskedTables.add(maskedTable);
            saveTempCsv(maskedTable);
        }
        return maskedTables;
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
