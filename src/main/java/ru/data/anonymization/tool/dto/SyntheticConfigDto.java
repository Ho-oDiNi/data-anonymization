package ru.data.anonymization.tool.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class SyntheticConfigDto implements Serializable {
    private String name;
    private String methodName;
    private String tableName;
    private int rowsCount;
    private String targetColumn;
}

