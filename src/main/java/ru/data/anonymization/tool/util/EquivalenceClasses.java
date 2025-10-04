package ru.data.anonymization.tool.util;

import java.util.*;

public class EquivalenceClasses {
    public static Map<String[], Integer> execute(List<String[]> source) {
        Map<String[], Integer> equivalenceClasses = new HashMap<>();

        for (String[] row : source) {
            String[] equivalenceClass = null;
            for (String[] item : equivalenceClasses.keySet()) {
                if (Arrays.deepEquals(item, row)) {
                    equivalenceClass = item;
                    break;
                }
            }
            if (Objects.isNull(equivalenceClass)) {
                equivalenceClass = row;
            }
            equivalenceClasses.put(equivalenceClass, equivalenceClasses.getOrDefault(equivalenceClass, 0) + 1);
        }

        return equivalenceClasses;
    }
}
