package ru.data.anonymization.tool.service;

import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.data.anonymization.tool.methods.options.type.*;
import ru.data.anonymization.tool.dto.DataPreparationDto;
import ru.data.anonymization.tool.dto.enums.RiskEnum;
import ru.data.anonymization.tool.dto.RiskDto;
import ru.data.anonymization.tool.dto.StatisticDto;
import ru.data.anonymization.tool.dto.StatisticResponseDto;
import ru.data.anonymization.tool.util.EquivalenceClasses;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static ru.data.anonymization.tool.methods.risk.RiskAssessment.*;


@Data
@Service
@RequiredArgsConstructor
public class StatisticService {

    private final DataPreparationService dataPreparationService;
    private final DatabaseConnectionService controllerDB;
    private final TableInfoService tableInfoService;
    @Getter
    private List<RiskDto> riskMethods = new ArrayList<>();

    private String table;
    private String column;

    private int countExtraStatic;

    private List<String> methodsForStatistic;
    private List<String> methodsForStatisticExtra;

    private List<StatisticDto> statisticNotMask;
    private List<StatisticDto> statisticMask;

    private StatisticResponseDto result;

    {
        methodsForStatistic = new ArrayList<>(
                Arrays.asList(
                        GeneralizationString.class.getSimpleName(),
                        GeneralizationValue.class.getSimpleName(),
                        MicroAggregation.class.getSimpleName(),
                        MicroAggregationBySingleAxis.class.getSimpleName(),
                        ValueReplacementByPattern.class.getSimpleName(),
                        ValueReplacementFromFile.class.getSimpleName()
                )
        );

        methodsForStatisticExtra = new ArrayList<>(
                Arrays.asList(
                        MicroAggregation.class.getSimpleName(),
                        MicroAggregationBySingleAxis.class.getSimpleName()
                )
        );
    }

    public void resetStatistic() {
        result = null;
        countExtraStatic = 0;
        statisticNotMask = new ArrayList<>();
        statisticMask = new ArrayList<>();
    }

    public void setRisk(String methodName, double proportion) {
        RiskDto riskDto = new RiskDto();
        riskDto.setName(methodName);
        riskDto.setProportion(proportion);
        riskMethods.add(riskDto);
    }

    public void clearRisk() {
        riskMethods.clear();
    }

    public StatisticResponseDto getStatistic() {
        if (result != null || statisticMask == null) return result;

        double min = 0;
        double max = 0;
        double avg = 0;
        double RMSE = 0;
        double MSE = 0;
        double MD = 0;
        double Shannon = 0;

        for (int i = 0; i < statisticMask.size(); i++) {
            min += calculatePercent(statisticMask.get(i).getMin(), statisticNotMask.get(i).getMin());
            max += calculatePercent(statisticMask.get(i).getMax(), statisticNotMask.get(i).getMax());
            avg += calculatePercent(statisticMask.get(i).getAvg(), statisticNotMask.get(i).getAvg());
            RMSE += calculatePercent(statisticMask.get(i).getRMSE(), statisticNotMask.get(i).getRMSE());
            MSE += calculatePercent(statisticMask.get(i).getMSE(), statisticNotMask.get(i).getMSE());
            MD += calculatePercent(statisticMask.get(i).getMD(), statisticNotMask.get(i).getMD());
            Shannon += calculatePercent(statisticMask.get(i).getShannon(), statisticNotMask.get(i).getShannon());
        }

        StatisticResponseDto.StatisticResponseDtoBuilder responseDto = StatisticResponseDto.builder();

        if (countExtraStatic != 0) {
            responseDto
                    .min(BigDecimal.valueOf(min / countExtraStatic))
                    .max(BigDecimal.valueOf(max / countExtraStatic))
                    .avg(BigDecimal.valueOf(avg / countExtraStatic))
                    .RMSE(BigDecimal.valueOf(RMSE / countExtraStatic))
                    .MSE(BigDecimal.valueOf(MSE / countExtraStatic))
                    .MD(BigDecimal.valueOf(MD / countExtraStatic));
        } else {
            responseDto
                    .min(null)
                    .max(null)
                    .avg(null)
                    .RMSE(null)
                    .MSE(null)
                    .MD(null);
        }
        result = responseDto
                //.Shannon(BigDecimal.valueOf(Shannon / statisticMask.size()))
                .risk(riskMethods)
                .build();

        return result;
    }

    public void setNotMaskStatistic(Map<String, List<String>> assessmentConfigMap) {
        List<DataPreparationDto> preparationList = dataPreparationService.getPreparationMethods();

        preparationList.forEach(attribute -> {
            List<String> assessmentList = assessmentConfigMap.get(attribute.getTableName());
            if (assessmentList != null && assessmentList.contains(attribute.getColumnName())) {
                statisticNotMask.add(getStatic(attribute, false));
            }
        });
    }

    public void setMaskStatistic(Map<String, List<String>> assessmentConfigMap) {
        List<DataPreparationDto> preparationList = dataPreparationService.getPreparationMethods();

        preparationList.forEach(attribute -> {
            List<String> assessmentList = assessmentConfigMap.get(attribute.getTableName());
            if (assessmentList != null && assessmentList.contains(attribute.getColumnName())) {
                statisticMask.add(getStatic(attribute, true));
            }
        });
    }

    public void calculateRisk(Map<String, List<String>> riskConfigMap) {
        if (riskMethods.isEmpty()) return;
        double riskA = 0;
        double riskB = 0;
        double riskC = 0;
        double riskGlobal = 0;

        int n = 0;

        for (String table : riskConfigMap.keySet()) {
            List<String[]> tableList = tableInfoService.getTableLikeList(table, riskConfigMap.get(table));
            long size = tableList.size();
            if (tableList.isEmpty()) continue;
            if (tableList.get(0).length == 0) continue;

            Map<String[], Integer> equivalence = EquivalenceClasses.execute(tableList);
            for (RiskDto riskMethod : riskMethods) {
                switch (RiskEnum.findByName(riskMethod.getName())) {
                    case PROSECUTOR_METRIC_A ->
                            riskA += calculateProsecutorMetricA(equivalence, size, riskMethod.getProportion());
                    case PROSECUTOR_METRIC_B -> riskB += calculateProsecutorMetricB(equivalence);
                    case PROSECUTOR_METRIC_C -> riskC += calculateProsecutorMetricC(equivalence, size);
                    case GLOBAL_RISK -> riskGlobal += calculateGlobalRisk(equivalence, riskMethod.getProportion(), size);
                }
            }
            n++;
        }
        if (n != 0){
            for (RiskDto riskMethod : riskMethods) {
                if (riskMethod.getName().equals("ProsecutorMetricA")) {
                    riskMethod.setResult(riskA/n);
                }
                if (riskMethod.getName().equals("ProsecutorMetricB")) {
                    riskMethod.setResult(riskB/n);
                }
                if (riskMethod.getName().equals("ProsecutorMetricC")) {
                    riskMethod.setResult(riskC/n);
                }
                if (riskMethod.getName().equals("GlobalRisk")) {
                    riskMethod.setResult(riskGlobal/n);
                }
            }
        }
    }

    private StatisticDto getStatic(DataPreparationDto dto, boolean isMask) {
        table = dto.getTableName();
        column = dto.getColumnName();

        StatisticDto.StatisticDtoBuilder statisticBuilder = StatisticDto
                .builder()
                .table(table)
                .column(column)
                .Shannon(getShannon());
        if (dto.getDataType().equals("Integer") || dto.getDataType().equals("Float")) {
            if (isMask) countExtraStatic++;
            statisticBuilder
                    .min(getMin())
                    .max(getMax())
                    .avg(getAverage())
                    .avg(getAverage())
                    .RMSE(getRMSE())
                    .MSE(getMSE())
                    .MD(getMD());
        }
        return statisticBuilder.build();
    }

    private double getMin() {
        ResultSet resultSet;
        try {
            resultSet = controllerDB.executeQuery(
                    "SELECT min(" + column + ") " +
                            "FROM " + table + ";"
            );
            resultSet.next();
            return resultSet.getDouble(1);
        } catch (SQLException e) {
            System.out.println("Всё, конец!");
        }
        return 0;
    }

    private double getMax() {
        ResultSet resultSet;
        try {
            resultSet = controllerDB.executeQuery(
                    "SELECT max(" + column + ") " +
                            "FROM " + table + ";"
            );
            resultSet.next();
            return resultSet.getDouble(1);
        } catch (SQLException e) {
            System.out.println("Всё, конец!");
        }
        return 0;
    }

    private double getAverage() {
        ResultSet resultSet;
        try {
            resultSet = controllerDB.executeQuery(
                    "SELECT avg(" + column + ") " +
                            "FROM " + table + ";"
            );
            resultSet.next();
            return resultSet.getDouble(1);
        } catch (SQLException e) {
            System.out.println("Всё, конец!");
        }
        return 0;
    }

    private double getRMSE() {
        return Math.sqrt(getMSE());
    }

    private double getMSE() {
        ResultSet resultSet;
        try {
            double sumOfSquaredErrors = 0.0;
            resultSet = controllerDB.executeQuery(
                    "SELECT " + column +
                            " FROM " + table + ";"
            );

            while (resultSet.next()) {
                double value = resultSet.getDouble(1);
                sumOfSquaredErrors += Math.pow(value, 2);
            }

            return sumOfSquaredErrors / getCount();
        } catch (SQLException e) {
            System.out.println("Всё, конец!");
        }
        return 0;
    }

    private double getMD() {
        double avg = getAverage();
        ResultSet resultSet;
        try {
            double meanDeviation = 0.0;
            resultSet = controllerDB.executeQuery(
                    "SELECT " + column +
                            " FROM " + table + ";"
            );

            while (resultSet.next()) {
                double value = resultSet.getDouble(1);
                meanDeviation += Math.abs(value - avg);
            }

            return meanDeviation / getCount();
        } catch (SQLException e) {
            System.out.println("Всё, конец!");
        }
        return 0;
    }


    private double getShannon() {
        List<Integer> uniqueCount = getUniqueCount();
        int count = getCount();

        if (uniqueCount != null) {
            return uniqueCount.stream().mapToDouble(unq -> (double) unq / count).map(value -> Math.abs((value) * log2(value))).sum();
        }
        return 0;
    }


    private List<Integer> getUniqueCount() {
        List<Integer> list = new ArrayList<>();
        ResultSet resultSet;
        try {
            resultSet = controllerDB.executeQuery(
                    "SELECT count(*) " +
                            "FROM " + table + " group by " + column + ";"
            );
            while (resultSet.next()) {
                list.add(resultSet.getInt(1));
            }

            return list;
        } catch (SQLException e) {
            System.out.println("Всё, конец!");
        }
        return null;
    }

    private int getCount() {
        ResultSet resultSet;
        try {
            resultSet = controllerDB.executeQuery(
                    "SELECT count(" + column + ") " +
                            "FROM " + table + ";"
            );
            resultSet.next();
            return resultSet.getInt(1);
        } catch (SQLException e) {
            System.out.println("Всё, конец!");
        }
        return 0;
    }

    private double log2(double x) {
        return Math.log(x) / Math.log(2);
    }

    private double calculatePercent(double maskVal, double notMaskVal) {
        if (notMaskVal == 0) return 0;
        return Math.abs(1 - maskVal / notMaskVal) * 100;
    }
}
