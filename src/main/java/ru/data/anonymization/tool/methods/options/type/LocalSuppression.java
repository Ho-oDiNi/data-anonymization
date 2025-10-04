package ru.data.anonymization.tool.methods.options.type;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.data.anonymization.tool.methods.options.MaskItem;
import ru.data.anonymization.tool.service.DatabaseConnectionService;
import ru.data.anonymization.tool.service.TableInfoService;

@Data
@NoArgsConstructor
public class LocalSuppression implements MaskItem {

    private TableInfoService tableInfoService;
    private String nameTable;
    private List<String> namesColumn;
    private int k;
    private int n;
    private String replacementValue;

    private String columnsRow;
    private String columnsRowCount;

    @Override
    public void start(DatabaseConnectionService controllerDB) throws Exception {
        Map<String, List<Object>> uniqueRecords = findUniqueRecords(controllerDB);
        switch (n) {
            case 1 -> deleteRecords(uniqueRecords, controllerDB);
            case 2 -> replaceWithNone(uniqueRecords, controllerDB);
            case 3 -> {
                var replacementValues = getReplacementValues(k, replacementValue, controllerDB);
                updateUniqueValues(controllerDB, uniqueRecords, replacementValues);
            }
            default -> System.out.println("Invalid suppression option.");
        }

    }

    @Override
    public String getTable() {
        return nameTable;
    }

    @Override
    public List<String> getColumn() {
        return namesColumn;
    }

    private Map<String, List<Object>> findUniqueRecords(DatabaseConnectionService controllerDB)
            throws
            SQLException {
        Map<String, List<Object>> uniqueRecords = new HashMap<>();

        String columnsPart = getColumnsNameAsString(namesColumn);
        String sql = "SELECT %s ,COUNT(*)  FROM %s GROUP BY %s HAVING COUNT(*) = 1;".formatted(
                columnsPart,
                nameTable,
                columnsPart
        );

        return getStringListMap(controllerDB, uniqueRecords, sql);

    }

    private Map<String, List<Object>> getStringListMap(
            DatabaseConnectionService controllerDB,
            Map<String, List<Object>> records,
            String sql) throws SQLException {
        try (PreparedStatement stmt = controllerDB.getPrepareStatement(sql)) {
            System.out.println(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    List<Object> values = new ArrayList<>();
                    for (String column : namesColumn) {
                        values.add(rs.getObject(column));
                    }
                    records.put(UUID.randomUUID().toString(), values);
                }
            }
            System.out.println("Found " + records.size() + " unique records.");
            return records;
        }
    }

    // n=1: Удаление записей
    private void deleteRecords(
            Map<String, List<Object>> uniqueRecords,
            DatabaseConnectionService controllerDB) throws SQLException {
        for (List<Object> values : uniqueRecords.values()) {
            String whereClause = buildWhereClause(values);
            String deleteSQL = "DELETE FROM " + nameTable + " WHERE " + whereClause;
            controllerDB.execute(deleteSQL);
        }
        System.out.println("Deleted unique records.");
    }

    // n=2: Замена уникальных значений на NULL
    private void replaceWithNone(
            Map<String, List<Object>> uniqueRecords,
            DatabaseConnectionService controllerDB)
            throws SQLException {
        for (List<Object> values : uniqueRecords.values()) {
            String whereClause = buildWhereClause(values);
            StringBuilder updateSQL = new StringBuilder("UPDATE " + nameTable + " SET ");
            for (int i = 0; i < namesColumn.size(); i++) {
                updateSQL.append(namesColumn.get(i)).append(" = NULL");
                if (i < namesColumn.size() - 1) {
                    updateSQL.append(", ");
                }
            }
            updateSQL.append(" WHERE ").append(whereClause);

            controllerDB.execute(updateSQL.toString());
        }
        System.out.println("Replaced unique values with NULL.");
    }

    // n=3: Сглаживание значений
    private Map<String, Object> getReplacementValues(
            int smoothingOption,
            String replacementValue,
            DatabaseConnectionService controllerDB)
            throws SQLException {

        Map<String, Object> replacementValues = new HashMap<>();

        if (smoothingOption == 1) {
            for (String column : namesColumn) {
                String attributeType = tableInfoService.getAttributeType(nameTable, column);
                if (attributeType.equals("integer") || attributeType.equals("real")) {
                    String sql =
                            "SELECT AVG(CAST(" + column + " AS REAL)) AS avg_val FROM "
                            + nameTable + " WHERE " + column + " IS NOT NULL";
                    try (ResultSet rs = controllerDB.executeQuery(sql)) {
                        if (rs.next()) {
                            replacementValues.put(column, rs.getDouble("avg_val"));
                        }
                    }
                } else if (attributeType.equals("date")) {
                    String sql =
                            """
                                            SELECT (TO_TIMESTAMP(AVG(EXTRACT(EPOCH FROM %s))))::DATE AS avg_date
                                            FROM %s
                                            WHERE %s IS NOT NULL;
                                    """.formatted(column, nameTable, column);
                    try (ResultSet rs = controllerDB.executeQuery(sql)) {
                        if (rs.next()) {
                            replacementValues.put(column, rs.getDate("avg_date"));
                        }
                    }
                }
            }
        } else if (smoothingOption == 2) { // Ввод вручную
            for (String column : namesColumn) {
                replacementValues.put(column, replacementValue);
            }
        }
        return replacementValues;
    }

    private void updateUniqueValues(
            DatabaseConnectionService controllerDB,
            Map<String, List<Object>> uniqueRecords,
            Map<String, Object> replacementValues) throws SQLException {
        for (List<Object> values : uniqueRecords.values()) {
            String whereClause = buildWhereClause(values);

            StringBuilder updateSQL = new StringBuilder("UPDATE " + nameTable + " SET ");
            int i = 0;
            int counterBadUpdates = 0;
            for (String column : namesColumn) {

                updateSQL.append(column).append(" = ");
                String replacement = replacementValues.get(column).toString();
                String attributeType = tableInfoService.getAttributeType(nameTable, column);
                switch (attributeType) {
                    case "integer" -> {
                        if (replacement.matches("^-?\\d+(\\.\\d+)?$")) {
                            updateSQL.append(Math.round(Double.parseDouble(replacement)));
                        } else {
                            counterBadUpdates++;
                        }
                    }
                    case "real" -> {
                        if (replacement.matches("^-?\\d+(\\.\\d+)?$")) {
                            updateSQL.append(Double.parseDouble(replacement));
                        } else {
                            counterBadUpdates++;
                        }
                    }
                    case "date" -> {
                        try {
                            LocalDate.parse(replacement);
                            updateSQL.append("'").append(replacement).append("'");
                        } catch (DateTimeParseException e) {
                            counterBadUpdates++;
                        }
                    }
                    default -> updateSQL.append("'").append(replacement).append("'");
                }
                if (i < namesColumn.size() - 1) {
                    updateSQL.append(", ");
                }
                i++;
            }
            updateSQL.append(" WHERE ").append(whereClause);
            if (counterBadUpdates == 0) {
                controllerDB.execute(updateSQL.toString());
            }
        }
        System.out.println("Smoothed unique values.");
    }

    // Вспомогательный метод для создания WHERE-условия
    private String buildWhereClause(List<Object> values) {
        StringBuilder where = new StringBuilder();
        for (int i = 0; i < namesColumn.size(); i++) {
            where.append(namesColumn.get(i));
            if (values.get(i) == null) {
                where.append(" IS NULL");
            } else {
                where.append(" = ");
                if (values.get(i) instanceof Number) {
                    where.append(values.get(i));
                } else {
                    where.append("'").append(values.get(i)).append("'");
                }
            }
            if (i < namesColumn.size() - 1) {
                where.append(" AND ");
            }
        }
        return where.toString();
    }

    private String getColumnsNameAsString(List<String> objects) {
        var stringBuilder = new StringBuilder();
        objects.forEach(object -> {
            stringBuilder.append(object);
            stringBuilder.append(", ");
        });
        return stringBuilder.substring(0, stringBuilder.lastIndexOf(","));
    }

}
