package ru.data.anonymization.tool.dto.enums;

import lombok.Getter;
import ru.data.anonymization.tool.methods.options.type.*;

@Getter
public enum MaskMethods {
        DateAging(
                        "DateAging",
                        ru.data.anonymization.tool.methods.options.type.DateAging.class,
                        "Метод Старения дат"),
        Decomposition(
                        "Decomposition",
                        ru.data.anonymization.tool.methods.options.type.Decomposition.class,
                        "Метод Декомпозиции"),
        // Строки
        GeneralizationString(
                        "GeneralizationString",
                        ru.data.anonymization.tool.methods.options.type.GeneralizationString.class,
                        "Метод Обобщения"),
        // Значения и даты
        GeneralizationValue(
                        "GeneralizationValue",
                        ru.data.anonymization.tool.methods.options.type.GeneralizationValue.class,
                        "Метод Обобщения по диапазону"),
        Identifier(
                        "Identifier",
                        ru.data.anonymization.tool.methods.options.type.Identifier.class,
                        "Введение Идентификаторов"),
        MicroAggregation("MicroAggregation", MicroAggregation.class, "Метод Микроагрегирования"),
        MicroAggregationBySingleAxis(
                        "MicroAggregationBySingleAxis",
                        MicroAggregationBySingleAxis.class,
                        "Микроагрегирование по оси"),
        Round("Round", Round.class, "Метод Округления"),
        // Округление даты
        RoundDate("RoundDate", RoundDate.class, "Метод Обобщения по формату"),
        Shuffle("Shuffle", Shuffle.class, "Метод Перемешивания"),
        ValueReplacement("ValueReplacement", ValueReplacement.class, "Метод маскирования"),
        ValueReplacementByPattern(
                        "ValueReplacementByPattern",
                        ValueReplacementByPattern.class,
                        "Метод Маскирования"),
        ValueReplacementFromFile(
                        "ValueReplacementFromFile",
                        ValueReplacementFromFile.class,
                        "Метод маскирования из файла"),
        ValueVariance("ValueVariance", ValueVariance.class, "Метод Добавления шума"),
    DeleteMethod("DeleteMethod", DeleteMethod.class, "Метод удаления"),
    SelectionMethod("SelectionMethod", SelectionMethod.class, "Метод выборки"),
    LocalSuppression("LocalSuppression", LocalSuppression.class, "Метод локального подавления");


        final String name;
        final Class<?> methodClass;
        final String nameRus;

        MaskMethods(String name, Class<?> methodClass, String nameRus) {
                this.name = name;
                this.methodClass = methodClass;
                this.nameRus = nameRus;
        }
}
