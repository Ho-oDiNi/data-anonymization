package ru.data.anonymization.tool.controller.method;

import java.io.IOException;
import java.util.List;
import java.util.function.UnaryOperator;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextFormatter.Change;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.converter.IntegerStringConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.data.anonymization.tool.builder.DialogBuilder;
import ru.data.anonymization.tool.dto.enums.ShowMode;
import ru.data.anonymization.tool.methods.options.type.LocalSuppression;
import ru.data.anonymization.tool.service.DepersonalizationService;
import ru.data.anonymization.tool.service.TableInfoService;
import ru.data.anonymization.tool.util.ComponentUtils;

@Component
@RequiredArgsConstructor
public class LocalSuppressionView {

    @FXML
    private TextField customName;
    @FXML
    private TextField tableName;

    @FXML
    private ListView<String> leftListView;
    @FXML
    private ListView<String> rightListView;
    @FXML
    private HBox buttonContainer;

    private static final int MAX_LENGTH = 40;

    private final TableInfoService tableInfoService;
    private final DepersonalizationService depersonalizationService;

    private Stage stage;

    // Новые элементы:
    @FXML
    private ComboBox<String> nChoiceBox;
    @FXML
    private HBox kContainer;
    @FXML
    private ComboBox<String> kChoiceBox;
    @FXML
    private HBox replacementValueContainer;
    @FXML
    private TextField replacementValueField;

    public void configView(String title, String table, ShowMode mode, String name, VBox vBox) throws
            IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(LocalSuppressionView.class.getResource(
                "local-suppression-view.fxml"));
        stage = ComponentUtils.modalStageView(
                fxmlLoader,
                "Настройка метода (" + title + ")",
                "gears.png"
        );

        tableName.setText(table);

        // Ограничение на ввод только положительных чисел в поле kValue
        UnaryOperator<TextFormatter.Change> integerFilter = change -> {
            String newText = change.getControlNewText();
            if (newText.matches("([1-9][0-9]*)?")) {
                return change;
            }
            return null;
        };

        //        kValue.setTextFormatter(new TextFormatter<>(
        //                new IntegerStringConverter(),
        //                1,
        //                integerFilter
        //        ));

        List<String> columnList = tableInfoService.getColumnNames(table);

        // Инициализация выпадающих списков для n и k
        nChoiceBox.getItems().addAll("1", "2", "3");
        kChoiceBox.getItems().addAll("1", "2");

        // Кнопка "Сохранить"
        Button saveButton = new Button("Сохранить");
        saveButton.setStyle("-fx-background-color: #4bbd50; -fx-text-fill: white;");
        saveButton.setOnAction(event -> saveAction(table, mode));
        buttonContainer.getChildren().add(saveButton);

        if (mode.equals(ShowMode.EDIT)) {
            customName.setDisable(true);
            customName.setText(name);

            LocalSuppression dto = (LocalSuppression) depersonalizationService.getMethod(name);

            List<String> selectedElement = dto.getColumn();
            columnList.removeAll(selectedElement);

            ObservableList<String> leftItems = FXCollections.observableArrayList(columnList);
            leftListView.setItems(leftItems);

            ObservableList<String> rightItems = FXCollections.observableArrayList(selectedElement);
            rightListView.setItems(rightItems);

            // Установка выбранных значений n, k, replacementValue из dto, если они есть
            nChoiceBox.setValue(String.valueOf(dto.getN()));

            if (dto.getN() == 3) {
                kContainer.setVisible(true);
                kContainer.setManaged(true);
                kChoiceBox.setValue(String.valueOf(dto.getK())); // kMode — способ расчета, нужно добавить в твой DTO

                if (dto.getK() == 2) {
                    replacementValueContainer.setVisible(true);
                    replacementValueContainer.setManaged(true);
                    replacementValueField.setText(dto.getReplacementValue()); // значение для замены
                }
            }

            Button deleteButton = new Button("Удалить");
            deleteButton.setStyle("-fx-background-color: #e83434; -fx-text-fill: white;");
            deleteButton.setOnAction(event -> deleteAction(name, vBox));
            buttonContainer.getChildren().add(deleteButton);
        } else {
            ObservableList<String> leftItems = FXCollections.observableArrayList(columnList);
            leftListView.setItems(leftItems);
        }

        stage.show();
    }

    @FXML
    private void onNChoiceChanged() {
        String selectedN = nChoiceBox.getValue();
        boolean isN3 = "3".equals(selectedN);

        kContainer.setVisible(isN3);
        kContainer.setManaged(isN3);

        if (!isN3) {
            replacementValueContainer.setVisible(false);
            replacementValueContainer.setManaged(false);
        }
    }

    @FXML
    private void onKChoiceChanged() {
        String selectedK = kChoiceBox.getValue();
        boolean isK2 = "2".equals(selectedK);

        replacementValueContainer.setVisible(isK2);
        replacementValueContainer.setManaged(isK2);
    }

    @FXML
    private void moveToRight() {
        String selectedItem = leftListView.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            leftListView.getItems().remove(selectedItem);
            rightListView.getItems().add(selectedItem);
        }
    }

    @FXML
    private void moveToLeft() {
        String selectedItem = rightListView.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            rightListView.getItems().remove(selectedItem);
            leftListView.getItems().add(selectedItem);
        }
    }

    @FXML
    private void moveToRightClick(MouseEvent mouseEvent) {
        if (mouseEvent.getClickCount() > 1) {
            moveToRight();
        }
    }

    @FXML
    private void moveToLeftClick(MouseEvent mouseEvent) {
        if (mouseEvent.getClickCount() > 1) {
            moveToLeft();
        }
    }

    private void saveAction(String table, ShowMode mode) {
        List<String> columns = rightListView.getItems().stream().toList();

        if (customName.getText().isEmpty() || customName.getText().isBlank()) {
            DialogBuilder.createErrorDialog("Нужно ввести название!");
        } else if (columns.isEmpty()) {
            DialogBuilder.createErrorDialog("Нужно выбрать хотя бы один атрибут!");
        } else {
            LocalSuppression dto = new LocalSuppression();
            dto.setNameTable(table);
            dto.setN(Integer.parseInt(nChoiceBox.getValue()));
            dto.setNamesColumn(columns);
            dto.setTableInfoService(tableInfoService);

            int k = kChoiceBox.getValue() != null ? Integer.parseInt(kChoiceBox.getValue()) : -1;
            dto.setK(k);
            String replacementValue = replacementValueField.getText();
            if (replacementValue != null) {
                dto.setReplacementValue(replacementValue);
            }
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

}
