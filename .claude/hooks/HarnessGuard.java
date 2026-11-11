import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Hook: Claude may not switch off its own guards without the user noticing.
 *
 * Event:     PreToolUse, matcher Edit|Write|Bash|PowerShell, "onFailure": "block"
 * Flow:      1. Edit/Write on .claude/settings*.json or .claude/hooks/** -> "ask", also when the user asked for it.
 *               The dialog says what changes: removed and added lines (max 8 each), plus a WARNING if a hook is
 *               removed or changed, or permissions get looser (bypassPermissions, "allow", no "deny", no onFailure).
 *            2. Shell command: each part (split at && || ; |) is checked on its own. "ask" if the harness path is
 *                 the target of a redirect               echo {} > .claude/settings.json
 *                 the destination of a copy              cp x .claude/hooks/      (copying FROM it passes)
 *                 an argument of a changing command      rm, mv, tee, sed -i, Set-Content, Remove-Item, ...
 *                 an argument of git rm/mv/checkout/restore
 *            3. Anything else -> exit 0, no output. Reading passes, also with "2>/dev/null" in the command.
 * Teaches:   "who guards the guards?". An agent that may edit settings.json can delete the hook that blocks it.
 * Gap:       a heuristic: "cd .claude && rm hooks/x" or a script that writes the file are not recognised.
 */
public class HarnessGuard {

    static final Pattern HARNESS_FILE = Pattern.compile("(^|/)\\.claude/(settings[^/]*\\.json$|hooks/)");
    static final Pattern HARNESS = Pattern.compile("\\.claude[/\\\\]+(settings|hooks)");
    static final Pattern REDIRECT_TO_HARNESS = Pattern.compile(
            ">{1,2}\\s*['\"]?[^\\s;&|>]*\\.claude[/\\\\]+(settings|hooks)");
    static final Pattern SEGMENTS = Pattern.compile("&&|\\|\\||[;|\\n]");
    static final Pattern ASSIGNMENT = Pattern.compile("\\w+=\\S*");
    static final Pattern LOOSER = Pattern.compile("bypassPermissions|acceptEdits|\"allow\"|dontAsk");
    static final int MAX_LINES = 8;

    static final Set<String> COPY = Set.of("cp", "copy", "copy-item", "cpi");
    static final Set<String> CHANGE = Set.of("rm", "del", "erase", "mv", "move", "remove-item", "ri", "move-item",
            "mi", "rename-item", "ren", "set-content", "out-file", "add-content", "clear-content", "new-item", "ni",
            "tee", "truncate", "chmod", "touch");
    static final Set<String> GIT_CHANGE = Set.of("rm", "mv", "checkout", "restore");

    public static void main(String[] args) throws Exception {
        HookInput input = HookInput.fromStdin();
        String tool = input.field("tool_name");

        if ("Edit".equals(tool) || "Write".equals(tool)) {
            String file = input.field("file_path");
            if (file != null && HARNESS_FILE.matcher(file.replace('\\', '/')).find()) {
                askWithChanges(file, input, "Write".equals(tool));
            }
            System.exit(0);
        }

        String command = input.field("command");
        if (command == null || !HARNESS.matcher(command).find()) {
            System.exit(0);
        }
        for (String segment : SEGMENTS.split(command)) {
            if (HARNESS.matcher(segment).find() && changesHarness(segment)) {
                ask(segment.strip());
            }
        }
        System.exit(0);
    }

    static boolean changesHarness(String segment) {
        if (REDIRECT_TO_HARNESS.matcher(segment).find()) {
            return true;
        }
        List<String> words = words(segment);
        if (words.isEmpty()) {
            return false;
        }
        String program = words.get(0).toLowerCase().replaceFirst("^.*[/\\\\]", "").replaceFirst("\\.exe$", "");
        List<String> arguments = words.subList(1, words.size());
        List<String> paths = arguments.stream().filter(a -> !a.startsWith("-")).toList();

        if (COPY.contains(program)) {
            return !paths.isEmpty() && HARNESS.matcher(paths.get(paths.size() - 1)).find(); // destination
        }
        if (CHANGE.contains(program)) {
            return paths.stream().anyMatch(p -> HARNESS.matcher(p).find());
        }
        if (program.equals("sed") || program.equals("perl")) {
            boolean inPlace = arguments.stream().anyMatch(a -> a.matches("-[a-z]*i.*"));
            return inPlace && paths.stream().anyMatch(p -> HARNESS.matcher(p).find());
        }
        if (program.equals("git") && !paths.isEmpty() && GIT_CHANGE.contains(paths.get(0))) {
            return paths.stream().anyMatch(p -> HARNESS.matcher(p).find());
        }
        return false;
    }

    /** Words of a command part without quotes, VAR=value prefixes and the PowerShell call operator. */
    static List<String> words(String segment) {
        List<String> words = new ArrayList<>();
        for (String word : segment.trim().split("\\s+")) {
            String token = word.replace("\"", "").replace("'", "");
            if (token.isEmpty() || (words.isEmpty() && (token.equals("&") || ASSIGNMENT.matcher(token).matches()))) {
                continue;
            }
            words.add(token);
        }
        return words;
    }

    /**
     * "ask" with a short summary for the confirmation dialog: which lines go, which come, and a warning
     * if the change removes a hook or loosens permissions. Edit: old_string vs new_string.
     * Write: the file on disk vs the new content.
     */
    static void askWithChanges(String file, HookInput input, boolean write) {
        String before;
        String after;
        if (write) {
            Path path = Path.of(file);
            if (!path.isAbsolute()) {
                path = Path.of(System.getenv().getOrDefault("CLAUDE_PROJECT_DIR", ".")).resolve(path);
            }
            try {
                before = Files.isRegularFile(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
            } catch (Exception e) {
                before = "";
            }
            after = input.field("content");
        } else {
            before = input.field("old_string");
            after = input.field("new_string");
        }
        List<String> removed = onlyIn(before, after);
        List<String> added = onlyIn(after, before);

        StringBuilder reason = new StringBuilder("Are you sure you want Claude to change ")
                .append(Path.of(file).getFileName()).append("? It configures the hooks and permissions that guard "
                        + "this session.");
        if (removed.stream().anyMatch(l -> l.contains(".claude/hooks/") || l.contains("\"command\""))) {
            reason.append("\nWARNING: this removes or changes a hook.");
        }
        if (added.stream().anyMatch(l -> LOOSER.matcher(l).find())
                || removed.stream().anyMatch(l -> l.contains("\"deny\"") || l.contains("onFailure"))) {
            reason.append("\nWARNING: this loosens permissions or fail-closed protection.");
        }
        reason.append(summary("Removed", "- ", removed)).append(summary("Added", "+ ", added));
        if (removed.isEmpty() && added.isEmpty()) {
            reason.append("\nNo visible change (only whitespace or brackets).");
        }
        reason.append("\nAllow only if you asked for exactly this change.");
        System.out.println("{\"hookSpecificOutput\": {\"hookEventName\": \"PreToolUse\", "
                + "\"permissionDecision\": \"ask\", \"permissionDecisionReason\": " + HookInput.quote(reason.toString())
                + "}}");
        System.exit(0);
    }

    /** Lines of "text" that are not in "other", trimmed, without lines that are only brackets or commas. */
    static List<String> onlyIn(String text, String other) {
        List<String> otherLines = new ArrayList<>(lines(other));
        List<String> result = new ArrayList<>();
        for (String line : lines(text)) {
            if (!otherLines.remove(line)) {
                result.add(line);
            }
        }
        return result;
    }

    static List<String> lines(String text) {
        if (text == null) {
            return List.of();
        }
        return text.lines().map(String::strip).filter(l -> !l.isEmpty() && !l.matches("[\\[\\]{}(),;]+")).toList();
    }

    static String summary(String title, String prefix, List<String> lines) {
        if (lines.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder("\n").append(title).append(":");
        for (String line : lines.subList(0, Math.min(MAX_LINES, lines.size()))) {
            out.append("\n  ").append(prefix).append(line.length() > 100 ? line.substring(0, 97) + "..." : line);
        }
        if (lines.size() > MAX_LINES) {
            out.append("\n  ... and ").append(lines.size() - MAX_LINES).append(" more lines");
        }
        return out.toString();
    }

    static void ask(String target) {
        String reason = "This changes the harness that guards this session (hooks or settings): " + target + ". "
                + "A hook could be switched off this way. Tell the user what you want to change and why.";
        System.out.println("{\"hookSpecificOutput\": {\"hookEventName\": \"PreToolUse\", "
                + "\"permissionDecision\": \"ask\", \"permissionDecisionReason\": " + HookInput.quote(reason) + "}}");
        System.exit(0);
    }
}
