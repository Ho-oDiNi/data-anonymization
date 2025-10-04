package ru.data.anonymization.tool.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import ru.data.anonymization.tool.builder.DialogBuilder;
import ru.data.anonymization.tool.service.ConfigSaveService;
import ru.data.anonymization.tool.util.ComponentUtils;

import java.io.File;

@Component
@RequiredArgsConstructor
public class SaveView {

    private final ConfigSaveService configSaveService;

    @FXML
    private Label filePath;
    @FXML
    private TextField customName;
    @FXML
    private HBox buttonContainer;
    private Stage stage;

    @SneakyThrows
    public void configView(String title) {
        FXMLLoader fxmlLoader = new FXMLLoader(SaveView.class.getResource("save-view.fxml"));
        stage = ComponentUtils.modalStageView(fxmlLoader, title, "upload.png");
        Button saveButton = new Button("Сохранить");
        saveButton.setStyle("-fx-background-color: #4bbd50; -fx-text-fill: white;");
        saveButton.setOnAction(event -> saveAction());
        buttonContainer.getChildren().add(saveButton);
        stage.show();
    }

    private void saveAction() {
        if (customName.getText().isEmpty() || customName.getText().isBlank()) {
            DialogBuilder.createErrorDialog("Введите имя файла!");
        } else if (filePath.getText().equals("Путь неуказан")) {
            DialogBuilder.createErrorDialog("Выберите путь для сохранения!");
        } else {
            String path = filePath.getText() + "\\" + customName.getText() + ".config";
            configSaveService.saveConfig(path);
            stage.close();
        }
    }

    @FXML
    private void chooseFile() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        File selectedDirectory = directoryChooser.showDialog(null);

        if (selectedDirectory != null) {
            filePath.setText(selectedDirectory.getAbsolutePath());
        }

    }
}
