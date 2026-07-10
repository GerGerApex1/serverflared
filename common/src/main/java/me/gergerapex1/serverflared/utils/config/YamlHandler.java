package me.gergerapex1.serverflared.utils.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import me.gergerapex1.serverflared.Constants;
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
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath))) {
            return yamlMapper.readValue(reader, clazz);
        }
    }
    public <T> void writeToYaml(String filePath, T object) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filePath), StandardCharsets.UTF_8)) {

            Map<String, String> commentsByPath = new LinkedHashMap<>();
            collectComments(object, "", commentsByPath);

            String yamlString = yamlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
            String yamlWithComments = insertCommentsIntoYaml(yamlString, commentsByPath);
            writer.write(yamlWithComments);
        }
    }
    public <T> void overwriteFileWithYaml(String filePath, T object) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filePath), StandardCharsets.UTF_8,
            StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {

            Map<String, String> commentsByPath = new LinkedHashMap<>();
            collectComments(object, "", commentsByPath);

            String yamlString = yamlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);

            writer.write(yamlString);
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

        // Java 8-compatible line splitting
        List<String> mutable = splitLines(yaml);

        for (Map.Entry<String, String> e : entries) {
            String path = e.getKey();                // e.g. "server.host"
            String commentText = e.getValue();       // raw comment text
            String[] parts = path.split("\\.");
            String lastKey = parts[parts.length - 1];
            int expectedIndent = Math.max(0, parts.length - 1) * 2; // 2 spaces per level

            boolean inserted = false;
            for (int i = 0; i < mutable.size(); i++) {
                String line = mutable.get(i);
                String stripped = stripLeading(line);
                if (stripped.startsWith(lastKey + ":")) {
                    int leading = countLeadingSpaces(line);
                    if (leading == expectedIndent) {
                        List<String> commentLines = Arrays.stream(splitLines(commentText).toArray(new String[0]))
                            .map(cl -> repeat(" ", expectedIndent) + "# " + cl)
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

    // Helpers for Java 8 compatibility
    private static List<String> splitLines(String s) {
        // Preserve trailing empty line if present
        String[] arr = s.split("\r?\n", -1);
        return new ArrayList<>(Arrays.asList(arr));
    }

    private static String stripLeading(String s) {
        int i = 0;
        int len = s.length();
        while (i < len) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t') {
                i++;
            } else {
                break;
            }
        }
        return s.substring(i);
    }

    private static int countLeadingSpaces(String s) {
        int i = 0;
        int len = s.length();
        while (i < len && s.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    private static String repeat(String str, int count) {
        if (count <= 0) return "";
        StringBuilder sb = new StringBuilder(str.length() * count);
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }
}
