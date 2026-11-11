import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Shared helper for the hooks: reads the hook JSON from stdin and pulls out string fields.
 *
 * The JDK has no JSON parser. The hook input is simple enough to read one string field
 * by name, e.g. field("file_path") from {"tool_input": {"file_path": "..."}}.
 */
public class HookInput {

    final String json;

    HookInput(String json) {
        this.json = json;
    }

    static HookInput fromStdin() throws IOException {
        return new HookInput(new String(System.in.readAllBytes(), StandardCharsets.UTF_8));
    }

    /** First string value with this key, unescaped; null if missing. */
    String field(String name) {
        String key = "\"" + name + "\"";
        int at = json.indexOf(key);
        while (at >= 0) {
            int i = skipSpaces(at + key.length());
            if (i < json.length() && json.charAt(i) == ':') {
                i = skipSpaces(i + 1);
                if (i < json.length() && json.charAt(i) == '"') {
                    return readString(i + 1);
                }
                return null; // not a string, e.g. null
            }
            at = json.indexOf(key, at + 1);
        }
        return null;
    }

    /** True if the key has the JSON value true, e.g. "stop_hook_active": true. */
    boolean flag(String name) {
        return json.matches("(?s).*\"" + name + "\"\\s*:\\s*true\\b.*");
    }

    private int skipSpaces(int i) {
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        return i;
    }

    private String readString(int i) {
        StringBuilder out = new StringBuilder();
        while (i < json.length()) {
            char c = json.charAt(i++);
            if (c == '"') {
                return out.toString();
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char e = json.charAt(i++);
            switch (e) {
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                case 'r' -> out.append('\r');
                case 'u' -> {
                    out.append((char) Integer.parseInt(json.substring(i, i + 4), 16));
                    i += 4;
                }
                default -> out.append(e); // \" \\ \/
            }
        }
        return out.toString();
    }

    /** Quotes a string for JSON output. */
    static String quote(String text) {
        if (text == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\t' -> out.append("\\t");
                case '\r' -> out.append("\\r");
                default -> out.append(c);
            }
        }
        return out.append('"').toString();
    }
}
