package ru.data.anonymization.tool.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Простая модель таблицы для хранения данных из CSV файлов.
 */
public class TableData {
    private final String name;
    private final List<String> columnNames = new ArrayList<>();
    private final List<List<String>> rows = new ArrayList<>();

    public TableData(String name, List<String> columnNames, List<List<String>> rows) {
        this.name = name;
        this.columnNames.addAll(columnNames);
        this.rows.addAll(rows);
    }

    public String getName() {
        return name;
    }

    public List<String> getColumnNames() {
        return Collections.unmodifiableList(columnNames);
    }

    public List<List<String>> getRows() {
        return Collections.unmodifiableList(rows);
    }
}
