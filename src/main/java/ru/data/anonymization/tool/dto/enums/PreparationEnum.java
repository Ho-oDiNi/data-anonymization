package ru.data.anonymization.tool.dto.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Getter
@RequiredArgsConstructor
public enum PreparationEnum {
    AVERAGE("average"),
    MEDIAN("median"),
    MODE("mode");

    final String name;
    private static final Map<String,PreparationEnum> map;

    static {
        map = new HashMap<>();
        for (PreparationEnum v : PreparationEnum.values()) {
            map.put(v.name, v);
        }
    }

    public static PreparationEnum findByName(String name) {
        return map.get(name);
    }
}
