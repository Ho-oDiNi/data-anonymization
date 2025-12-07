package ru.data.anonymization.tool.methods.options;

import ru.data.anonymization.tool.service.DatabaseConnectionService;
import ru.data.anonymization.tool.service.RowSelectionService;

import java.io.Serializable;
import java.util.List;

public interface MaskItem extends Serializable {

    void start(DatabaseConnectionService controllerDB, RowSelectionService rowSelectionService) throws Exception;

    String getTable();

    List<String> getColumn();
}
