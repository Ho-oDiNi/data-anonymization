package ru.data.anonymization.tool.controller.method;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.converter.IntegerStringConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.data.anonymization.tool.methods.options.type.ValueVariance;
import ru.data.anonymization.tool.builder.DialogBuilder;
import ru.data.anonymization.tool.dto.enums.ShowMode;
import ru.data.anonymization.tool.service.DepersonalizationService;
import ru.data.anonymization.tool.util.ComponentUtils;

import java.io.IOException;
import java.util.Locale;
import java.util.function.UnaryOperator;

@Component
@RequiredArgsConstructor
public class ValueVarianceView {
    private static final int MAX_LENGTH = 40;

    private final DepersonalizationService depersonalizationService;

    @FXML
    private TextField variance;

    @FXML
    private TextField customName;
    @FXML
    private TextField tableName;
    @FXML
    private TextField columnName;
    @FXML
    private HBox buttonContainer;

    private Stage stage;

    public void configView(String title, String table, String column, String dataType, ShowMode mode, String name, VBox vBox) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(ValueVarianceView.class.getResource("value-variance-view.fxml"));
        stage = ComponentUtils.modalStageView(fxmlLoader, "Настройка метода (" + title + ")", "gears.png");
        tableName.setText(table);

        columnName.setText(column);

        UnaryOperator<TextFormatter.Change> integerFilter = change -> {
            String newText = change.getControlNewText();
            if (newText.matches("(^[1-9][0-9]?$|^100$)?")) {
                return change;
            }
            return null;
        };
        variance.setTextFormatter(new TextFormatter<>(new IntegerStringConverter(), 1, integerFilter));

        Button saveButton = new Button("Сохранить");
        saveButton.setStyle("-fx-background-color: #4bbd50; -fx-text-fill: white;");
        saveButton.setOnAction(event -> saveAction(table, column, dataType, mode));
        buttonContainer.getChildren().add(saveButton);

        if (mode.equals(ShowMode.EDIT)) {
            customName.setDisable(true);
            customName.setText(name);

            int value = ((ValueVariance) depersonalizationService.getMethod(name)).getSigma();
            variance.setText(String.valueOf(value));


            Button deleteButton = new Button("Удалить");
            deleteButton.setStyle("-fx-background-color: #e83434; -fx-text-fill: white;");
            deleteButton.setOnAction(event -> deleteAction(name, vBox));
            buttonContainer.getChildren().add(deleteButton);
        }

        stage.show();
    }

    private void saveAction(String table, String column, String dataType, ShowMode mode) {
        if (customName.getText().isEmpty() || customName.getText().isBlank()) {
            DialogBuilder.createErrorDialog("Нужно ввести название!");
        } else if (variance.getText().isEmpty() || variance.getText().isBlank()) {
            DialogBuilder.createErrorDialog("Нужно ввести шум!");
        } else {
            ValueVariance dto;
            if (depersonalizationService.isContainsKey(customName.getText())) {
                dto = (ValueVariance) depersonalizationService.getMethod(customName.getText());
                dto.setSigma(Integer.parseInt(variance.getText()));
            } else {
                dto = new ValueVariance();
                dto.setNameTable(table);
                dto.setNameColumn(column);
                dto.setSigma(Integer.parseInt(variance.getText()));
                dto.setDataType(ValueVariance.DataType.valueOf(dataType.toUpperCase(Locale.ROOT)));
            }


            if (mode.equals(ShowMode.EDIT) || !depersonalizationService.isContainsKey(customName.getText())) {
                String name = customName.getText().length() < MAX_LENGTH ? customName.getText() : customName.getText().substring(0, MAX_LENGTH);
                depersonalizationService.addMethod(name, dto);
                stage.close();
            } else {
                DialogBuilder.createErrorDialog("Такое название уже существует!");
            }
        }
    }

    private void deleteAction(String name, VBox vBox) {
        depersonalizationService.removeMethod(name);
        Node node = vBox.lookup("#" + name.replace(" ", ""));
        vBox.getChildren().remove(node);
        stage.close();
    }
}