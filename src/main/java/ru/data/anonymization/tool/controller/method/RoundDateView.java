package ru.data.anonymization.tool.controller.method;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.data.anonymization.tool.methods.options.type.RoundDate;
import ru.data.anonymization.tool.builder.DialogBuilder;
import ru.data.anonymization.tool.dto.enums.ShowMode;
import ru.data.anonymization.tool.service.DepersonalizationService;
import ru.data.anonymization.tool.util.ComponentUtils;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class RoundDateView {
    private static final int MAX_LENGTH = 40;

    private final DepersonalizationService depersonalizationService;

    @FXML
    public ComboBox<String> roundType;
    @FXML
    private TextField columnName;
    @FXML
    private TextField customName;
    @FXML
    private TextField tableName;
    @FXML
    private HBox buttonContainer;

    private Stage stage;

    public void configView(String title, String table, String column, ShowMode mode, String name, VBox vBox) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(RoundDateView.class.getResource("round-date-view.fxml"));
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

            String type = ((RoundDate) depersonalizationService.getMethod(name)).getType();
            roundType.getSelectionModel().select(type);

            Button deleteButton = new Button("Удалить");
            deleteButton.setStyle("-fx-background-color: #e83434; -fx-text-fill: white;");
            deleteButton.setOnAction(event -> deleteAction(name, vBox));
            buttonContainer.getChildren().add(deleteButton);
        } else {
            roundType.getSelectionModel().select(0);
        }

        stage.show();
    }

    private void saveAction(String table, String column, ShowMode mode) {
        String type = roundType.getSelectionModel().getSelectedItem();

        if (customName.getText().isEmpty() || customName.getText().isBlank()) {
            DialogBuilder.createErrorDialog("Нужно ввести название!");
        } else if (type == null) {
            DialogBuilder.createErrorDialog("Нужно выбрать тип округления!");
        } else {
            RoundDate dto = new RoundDate();
            dto.setNameTable(table);
            dto.setNameColumn(column);
            dto.setType(type);
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

    @FXML
    private void selectRoundType() {
    }
}