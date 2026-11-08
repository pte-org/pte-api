package com.pte.itembank.internal.generation.prompt;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.StringNode;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Makes the JSON example of a reply from its rows. A row is {@code name: value}; the value is read as JSON when it is
 * JSON (a number, a list, {@code null}, a quoted text) and is otherwise taken as plain text, so a writer types
 * {@code Environmental Science} or {@code [0, 2, 4]} without thinking about quotes.
 */
public final class PromptFieldsJson {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final String ROW_SEPARATOR = ":";
    private static final String INDENT = "  ";
    private static final String LINE_BREAK = "\n";
    private static final String COMMA = ",";
    private static final String NAME_VALUE_SEPARATOR = ": ";

    /** One row of a reply: the field name and the example value as typed. */
    public record Row(String name, String value) {
    }

    private PromptFieldsJson() {
    }

    /** The row written as {@code name: value}, or null when it has no field name. */
    public static Row parseRow(String item) {
        int separator = item.indexOf(ROW_SEPARATOR);
        if (separator < 1 || item.substring(0, separator).isBlank()) {
            return null;
        }
        return new Row(item.substring(0, separator).strip(), item.substring(separator + 1).strip());
    }

    /** The reply as indented JSON; a row without a field name is left out. */
    public static String compose(List<String> rows) {
        List<Row> parsed = rows.stream().map(PromptFieldsJson::parseRow).filter(Objects::nonNull).toList();
        if (parsed.isEmpty()) {
            return "{}";
        }
        StringBuilder json = new StringBuilder("{").append(LINE_BREAK);
        for (int index = 0; index < parsed.size(); index++) {
            Row row = parsed.get(index);
            json.append(INDENT).append(quote(row.name())).append(NAME_VALUE_SEPARATOR);
            write(valueOf(row.value()), 1, json);
            json.append(index < parsed.size() - 1 ? COMMA : "").append(LINE_BREAK);
        }
        return json.append("}").toString();
    }

    static JsonNode valueOf(String raw) {
        if (raw.isBlank()) {
            return StringNode.valueOf("");
        }
        try {
            return MAPPER.readTree(raw);
        } catch (JacksonException notJson) {
            return StringNode.valueOf(raw);
        }
    }

    private static void write(JsonNode node, int depth, StringBuilder json) {
        if (node.isObject()) {
            List<Map.Entry<String, JsonNode>> entries = List.copyOf(node.properties());
            writeContainer(entries.size(), true, depth, json, index -> quote(entries.get(index).getKey()),
                    index -> entries.get(index).getValue());
        } else if (node.isArray()) {
            writeContainer(node.size(), false, depth, json, index -> null, node::get);
        } else {
            json.append(node.toString());
        }
    }

    private static void writeContainer(int size, boolean object, int depth, StringBuilder json,
                                       java.util.function.IntFunction<String> name,
                                       java.util.function.IntFunction<JsonNode> value) {
        String open = object ? "{" : "[";
        String close = object ? "}" : "]";
        if (size == 0) {
            json.append(open).append(close);
            return;
        }
        json.append(open).append(LINE_BREAK);
        for (int index = 0; index < size; index++) {
            json.append(INDENT.repeat(depth + 1));
            if (object) {
                json.append(name.apply(index)).append(NAME_VALUE_SEPARATOR);
            }
            write(value.apply(index), depth + 1, json);
            json.append(index < size - 1 ? COMMA : "").append(LINE_BREAK);
        }
        json.append(INDENT.repeat(depth)).append(close);
    }

    private static String quote(String text) {
        return StringNode.valueOf(text).toString();
    }
}
