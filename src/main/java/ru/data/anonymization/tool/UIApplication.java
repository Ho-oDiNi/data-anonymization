package ru.data.anonymization.tool;

import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;
import ru.data.anonymization.tool.config.AppContext;
import ru.data.anonymization.tool.controller.AuthScene;
import ru.data.anonymization.tool.service.DatabaseConnectionService;

import java.util.Objects;

@Configuration
public class UIApplication extends Application {

    @Override
    public void init() {
        String[] args = getParameters().getRaw().toArray(new String[0]);

        AppContext.applicationContext = new SpringApplicationBuilder()
                .sources(DataToolsAnonymizationUIApplication.class)
                .run(args);
    }

    @Override
    public void stop() {
        DatabaseConnectionService database = AppContext.applicationContext.getBean(DatabaseConnectionService.class);
        database.disconnect();
        AppContext.applicationContext.close();
    }

    @Override
    public void start(Stage stage) {
        stage.getIcons().add(new Image(Objects.requireNonNull(this.getClass().getResourceAsStream("icon.png"))));
        AuthScene.loadView(stage);
    }
}
