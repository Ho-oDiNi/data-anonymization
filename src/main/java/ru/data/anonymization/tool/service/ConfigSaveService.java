package ru.data.anonymization.tool.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.data.anonymization.tool.builder.DialogBuilder;
import ru.data.anonymization.tool.dto.SaveDto;

import java.io.*;

@Service
@RequiredArgsConstructor
public class ConfigSaveService {

    private final DepersonalizationService depersonalizationService;
    private final DataPreparationService preparationService;

    public void saveConfig(String path) {
        SaveDto save = new SaveDto();
        save.setPreparationMap(preparationService.getPreparationMap());
        save.setMethodsMap(depersonalizationService.getMethodsMap());
        try {
            FileOutputStream outputStream = new FileOutputStream(path);
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
            objectOutputStream.writeObject(save);
            objectOutputStream.close();
        } catch (IOException e) {
            DialogBuilder.createErrorDialog("Не удалось сохранить файл!");
        }
    }

    public void downloadConfig(String path) {
        SaveDto save;
        try {
            FileInputStream fileInputStream = new FileInputStream(path);
            ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
            save = (SaveDto) objectInputStream.readObject();

            preparationService.setPreparationMap(save.getPreparationMap());
            depersonalizationService.setMethodsMap(save.getMethodsMap());
        } catch (IOException | ClassNotFoundException e) {
            DialogBuilder.createErrorDialog("Не удалось прочитать файл!");
        }
    }
}
