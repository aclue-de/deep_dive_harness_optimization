import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader (JDK only). Objects become Map, arrays List, numbers Double,
 * strings String, true/false Boolean, null stays null.
 */
public class Json {

    private final String text;
    private int pos;

    private Json(String text) {
        this.text = text;
    }

    /** Parses one JSON document; throws on malformed input. */
    static Object parse(String text) {
        Json json = new Json(text);
        Object value = json.value();
        json.skipSpaces();
        if (json.pos != text.length()) {
            throw new IllegalArgumentException("unexpected data at " + json.pos);
        }
        return value;
    }

    /** Follows the keys through nested objects; null if any step is missing. */
    static Object get(Object node, String... path) {
        for (String key : path) {
            if (!(node instanceof Map<?, ?> map)) {
                return null;
            }
            node = map.get(key);
        }
        return node;
    }

    private Object value() {
        skipSpaces();
        return switch (text.charAt(pos)) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++;
        skipSpaces();
        if (text.charAt(pos) == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipSpaces();
            String key = string();
            skipSpaces();
            expect(':');
            map.put(key, value());
            skipSpaces();
            if (text.charAt(pos) == ',') {
                pos++;
            } else {
                expect('}');
                return map;
            }
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        pos++;
        skipSpaces();
        if (text.charAt(pos) == ']') {
            pos++;
            return list;
        }
        while (true) {
            list.add(value());
            skipSpaces();
            if (text.charAt(pos) == ',') {
                pos++;
            } else {
                expect(']');
                return list;
            }
        }
    }

    private String string() {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (true) {
            char c = text.charAt(pos++);
            if (c == '"') {
                return out.toString();
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char e = text.charAt(pos++);
            switch (e) {
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                case 'r' -> out.append('\r');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'u' -> {
                    out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    pos += 4;
                }
                default -> out.append(e); // \" \\ \/
            }
        }
    }

    private Double number() {
        int start = pos;
        while (pos < text.length() && "+-.eE0123456789".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
        if (start == pos) {
            throw new IllegalArgumentException("unexpected character at " + pos);
        }
        return Double.valueOf(text.substring(start, pos));
    }

    private Object literal(String word, Object value) {
        if (!text.startsWith(word, pos)) {
            throw new IllegalArgumentException("unexpected character at " + pos);
        }
        pos += word.length();
        return value;
    }

    private void expect(char c) {
        if (text.charAt(pos) != c) {
            throw new IllegalArgumentException("expected " + c + " at " + pos);
        }
        pos++;
    }

    private void skipSpaces() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }
}
