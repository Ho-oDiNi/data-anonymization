package ru.data.anonymization.tool.methods.options.type;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.data.anonymization.tool.methods.options.MaskItem;
import ru.data.anonymization.tool.service.DatabaseConnectionService;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteMethod implements MaskItem {

    private String nameTable;
    private List<String> nameColumns;

    @Override
    public void start(DatabaseConnectionService controllerDB) throws Exception {
        for (var column : nameColumns) {
            String sql = "ALTER TABLE %1$s DROP COLUMN %2$s;".formatted(nameTable, column);
            controllerDB.execute(sql);
        }
    }

    @Override
    public String getTable() {
        return nameTable;
    }

    @Override
    public List<String> getColumn() {
        return nameColumns;
    }

}
