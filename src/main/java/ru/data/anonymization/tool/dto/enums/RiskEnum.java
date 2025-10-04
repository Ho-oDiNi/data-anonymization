package ru.data.anonymization.tool.dto.enums;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public enum RiskEnum {
    PROSECUTOR_METRIC_A("ProsecutorMetricA"),
    PROSECUTOR_METRIC_B("ProsecutorMetricB"),
    PROSECUTOR_METRIC_C("ProsecutorMetricC"),
    GLOBAL_RISK("GlobalRisk");

    final String name;

    RiskEnum(String name) {
        this.name = name;
    }

    private static final Map<String,RiskEnum> map;
    static {
        map = new HashMap<>();
        for (RiskEnum v : RiskEnum.values()) {
            map.put(v.name, v);
        }
    }

    public static RiskEnum findByName(String name) {
        return map.get(name);
    }
}
