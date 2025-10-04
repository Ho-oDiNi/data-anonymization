package ru.data.anonymization.tool.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class DataPreparationDto implements Serializable {
    private String tableName;
    private String columnName;
    private String typeAttribute;
    private String dataType;
    private String preparationMethod;
}
