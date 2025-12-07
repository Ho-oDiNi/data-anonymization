package ru.data.anonymization.tool.methods.options.type;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.data.anonymization.tool.methods.options.MaskItem;
import ru.data.anonymization.tool.methods.options.type.ValueVariance.DataType;
import ru.data.anonymization.tool.service.DatabaseConnectionService;
import ru.data.anonymization.tool.service.RowSelectionService;
import ru.data.anonymization.tool.service.TableInfoService;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Slf4j
public class SelectionMethod implements MaskItem {

    private String nameTable;
    private String nameColumn;
    private int percent;

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
        log.info("TUT");
        List<Object> objects = new ArrayList<>();
        String sql = "SELECT %1$s FROM %2$s;".formatted(nameColumn, nameTable);
        var resultSet = controllerDB.executeQuery(sql);
        try (resultSet) {
            while (resultSet.next()) {
                Object object = resultSet.getObject(1);
                if (object != null) {
                    objects.add(object);
                }
            }
        }

        Collections.shuffle(objects);
        int newSize = (int) Math.round ((objects.size() * (double) percent) / 100);
        List<Object> selectionObject = new ArrayList<>();
        for (int i = 0; i < newSize; i++) {
            selectionObject.add(objects.get(i));
        }
        sql = "DELETE FROM %1$s WHERE %2$s NOT IN (%3$S);".formatted(
                nameTable,
                nameColumn,
                objectsToString(selectionObject)
        );
        controllerDB.execute(sql);
    }

    private String objectsToString(List<Object> objects) {
        var stringBuilder = new StringBuilder();
        objects.forEach(object -> {
            stringBuilder.append(object);
            stringBuilder.append(", ");
        });
        return stringBuilder.substring(0, stringBuilder.lastIndexOf(","));
    }

}
