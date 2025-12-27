package ru.data.anonymization.tool.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.data.anonymization.tool.builder.DialogBuilder;
import ru.data.anonymization.tool.dto.SyntheticConfigDto;
import ru.data.anonymization.tool.dto.enums.ShowMode;
import ru.data.anonymization.tool.service.SyntheticMethodService;
import ru.data.anonymization.tool.service.TableInfoService;
import ru.data.anonymization.tool.util.ComponentUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SyntheticMethodView {

    private static final int MAX_LENGTH = 40;

    private final TableInfoService tableInfoService;
    private final SyntheticMethodService syntheticMethodService;

    @FXML
    private TextField syntheticName;
    @FXML
    private TextField tableName;
    @FXML
    private Spinner<Integer> rowsCount;
    @FXML
    private ComboBox<String> targetColumn;
    @FXML
    private HBox buttonContainer;

    private Stage stage;

    public void configView(String title, String table, ShowMode mode, String name, VBox config)
            throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(SyntheticMethodView.class.getResource(
                "synthetic-method-view.fxml"));
        stage = ComponentUtils.modalStageView(
                fxmlLoader,
                "Настройка метода (" + title + ")",
                "gears.png"
        );

        tableName.setText(table);
        configureTargetColumn(table);
        configureRowsSpinner();

        Button saveButton = new Button("Сохранить");
        saveButton.setStyle("-fx-background-color: #4bbd50; -fx-text-fill: white;");
        saveButton.setOnAction(event -> saveAction(table, mode));
        buttonContainer.getChildren().add(saveButton);

        if (mode == ShowMode.EDIT) {
            syntheticName.setDisable(true);
            syntheticName.setText(name);

            SyntheticConfigDto dto = syntheticMethodService.getConfig(name);
            if (dto != null) {
                rowsCount.getValueFactory().setValue(dto.getRowsCount());
                String target = dto.getTargetColumn();
                if (target == null || target.isBlank()) {
                    targetColumn.getSelectionModel().select(0);
                } else {
                    targetColumn.getSelectionModel().select(target);
                }
            }

            Button deleteButton = new Button("Удалить");
            deleteButton.setStyle("-fx-background-color: #e83434; -fx-text-fill: white;");
            deleteButton.setOnAction(event -> deleteAction(name, config));
            buttonContainer.getChildren().add(deleteButton);
        }

        stage.show();
    }

    private void configureTargetColumn(String table) {
        List<String> columns = new ArrayList<>();
        columns.add("<пусто>");
        columns.addAll(tableInfoService.getColumnNames(table));
        targetColumn.setItems(FXCollections.observableArrayList(columns));
        targetColumn.getSelectionModel().select(0);
    }

    private void configureRowsSpinner() {
        SpinnerValueFactory.IntegerSpinnerValueFactory factory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, Integer.MAX_VALUE, 0);
        rowsCount.setValueFactory(factory);
        rowsCount.setEditable(true);
    }

    private void saveAction(String table, ShowMode mode) {
        String name = syntheticName.getText();
        if (name == null || name.isBlank()) {
            DialogBuilder.createErrorDialog("Нужно ввести название");
            return;
        }

        String trimmedName = name.length() < MAX_LENGTH ? name : name.substring(0, MAX_LENGTH);
        if (mode == ShowMode.CREATE && syntheticMethodService.hasConfig(trimmedName)) {
            DialogBuilder.createErrorDialog("Такое название уже существует");
            return;
        }

        SyntheticConfigDto dto = new SyntheticConfigDto();
        dto.setName(trimmedName);
        dto.setTableName(table);
        dto.setMethodName("Bayesian Network");
        dto.setRowsCount(rowsCount.getValue());

        String targetValue = targetColumn.getSelectionModel().getSelectedItem();
        if (targetValue != null && !"<пусто>".equals(targetValue)) {
            dto.setTargetColumn(targetValue);
        }

        syntheticMethodService.addConfig(trimmedName, dto);
        stage.close();
    }

    private void deleteAction(String name, VBox config) {
        syntheticMethodService.removeConfig(name);
        if (config != null) {
            config.getChildren().removeIf(node -> name.replace(" ", "").equals(node.getId()));
        }
        stage.close();
    }
}

