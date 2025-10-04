package ru.data.anonymization.tool.controller.method;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.converter.DoubleStringConverter;
import javafx.util.converter.IntegerStringConverter;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.data.anonymization.tool.methods.options.type.GeneralizationValue;
import ru.data.anonymization.tool.builder.DialogBuilder;
import ru.data.anonymization.tool.dto.enums.ShowMode;
import ru.data.anonymization.tool.service.DepersonalizationService;
import ru.data.anonymization.tool.util.ComponentUtils;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class GeneralizationValueView {

    private static final int MAX_LENGTH = 40;
    private final DepersonalizationService depersonalizationService;

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
    private HBox valueContainer;
    @FXML
    private VBox elementView;
    @FXML
    ComboBox<String> instruction;
    @FXML
    private HBox instructionBox;

    private TextField name;
    private Node from;
    private Node to;

    private HashMap<String, Value> value;

    @AllArgsConstructor
    static class Value {

        public String minValue;
        public String maxValue;

    }

    private Stage stage;
    private boolean isDate = false;
    private String type;

    public void configView(
            String title,
            String table,
            String column,
            String dateType,
            ShowMode mode,
            String name,
            VBox vBox) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(GeneralizationValueView.class.getResource(
                "generalization-value-view.fxml"));
        stage = ComponentUtils.modalStageView(
                fxmlLoader,
                "Настройка метода (" + title + ")",
                "gears.png"
        );
        tableName.setText(table);
        columnName.setText(column);
        type = dateType;
        if (dateType != null && type.equals("Date")) {
            instructionBox.setVisible(false);
        }

        value = new HashMap<>();

        Button saveButton = new Button("Сохранить");
        saveButton.setStyle("-fx-background-color: #4bbd50; -fx-text-fill: white;");
        saveButton.setOnAction(event -> saveAction(table, column, mode));
        buttonContainer.getChildren().add(saveButton);

        if (mode.equals(ShowMode.EDIT)) {
            customName.setDisable(true);
            customName.setText(name);

            GeneralizationValue dto = (GeneralizationValue) depersonalizationService.getMethod(name);
            tableName.setText(dto.getTable());
            newTableName.setText(dto.getGeneralizationTable());

            instruction.getSelectionModel().select(dto.getInstruct());

            List<String> generalizationName = new ArrayList<>(dto.getGeneralizationName());
            List<?> minValue = new ArrayList<>(dto.getMinValue());
            List<?> maxValue = new ArrayList<>(dto.getMaxValue());
            for (int i = 0; i < generalizationName.size(); i++) {
                value.put(
                        generalizationName.get(i),
                        new Value(minValue.get(i).toString(), maxValue.get(i).toString())
                );
            }

            isDate = dto.isDate();
            type = dto.getDateType();

            Button deleteButton = new Button("Удалить");
            deleteButton.setStyle("-fx-background-color: #e83434; -fx-text-fill: white;");
            deleteButton.setOnAction(event -> deleteAction(name, vBox));
            buttonContainer.getChildren().add(deleteButton);
            viewElem();
        } else {
            instruction.getSelectionModel().select(0);
        }
        createField();
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
            GeneralizationValue dto = new GeneralizationValue();
            dto.setNameTable(table);
            dto.setNameColumn(column);
            dto.setGeneralizationTable(newTableName.getText());
            dto.setInstruct(instruction.getSelectionModel().getSelectedItem());

            List<String> generalizationName = new ArrayList<>();

            if (type.equals("Date")) {
                List<LocalDate> minValue = new ArrayList<>();
                List<LocalDate> maxValue = new ArrayList<>();

                for (String key : value.keySet()) {
                    generalizationName.add(key);
                    Value val = value.get(key);
                    minValue.add(LocalDate.parse(val.minValue));
                    maxValue.add(LocalDate.parse(val.maxValue));
                }
                dto.setMinValue(minValue);
                dto.setMaxValue(maxValue);
            } else if (type.equals("Integer")) {
                List<Integer> minValue = new ArrayList<>();
                List<Integer> maxValue = new ArrayList<>();
                for (String key : value.keySet()) {
                    generalizationName.add(key);
                    Value val = value.get(key);
                    minValue.add(Integer.parseInt(val.minValue));
                    maxValue.add(Integer.parseInt(val.maxValue));
                }
                dto.setMinValue(minValue);
                dto.setMaxValue(maxValue);
            } else {
                List<Double> minValue = new ArrayList<>();
                List<Double> maxValue = new ArrayList<>();
                for (String key : value.keySet()) {
                    generalizationName.add(key);
                    Value val = value.get(key);
                    minValue.add(Double.parseDouble(val.minValue));
                    maxValue.add(Double.parseDouble(val.maxValue));
                }
                dto.setMinValue(minValue);
                dto.setMaxValue(maxValue);
            }
            dto.setGeneralizationName(generalizationName);
            dto.setDate(isDate);
            dto.setDateType(type);
            if (mode.equals(ShowMode.EDIT)
                || !depersonalizationService.isContainsKey(customName.getText())) {
                String name = customName.getText().length() < MAX_LENGTH ? customName.getText()
                        : customName.getText().substring(0, MAX_LENGTH);
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
        if (name.getText().isEmpty() || name.getText().isBlank()) {
            DialogBuilder.createErrorDialog("Заполните поля для добовление елемента!");
        } else {
            Value val = null;
            if (type.equals("Date")) {
                val = new Value(
                        ((DatePicker) from).getValue().toString(),
                        ((DatePicker) to).getValue().toString()
                );
            } else {
                if (((TextField) from).getText().isEmpty() || ((TextField) from).getText().isBlank()
                    || ((TextField) to).getText().isEmpty() || ((TextField) to).getText()
                                                                               .isBlank()) {
                    DialogBuilder.createErrorDialog("Заполните поля для добовление елемента!");
                } else {
                    val = new Value(((TextField) from).getText(), ((TextField) to).getText());
                }
            }
            value.put(name.getText(), val);
            viewElem();
        }
    }

    private void viewElem() {
        elementView.getChildren().clear();

        for (String key : value.keySet()) {
            HBox hBox = new HBox(10);
            hBox.setAlignment(Pos.CENTER);

            Value val = value.get(key);

            Label nameLabel = new Label(key);
            Label fromLabel = new Label(val.minValue);
            Label toLabel = new Label(val.maxValue);

            Button remove = new Button();
            remove.setText("Удалить");

            remove.setOnAction(e -> {
                value.remove(key);

                elementView.getChildren().remove(hBox);
                viewElem();
            });

            hBox.getChildren().addAll(
                    nameLabel,
                    new Label("("),
                    fromLabel,
                    new Label(";"),
                    toLabel,
                    new Label(" ] "),

                    remove
            );
            elementView.getChildren().add(hBox);
        }
    }

    private void createField() {
        name = new TextField();
        name.minWidth(50);
        if (type.equals("Date")) {
            isDate = true;
            from = new DatePicker(LocalDate.now());
            to = new DatePicker(LocalDate.now());
        } else {
            from = new TextField();
            to = new TextField();
            UnaryOperator<TextFormatter.Change> integerFilter = getChangeUnaryOperator();
            if (type.equals("Integer")) {
                ((TextField) from).setTextFormatter(new TextFormatter<>(
                        new IntegerStringConverter(),
                        0,
                        integerFilter
                ));
                ((TextField) to).setTextFormatter(new TextFormatter<>(
                        new IntegerStringConverter(),
                        0,
                        integerFilter
                ));
            } else {
                ((TextField) from).setTextFormatter(new TextFormatter<>(
                        new DoubleStringConverter(),
                        0.0,
                        integerFilter
                ));
                ((TextField) to).setTextFormatter(new TextFormatter<>(
                        new DoubleStringConverter(),
                        0.0,
                        integerFilter
                ));
            }
        }
        valueContainer.getChildren().addAll(name, new Label("( "), from, new Label(" ; "), to);
    }

    private UnaryOperator<TextFormatter.Change> getChangeUnaryOperator() {
        UnaryOperator<TextFormatter.Change> integerFilter = null;
        if (type.equals("Integer")) {
            integerFilter = change -> {
                String newText = change.getControlNewText();
                if (newText.matches("-?(0|[1-9][0-9]*)?")) {
                    return change;
                }
                return null;
            };
        } else if (type.equals("Float")) {
            integerFilter = change -> {
                Pattern validEditingState = Pattern.compile("-?(([1-9][0-9]*)|0)?(\\.[0-9]*)?");
                String newText = change.getControlNewText();
                if (validEditingState.matcher(newText).matches()) {
                    return change;
                }
                return null;
            };
        }
        return integerFilter;
    }

}