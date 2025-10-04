package ru.data.anonymization.tool.controller.method;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.data.anonymization.tool.methods.options.type.GeneralizationString;
import ru.data.anonymization.tool.builder.DialogBuilder;
import ru.data.anonymization.tool.dto.enums.ShowMode;
import ru.data.anonymization.tool.service.DepersonalizationService;
import ru.data.anonymization.tool.util.ComponentUtils;

import java.io.IOException;
import java.util.HashMap;

@Component
@RequiredArgsConstructor
public class GeneralizationStringView {
    private static final int MAX_LENGTH = 40;
    private final DepersonalizationService depersonalizationService;

    private HashMap<String, String> value;
    @FXML
    private TextField newTableName;
    @FXML
    private TextField columnName;
    @FXML
    private TextField customName;
    @FXML
    private TextField tableName;
    @FXML
    private HBox buttonContainer;
    @FXML
    private VBox elementView;

    @FXML
    private TextField from;
    @FXML
    private TextField to;


    private Stage stage;

    public void configView(String title, String table, String column, ShowMode mode, String name, VBox vBox) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(GeneralizationStringView.class.getResource("generalization-string-view.fxml"));
        stage = ComponentUtils.modalStageView(fxmlLoader, "Настройка метода (" + title + ")", "gears.png");
        tableName.setText(table);
        value = new HashMap<>();

        columnName.setText(column);

        Button saveButton = new Button("Сохранить");
        saveButton.setStyle("-fx-background-color: #4bbd50; -fx-text-fill: white;");
        saveButton.setOnAction(event -> saveAction(table, column, mode));
        buttonContainer.getChildren().add(saveButton);

        if (mode.equals(ShowMode.EDIT)) {
            customName.setDisable(true);
            customName.setText(name);

            GeneralizationString dto = (GeneralizationString) depersonalizationService.getMethod(name);
            tableName.setText(dto.getTable());
            newTableName.setText(dto.getGeneralizationTable());
            value = new HashMap<>(dto.getValues());

            Button deleteButton = new Button("Удалить");
            deleteButton.setStyle("-fx-background-color: #e83434; -fx-text-fill: white;");
            deleteButton.setOnAction(event -> deleteAction(name, vBox));
            buttonContainer.getChildren().add(deleteButton);
            viewElem();
        }

        stage.show();
    }

    private void saveAction(String table, String column, ShowMode mode) {

        if (customName.getText().isEmpty() || customName.getText().isBlank()) {
            DialogBuilder.createErrorDialog("Нужно ввести название!");
        } else if (newTableName.getText().isEmpty() || newTableName.getText().isBlank()) {
            DialogBuilder.createErrorDialog("Нужно ввести имя новой таблицы!");
        } else if (value.isEmpty()) {
            DialogBuilder.createErrorDialog("Нужно заполнить таблицу обобщения!");
        } else {
            GeneralizationString dto = new GeneralizationString();
            dto.setNameTable(table);
            dto.setNameColumn(column);
            dto.setGeneralizationTable(newTableName.getText());
            dto.setValues(value);
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
    private void addElem() {
        if (from.getText().isEmpty() || from.getText().isBlank() || to.getText().isEmpty() || to.getText().isBlank()) {
            DialogBuilder.createErrorDialog("Заполните поля для добовление елемента!");
        } else {
            value.put(from.getText(), to.getText());
            viewElem();
        }
    }

    public void viewElem() {
        elementView.getChildren().clear();

        for (String key : value.keySet()) {
            HBox hBox = new HBox(10);
            hBox.setAlignment(Pos.CENTER);

            Label fromLabel = new Label(key);
            Label toLabel = new Label(value.get(key));

            Button remove = new Button();
            remove.setText("Удалить");

            remove.setOnAction(e -> {
                value.remove(key);
                elementView.getChildren().remove(hBox);
                viewElem();
            });

            hBox.getChildren().add(fromLabel);
            hBox.getChildren().add(new Label("->"));
            hBox.getChildren().add(toLabel);
            hBox.getChildren().add(remove);

            elementView.getChildren().add(hBox);
        }
    }
}