package dev.ringlab.adapter.out.registry;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.*;
import java.util.*;

/** Restricted RFC 8785 profile; deliberately independent of Jackson's output escaping defaults. */
public final class CanonicalJson {
    public static final long MAX_INTEGER = 9007199254740991L;
    public static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
    private static final JsonFactory FACTORY = JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(80).maxStringLength(10_000_000).build()).build();
    private CanonicalJson() {}

    public static JsonNode parse(byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            if (text.startsWith("\uFEFF")) throw new IllegalArgumentException();
            try (JsonParser parser = FACTORY.createParser(text)) {
                if (parser.nextToken() == null) throw new IllegalArgumentException();
                JsonNode node = read(parser);
                if (parser.nextToken() != null) throw new IllegalArgumentException();
                return node;
            }
        } catch (Exception e) {
            if (e instanceof ContractException c) throw c;
            throw new ContractException("/", "INVALID_JSON", "Use strict UTF-8 JSON with unique keys and safe integer tokens.");
        }
    }

    private static JsonNode read(JsonParser p) throws java.io.IOException {
        return switch (p.currentToken()) {
            case START_OBJECT -> {
                ObjectNode o = MAPPER.createObjectNode();
                while (p.nextToken() != JsonToken.END_OBJECT) {
                    if (p.currentToken() != JsonToken.FIELD_NAME) throw new IllegalArgumentException();
                    String key = p.currentName(); checkString(key); p.nextToken(); o.set(key, read(p));
                }
                yield o;
            }
            case START_ARRAY -> {
                ArrayNode a = MAPPER.createArrayNode();
                while (p.nextToken() != JsonToken.END_ARRAY) a.add(read(p));
                yield a;
            }
            case VALUE_STRING -> { checkString(p.getText()); yield TextNode.valueOf(p.getText()); }
            case VALUE_NUMBER_INT -> {
                String token = p.getText();
                if (!token.matches("-?(0|[1-9][0-9]*)") || token.equals("-0")) throw new IllegalArgumentException();
                long value = Long.parseLong(token);
                if (value < -MAX_INTEGER || value > MAX_INTEGER) throw new IllegalArgumentException();
                yield LongNode.valueOf(value);
            }
            case VALUE_TRUE -> BooleanNode.TRUE;
            case VALUE_FALSE -> BooleanNode.FALSE;
            case VALUE_NULL -> NullNode.instance;
            default -> throw new IllegalArgumentException();
        };
    }

    public static byte[] bytes(JsonNode value) {
        StringBuilder result = new StringBuilder(); append(value, result);
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }
    public static JsonNode tree(Object value) { return MAPPER.valueToTree(value); }
    public static String digest(JsonNode value) { return hash(bytes(value)); }
    public static String hash(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static void append(JsonNode n, StringBuilder out) {
        if (n == null) throw new IllegalArgumentException("Missing JSON value");
        if (n.isObject()) {
            out.append('{'); List<String> keys = new ArrayList<>(); n.fieldNames().forEachRemaining(keys::add);
            Collections.sort(keys); boolean first = true;
            for (String key : keys) { if (!first) out.append(','); first = false; quote(key, out); out.append(':'); append(n.get(key), out); }
            out.append('}');
        } else if (n.isArray()) {
            out.append('['); boolean first = true;
            for (JsonNode child : n) { if (!first) out.append(','); first = false; append(child, out); } out.append(']');
        } else if (n.isTextual()) quote(n.textValue(), out);
        else if (n.isNull() || n.isBoolean()) out.append(n);
        else if (n.isIntegralNumber() && n.canConvertToLong() && n.longValue() >= -MAX_INTEGER && n.longValue() <= MAX_INTEGER) out.append(n.longValue());
        else throw new ContractException("/", "INVALID_NUMBER", "Only safe integer JSON numbers are allowed.");
    }
    private static void checkString(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) throw new IllegalArgumentException("Invalid Unicode");
            } else if (Character.isLowSurrogate(c)) throw new IllegalArgumentException("Invalid Unicode");
        }
    }
    private static void quote(String value, StringBuilder out) {
        checkString(value); out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b"); case '\t' -> out.append("\\t"); case '\n' -> out.append("\\n");
                case '\f' -> out.append("\\f"); case '\r' -> out.append("\\r");
                default -> { if (c < 32) out.append(String.format(Locale.ROOT, "\\u%04x", (int)c)); else out.append(c); }
            }
        }
        out.append('"');
    }
}
