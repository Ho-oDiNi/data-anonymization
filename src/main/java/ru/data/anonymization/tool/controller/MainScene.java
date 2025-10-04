package ru.data.anonymization.tool.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.converter.DoubleStringConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.data.anonymization.tool.config.AppContext;
import ru.data.anonymization.tool.dto.DataPreparationDto;
import ru.data.anonymization.tool.dto.enums.MaskMethods;
import ru.data.anonymization.tool.dto.RiskDto;
import ru.data.anonymization.tool.dto.StatisticResponseDto;
import ru.data.anonymization.tool.service.*;
import ru.data.anonymization.tool.util.ComponentUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MainScene {

    private final TableInfoService tableInfoService;
    private final DepersonalizationService depersonalizationService;
    private final StatisticService statisticService;
    private final DataPreparationService preparationService;
    private final ViewService viewService;

    private final SaveView saveView;
    private final DownloadView downloadView;
    private final MaskingView maskingView;

    private int page = 1;
    private int totalPageCount;
    private Tab currentTab;
    private String currentTableName;

    private final List<MaskMethods> universalMaskMethods = new ArrayList<>();
    private final List<MaskMethods> stringMaskMethods = new ArrayList<>();
    private final List<MaskMethods> integerMaskMethods = new ArrayList<>();
    private final List<MaskMethods> floatMaskMethods = new ArrayList<>();
    private final List<MaskMethods> dateMaskMethods = new ArrayList<>();

    @FXML
    private Button startButton;
    @FXML
    private TextField currentPage;
    @FXML
    private TextField proportionA;
    @FXML
    private TextField proportionGlobal;
    @FXML
    private Label totalPages;
    @FXML
    private TabPane tabPane;
    @FXML
    private VBox masking;
    @FXML
    private VBox universalMasking;
    @FXML
    private Label statisticTitle;
    @FXML
    private Label statisticBody;
    @FXML
    private VBox config;
    @FXML
    private VBox riskAttribute;
    @FXML
    private VBox assessmentAttribute;

    @FXML
    private CheckBox ProsecutorMetricA;
    @FXML
    private CheckBox ProsecutorMetricB;
    @FXML
    private CheckBox ProsecutorMetricC;
    @FXML
    private CheckBox GlobalRisk;

    @FXML
    private CheckBox assessmentMin;
    @FXML
    private CheckBox assessmentMax;
    @FXML
    private CheckBox assessmentAvg;
    @FXML
    private CheckBox assessmentRMSE;
    @FXML
    private CheckBox assessmentMSE;
    @FXML
    private CheckBox assessmentMD;
    @FXML
    private CheckBox assessmentShannon;

    @FXML
    private ComboBox<String> columnList;
    @FXML
    private ComboBox<String> columnPreparationList;
    @FXML
    private ComboBox<String> typeAttributeList;
    @FXML
    private ComboBox<String> dateTypeList;
    @FXML
    private ComboBox<String> preparationMethodList;

    @FXML
    void initialize() {
        selectPreparationConfigColumnList();
        selectPreparationConfigColumnPreparationList();
        configListOfMaskingMethods(); // Заполняем листы с типами методов обезличивания
        setMaskingMethods();  // отображаем универсальные методы обезличивания
        selectDataType(); // отображаем методы обезличивания для конкретного типа
        refreshTables();

        currentPage.textProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue.equals(newValue)) {
                return;
            }
            int value;
            try {
                value = Integer.parseInt(newValue);
            } catch (Exception ignore) {
                value = -1;
            }
            if (value <= totalPageCount && value > 0) {
                page = value;
                currentTab.setContent(tableInfoService.buildData(currentTab.getText(), page));
            } else {
                currentPage.setText(oldValue);
            }

        });

        proportionA.setTextFormatter(new TextFormatter<>(
                new DoubleStringConverter(),
                1.0,
                change -> {
                    String newText = change.getControlNewText();
                    if (newText.matches("(([1-9][0-9]*)|0)?(\\.[0-9]*)?")) {
                        return change;
                    }
                    return null;
                }
        ));

        proportionGlobal.setTextFormatter(new TextFormatter<>(
                new DoubleStringConverter(),
                1.0,
                change -> {
                    String newText = change.getControlNewText();
                    if (newText.matches("(([1-9][0-9]*)|0)?(\\.[0-9]*)?")) {
                        return change;
                    }
                    return null;
                }
        ));
    }

    private void refreshTables() {

        //Создаем таблицы
        tabPane.getTabs().clear();
        tabPane.getSelectionModel().selectedItemProperty().addListener(
                (ov, t, t1) -> {
                    if (t1 == null) {
                        return;
                    }
                    page = 1;
                    currentTableName = (String) t1.getUserData(); // Берём оригинал
                    t1.setContent(tableInfoService.buildData(currentTableName, page));

                    currentTab = t1;
                    currentPage.setText(String.valueOf(page));

                    totalPageCount = (int) Math.ceil(
                            (double) tableInfoService.getTableSize((String) t1.getUserData())
                            / 500);
                    totalPages.setText(String.valueOf(totalPageCount));

                    setPreparation();
                    setColumnListForRiskAndAssessment();
                }
        );
        tableInfoService.getTables().forEach(name -> {
            Tab tab = new Tab();
            tab.setUserData(name);
            tab.setText(name.replaceAll("_", "__"));
            tab.setContent(new Label("Can't show table"));
            tabPane.getTabs().add(tab);
        });
    }

    public static void loadView(Stage stage) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(AuthScene.class.getResource("main-scene.fxml"));
            fxmlLoader.setControllerFactory(AppContext.applicationContext::getBean);
            Parent root = fxmlLoader.load();
            stage.setScene(new Scene(root));
            stage.setTitle("Data Tools Anonymization");
            stage.setResizable(true);
            stage.centerOnScreen();
            stage.setMinWidth(800);
            stage.setMinHeight(400);
            stage.show();
            initViewProperty(root);
            AppContext.stage = stage;
        } catch (IOException ignore) {
        }

    }

    public static void initViewProperty(Parent root) {
        HBox boxTabPane = (HBox) root.lookup("#boxTabPane");
        TabPane tabPane = (TabPane) root.lookup("#tabPane");
        HBox.setHgrow(boxTabPane, Priority.ALWAYS);
        HBox.setHgrow(tabPane, Priority.ALWAYS);

        HBox main = (HBox) root.lookup("#main");
        main.prefWidthProperty().bind(((AnchorPane) root).widthProperty());
        main.prefHeightProperty().bind(((AnchorPane) root).heightProperty());
    }

    //Конфирурируем листы с методами обезличивания
    private void configListOfMaskingMethods() {
        universalMaskMethods.add(MaskMethods.Identifier);
        universalMaskMethods.add(MaskMethods.Decomposition);
        universalMaskMethods.add(MaskMethods.Shuffle);
        universalMaskMethods.add(MaskMethods.DeleteMethod);
        universalMaskMethods.add(MaskMethods.SelectionMethod);
        universalMaskMethods.add(MaskMethods.LocalSuppression);

        //        universalMaskMethods.add(MaskMethods.ValueReplacement);
        //        universalMaskMethods.add(MaskMethods.ValueReplacementFromFile);
        universalMaskMethods.add(MaskMethods.MicroAggregation);
        //        universalMaskMethods.add(MaskMethods.MicroAggregationBySingleAxis);

        stringMaskMethods.add(MaskMethods.GeneralizationString);
        stringMaskMethods.add(MaskMethods.ValueReplacementByPattern);

        integerMaskMethods.add(MaskMethods.GeneralizationValue);
        integerMaskMethods.add(MaskMethods.ValueVariance);

        floatMaskMethods.add(MaskMethods.GeneralizationValue);
        floatMaskMethods.add(MaskMethods.Round);
        floatMaskMethods.add(MaskMethods.ValueVariance);

        dateMaskMethods.add(MaskMethods.DateAging);
        dateMaskMethods.add(MaskMethods.GeneralizationValue);
        dateMaskMethods.add(MaskMethods.ValueVariance);
        dateMaskMethods.add(MaskMethods.RoundDate);
    }

    private void setPreparation() {
        columnList.getItems().clear();
        columnList.setPromptText("Выберите атрибут");

        columnPreparationList.getItems().clear();
        columnPreparationList.setPromptText("Выберите атрибут");

        List<String> list = tableInfoService.getColumnNames(currentTableName);
        columnList.getItems().addAll(list);
        columnList.getSelectionModel().select(0);

        columnPreparationList.getItems().addAll(list);
        columnPreparationList.getSelectionModel().select(0);
    }

    //Выбрали атрибут
    @FXML
    private void selectPreparationConfigColumnList() {
        String key = currentTableName + "_" + columnList.getSelectionModel().getSelectedItem();

        // Считывание с сервиса
        DataPreparationDto dto = preparationService.getPreparation(key);
        if (dto != null) {
            System.out.println("Отображение из памяти DataType");
            switch (dto.getDataType()) {
                case "String" -> dateTypeList.getSelectionModel().select(0);
                case "Integer" -> dateTypeList.getSelectionModel().select(1);
                case "Float" -> dateTypeList.getSelectionModel().select(2);
                case "Date" -> dateTypeList.getSelectionModel().select(3);
            }
        } else {
            dateTypeList.getSelectionModel().select(0);
        }
    }

    @FXML
    private void selectPreparationConfigColumnPreparationList() {
        String key = currentTableName + "_" + columnPreparationList.getSelectionModel()
                                                                   .getSelectedItem();

        typeAttributeList.setOnAction(null);
        preparationMethodList.setOnAction(null);

        // Считывание с сервиса
        DataPreparationDto dto = preparationService.getPreparation(key);
        if (dto != null) {
            System.out.println("Отображение из памяти");
            switch (dto.getTypeAttribute()) {
                case "Insensitive" -> typeAttributeList.getSelectionModel().select(0);
                case "Sensitive" -> typeAttributeList.getSelectionModel().select(1);
                case "Quasi-Identifying" -> typeAttributeList.getSelectionModel().select(2);
                case "Identifying" -> typeAttributeList.getSelectionModel().select(3);
            }
            switch (dto.getPreparationMethod()) {
                case "none" -> preparationMethodList.getSelectionModel().select(0);
                case "average" -> preparationMethodList.getSelectionModel().select(1);
                case "median" -> preparationMethodList.getSelectionModel().select(2);
                case "mode" -> preparationMethodList.getSelectionModel().select(3);
            }
        } else {
            typeAttributeList.getSelectionModel().select(0);
            preparationMethodList.getSelectionModel().select(0);
        }

        typeAttributeList.setOnAction(t -> savePreparation());
        preparationMethodList.setOnAction(t -> savePreparation());
    }

    // Сохранение подготовки
    @FXML
    private void savePreparation() {
        if (currentTableName == null
            || columnPreparationList.getSelectionModel().getSelectedItem() == null) {
            return;
        }

        String key = currentTableName + "_" + columnPreparationList.getSelectionModel()
                                                                   .getSelectedItem();

        DataPreparationDto dto = preparationService.getPreparation(key);
        if (dto == null) {
            dto = new DataPreparationDto();
            dto.setTableName(currentTableName);
            dto.setColumnName(columnPreparationList.getSelectionModel().getSelectedItem());
            dto.setDataType("String");
        }
        dto.setTypeAttribute(typeAttributeList.getSelectionModel().getSelectedItem());
        dto.setPreparationMethod(preparationMethodList.getSelectionModel().getSelectedItem());

        if (dto.getTypeAttribute().equals("Insensitive") && dto.getPreparationMethod()
                                                               .equals("none") && dto.getDataType()
                                                                                     .equals("String")) {
            preparationService.removeMethod(key);
        } else {
            preparationService.addPreparation(key, dto);
        }
        System.out.println(
                "Save1: " + currentTableName + "->" + columnPreparationList.getSelectionModel()
                                                                           .getSelectedItem()
                + "  [" + typeAttributeList.getSelectionModel().getSelectedItem() + ","
                + preparationMethodList.getSelectionModel().getSelectedItem() + "]");
    }

    private void savePreparationDataType(String dataType) {
        if (currentTableName == null || columnList.getSelectionModel().getSelectedItem() == null) {
            return;
        }
        if (dataType != null && !dataType.isBlank()) {
            String key = currentTableName + "_" + columnList.getSelectionModel().getSelectedItem();

            DataPreparationDto dto = preparationService.getPreparation(key);
            if (dto == null) {
                dto = new DataPreparationDto();
                dto.setTableName(currentTableName);
                dto.setColumnName(columnList.getSelectionModel().getSelectedItem());
                dto.setTypeAttribute("Insensitive");
                dto.setPreparationMethod("none");
            }
            dto.setDataType(dateTypeList.getSelectionModel().getSelectedItem());
            if (dto.getTypeAttribute().equals("Insensitive") && dto.getPreparationMethod().equals(
                    "none") && dto.getDataType().equals("String")) {
                preparationService.removeMethod(key);
            } else {
                preparationService.addPreparation(key, dto);
            }
            System.out.println("Save2: " + currentTableName + "->" + columnList.getSelectionModel()
                                                                               .getSelectedItem()
                               + "  [" + typeAttributeList.getSelectionModel().getSelectedItem()
                               + "," + preparationMethodList.getSelectionModel().getSelectedItem()
                               + "]");
        }
    }

    // Выставляем чекбоксы атрибутов для рисков и оценки потерь
    private void setColumnListForRiskAndAssessment() {
        riskAttribute.getChildren().clear();
        assessmentAttribute.getChildren().clear();

        List<String> columns = tableInfoService.getColumnNames(currentTableName);

        columns.forEach(col -> {
            List<String> activeColumn = depersonalizationService.getRiskConfig(currentTableName);

            CheckBox checkBox = new CheckBox(col);
            checkBox.setText(col);
            checkBox.setMnemonicParsing(false);
            if (activeColumn != null && activeColumn.contains(col)) {
                checkBox.setSelected(true);
            }

            checkBox.setOnAction(event -> {
                List<String> riskColumns = new ArrayList<>();
                for (int i = 0; i < riskAttribute.getChildren().size(); i++) {
                    if (riskAttribute.getChildren().get(i) instanceof CheckBox check) {
                        if (check.isSelected()) {
                            riskColumns.add(check.getText());
                        }
                    }
                }
                if (!riskColumns.isEmpty()) {
                    depersonalizationService.addRiskConfig(currentTableName, riskColumns);
                } else {
                    depersonalizationService.removeRiskConfig(currentTableName);
                }
            });
            riskAttribute.getChildren().add(checkBox);
        });

        columns.forEach(col -> {
            List<String> activeColumn = depersonalizationService.getAssessmentConfig(
                    currentTableName);

            CheckBox checkBox = new CheckBox(col);
            checkBox.setText(col);
            checkBox.setMnemonicParsing(false);
            if (activeColumn != null && activeColumn.contains(col)) {
                checkBox.setSelected(true);
            }

            checkBox.setOnAction(event -> {
                List<String> assessmentColumns = new ArrayList<>();
                for (int i = 0; i < assessmentAttribute.getChildren().size(); i++) {
                    if (assessmentAttribute.getChildren().get(i) instanceof CheckBox check) {
                        if (check.isSelected()) {
                            assessmentColumns.add(check.getText());
                        }
                    }
                }
                if (!assessmentColumns.isEmpty()) {
                    depersonalizationService.addAssessmentConfig(
                            currentTableName,
                            assessmentColumns
                    );
                } else {
                    depersonalizationService.removeAssessmentConfig(currentTableName);
                }
            });

            assessmentAttribute.getChildren().add(checkBox);
        });

    }

    //Создаем кнопки для методов обезличивания с определенным типом
    @FXML
    private void selectDataType() {
        savePreparationDataType(dateTypeList.getValue());

        masking.getChildren().clear();
        if (dateTypeList.getValue() == null) {
            return;
        }

        List<MaskMethods> listOfMethods = null;
        switch (dateTypeList.getValue()) {
            case "String" -> listOfMethods = stringMaskMethods;
            case "Integer" -> listOfMethods = integerMaskMethods;
            case "Float" -> listOfMethods = floatMaskMethods;
            case "Date" -> listOfMethods = dateMaskMethods;
        }

        if (listOfMethods != null) {
            listOfMethods.forEach(method -> masking.getChildren().add(createMethodButton(method)));
        }
    }

    //Создаем кнопки для удиверсальных методов обезличивания
    private void setMaskingMethods() {
        universalMaskMethods.forEach(method -> universalMasking.getChildren()
                                                               .add(createMethodButton(method)));
    }

    public Button createMethodButton(MaskMethods method) {
        Button button = ComponentUtils.createButton(method.getNameRus(), method.getName());
        button.setOnAction(event -> maskConfiguration(method));
        button.setPrefWidth(200);
        return button;
    }

    //Создаем кнопки для редактирования методов
    public void setConfigMethods() {
        viewService.setConfigMethods(config);
    }

    public void PageBack() {
        if (page > 1) {
            currentPage.setText(String.valueOf(--page));
        }
    }

    public void PageNext() {
        if (page < totalPageCount) {
            currentPage.setText(String.valueOf(++page));
        }

    }

    public void maskConfiguration(MaskMethods nameMethod) {
        viewService.maskConfiguration(
                nameMethod,
                currentTableName,
                columnList.getSelectionModel().getSelectedItem(),
                dateTypeList.getSelectionModel().getSelectedItem()
        );
    }

    @FXML
    private void startMasking() {
        AppContext.needRefresh = true;
        startButton.setDisable(true);

        statisticService.clearRisk();
        if (ProsecutorMetricA.isSelected()) {
            statisticService.setRisk(
                    ProsecutorMetricA.getText(),
                    Double.parseDouble(proportionA.getText())
            );
        }
        if (ProsecutorMetricB.isSelected()) {
            statisticService.setRisk(ProsecutorMetricB.getText(), 0);
        }
        if (ProsecutorMetricC.isSelected()) {
            statisticService.setRisk(ProsecutorMetricC.getText(), 0);
        }
        if (GlobalRisk.isSelected()) {
            statisticService.setRisk(
                    GlobalRisk.getText(),
                    Double.parseDouble(proportionGlobal.getText())
            );
        }
        Runnable task = () -> {
            Platform.runLater(() -> maskingView.configView("Обезличивание"));
            String time = depersonalizationService.start();
            Platform.runLater(() -> {
                maskingView.setTime(time);
                StatisticResponseDto dto = statisticService.getStatistic();
                refreshTables();
                if (dto != null) {
                    StringBuilder statistic = new StringBuilder();

                    if (assessmentMin.isSelected()) {
                        statistic.append("min: ").append(dto.getMin()).append("\n");
                    }
                    if (assessmentMax.isSelected()) {
                        statistic.append("max: ").append(dto.getMax()).append("\n");
                    }
                    if (assessmentAvg.isSelected()) {
                        statistic.append("avg: ").append(dto.getAvg()).append("\n");
                    }
                    if (assessmentRMSE.isSelected()) {
                        statistic.append("RMSE: ").append(dto.getRMSE()).append("\n");
                    }
                    if (assessmentMSE.isSelected()) {
                        statistic.append("MSE: ").append(dto.getMSE()).append("\n");
                    }
                    if (assessmentMD.isSelected()) {
                        statistic.append("MD: ").append(dto.getMD()).append("\n");
                    }
                    if (assessmentShannon.isSelected()) {
                        statistic.append("Shannon: ").append(dto.getShannon()).append("\n");
                    }

                    for (RiskDto risk : dto.getRisk()) {
                        statistic.append(risk.getName())
                                 .append(": ")
                                 .append(risk.getResult())
                                 .append("\n");
                    }

                    statisticTitle.setText("Статистика по обезличиванию");

                    if (statistic.isEmpty()) {
                        statisticBody.setText("Вы ничего не выбрали");
                    } else {
                        statisticBody.setText(statistic.toString());
                    }

                }
            });
        };
        Thread thread = new Thread(task);
        thread.start();
    }

    @FXML
    private void saveConfig() {
        saveView.configView("Сохранить конфигурацию");
    }

    @FXML
    private void downloadConfig() {
        downloadView.configView("Загрузить конфигурацию", config);
    }

    @FXML
    private void backToOriginDatabase() {
        AppContext.needRefresh = false;
        startButton.setDisable(false);
        depersonalizationService.backToOriginDatabase();
        refreshTables();
        statisticTitle.setText(null);
        statisticBody.setText(null);
    }

}