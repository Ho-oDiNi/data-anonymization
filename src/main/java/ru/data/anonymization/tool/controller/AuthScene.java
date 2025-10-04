package ru.data.anonymization.tool.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.data.anonymization.tool.config.AppContext;
import ru.data.anonymization.tool.service.DatabaseConnectionService;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class AuthScene {

    private static Stage myStage;
    private final DatabaseConnectionService connection;

    @FXML
    private Label error;

    @FXML
    private TextField host;

    @FXML
    private TextField port;

    @FXML
    private TextField database;

    @FXML
    private TextField username;

    @FXML
    private PasswordField password;

    @FXML
    private void connection(ActionEvent event) {
        event.consume();
        if (isValid()) {
            connection.setConnection(host.getText(), port.getText(), database.getText(), username.getText(), password.getText());
            if (connection.connect()) {
                MainScene.loadView(myStage);
            } else {
                error.setText("Ошибка подключения");
            }
        } else {
            error.setText("Заполните поля");
        }
    }

    public static void loadView(Stage stage) {
        myStage = stage;

        try {
            FXMLLoader fxmlLoader = new FXMLLoader(AuthScene.class.getResource("auth-scene.fxml"));
            fxmlLoader.setControllerFactory(AppContext.applicationContext::getBean);
            Parent root = fxmlLoader.load();
            stage.setScene(new Scene(root));
            stage.setTitle("Authorization");
            stage.setResizable(false);
            stage.show();

            VBox main = (VBox) root.lookup("#main");
            main.prefWidthProperty().bind(((AnchorPane) root).widthProperty());
            main.prefHeightProperty().bind(((AnchorPane) root).heightProperty());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean isValid() {
        return checkFieldIsValid(host)
                & checkFieldIsValid(port)
                & checkFieldIsValid(database)
                & checkFieldIsValid(username)
                & checkFieldIsValid(password);
    }

    private boolean checkFieldIsValid(TextField field) {
        boolean isValid = field.getText() == null || field.getText().isBlank();
        if (isValid) {
            field.setStyle("-fx-border-color: red");
        } else {
            field.setStyle("");
        }
        return !isValid;
    }
}
