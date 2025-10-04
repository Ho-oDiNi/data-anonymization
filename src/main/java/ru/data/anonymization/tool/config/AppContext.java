package ru.data.anonymization.tool.config;

import javafx.stage.Stage;
import org.springframework.context.ConfigurableApplicationContext;

public class AppContext {
    public static ConfigurableApplicationContext applicationContext;
    public static Stage stage;
    public static boolean needRefresh;
}
