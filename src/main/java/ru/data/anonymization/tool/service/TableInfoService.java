package ru.data.anonymization.tool.service;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.Callback;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.data.anonymization.tool.dto.AttributeTypeDto;
import ru.data.anonymization.tool.dto.TableData;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class TableInfoService {

    public static final int PAGE_SIZE = 500;

    private enum DataSourceType {
        NONE,
        DATABASE,
        CSV
    }

    private final DatabaseConnectionService connection;
    private final Map<String, TableData> csvTables = new ConcurrentHashMap<>();
    private DataSourceType dataSourceType = DataSourceType.NONE;

    public void useDatabaseSource() {
        dataSourceType = DataSourceType.DATABASE;
        csvTables.clear();
    }

    public void loadCsvData(TableData tableData) {
        System.out.println("[DEBUG] TableInfoService.loadCsvData: name=" + tableData.getName()
                + ", rows=" + tableData.getRows().size()
                + ", columns=" + tableData.getColumnNames().size());

        loadCsvData(List.of(tableData));
    }

    public void loadCsvData(List<TableData> tables) {
        dataSourceType = DataSourceType.CSV;
        csvTables.clear();
        tables.forEach(table -> csvTables.put(table.getName(), table));
    }

    public Optional<TableData> getCsvTable(String name) {
        return Optional.ofNullable(csvTables.get(name));
    }

    public boolean hasData() {
        return dataSourceType != DataSourceType.NONE && !getTables().isEmpty();
    }

    public boolean isDatabaseSourceActive() {
        return dataSourceType == DataSourceType.DATABASE;
    }

    public boolean isCsvSourceActive() {
        return dataSourceType == DataSourceType.CSV;
    }

    public List<String> getTables() {
        List<String> list = new ArrayList<>();
        if (dataSourceType == DataSourceType.CSV) {
            list.addAll(csvTables.keySet());
            return list;
        }
        if (dataSourceType == DataSourceType.NONE || !connection.isConnected()) {
            return list;
        }
        try {
            ResultSet resultSet = connection.executeQuery(
                    "SELECT tablename FROM pg_catalog.pg_tables where schemaname = 'public';");
            while (resultSet.next()) {
                list.add(resultSet.getString(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public TableView<ObservableList<String>> buildData(String nameTable, int page) {
        System.out.println("[DEBUG] buildData(" + nameTable + ", page=" + page + "), dataSourceType=" + dataSourceType);
        page--;
        ObservableList<ObservableList<String>> data = FXCollections.observableArrayList();
        TableView<ObservableList<String>> tableview = new TableView<>();
        tableview.prefHeight(1000);
        if (nameTable == null) {
            return tableview;
        }
        if (dataSourceType == DataSourceType.CSV) {
            fillTableFromCsv(nameTable, tableview, data, page);
            return tableview;
        }

        if (dataSourceType == DataSourceType.NONE || !connection.isConnected()) {
            return tableview;
        }

        List<String> columnNames = getColumnNames(nameTable);
        if (columnNames.isEmpty()) {
            return tableview;
        }
        String firstColumn = columnNames.get(0);
        try {
            String SQL =
                    "SELECT * from " + nameTable + " ORDER BY " + firstColumn + " OFFSET " + (page
                                                                                              * PAGE_SIZE)
                    + " LIMIT " + PAGE_SIZE;
            ResultSet rs = connection.executeQuery(SQL);
            addColumns(rs, tableview);
            fillRows(rs, data);
            tableview.setItems(data);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error on Building Data");
        }
        return tableview;
    }

    public int getTableSize(String nameTable) {
        int size = 0;
        if (nameTable == null) {
            return size;
        }
        if (dataSourceType == DataSourceType.CSV) {
            TableData tableData = csvTables.get(nameTable);
            if (tableData != null) {
                return tableData.getRows().size();
            }
            return size;
        }
        if (dataSourceType == DataSourceType.NONE || !connection.isConnected()) {
            return size;
        }
        try {
            String SQL = "SELECT count(*) from " + nameTable;
            ResultSet rs = connection.executeQuery(SQL);
            rs.next();
            size = rs.getInt(1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return size;
    }

    // Лист имен колонок
    public List<String> getColumnNames(String tableName) {
        List<String> columns = new ArrayList<>();
        if (tableName == null) {
            return columns;
        }
        if (dataSourceType == DataSourceType.CSV) {
            TableData tableData = csvTables.get(tableName);
            if (tableData != null) {
                columns.addAll(tableData.getColumnNames());
            }
            return columns;
        }
        if (dataSourceType == DataSourceType.NONE || !connection.isConnected()) {
            return columns;
        }

        try {
            ResultSet resultSet = connection.executeQuery(
                    "SELECT * FROM " + tableName + " LIMIT 0;"); // Тут прописать столбцы
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                columns.add(metaData.getColumnName(i));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return columns;
    }

    public List<String> getTableNames() {
        List<String> tables = new ArrayList<>();
        if (dataSourceType == DataSourceType.CSV) {
            tables.addAll(csvTables.keySet());
            return tables;
        }
        if (dataSourceType == DataSourceType.NONE || !connection.isConnected()) {
            return tables;
        }

        try {
            ResultSet resultSet = connection.executeQuery(
                    "SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE';");
            while (resultSet.next()) {
                tables.add(resultSet.getString(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return tables;
    }

    public List<String[]> getTableLikeList(String table, List<String> column) {
        List<String[]> source = new ArrayList<>();
        ArrayList<String> row;

        if (table == null || column == null || column.isEmpty()) {
            return source;
        }

        AttributeTypeDto attributeTypeDto = new AttributeTypeDto();
        if (dataSourceType == DataSourceType.CSV) {
            TableData tableData = csvTables.get(table);
            if (tableData == null) {
                return source;
            }
            for (List<String> csvRow : tableData.getRows()) {
                row = new ArrayList<>();
                for (String col : column) {
                    int columnIndex = tableData.getColumnNames().indexOf(col);
                    if (columnIndex >= 0 && columnIndex < csvRow.size()) {
                        String value = csvRow.get(columnIndex);
                        row.add(Optional.ofNullable(value).orElse("NULL"));
                    }
                }
                source.add(row.toArray(new String[0]));
            }
            return source;
        }

        if (dataSourceType == DataSourceType.NONE || !connection.isConnected()) {
            return source;
        }
        try {
            for (String col : column) {
                attributeTypeDto.setTable(table);
                attributeTypeDto.setColumn(col);
            }

            ResultSet resultSet = connection.executeQuery("SELECT * FROM " + table + ";");
            while (resultSet.next()) {
                row = new ArrayList<>();

                for (String col : column) {
                    attributeTypeDto.setTable(table);
                    attributeTypeDto.setColumn(col);

                    Object value = resultSet.getObject(col);
                    if (value != null) {
                        row.add(value.toString());
                    } else {
                        row.add("NULL");
                    }
                }
                source.add(row.toArray(new String[0]));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return source;
    }

    public String getAttributeType(String tableName, String columnName) {
        if (dataSourceType != DataSourceType.DATABASE || !connection.isConnected()) {
            return "Error";
        }
        var queryToGetDataType = """
                SELECT  data_type
                FROM information_schema.columns
                WHERE table_name = ? AND column_name = ?;
                """;
        try {
            var prepareStatement = connection.getPrepareStatement(queryToGetDataType);
            prepareStatement.setString(1, tableName);
            prepareStatement.setString(2, columnName);
            var res = prepareStatement.executeQuery();
             res.next();
             return res.getString(1);
        } catch (SQLException e) {
            System.out.println(e.getLocalizedMessage());
            return "Error";
        }

    }

    private void fillTableFromCsv(String nameTable, TableView<ObservableList<String>> tableview,
                                  ObservableList<ObservableList<String>> data, int page) {
        TableData tableData = csvTables.get(nameTable);
        if (tableData == null) {
            return;
        }
        for (int i = 0; i < tableData.getColumnNames().size(); i++) {
            final int columnIndex = i;
            TableColumn<ObservableList<String>, String> col = new TableColumn<>(
                    tableData.getColumnNames().get(i)
            );
            col.setReorderable(false);
            col.setCellValueFactory((Callback<TableColumn.CellDataFeatures<ObservableList<String>, String>, ObservableValue<String>>
                    ) param -> {
                Object elem = param.getValue().get(columnIndex);
                if (elem != null) {
                    return new SimpleStringProperty(param.getValue().get(columnIndex).toString());
                }
                return null;
            });

            tableview.getColumns().addAll(col);
        }

        int startIndex = Math.max(page, 0) * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, tableData.getRows().size());
        for (List<String> rowData : tableData.getRows().subList(Math.min(startIndex, tableData.getRows().size()), endIndex)) {
            ObservableList<String> row = FXCollections.observableArrayList(rowData);
            data.add(row);
        }
        tableview.setItems(data);
    }

    private void addColumns(ResultSet rs, TableView<ObservableList<String>> tableview) throws SQLException {
        for (int i = 0; i < rs.getMetaData().getColumnCount(); i++) {
            final int j = i;
            TableColumn<ObservableList<String>, String> col = new TableColumn<>(
                    rs.getMetaData().getColumnName(i + 1)
            );
            col.setReorderable(false);
            col.setCellValueFactory((Callback<TableColumn.CellDataFeatures<ObservableList<String>, String>, ObservableValue<String>>
                    ) param -> {
                Object elem = param.getValue().get(j);
                if (elem != null) {
                    return new SimpleStringProperty(param.getValue().get(j).toString());
                }
                return null;
            });

            tableview.getColumns().addAll(col);
        }
    }

    private void fillRows(ResultSet rs, ObservableList<ObservableList<String>> data) throws SQLException {
        while (rs.next()) {
            ObservableList<String> row = FXCollections.observableArrayList();
            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                row.add(rs.getString(i));
            }
            data.add(row);

        }
    }

}
