package ru.data.anonymization.tool.controller.method;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.data.anonymization.tool.methods.options.type.ValueReplacementByPattern;
import ru.data.anonymization.tool.builder.DialogBuilder;
import ru.data.anonymization.tool.dto.enums.ShowMode;
import ru.data.anonymization.tool.service.DepersonalizationService;
import ru.data.anonymization.tool.util.ComponentUtils;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class ValueReplacementByPatternView {
    private static final int MAX_LENGTH = 40;

    private final DepersonalizationService depersonalizationService;

    @FXML
    private TextField regex;
    @FXML
    private TextField replacement;

    @FXML
    private TextField customName;
    @FXML
    private TextField tableName;
    @FXML
    private TextField columnName;
    @FXML
    private HBox buttonContainer;

    private Stage stage;

    public void configView(String title, String table, String column, ShowMode mode, String name, VBox vBox) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(ValueReplacementByPatternView.class.getResource("value-replacement-by-pattern-view.fxml"));
        stage = ComponentUtils.modalStageView(fxmlLoader, "Настройка метода (" + title + ")", "gears.png");
        tableName.setText(table);

        columnName.setText(column);

        Button saveButton = new Button("Сохранить");
        saveButton.setStyle("-fx-background-color: #4bbd50; -fx-text-fill: white;");
        saveButton.setOnAction(event -> saveAction(table, column, mode));
        buttonContainer.getChildren().add(saveButton);

        if (mode.equals(ShowMode.EDIT)) {
            customName.setDisable(true);
            customName.setText(name);

            ValueReplacementByPattern value = (ValueReplacementByPattern) depersonalizationService.getMethod(name);
            regex.setText(String.valueOf(value.getRegex()));
            replacement.setText(String.valueOf(value.getReplacement()));


            Button deleteButton = new Button("Удалить");
            deleteButton.setStyle("-fx-background-color: #e83434; -fx-text-fill: white;");
            deleteButton.setOnAction(event -> deleteAction(name, vBox));
            buttonContainer.getChildren().add(deleteButton);
        }

        stage.show();
    }

    private void saveAction(String table, String column, ShowMode mode) {

        if (customName.getText().isEmpty() || customName.getText().isBlank()) {
            DialogBuilder.createErrorDialog("Нужно ввести название!");
        } else if (regex.getText().isEmpty() || regex.getText().isBlank()) {
            DialogBuilder.createErrorDialog("Нужно ввести регулярное выражение!");
        } else if (replacement.getText().isEmpty() || replacement.getText().isBlank()) {
            DialogBuilder.createErrorDialog("Нужно ввести значение для замены!");
        } else {
            ValueReplacementByPattern dto = new ValueReplacementByPattern();
            dto.setNameTable(table);
            dto.setNameColumn(column);
            dto.setRegex(regex.getText());
            dto.setReplacement(replacement.getText());

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