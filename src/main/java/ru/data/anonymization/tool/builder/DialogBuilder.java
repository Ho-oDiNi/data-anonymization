package ru.data.anonymization.tool.builder;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

public class DialogBuilder {
    public static void createErrorDialog(String message) {
        Alert alert = new Alert(Alert.AlertType.NONE, message, ButtonType.APPLY);
        alert.setTitle("Ошибка");
        alert.show();
    }
}
