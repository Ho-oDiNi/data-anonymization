package ru.data.anonymization.tool.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class StatisticResponseDto {
    private BigDecimal min;
    private BigDecimal max;
    private BigDecimal avg;
    private BigDecimal RMSE;
    private BigDecimal MSE;
    private BigDecimal MD;
    private BigDecimal Shannon;
    private List<RiskDto> risk;
}
