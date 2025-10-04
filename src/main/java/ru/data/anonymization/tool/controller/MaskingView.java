package ru.data.anonymization.tool.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import ru.data.anonymization.tool.util.ComponentUtils;

@Component
@RequiredArgsConstructor
public class MaskingView {

    @FXML
    private ImageView load;
    @FXML
    private Label message;

    @SneakyThrows
    public void configView(String title) {
        FXMLLoader fxmlLoader = new FXMLLoader(MaskingView.class.getResource("builder-view.fxml"));
        Stage stage = ComponentUtils.modalStageView(fxmlLoader, title, "start.png");
        Image image = ComponentUtils.getImage("load.gif");
        load.setImage(image);
        stage.show();
    }

    public void setTime(String time) {
        Image image = ComponentUtils.getImage("checked.png");
        load.setImage(image);
        if (time == null) time = "0";
        System.out.println();
        message.setText("Завершилось за "+time+" сек.");
    }
}
