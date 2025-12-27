package ru.data.anonymization.tool.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import ru.data.anonymization.tool.dto.SyntheticConfigDto;
import ru.data.anonymization.tool.dto.TableData;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SyntheticMethodService {

    private final TableInfoService tableInfoService;
    private final Map<String, SyntheticConfigDto> syntheticConfigMap = new HashMap<>();

    public SyntheticMethodService(TableInfoService tableInfoService) {
        this.tableInfoService = tableInfoService;
    }

    public void addConfig(String name, SyntheticConfigDto config) {
        syntheticConfigMap.put(name, config);
    }

    public boolean hasConfig(String name) {
        return syntheticConfigMap.containsKey(name);
    }

    public void removeConfig(String name) {
        syntheticConfigMap.remove(name);
    }

    public SyntheticConfigDto getConfig(String name) {
        return syntheticConfigMap.get(name);
    }

    public List<SyntheticConfigDto> getConfigs() {
        return new ArrayList<>(syntheticConfigMap.values());
    }

    public Set<String> getConfigNames() {
        return syntheticConfigMap.keySet();
    }

    public void setConfigMap(Map<String, SyntheticConfigDto> configMap) {
        syntheticConfigMap.clear();
        if (configMap != null) {
            syntheticConfigMap.putAll(configMap);
        }
    }

    public Map<String, SyntheticConfigDto> getConfigMap() {
        return syntheticConfigMap;
    }

    public String sendTableData(String tableName, String methodName)
            throws IOException, InterruptedException {
        TableData tableData = tableInfoService.getTableData(tableName);
        if (tableData == null) {
            throw new IllegalStateException("Не удалось получить данные таблицы");
        }

        Path scriptPath = resolveScriptPath(methodName);

        JSONObject payload = new JSONObject();
        payload.put("method", methodName);
        payload.put("config", new JSONObject());
        payload.put("table", tableData.getName());
        payload.put("columns", tableData.getColumnNames());

        JSONArray rows = new JSONArray();
        for (List<String> row : tableData.getRows()) {
            rows.put(new JSONArray(row));
        }
        payload.put("rows", rows);

        List<String> outputLines = runScript(payload.toString(), null, scriptPath);
        return extractMessage(outputLines);
    }

    public List<TableData> generateSyntheticTables(List<SyntheticConfigDto> configs)
            throws IOException, InterruptedException {
        List<TableData> syntheticTables = new ArrayList<>();

        for (SyntheticConfigDto config : configs) {
            TableData sourceTable = tableInfoService.getTableData(config.getTableName());
            if (sourceTable == null) {
                throw new IllegalStateException(
                        "Не удалось получить данные таблицы: " + config.getTableName()
                );
            }

            Path scriptPath = resolveScriptPath(config.getMethodName());

            JSONObject payload = new JSONObject();
            payload.put("method", Optional.ofNullable(config.getMethodName()).orElse(""));
            payload.put("table", sourceTable.getName());
            payload.put("columns", sourceTable.getColumnNames());

            JSONObject configJson = new JSONObject();
            configJson.put("n", config.getRowsCount());
            configJson.put("target", Optional.ofNullable(config.getTargetColumn()).orElse(""));
            configJson.put("name", Optional.ofNullable(config.getName()).orElse(""));
            payload.put("config", configJson);

            JSONArray rows = new JSONArray();
            for (List<String> row : sourceTable.getRows()) {
                rows.put(new JSONArray(row));
            }
            payload.put("rows", rows);

            List<String> output = runScript(payload.toString(), config, scriptPath);
            syntheticTables.add(extractTable(output, config));
        }

        return syntheticTables;
    }

    private List<String> runScript(String payload, SyntheticConfigDto config, Path scriptPath)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("python");
        command.add(scriptPath.toAbsolutePath().toString());

        if (config != null && config.getRowsCount() > 0) {
            command.add("--n");
            command.add(String.valueOf(config.getRowsCount()));
        }
        if (config != null && config.getTargetColumn() != null && !config.getTargetColumn().isBlank()) {
            command.add("--target");
            command.add(config.getTargetColumn());
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            writer.write(payload);
            writer.flush();
            process.getOutputStream().close();

            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String scriptOutput = String.join("\n", lines);
                throw new IllegalStateException(
                        "Скрипт завершился с кодом: " + exitCode
                                + (scriptOutput.isBlank() ? "" : "\nВывод скрипта:\n" + scriptOutput)
                );
            }
            return lines;
        }
    }

    private String extractMessage(List<String> outputLines) {
        for (int i = outputLines.size() - 1; i >= 0; i--) {
            String line = outputLines.get(i);
            if (line == null || line.isBlank()) {
                continue;
            }
            try {
                JSONObject response = new JSONObject(line);
                if (response.has("message")) {
                    return response.getString("message");
                }
                if (response.has("status")) {
                    return response.getString("status");
                }
                return line;
            } catch (Exception ignored) {
                return line;
            }
        }
        return "Ответ от скрипта не получен";
    }

    private TableData extractTable(List<String> outputLines, SyntheticConfigDto config) {
        for (int i = outputLines.size() - 1; i >= 0; i--) {
            String line = outputLines.get(i);
            if (line == null || line.isBlank()) {
                continue;
            }

            try {
                JSONObject response = new JSONObject(line);
                if (response.has("status") && response.getString("status").equalsIgnoreCase("ok")) {
                    JSONArray dataArray = response.optJSONArray("data_synth");
                    if (dataArray == null || dataArray.isEmpty()) {
                        throw new IllegalStateException("Скрипт не вернул синтетические данные");
                    }

                    List<String> columns = extractColumns(dataArray);
                    List<List<String>> rows = new ArrayList<>();
                    for (int idx = 0; idx < dataArray.length(); idx++) {
                        JSONObject rowObject = dataArray.getJSONObject(idx);
                        List<String> row = columns.stream()
                                .map(column -> rowObject.opt(column) == null
                                        ? null
                                        : String.valueOf(rowObject.get(column)))
                                .collect(Collectors.toList());
                        rows.add(row);
                    }

                    String tableName = Optional.ofNullable(config.getName())
                            .filter(name -> !name.isBlank())
                            .orElseGet(() -> config.getTableName() + "_synthetic");

                    return new TableData(tableName, columns, rows);
                }
            } catch (Exception ignored) {
                // Пробуем разобрать следующую строку
            }
        }

        throw new IllegalStateException("Не удалось получить результат синтеза данных");
    }

    private List<String> extractColumns(JSONArray dataArray) {
        Set<String> columns = new java.util.LinkedHashSet<>();
        for (int i = 0; i < dataArray.length(); i++) {
            JSONObject rowObject = dataArray.getJSONObject(i);
            columns.addAll(rowObject.keySet());
        }
        return new ArrayList<>(columns);
    }

    private Path resolveScriptPath(String methodName) {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("Не указан метод синтеза");
        }

        return switch (methodName.toLowerCase()) {
            case "bayesian network" -> Paths.get("scripts", "bayesian_network.py");
            case "tgan" -> Paths.get("scripts", "tgan.py");
            case "tvae" -> Paths.get("scripts", "tvae.py");
            case "ctgan" -> Paths.get("scripts", "ctgan.py");
            case "pategan" -> Paths.get("scripts", "pategan.py");
            default -> throw new IllegalArgumentException("Неизвестный метод синтеза: " + methodName);
        };
    }
}
