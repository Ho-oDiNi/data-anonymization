package ru.data.anonymization.tool.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;
import ru.data.anonymization.tool.methods.options.MaskItem;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DepersonalizationService {

    private final DatabaseConnectionService controllerDB;
    private final DataPreparationService dataPreparationService;
    private final StatisticService statisticService;

    @Getter
    @Setter
    private Map<String, MaskItem> methodsMap = new HashMap<>();

    private final Map<String, List<String>> riskConfigMap = new HashMap<>();
    private final Map<String, List<String>> assessmentConfigMap = new HashMap<>();

    private List<MaskItem> methods = new ArrayList<>();
    private String oldConnection = null;

    public void addRiskConfig(String key, List<String> columns) {
        riskConfigMap.put(key, columns);
    }

    public List<String> getRiskConfig(String key) {
        return riskConfigMap.get(key);
    }

    public void removeRiskConfig(String key) {
        riskConfigMap.remove(key);
    }

    public void addAssessmentConfig(String key, List<String> columns) {
        assessmentConfigMap.put(key, columns);
    }

    public void removeAssessmentConfig(String key) {
        assessmentConfigMap.remove(key);
    }

    public List<String> getAssessmentConfig(String key) {
        return assessmentConfigMap.get(key);
    }

    public void addMethod(String name, MaskItem method) {
        methodsMap.put(name, method);
    }

    public void removeMethod(String name) {
        methodsMap.remove(name);
    }

    public boolean isContainsKey(String name) {
        return methodsMap.containsKey(name);
    }

    public MaskItem getMethod(String name) {
        return methodsMap.get(name);
    }

    public List<String> getConfig() {
        List<String> tableMethods = new ArrayList<>();
        methodsMap.forEach((key, method) -> tableMethods.add(key));
        return tableMethods;
    }

    public String start() {
        methods = new ArrayList<>(methodsMap.values());

        if (methods.isEmpty()) {
            return null;
        }
        String time = "0";
        try {
            init();
            time = masking();
        } catch (Exception e) {
            System.out.println(e.getLocalizedMessage());
            System.out.println(e.getMessage());
            e.printStackTrace();
            controllerDB.disconnect();
        }
        return time;
    }

    private void init() throws Exception {
        oldConnection = controllerDB.getDatabase();
        String maskDB = "mask_" + controllerDB.getDatabase();
        var someREs = controllerDB.executeQuery(
                "SELECT pid, usename, datname, application_name, state \n"
                + "FROM pg_stat_activity \n"
                + "WHERE datname = 'mad';");
        while (someREs.next()) {
            System.out.println("PID: " + someREs.getInt("pid"));
            System.out.println("User: " + someREs.getString("usename"));
            System.out.println("Database: " + someREs.getString("datname"));
            System.out.println("Application: " + someREs.getString("application_name"));
            System.out.println("State: " + someREs.getString("state"));
            System.out.println("----------------------------");
        }
        controllerDB.execute("DROP DATABASE IF EXISTS " + maskDB + ";");
        controllerDB.execute(
                "CREATE DATABASE " + maskDB
                + " WITH TEMPLATE " + controllerDB.getDatabase()
                + " OWNER " + controllerDB.getUsername() + ";");
        controllerDB.setNameDB(maskDB);

        controllerDB.disconnect();
        controllerDB.connect();
    }

    public void backToOriginDatabase() {
        if (oldConnection == null) {
            return;
        }
        controllerDB.setNameDB(oldConnection);
        controllerDB.disconnect();
        controllerDB.connect();
    }

    private String masking() throws Exception {
        statisticService.resetStatistic();
        statisticService.setNotMaskStatistic(assessmentConfigMap);

        dataPreparationService.start();

        long start = System.currentTimeMillis();

        for (MaskItem method : methods) {
            method.start(controllerDB);
        }

        long end = System.currentTimeMillis();

        statisticService.calculateRisk(riskConfigMap);
        statisticService.setMaskStatistic(assessmentConfigMap);

        NumberFormat formatter = new DecimalFormat("#0.00");
        return formatter.format((end - start) / 1000d).replace(",", ".");
    }

}
