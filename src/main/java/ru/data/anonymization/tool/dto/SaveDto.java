package ru.data.anonymization.tool.dto;

import lombok.Data;
import ru.data.anonymization.tool.methods.options.MaskItem;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Data
public class SaveDto implements Serializable {
    private Map<String, DataPreparationDto> preparationMap = new HashMap<>();
    private Map<String, MaskItem> methodsMap = new HashMap<>();
}
