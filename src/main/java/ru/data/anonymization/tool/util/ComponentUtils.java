package ru.data.anonymization.tool.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;
import ru.data.anonymization.tool.config.AppContext;

import java.io.IOException;
import java.util.Objects;

public class ComponentUtils {

    public static Stage modalStageView(FXMLLoader fxmlLoader, String title, String image) throws IOException {
        fxmlLoader.setControllerFactory(AppContext.applicationContext::getBean);
        Stage stage = new Stage();
        stage.setScene(new Scene(fxmlLoader.load()));
        stage.setTitle(title);
        stage.setResizable(false);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(AppContext.stage.getScene().getWindow());
        stage.getIcons().add(new Image(Objects.requireNonNull(ComponentUtils.class.getResourceAsStream(image))));
        return stage;
    }

    public static Button createButton(String name, String id) {
        return createButton(name, id, null);
    }

    public static Button createButton(String name, String id, String style) {
        Button button = new Button(name);
        button.setStyle(style);
        button.setId(id);
        button.setOnMouseEntered(event -> button.getScene().setCursor(javafx.scene.Cursor.HAND));
        button.setOnMouseExited(event -> button.getScene().setCursor(javafx.scene.Cursor.DEFAULT));
        return button;
    }

    public static Image getImage(String name) {
        return new Image(Objects.requireNonNull(ComponentUtils.class.getResourceAsStream(name)));
    }
}
