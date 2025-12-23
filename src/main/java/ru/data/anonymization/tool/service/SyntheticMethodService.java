package ru.data.anonymization.tool.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
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
import java.util.List;

@Service
public class SyntheticMethodService {

    private final TableInfoService tableInfoService;
    private final Path scriptPath = Paths.get("scripts", "synthetic_receiver.py");

    public SyntheticMethodService(TableInfoService tableInfoService) {
        this.tableInfoService = tableInfoService;
    }

    public String sendTableData(String tableName, String methodName)
            throws IOException, InterruptedException {
        TableData tableData = tableInfoService.getTableData(tableName);
        if (tableData == null) {
            throw new IllegalStateException("Не удалось получить данные таблицы");
        }

        JSONObject payload = new JSONObject();
        payload.put("method", methodName);
        payload.put("table", tableData.getName());
        payload.put("columns", tableData.getColumnNames());

        JSONArray rows = new JSONArray();
        for (List<String> row : tableData.getRows()) {
            rows.put(new JSONArray(row));
        }
        payload.put("rows", rows);

        List<String> outputLines = runScript(payload.toString());
        return extractMessage(outputLines);
    }

    private List<String> runScript(String payload) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(
                "python",
                scriptPath.toAbsolutePath().toString()
        );
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
                throw new IllegalStateException("Скрипт завершился с кодом: " + exitCode);
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
}
