import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Status line for slide 45: shows how the session uses the prompt cache.
 * <p>
 * Claude Code sends session data as JSON on stdin and shows the first line this prints.
 * Start: claude --settings tools/status-line/settings.json   (needs Claude Code 2.1.260 or later)
 * <p>
 * read 2.1k write 45.2k │ hit 61% │ misses 2 (90.4k re-cached, last: tools_changed +0/-1)
 *   │ total $0.80 │ ctx ███░░░░░ 42% │ Opus 5.5 · high
 * <p>
 * read / write = tokens read from / written to the cache by the last request,
 * hit = share of all input tokens served from the cache this session,
 * misses = requests that re-wrote content the cache already held.
 * Set NO_COLOR to switch the colors off. Errors print a message instead of failing,
 * because a failing command blanks the status line.
 */
public class StatusLine {

    static final boolean COLOR = System.getenv("NO_COLOR") == null || System.getenv("NO_COLOR").isEmpty();

    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        try {
            String input = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
            out.println(render(Json.parse(input)));
        } catch (Exception e) {
            out.println("cache status line: invalid input");
        }
    }

    static String render(Object root) {
        Object cache = Json.get(root, "prompt_cache");
        if (cache == null) {
            return "cache: waiting for first response (needs Claude Code >= 2.1.260)";
        }
        List<String> parts = new ArrayList<>();
        if (Boolean.FALSE.equals(Json.get(cache, "caching_observed"))) {
            parts.add(color("1;33", "CACHING OFF"));
        }
        parts.add(dim("read") + " " + color("1", tokens(number(root, "context_window", "current_usage", "cache_read_input_tokens")))
                + " " + dim("write") + " " + color("1", tokens(number(root, "context_window", "current_usage", "cache_creation_input_tokens"))));
        parts.add(hit(number(cache, "hit_ratio")));
        parts.add(misses(cache));
        long rebuilds = Math.round(orZero(number(cache, "expected_rebuilds")));
        if (rebuilds > 0) {
            parts.add(color("33", "rebuilds " + rebuilds));
        }
        parts.add(dim("total") + " " + color("1", dollars(orZero(number(root, "cost", "total_cost_usd")))));
        parts.add(context(number(root, "context_window", "used_percentage")));
        String model = model(root);
        if (!model.isEmpty()) {
            parts.add(color("1;36", model));
        }
        return String.join(" " + dim("│") + " ", parts);
    }

    static String hit(Double ratio) {
        if (ratio == null) {
            return dim("hit") + " -";
        }
        return dim("hit") + " " + color(ratio < 0.8 ? "33" : "32", Math.round(ratio * 100) + "%");
    }

    static String misses(Object cache) {
        long misses = Math.round(orZero(number(cache, "misses")));
        if (misses == 0) {
            return dim("misses") + " " + color("32", "0");
        }
        return color("31", "misses " + misses + " (" + tokens(number(cache, "miss_recache_tokens"))
                + " re-cached, last: " + cause(Json.get(cache, "last_miss_cause")) + ")");
    }

    static String cause(Object cause) {
        List<String> words = new ArrayList<>();
        words.add(Json.get(cause, "causes") instanceof List<?> list
                ? list.stream().map(String::valueOf).collect(Collectors.joining(","))
                : "cause unknown");
        Double added = number(cause, "tools_added");
        Double removed = number(cause, "tools_removed");
        if (added != null || removed != null) {
            words.add("+" + Math.round(orZero(added)) + "/-" + Math.round(orZero(removed)));
        }
        return String.join(" ", words);
    }

    static String context(Double used) {
        if (used == null) {
            return dim("ctx") + " -";
        }
        String code = used >= 80 ? "31" : used >= 50 ? "33" : "32";
        return dim("ctx") + " " + color(code, bar(used) + " " + Math.round(used) + "%");
    }

    static String model(Object root) {
        Object name = Json.get(root, "model", "display_name");
        if (name == null) {
            name = Json.get(root, "model", "id");
        }
        List<String> words = new ArrayList<>();
        for (Object word : new Object[] {name, Json.get(root, "effort", "level")}) {
            if (word instanceof String s && !s.isEmpty()) {
                words.add(s);
            }
        }
        return String.join(" · ", words);
    }

    static Double number(Object root, String... path) {
        return Json.get(root, path) instanceof Double d ? d : null;
    }

    static double orZero(Double value) {
        return value == null ? 0 : value;
    }

    static String tokens(Double value) {
        if (value == null) {
            return "-";
        }
        if (value >= 999_950) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000);
        }
        return value >= 1000 ? String.format(Locale.ROOT, "%.1fk", value / 1000) : String.valueOf(Math.round(value));
    }

    static String dollars(double value) {
        return String.format(Locale.ROOT, "$%.2f", value);
    }

    static String bar(double percent) {
        int filled = (int) Math.round(Math.clamp(percent, 0, 100) / 100 * 8);
        return "█".repeat(filled) + "░".repeat(8 - filled);
    }

    static String dim(String text) {
        return color("2", text);
    }

    static String color(String code, String text) {
        return COLOR ? "\u001b[" + code + "m" + text + "\u001b[0m" : text;
    }
}
