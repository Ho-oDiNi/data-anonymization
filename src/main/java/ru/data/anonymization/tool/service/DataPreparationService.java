package ru.data.anonymization.tool.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;
import ru.data.anonymization.tool.dto.DataPreparationDto;
import ru.data.anonymization.tool.dto.enums.PreparationEnum;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DataPreparationService {

    private final DatabaseConnectionService controllerDB;

    @Getter
    @Setter
    private Map<String, DataPreparationDto> preparationMap = new HashMap<>();

    public void addPreparation(String name, DataPreparationDto method) {
        preparationMap.put(name, method);
    }

    public void removeMethod(String name) {
        preparationMap.remove(name);
    }

    public boolean isContainsKey(String name) {
        return preparationMap.containsKey(name);
    }

    public DataPreparationDto getPreparation(String name) {
        return preparationMap.get(name);
    }

    public List<DataPreparationDto> getPreparationMethods() {
        return new ArrayList<>(preparationMap.values());
    }

    public void start() throws Exception {
        List<DataPreparationDto> preparationMethods = new ArrayList<>(preparationMap.values());

        if (preparationMethods.isEmpty()) return;

        for (DataPreparationDto preparationDto : preparationMethods) {
            if (preparationDto.getPreparationMethod().equals("none")) continue;
            switch (PreparationEnum.findByName(preparationDto.getPreparationMethod())) {
                case AVERAGE -> average(preparationDto.getTableName(), preparationDto.getColumnName());
                case MEDIAN -> median(preparationDto.getTableName(), preparationDto.getColumnName());
                case MODE -> mode(preparationDto.getTableName(), preparationDto.getColumnName());
            }
        }
    }

    private void average(String table, String column) throws Exception {
        ResultSet resultSet = controllerDB.executeQuery("select avg(" + column + ")" + " FROM " + table + ";");
        resultSet.next();
        Object value = resultSet.getObject(1);
        controllerDB.execute("UPDATE " + table + " SET " + column + "=" + value + " WHERE " + column + " is null;");
    }

    private void median(String table, String column) throws Exception {
        ResultSet resultSet = controllerDB.executeQuery("select  count(*) from " + table + " WHERE " + column + " IS NOT NULL;");
        resultSet.next();
        long size = resultSet.getLong(1);
        long meddle = (size / 2) - 1;
        long value;

        if ((size % 2) == 1) {
            resultSet = controllerDB.executeQuery("SELECT " + column + " FROM " + table + " WHERE " + column + " IS NOT NULL ORDER BY " + column + " OFFSET " + meddle + " LIMIT 1;");
            resultSet.next();
            value = resultSet.getLong(1);
        } else {
            resultSet = controllerDB.executeQuery("SELECT " + column + " FROM " + table + " WHERE " + column + " IS NOT NULL ORDER BY " + column + " OFFSET " + meddle + " LIMIT 2;");
            resultSet.next();
            value = resultSet.getLong(1);
            resultSet.next();
            value = (value + resultSet.getLong(1)) / 2;
        }

        controllerDB.execute("UPDATE " + table + " SET " + column + "=" + value + " WHERE " + column + " is null;");
    }

    private void mode(String table, String column) throws Exception {
        ResultSet resultSet = controllerDB.executeQuery("select  mode() within group (order by " + column + ") from " + table + ";");
        resultSet.next();
        controllerDB.execute("UPDATE " + table + " SET " + column + "=" + resultSet.getObject(1) + " WHERE " + column + " is null;");
    }
}
