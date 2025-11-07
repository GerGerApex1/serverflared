package me.gergerapex1.serverflared.utils.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class YamlHandler {
    private final ObjectMapper yamlMapper;

    public YamlHandler() {
        YAMLFactory yamlFactory = YAMLFactory.builder()
            .build();
        this.yamlMapper = new ObjectMapper(yamlFactory);
    }

    public <T> T readFromYaml(String filePath, Class<T> clazz) throws IOException {
        try (var reader = Files.newBufferedReader(Path.of(filePath))) {
            return yamlMapper.readValue(reader, clazz);
        }
    }
    public <T> void writeToYaml(String filePath, T object) throws IOException {
        try (var writer = Files.newBufferedWriter(Path.of(filePath))) {
            yamlMapper.writerWithDefaultPrettyPrinter().writeValue(writer, object);
        }
    }
}
