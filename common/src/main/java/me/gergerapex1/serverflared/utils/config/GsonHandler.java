package me.gergerapex1.serverflared.utils.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class GsonHandler {
    private final Gson gson;

    public GsonHandler() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public <T> T readFromJson(String filePath, Class<T> clazz) throws IOException {
        try (FileReader reader = new FileReader(filePath)) {
            return gson.fromJson(reader, clazz);
        }
    }

    public <T> void writeToJson(String filePath, T object) throws IOException {
        try (FileWriter writer = new FileWriter(filePath)) {
            gson.toJson(object, writer);
        }
    }
}
