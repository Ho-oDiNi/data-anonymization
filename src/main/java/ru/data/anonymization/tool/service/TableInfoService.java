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

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TableInfoService {

    private final DatabaseConnectionService connection;

    public List<String> getTables() {
        List<String> list = new ArrayList<>();
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

    public TableView buildData(String nameTable, int page) {
        page--;
        ObservableList<ObservableList> data = FXCollections.observableArrayList();
        TableView tableview = new TableView();
        tableview.prefHeight(1000);
        String firstColumn = getColumnNames(nameTable).get(0);
        try {
            String SQL =
                    "SELECT * from " + nameTable + " ORDER BY " + firstColumn + " OFFSET " + (page
                                                                                              * 500)
                    + " LIMIT 500";
            ResultSet rs = connection.executeQuery(SQL);
            for (int i = 0; i < rs.getMetaData().getColumnCount(); i++) {
                final int j = i;
                TableColumn col = new TableColumn(rs.getMetaData().getColumnName(i + 1));
                col.setReorderable(false);
                col.setCellValueFactory((Callback<TableColumn.CellDataFeatures<ObservableList, String>, ObservableValue<String>>) param -> {
                    Object elem = param.getValue().get(j);
                    if (elem != null) {
                        return new SimpleStringProperty(param.getValue().get(j).toString());
                    }
                    return null;
                });

                tableview.getColumns().addAll(col);
            }
            while (rs.next()) {
                ObservableList<String> row = FXCollections.observableArrayList();
                for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                    row.add(rs.getString(i));
                }
                data.add(row);

            }
            tableview.setItems(data);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error on Building Data");
        }
        return tableview;
    }

    public int getTableSize(String nameTable) {
        int size = 0;
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
        ResultSet resultSet;
        List<String> columns = new ArrayList<>();

        try {
            resultSet = connection.executeQuery(
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
        ResultSet resultSet;
        List<String> tables = new ArrayList<>();

        try {
            resultSet = connection.executeQuery(
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

        AttributeTypeDto attributeTypeDto = new AttributeTypeDto();
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

}
