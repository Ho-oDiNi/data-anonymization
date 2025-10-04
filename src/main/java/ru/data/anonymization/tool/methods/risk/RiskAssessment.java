package ru.data.anonymization.tool.methods.risk;

import ru.data.anonymization.tool.util.Factorial;

import java.util.Map;
import java.util.stream.Collectors;

public class RiskAssessment {
    public static double calculateProsecutorMetricA(Map<String[], Integer> equivalenceClasses, long n, double threshold) {
        return equivalenceClasses
                .values()
                .stream()
                .mapToDouble((classSize) -> classSize * ((1.0 / classSize > threshold) ? 1 : 0))
                .sum() / n;
    }

    public static double calculateProsecutorMetricB(Map<String[], Integer> equivalenceClasses) {
        return equivalenceClasses
                .values()
                .stream()
                .mapToDouble((classSize) -> 1.0 / classSize)
                .max()
                .getAsDouble();
    }

    public static double calculateProsecutorMetricC(Map<String[], Integer> equivalenceClasses, long n) {
        return (double) equivalenceClasses.size() / n;
    }

    public static Map<String[], Double> calculateRiskWithoutIdentificationTable(Map<String[], Integer> equivalenceClasses, double proportion) {
        return equivalenceClasses
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, (entry) -> switch (entry.getValue()) {
                    case 1 -> getRiskForF1(proportion);
                    case 2 -> getRiskForF2(proportion);
                    case 3 -> getRiskForF3(proportion);
                    default -> getRiskForF4AndMore(entry.getValue(), proportion);
                }));
    }

    private static double getRiskForF1(double proportion) {
        return -Math.log(proportion) * proportion / (1 - proportion);
    }

    private static double getRiskForF2(double proportion) {
        double q = 1 - proportion;
        return (proportion / Math.pow(q, 2)) * (proportion * Math.log(proportion) + (q));
    }

    private static double getRiskForF3(double proportion) {
        double q = 1 - proportion;
        return (proportion / (2 * Math.pow(q, 3))) * (q * (3 * q - 2) - 2 * Math.pow(proportion, 2) * Math.log(proportion));
    }

    private static double getRiskForF4AndMore(Integer value, double proportion) {
        double sumResult = 1;
        for (int i = 1; i < 8; i++) {
            double divider = 1;
            for (int j = 1; j <= i; j++) {
                divider *= value + j;
            }
            sumResult += Factorial.calculate(i) * Math.pow(1 - proportion, i) / divider;
        }
        return sumResult * proportion / value;
    }

    public static double calculateGlobalRisk(Map<String[], Integer> equivalenceClasses, double proportion, long n) {
        return calculateRiskWithoutIdentificationTable(equivalenceClasses, proportion)
                .entrySet()
                .stream()
                .mapToDouble((entry) -> equivalenceClasses.get(entry.getKey()) * entry.getValue())
                .sum() / n;
    }
}
