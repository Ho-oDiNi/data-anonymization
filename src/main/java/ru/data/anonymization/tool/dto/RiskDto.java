package ru.data.anonymization.tool.dto;

import lombok.Data;

@Data
public class RiskDto {
    private String name;
    private double proportion;
    private double result;
}
