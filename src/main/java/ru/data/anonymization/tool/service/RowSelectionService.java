package ru.data.anonymization.tool.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class RowSelectionService {

    private static final int MAX_PERCENTAGE = 100;
    private final TableInfoService tableInfoService;
    private final Map<String, Set<Integer>> selectionByTable = new ConcurrentHashMap<>();
    private final Map<String, Integer> percentageByTable = new HashMap<>();
    private final Random random = new Random();

    public void updateSelection(String tableName, int percentage) {
        if (tableName == null || percentage < 0) {
            return;
        }
        percentageByTable.put(tableName, percentage);
        int totalRows = tableInfoService.getTableSize(tableName);
        if (totalRows <= 0) {
            selectionByTable.remove(tableName);
            return;
        }

        if (percentage >= MAX_PERCENTAGE) {
            selectionByTable.put(tableName, IntStream.rangeClosed(1, totalRows).boxed().collect(Collectors.toSet()));
            return;
        }

        int required = (int) Math.round(totalRows * (percentage / 100d));
        required = Math.max(required, percentage > 0 ? 1 : 0);

        Set<Integer> selected = new HashSet<>();
        while (selected.size() < required) {
            int value = random.nextInt(totalRows) + 1;
            selected.add(value);
        }
        selectionByTable.put(tableName, selected);
    }

    public boolean shouldMask(String tableName, int rowIndex) {
        Set<Integer> selected = selectionByTable.get(tableName);
        if (selected == null || selected.isEmpty()) {
            return true;
        }
        return selected.contains(rowIndex);
    }

    public Set<Integer> getSelection(String tableName) {
        return selectionByTable.getOrDefault(tableName, Set.of());
    }

    public String getFirstColumnName(String tableName) {
        var columns = tableInfoService.getColumnNames(tableName);
        if (columns.isEmpty()) {
            return null;
        }
        return columns.get(0);
    }

    public int getSavedPercentage(String tableName) {
        return percentageByTable.getOrDefault(tableName, MAX_PERCENTAGE);
    }

    public void clear() {
        selectionByTable.clear();
        percentageByTable.clear();
    }
}
