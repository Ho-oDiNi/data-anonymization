package ru.data.anonymization.tool.methods.options.type;

import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.data.anonymization.tool.methods.options.MaskItem;
import ru.data.anonymization.tool.service.DatabaseConnectionService;
import ru.data.anonymization.tool.service.RowSelectionService;

import java.sql.ResultSet;
import java.util.List;

@Data
@Slf4j
@NoArgsConstructor
public class GeneralizationValue implements MaskItem {

    private String dateType;
    private String nameTable;
    private String nameColumn;
    private String generalizationTable;
    private List<String> generalizationName;
    private List<?> minValue;
    private List<?> maxValue;
    private boolean isDate;
    private String instruct;

    @Override
    public String getTable() {
        return nameTable;
    }

    @Override
    public List<String> getColumn() {
        return List.of(nameColumn);
    }

    @Override
    public void start(DatabaseConnectionService controllerDB, RowSelectionService rowSelectionService) throws Exception {
        String isChangeNameColumn = "is_change_temp_column";
        String newColumn = "is_new_temp_column";
        int countGroup = 0;
        controllerDB.execute("ALTER TABLE " + nameTable + " ADD COLUMN " + isChangeNameColumn
                             + " TEXT");
        controllerDB.execute(
                "UPDATE " + nameTable + " SET " + isChangeNameColumn + " = " + nameColumn
                + "::TEXT");
        if (instruct.equals("default")) {
            controllerDB.execute("DROP TABLE IF EXISTS " + generalizationTable + ";");
            controllerDB.execute("CREATE TABLE IF NOT EXISTS " + generalizationTable
                                 + " (id SERIAL PRIMARY KEY, value text);");
        } else {
            if (isDate) {
                controllerDB.execute(
                        "ALTER TABLE " + nameTable + " ADD COLUMN " + newColumn + " DATE;"
                );
                controllerDB.execute(
                        "UPDATE " + nameTable + " SET " + newColumn + " = " + nameColumn
                );
            } else {
                controllerDB.execute("ALTER TABLE " + nameTable + " ADD COLUMN " + newColumn
                                     + " FLOAT;");
                controllerDB.execute(
                        "UPDATE " + nameTable + " SET " + newColumn + " = " + nameColumn
                        + ";");
            }
        }
        List<Object> objects = new ArrayList<>();
        for (int i = 0; i < generalizationName.size(); i++) {
            objects.add(i + 1);
            String notInIds = "AND %s NOT IN (%s);".formatted(
                    isChangeNameColumn,
                    objectsToString(objects)
            );
            if (instruct.equals("default")) {
                controllerDB.execute("INSERT INTO " + generalizationTable + " (value) VALUES ('"
                                     + generalizationName.get(i) + "');");
            } else {
                countGroup++;
            }
            if (isDate) {
                System.out.println("UPDATE " + nameTable +
                                   " SET " + isChangeNameColumn + "=" + (i + 1) +
                                   " WHERE " + nameColumn + ">'" + minValue.get(i) +
                                   "' AND " + nameColumn + "<='" + maxValue.get(i) + "'" + notInIds
                );
                controllerDB.execute(
                        "UPDATE " + nameTable +
                        " SET " + isChangeNameColumn + "=" + (i + 1) +
                        " WHERE " + nameColumn + ">'" + minValue.get(i) +
                        "' AND " + nameColumn + "<='" + maxValue.get(i) + "'" + notInIds
                );

            } else {
                var changeSql = "UPDATE " + nameTable +
                                " SET " + isChangeNameColumn + "=" + (i + 1) +
                                " WHERE " + nameColumn + ">'" + minValue.get(i) + "'" +
                                " AND " + nameColumn + "<='" + maxValue.get(i) + "'" + notInIds;
                controllerDB.execute(changeSql);
            }
        }
/*        String sql = "SELECT " + isChangeNameColumn + " FROM " + nameTable + ";";
        System.out.println(sql);
        try (var resultSet1 = controllerDB.executeQuery(sql)) {
            while (resultSet1.next()) {
                System.out.println(resultSet1.getObject(1));
            }
        }*/

        switch (instruct) {
            case "average" -> {
                for (int i = 1; i <= countGroup; i++) {
                    ResultSet resultSet;
                    if (dateType.equals("Date")) {
                        String sql =
                                """
                                                SELECT (TO_TIMESTAMP(AVG(EXTRACT(EPOCH FROM %s))))::DATE AS avg_date
                                                FROM %s
                                                WHERE %s IS NOT NULL
                                                AND %s LIKE '%d';
                                        """.formatted(
                                        nameColumn,
                                        nameTable,
                                        nameColumn,
                                        isChangeNameColumn,
                                        i
                                );
                        resultSet = controllerDB.executeQuery(sql);
                    } else {
                        resultSet = controllerDB.executeQuery(
                                "select avg(" + nameColumn + ")" + " FROM " + nameTable + " WHERE "
                                + nameColumn + " is not null and " + isChangeNameColumn + " LIKE '"
                                + i
                                + "';");
                    }

                    resultSet.next();
                    Object value = resultSet.getObject(1);
                    if (isDate) {
                        controllerDB.execute(
                                "UPDATE " + nameTable + " SET " + newColumn + "='" + value
                                + "' WHERE " + isChangeNameColumn + " LIKE '" + i + "';");
                    } else {
                        controllerDB.execute(
                                "UPDATE " + nameTable + " SET " + newColumn + "=" + value
                                + " WHERE " + isChangeNameColumn + " LIKE '" + i + "';");
                    }
                }
            }
            case "median" -> {
                for (int i = 1; i <= generalizationName.size(); i++) {
                    ResultSet resultSet = controllerDB.executeQuery(
                            "select  count(*) from " + nameTable + " WHERE " + nameColumn
                            + " IS NOT NULL and " + isChangeNameColumn + " LIKE '" + i + "';");
                    resultSet.next();
                    long size = resultSet.getLong(1);
                    long meddle = Math.round((float) size / 2) - 1;
                    Object value;

                    if ((size % 2) == 1) {
                        resultSet = controllerDB.executeQuery(
                                "SELECT " + nameColumn + " FROM " + nameTable + " WHERE "
                                + nameColumn + " IS NOT NULL and " + isChangeNameColumn + " LIKE '"
                                + i
                                + "' ORDER BY " + nameColumn + " OFFSET " + meddle + " LIMIT 1;");
                        resultSet.next();
                        value = resultSet.getObject(1);
                    } else {
                        resultSet = controllerDB.executeQuery(
                                "SELECT " + nameColumn + " FROM " + nameTable + " WHERE "
                                + nameColumn + " IS NOT NULL and " + isChangeNameColumn + " LIKE '"
                                + i
                                + "' ORDER BY " + nameColumn + " OFFSET " + meddle + " LIMIT 2;");
                        resultSet.next();
                        value = resultSet.getObject(1);
                        if (value instanceof Number number) {
                            resultSet.next();
                            value = (number.intValue() + resultSet.getLong(1)) / 2;
                        } else {
                            resultSet.next();
                            LocalDate secValue = resultSet.getDate(1).toLocalDate();
                            long daysBetween = ChronoUnit.DAYS.between(
                                    ((Date) value).toLocalDate(),
                                    secValue
                            );
                            value = ((Date) value).toLocalDate().plusDays(daysBetween / 2);
                        }

                    }

                    if (isDate) {
                        controllerDB.execute(
                                "UPDATE " + nameTable + " SET " + newColumn + "='" + value
                                + "' WHERE " + isChangeNameColumn + " LIKE '" + i + "';");
                    } else {
                        controllerDB.execute(
                                "UPDATE " + nameTable + " SET " + newColumn + "=" + value
                                + " WHERE " + isChangeNameColumn + " LIKE '" + i + "';");
                    }
                }
            }
            case "mode" -> {
                for (int i = 1; i <= generalizationName.size(); i++) {
                    ResultSet resultSet = controllerDB.executeQuery(
                            "select  mode() within group (order by " + nameColumn + ") from "
                            + nameTable + " WHERE " + isChangeNameColumn + " LIKE '" + i + "';");
                    resultSet.next();
                    Object value = resultSet.getObject(1);

                    if (isDate) {
                        controllerDB.execute(
                                "UPDATE " + nameTable + " SET " + newColumn + "='" + value
                                + "' WHERE " + isChangeNameColumn + " LIKE '" + i + "';");
                    } else {
                        controllerDB.execute(
                                "UPDATE " + nameTable + " SET " + newColumn + "=" + value
                                + " WHERE " + isChangeNameColumn + " LIKE '" + i + "';");
                    }
                }
            }
        }

        controllerDB.execute("ALTER TABLE " + nameTable + " DROP COLUMN " + nameColumn + ";");
        if (instruct.equals("default")) {
            controllerDB.execute(
                    "ALTER TABLE " + nameTable + " RENAME COLUMN " + isChangeNameColumn + " TO "
                    + nameColumn + ";");
        } else {
            controllerDB.execute(
                    "ALTER TABLE " + nameTable + " DROP COLUMN " + isChangeNameColumn + ";");
            controllerDB.execute(
                    "ALTER TABLE " + nameTable + " RENAME COLUMN " + newColumn + " TO " + nameColumn
                    + ";");
        }

    }

    private String objectsToString(List<Object> objects) {
        var stringBuilder = new StringBuilder();
        objects.forEach(object -> {
            stringBuilder.append("'");
            stringBuilder.append(object);
            stringBuilder.append("'");
            stringBuilder.append(", ");
        });
        return stringBuilder.substring(0, stringBuilder.lastIndexOf(","));
    }

}
