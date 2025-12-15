package ru.data.anonymization.tool.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class SelectionService {

    private final Map<String, Set<Integer>> selectedRowsByTable = new ConcurrentHashMap<>();
    private final Map<String, Integer> selectedPercentByTable = new ConcurrentHashMap<>();

    public void selectRows(String tableName, int totalRows, int percent) {
        selectedPercentByTable.put(tableName, percent);

        if (percent >= 100 || totalRows <= 0) {
            selectedRowsByTable.remove(tableName);
            return;
        }

        int selectionSize = (int) Math.round((totalRows * (double) percent) / 100);
        List<Integer> allIndexes = IntStream.range(0, totalRows)
                                            .boxed()
                                            .collect(Collectors.toCollection(ArrayList::new));

        Collections.shuffle(allIndexes);

        Set<Integer> selectedIndexes = new HashSet<>(allIndexes.subList(0, selectionSize));
        selectedRowsByTable.put(tableName, selectedIndexes);
    }

    public boolean hasCustomSelection(String tableName) {
        return selectedRowsByTable.containsKey(tableName);
    }

    public boolean isRowSelected(String tableName, int rowIndex) {
        Set<Integer> selectedRows = selectedRowsByTable.get(tableName);
        if (selectedRows == null) {
            return true;
        }
        return selectedRows.contains(rowIndex);
    }

    public int getSelectionPercent(String tableName) {
        return selectedPercentByTable.getOrDefault(tableName, 100);
    }

    public Set<Integer> getSelectedRows(String tableName) {
        return selectedRowsByTable.getOrDefault(tableName, Collections.emptySet());
    }

    public void clearAll() {
        selectedRowsByTable.clear();
        selectedPercentByTable.clear();
    }
}

