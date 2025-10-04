package ru.data.anonymization.tool.service;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.data.anonymization.tool.methods.options.MaskItem;
import ru.data.anonymization.tool.builder.DialogBuilder;
import ru.data.anonymization.tool.config.AppContext;
import ru.data.anonymization.tool.controller.method.*;
import ru.data.anonymization.tool.dto.enums.MaskMethods;
import ru.data.anonymization.tool.dto.enums.ShowMode;
import ru.data.anonymization.tool.util.ComponentUtils;

@Service
@RequiredArgsConstructor
public class ViewService {

    private final DepersonalizationService depersonalizationService;

    private final ShuffleView shuffleView;
    private final DateAgingView dateAgingView;
    private final IdentifierView identifierView;
    private final MicroAggregationView microAggregationView;
    private final LocalSuppressionView localSuppressionView;
    private final MicroAggregationBySingleAxisView microAggregationBySingleAxisView;
    private final ValueReplacementView valueReplacementView;
    private final ValueReplacementByPatternView valueReplacementByPatternView;
    private final RoundView roundView;
    private final ValueReplacementFromFileView valueReplacementFromFileView;
    private final ValueVarianceView valueVarianceView;
    private final SelectionMethodView selectionMethodView;
    private final DecompositionView decompositionView;
    private final GeneralizationStringView generalizationStringView;
    private final GeneralizationValueView generalizationValueView;
    private final RoundDateView roundDateView;
    private final DeleteMethodView deleteMethodView;

    //Создаем кнопки для редактирования методов
    public void setConfigMethods(VBox config) {
        config.getChildren().clear();
        depersonalizationService.getConfig().forEach(name -> {
            HBox buttonBox = new HBox();
            buttonBox.setAlignment(Pos.CENTER);

            buttonBox.setStyle(
                    "-fx-padding: 10px 0px 10px 0px; -fx-background-color: #D3D3D3; -fx-border-color: #C0C0C0;");
            buttonBox.setId(name.replace(" ", ""));

            Button button = ComponentUtils.createButton(
                    name,
                    null,
                    "-fx-background-color: transparent;-fx-focus-color: transparent;-fx-faint-focus-color: transparent; -fx-font-size: 18px;"
            );
            button.setOnAction(event -> settingsConfiguration(config, name));

            Button removeButton = ComponentUtils.createButton(
                    "X",
                    null,
                    "-fx-background-color: red; -fx-text-fill: white; -fx-border-radius: 20;"
            );
            removeButton.setOnAction(event -> {
                depersonalizationService.removeMethod(name);
                config.getChildren().remove(buttonBox);
            });

            buttonBox.getChildren().add(button);
            buttonBox.getChildren().add(removeButton);
            config.getChildren().add(buttonBox);

        });
    }

    public void maskConfiguration(
            MaskMethods nameMethod,
            String currentTableName,
            String currentColumnName,
            String dateType) {
        if (AppContext.needRefresh) {
            DialogBuilder.createErrorDialog("Сначало нужно обновить соединение!");
            return;
        }
        try {
            switch (nameMethod.getName()) {
                case "Shuffle" -> shuffleView.configView(
                        nameMethod.getNameRus(),
                        currentTableName,
                        ShowMode.CREATE,
                        null,
                        null
                );
                case "DateAging" -> dateAgingView.configView(
                        nameMethod.getNameRus(),
                        currentTableName,
                        currentColumnName,
                        ShowMode.CREATE,
                        null,
                        null
                );
                case "Identifier" -> identifierView.configView(
                        nameMethod.getNameRus(),
                        currentTableName,
                        ShowMode.CREATE,
                        null,
                        null
                );
                case "MicroAggregation" -> microAggregationView.configView(
                        nameMethod.getNameRus(),
                        currentTableName,
                        ShowMode.CREATE,
                        null,
                        null
                );
                case "MicroAggregationBySingleAxis" -> microAggregationBySingleAxisView.configView(
                        nameMethod.getNameRus(),
                        currentTableName,
                        ShowMode.CREATE,
                        null,
                        null
                );
                case "ValueReplacement" -> valueReplacementView.configView(
                        nameMethod.getNameRus(),
                        currentTableName,
                        currentColumnName,
                        ShowMode.CREATE,
                        null,
                        null
                );
                case "ValueReplacementByPattern" -> valueReplacementByPatternView.configView(
                        nameMethod.getNameRus(),
                        currentTableName,
                        currentColumnName,
                        ShowMode.CREATE,
                        null,
                        null
                );
                case "Round" -> roundView.configView(
                        nameMethod.getNameRus(),
                        currentTableName,
                        currentColumnName,
                        ShowMode.CREATE,
                        null,
                        null
                );
                case "ValueReplacementFromFile" -> valueReplacementFromFileView.configView(
                        nameMethod.getNameRus(),
                        currentTableName,
                        currentColumnName,
                        ShowMode.CREATE,
                        null,
                        null
                );
                case "ValueVariance" -> valueVarianceView.configView(
                        nameMethod.getNameRus(),
                        currentTableName,
                        currentColumnName,
                        dateType,
                        ShowMode.CREATE,
                        null,
                        null
                );
                case "Decomposition" -> decompositionView.configView(
                        nameMethod.getNameRus(),
                        currentTableName,
                        ShowMode.CREATE,
                        null,
                        null
                );
                case "GeneralizationString" -> generalizationStringView.configView(
                        nameMethod.getNameRus(),
                        currentTableName,
                        currentColumnName,
                        ShowMode.CREATE,
                        null,
                        null
                );
                case "GeneralizationValue" -> generalizationValueView.configView(
                        nameMethod.getNameRus(),
                        currentTableName,
                        currentColumnName,
                        dateType,
                        ShowMode.CREATE,
                        null,
                        null
                );
                case "RoundDate" -> roundDateView.configView(
                        nameMethod.getNameRus(),
                        currentTableName,
                        currentColumnName,
                        ShowMode.CREATE,
                        null,
                        null
                );
                case "DeleteMethod" -> deleteMethodView.configView(
                        nameMethod.getNameRus(),
                        currentTableName,
                        ShowMode.CREATE,
                        null,
                        null
                );
                case "SelectionMethod" -> selectionMethodView.configView(
                        nameMethod.getNameRus(),
                        currentTableName,
                        currentColumnName,
                        ShowMode.CREATE,
                        null,
                        null
                );
                case "LocalSuppression" -> localSuppressionView.configView(
                        nameMethod.getNameRus(),
                        currentTableName,
                        ShowMode.CREATE,
                        null,
                        null
                );
                default -> System.out.println("View not found");
            }
        } catch (Exception ignore) {
        }
    }

    private void settingsConfiguration(VBox config, String name) {
        if (AppContext.needRefresh) {
            DialogBuilder.createErrorDialog("Сначало нужно обновить соединение!");
            return;
        }
        MaskItem item = depersonalizationService.getMethod(name);
        MaskMethods nameMethod = MaskMethods.valueOf(item.getClass().getSimpleName());

        try {
            switch (nameMethod.getName()) {
                case "Shuffle" -> shuffleView.configView(
                        nameMethod.getNameRus(),
                        item.getTable(),
                        ShowMode.EDIT,
                        name,
                        config
                );
                case "DateAging" -> dateAgingView.configView(
                        nameMethod.getNameRus(),
                        item.getTable(),
                        item.getColumn().get(0),
                        ShowMode.EDIT,
                        name,
                        config
                );
                case "Identifier" -> identifierView.configView(
                        nameMethod.getNameRus(),
                        item.getTable(),
                        ShowMode.EDIT,
                        name,
                        config
                );
                case "MicroAggregation" -> microAggregationView.configView(
                        nameMethod.getNameRus(),
                        item.getTable(),
                        ShowMode.EDIT,
                        name,
                        config
                );
                case "MicroAggregationBySingleAxis" -> microAggregationBySingleAxisView.configView(
                        nameMethod.getNameRus(),
                        item.getTable(),
                        ShowMode.EDIT,
                        name,
                        config
                );
                case "ValueReplacement" -> valueReplacementView.configView(
                        nameMethod.getNameRus(),
                        item.getTable(),
                        item.getColumn().get(0),
                        ShowMode.EDIT,
                        name,
                        config
                );
                case "ValueReplacementByPattern" -> valueReplacementByPatternView.configView(
                        nameMethod.getNameRus(),
                        item.getTable(),
                        item.getColumn().get(0),
                        ShowMode.EDIT,
                        name,
                        config
                );
                case "Round" -> roundView.configView(
                        nameMethod.getNameRus(),
                        item.getTable(),
                        item.getColumn().get(0),
                        ShowMode.EDIT,
                        name,
                        config
                );
                case "ValueReplacementFromFile" -> valueReplacementFromFileView.configView(
                        nameMethod.getNameRus(),
                        item.getTable(),
                        item.getColumn().get(0),
                        ShowMode.EDIT,
                        name,
                        config
                );
                case "ValueVariance" -> valueVarianceView.configView(
                        nameMethod.getNameRus(),
                        item.getTable(),
                        item.getColumn().get(0),
                        null,
                        ShowMode.EDIT,
                        name,
                        config
                );
                case "Decomposition" -> decompositionView.configView(
                        nameMethod.getNameRus(),
                        item.getTable(),
                        ShowMode.EDIT,
                        name,
                        config
                );
                case "GeneralizationString" -> generalizationStringView.configView(
                        nameMethod.getNameRus(),
                        item.getTable(),
                        item.getColumn().get(0),
                        ShowMode.EDIT,
                        name,
                        config
                );
                case "GeneralizationValue" -> generalizationValueView.configView(
                        nameMethod.getNameRus(),
                        item.getTable(),
                        item.getColumn().get(0),
                        null,
                        ShowMode.EDIT,
                        name,
                        config
                );
                case "RoundDate" -> roundDateView.configView(
                        nameMethod.getNameRus(),
                        item.getTable(),
                        item.getColumn().get(0),
                        ShowMode.EDIT,
                        name,
                        config
                );
                case "DeleteMethod" -> deleteMethodView.configView(
                        nameMethod.getNameRus(),
                        item.getTable(),
                        ShowMode.EDIT,
                        name,
                        config
                );
                case "SelectionMethod" -> selectionMethodView.configView(
                        nameMethod.getNameRus(),
                        item.getTable(),
                        item.getColumn().get(0),
                        ShowMode.CREATE,
                        null,
                        null
                );
                case "LocalSuppression" -> localSuppressionView.configView(
                        nameMethod.getNameRus(),
                        item.getTable(),
                        ShowMode.EDIT,
                        name,
                        config
                );
                default -> System.out.println("View not found");
            }
        } catch (Exception ignore) {
        }
    }

}
