import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic checks for AGENTS.md: the rules a script can check without judgement.
 *
 * Usage:  java CheckAgentsMd.java [path/to/AGENTS.md]
 * Output: one line per finding (VIOLATION / WARNING). Exit code 0 = passed, 1 = violations found.
 *
 * Used by the skill agents-md-review; a hook can call it the same way.
 * No LLM, no network, JDK only.
 */
public class CheckAgentsMd {

    static final int MAX_LINES = 200;    // hard limit
    static final int TARGET_LINES = 120; // recommendation
    static final Pattern TOOL_SYNTAX = Pattern.compile("(?<![\\w`])(@workspace|@codebase|@terminal|@cursor|@file|#file)\\b");
    static final Pattern AT_IMPORT = Pattern.compile("^\\s*@[\\w./-]+\\s*$");
    static final Pattern FAILURE_PROTOCOL = Pattern.compile("(?i)^#{1,6}\\s*failure protocol\\b");
    static final Map<String, Pattern> TIERS = Map.of(
            "Always", Pattern.compile("(?i)(\\*\\*|^#+\\s*)always\\b"),
            "Ask first", Pattern.compile("(?i)(\\*\\*|^#+\\s*)ask first\\b"),
            "Never", Pattern.compile("(?i)(\\*\\*|^#+\\s*)never\\b"));

    static final List<String> violations = new ArrayList<>();
    static final List<String> warnings = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        Path path = Path.of(args.length > 0 ? args[0] : "AGENTS.md");
        if (!Files.isRegularFile(path)) {
            violations.add("AGENTS.md is missing in the repository root");
            report();
        }

        List<String> lines = Files.readAllLines(path);
        if (lines.size() > MAX_LINES) {
            violations.add(lines.size() + " lines, max " + MAX_LINES);
        } else if (lines.size() > TARGET_LINES) {
            warnings.add(lines.size() + " lines, target " + TARGET_LINES);
        }

        boolean inCodeBlock = false;
        boolean hasFailureProtocol = false;
        for (int number = 1; number <= lines.size(); number++) {
            String text = lines.get(number - 1);
            if (text.strip().startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                continue;
            }
            if (inCodeBlock) {
                continue;
            }
            Matcher tool = TOOL_SYNTAX.matcher(text);
            boolean hasToolSyntax = false;
            while (tool.find()) {
                hasToolSyntax = true;
                violations.add("line " + number + ": tool-specific syntax '" + tool.group(1) + "'");
            }
            if (!hasToolSyntax && AT_IMPORT.matcher(text).matches()) {
                violations.add("line " + number + ": @-import '" + text.strip() + "' (plain Markdown only)");
            }
            if (FAILURE_PROTOCOL.matcher(text).find()) {
                hasFailureProtocol = true;
            }
        }
        if (!hasFailureProtocol) {
            violations.add("section 'Failure Protocol' is missing");
        }

        for (String tier : List.of("Always", "Ask first", "Never")) {
            if (lines.stream().noneMatch(line -> TIERS.get(tier).matcher(line).find())) {
                warnings.add("boundary tier '" + tier + "' not found");
            }
        }

        if (!isTracked(path)) {
            warnings.add("AGENTS.md is not tracked by git");
        }
        report();
    }

    static boolean isTracked(Path path) {
        try {
            Path dir = path.toAbsolutePath().getParent();
            Process git = new ProcessBuilder("git", "ls-files", "--error-unmatch", path.getFileName().toString())
                    .directory(dir.toFile()).redirectErrorStream(true).start();
            git.getInputStream().readAllBytes();
            return git.waitFor() == 0;
        } catch (Exception e) {
            return true; // git not installed: no warning
        }
    }

    static void report() {
        System.out.println("AGENTS.md check: " + (violations.isEmpty() ? "passed" : "failed"));
        violations.forEach(v -> System.out.println("VIOLATION " + v));
        warnings.forEach(w -> System.out.println("WARNING   " + w));
        System.exit(violations.isEmpty() ? 0 : 1);
    }
}
