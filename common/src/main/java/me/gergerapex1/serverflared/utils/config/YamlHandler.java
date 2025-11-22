package me.gergerapex1.serverflared.utils.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import me.gergerapex1.serverflared.utils.config.annonations.Comment;

public class YamlHandler {
    private final ObjectMapper yamlMapper;

    public YamlHandler() {
        YAMLFactory yamlFactory = YAMLFactory.builder()
            .build();
        ObjectMapper objectMapper = new ObjectMapper(yamlFactory)
            .findAndRegisterModules();
        this.yamlMapper = objectMapper;
    }

    public <T> T readFromYaml(String filePath, Class<T> clazz) throws IOException {
        try (var reader = Files.newBufferedReader(Path.of(filePath))) {
            return yamlMapper.readValue(reader, clazz);
        }
    }
    public <T> void writeToYaml(String filePath, T object) throws IOException {
        try (var writer = Files.newBufferedWriter(Path.of(filePath), StandardCharsets.UTF_8)) {

            Map<String, String> commentsByPath = new LinkedHashMap<>();
            collectComments(object, "", commentsByPath);

            String yamlString = yamlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
            String yamlWithComments = insertCommentsIntoYaml(yamlString, commentsByPath);
            writer.write(yamlWithComments);
        }
    }
    private static void collectComments(Object obj, String prefix, Map<String, String> out) {
        if (obj == null) return;
        Class<?> cls = obj.getClass();
        Comment typeComment = cls.getAnnotation(Comment.class);
        if (typeComment != null) {
            String key = prefix.isEmpty() ? cls.getSimpleName().toLowerCase() : prefix;
            out.putIfAbsent(key, typeComment.value());
        }

        for (Field f : cls.getDeclaredFields()) {
            f.setAccessible(true);
            String name = f.getName();
            String path = prefix.isEmpty() ? name : prefix + "." + name;
            Comment c = f.getAnnotation(Comment.class);
            if (c != null) out.putIfAbsent(path, c.value());

            Object child = null;
            try {
                child = f.get(obj);
            } catch (IllegalAccessException ignored) {}

            if (child != null && !isSimpleValue(child.getClass())) {
                collectComments(child, path, out);
            }
        }
    }

    private static boolean isSimpleValue(Class<?> c) {
        return c.isPrimitive()
            || Number.class.isAssignableFrom(c)
            || CharSequence.class.isAssignableFrom(c)
            || Boolean.class.isAssignableFrom(c)
            || c.isEnum();
    }

    /**
     * Insert comment lines before YAML keys matching the annotated dotted paths.
     * Assumes 2-space indentation per level (Jackson default).
     */
    private static String insertCommentsIntoYaml(String yaml, Map<String, String> commentsByPath) {
        // Sort by descending path length so nested entries are handled before parents
        List<Entry<String, String>> entries = new ArrayList<>(commentsByPath.entrySet());
        entries.sort(Comparator.comparingInt(e -> -e.getKey().length()));

        List<String> mutable = yaml.lines().collect(Collectors.toCollection(ArrayList::new));

        for (Map.Entry<String, String> e : entries) {
            String path = e.getKey();                // e.g. "server.host"
            String commentText = e.getValue();       // raw comment text
            String[] parts = path.split("\\.");
            String lastKey = parts[parts.length - 1];
            int expectedIndent = Math.max(0, parts.length - 1) * 2; // 2 spaces per level

            boolean inserted = false;
            for (int i = 0; i < mutable.size(); i++) {
                String line = mutable.get(i);
                String stripped = line.stripLeading();
                if (stripped.startsWith(lastKey + ":")) {
                    int leading = line.length() - stripped.length();
                    if (leading == expectedIndent) {
                        List<String> commentLines = Arrays.stream(commentText.split("\n"))
                            .map(cl -> " ".repeat(expectedIndent) + "# " + cl)
                            .collect(Collectors.toList());
                        mutable.addAll(i, commentLines);
                        inserted = true;
                        break;
                    }
                }
            }
            // If the key wasn't found, we skip it (maybe defaulted away)
        }

        return String.join("\n", mutable);
    }
}
