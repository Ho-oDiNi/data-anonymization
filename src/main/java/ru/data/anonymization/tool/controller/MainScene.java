package ru.data.anonymization.tool.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.converter.DoubleStringConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.data.anonymization.tool.config.AppContext;
import ru.data.anonymization.tool.dto.DataPreparationDto;
import ru.data.anonymization.tool.dto.enums.MaskMethods;
import ru.data.anonymization.tool.dto.RiskDto;
import ru.data.anonymization.tool.dto.StatisticResponseDto;
import ru.data.anonymization.tool.dto.TableData;
import ru.data.anonymization.tool.service.*;
import ru.data.anonymization.tool.util.ComponentUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MainScene {

    private final TableInfoService tableInfoService;
    private final DepersonalizationService depersonalizationService;
    private final StatisticService statisticService;
    private final DataPreparationService preparationService;
    private final ViewService viewService;
    private final SelectionService selectionService;

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
    private Slider selectionSlider;
    @FXML
    private Label selectionValue;
    @FXML
    private Button selectionButton;

    @FXML
    void initialize() {
        selectPreparationConfigColumnList();
        selectPreparationConfigColumnPreparationList();
        configListOfMaskingMethods(); // Заполняем листы с типами методов обезличивания
        setMaskingMethods();  // отображаем универсальные методы обезличивания
        selectDataType(); // отображаем методы обезличивания для конкретного типа
        refreshTables();

        selectionSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            int percent = newValue.intValue();
            if (selectionSlider.getValue() != percent) {
                selectionSlider.setValue(percent);
            }
            selectionValue.setText(percent + "%");
        });
        selectionSlider.setValue(100);
        selectionValue.setText("100%");

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
                String tableName = currentTableName;
                if (currentTab != null && tableName != null) {
                    updateCurrentTabContent();
                }
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
        selectionService.clearAll();
        tabPane.getTabs().clear();
        List<String> tables = tableInfoService.getTables();

        boolean hasData = !tables.isEmpty();
        configureDataControls(hasData);



        if (!hasData) {
            Tab tab = new Tab("Нет данных");
            TableView<ObservableList<String>> emptyTable = tableInfoService.buildData(null, 1);
            emptyTable.setPlaceholder(new Label(""));

            tab.setContent(emptyTable);
            tabPane.getTabs().add(tab);
            currentTab = null;
            currentTableName = null;
            currentPage.setText("0");
            totalPages.setText("0");
            columnList.getItems().clear();
            columnPreparationList.getItems().clear();
            selectionSlider.setValue(100);
            return;
        }

        tables.forEach(name -> {
            Tab tab = new Tab();
            tab.setUserData(name);
            tab.setText(name.replaceAll("_", "__"));
            tab.setContent(new Label("Can't show table"));
            tab.setOnSelectionChanged(event -> {
                if (tab.isSelected()) {
                    showSelectedTable(tab);
                }
            });
            tabPane.getTabs().add(tab);
        });
        tabPane.getSelectionModel().select(0);
        showSelectedTable(tabPane.getSelectionModel().getSelectedItem());
    }

    private void showSelectedTable(Tab tab) {
        if (tab == null || tab.getUserData() == null) {
            return;
        }

        page = 1;
        currentTableName = (String) tab.getUserData();
        currentTab = tab;
        updateCurrentTabContent();
        currentPage.setText(String.valueOf(page));

        totalPageCount = (int) Math.ceil(
                (double) tableInfoService.getTableSize(currentTableName)
                        / TableInfoService.PAGE_SIZE);
        totalPages.setText(String.valueOf(totalPageCount));

        setPreparation();
        setColumnListForRiskAndAssessment();
        selectionSlider.setValue(selectionService.getSelectionPercent(currentTableName));
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
            AppContext.stage = stage;
        } catch (IOException ignore) {
        }

    }

    private void configureDataControls(boolean hasData) {
        startButton.setDisable(!hasData);
        columnList.setDisable(!hasData);
        columnPreparationList.setDisable(!hasData);
        dateTypeList.setDisable(!hasData);
        selectionSlider.setDisable(!hasData);
        selectionButton.setDisable(!hasData);
    }

    //Конфирурируем листы с методами обезличивания
    private void configListOfMaskingMethods() {
        // Сбрасываем ранее добавленные методы, чтобы избежать дублирования при повторной инициализации
        universalMaskMethods.clear();
        stringMaskMethods.clear();
        integerMaskMethods.clear();
        floatMaskMethods.clear();
        dateMaskMethods.clear();

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
        columnPreparationList.getItems().addAll(list);

        if (!list.isEmpty()) {
            columnList.getSelectionModel().select(0);
            columnPreparationList.getSelectionModel().select(0);
        }
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

    @FXML
    private void loadData() {
        ChoiceDialog<String> dialog = new ChoiceDialog<>("CSV файл", List.of("CSV файл", "База данных"));
        dialog.setTitle("Загрузка данных");
        dialog.setHeaderText("Выберите источник данных");
        dialog.setContentText("Источник:");
        dialog.showAndWait().ifPresent(choice -> {
            if (choice.equals("CSV файл")) {
                loadCsvDataFromFile();
            } else {
                AuthScene.loadView(AppContext.stage);
            }
        });
    }

    @FXML
    private void applySelection() {
        if (currentTableName == null) {
            return;
        }

        int percent = (int) selectionSlider.getValue();
        int tableSize = tableInfoService.getTableSize(currentTableName);

        selectionService.selectRows(currentTableName, tableSize, percent);
        selectionSlider.setValue(selectionService.getSelectionPercent(currentTableName));
        updateCurrentTabContent();
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

        if (columns.isEmpty()) {
            return;
        }

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

    private void updateCurrentTabContent() {
        if (currentTab == null || currentTableName == null) {
            return;
        }

        TableView<ObservableList<String>> tableView = tableInfoService.buildData(currentTableName, page);
        applyRowHighlighting(tableView);
        currentTab.setContent(tableView);
    }

    private void applyRowHighlighting(TableView<ObservableList<String>> tableView) {
        String tableName = currentTableName;
        int currentPageNumber = page;

        if (tableName == null) {
            return;
        }

        tableView.setRowFactory(view -> new TableRow<>() {
            @Override
            protected void updateItem(ObservableList<String> item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setStyle("");
                    return;
                }

                if (!selectionService.hasCustomSelection(tableName)) {
                    setStyle("");
                    return;
                }

                int globalIndex = (currentPageNumber - 1) * TableInfoService.PAGE_SIZE + getIndex();
                if (selectionService.isRowSelected(tableName, globalIndex)) {
                    setStyle("-fx-background-color: #e3f2fd;");
                } else {
                    setStyle("");
                }
            }
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
        universalMasking.getChildren().clear();
        universalMaskMethods.forEach(method -> universalMasking.getChildren()
                                                               .add(createMethodButton(method)));
    }

    private void loadCsvDataFromFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Загрузка CSV файла");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV файлы", "*.csv"));

        var file = fileChooser.showOpenDialog(AppContext.stage);
        if (file == null) {
            return;
        }

        try {
            TableData tableData = parseCsv(file.toPath());
            tableInfoService.loadCsvData(tableData);
            refreshTables();
        } catch (IOException e) {
            showError("Не удалось загрузить CSV: " + e.getMessage());
        }
    }

    private TableData parseCsv(Path filePath) throws IOException {
        List<String> lines = Files.readAllLines(filePath).stream()
                .filter(line -> !line.isBlank())
                .collect(Collectors.toList());

        if (lines.isEmpty()) {
            throw new IOException("Файл пустой или недоступен");
        }

        String delimiter = lines.get(0).contains(";") ? ";" : ",";
        List<String> headers = Arrays.stream(lines.get(0).split(delimiter, -1))
                .map(String::trim)
                .collect(Collectors.toList());

        List<List<String>> rows = lines.stream()
                .skip(1)
                .map(line -> Arrays.stream(line.split(delimiter, -1))
                        .map(String::trim)
                        .collect(Collectors.toList()))
                .collect(Collectors.toList());

        String tableName = filePath.getFileName().toString().replaceFirst("\\.[^.]+$", "");
        return new TableData(tableName, headers, rows);
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
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