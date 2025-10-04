package ru.data.anonymization.tool.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import ru.data.anonymization.tool.builder.DialogBuilder;
import ru.data.anonymization.tool.service.ConfigSaveService;
import ru.data.anonymization.tool.service.ViewService;
import ru.data.anonymization.tool.util.ComponentUtils;

@Component
@RequiredArgsConstructor
public class DownloadView {

    private final ConfigSaveService configSaveService;
    private final ViewService viewService;

    @FXML
    private Label filePath;
    @FXML
    private HBox buttonContainer;
    private Stage stage;

    @SneakyThrows
    public void configView(String title, VBox config) {
        FXMLLoader fxmlLoader = new FXMLLoader(DownloadView.class.getResource("download-view.fxml"));
        stage = ComponentUtils.modalStageView(fxmlLoader, title, "download.png");
        Button saveButton = new Button("Загрузить");
        saveButton.setStyle("-fx-background-color: #4bbd50; -fx-text-fill: white;");
        saveButton.setOnAction(event -> saveAction(config));
        buttonContainer.getChildren().add(saveButton);
        stage.show();
    }

    private void saveAction(VBox config) {
        if (filePath.getText().equals("Файл неуказан")) {
            DialogBuilder.createErrorDialog("Выберите файл для загрузки!");
        } else {
            configSaveService.downloadConfig(filePath.getText());
            viewService.setConfigMethods(config);
            stage.close();
        }
    }

    @FXML
    private void chooseFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите файл");
        var selectedFile = fileChooser.showOpenDialog(null);

        if (selectedFile != null) {
            filePath.setText(selectedFile.getAbsolutePath());
        }
    }
}
