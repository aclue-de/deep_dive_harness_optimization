import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Tests for the hooks in .claude/hooks: no LLM, no Claude Code. Runs on Windows and macOS.
 *
 * Each test sends a fixed hook JSON on stdin and checks exit code and output.
 * Run from the repo root:  java tests/harness/HooksTest.java   (exit code 0 = all green)
 */
public class HooksTest {

    static final Path ROOT = Path.of("").toAbsolutePath();
    static final Path HOOKS = ROOT.resolve(".claude/hooks");
    // Fake secrets are assembled at runtime so secret guards do not block edits to this file
    static final String FAKE_AWS_KEY = "AKIA" + "IOSFODNN7EXAMPLE";
    static final String TEST_IBAN = "DE89 3704 0044 " + "0532 0130 00";
    static final String TEST_CARD = "4111 1111 " + "1111 1111";

    static int failures = 0;

    record Result(int exit, String stdout, String stderr) {}

    interface Body {
        void run(Path project) throws Exception;
    }

    public static void main(String[] args) throws Exception {
        // Exercise 1 PromptSecretGuard: UserPromptSubmit, blocks secrets and personal data
        test("prompt secrets: key, IBAN, card and e-mail lists are blocked", p -> {
            for (String text : List.of("Use this key: " + FAKE_AWS_KEY,
                    "Refund to " + TEST_IBAN + " AND notify me",
                    "Card " + TEST_CARD + " was declined",
                    "Mail a@example.com, b@example.com and c@example.com")) {
                Result r = run("PromptSecretGuard.java", prompt(text), p);
                check(r.exit == 0 && r.stdout.contains("\"decision\": \"block\""),
                        "block for: " + text + ", got: " + r.stdout);
            }
        });
        test("prompt secrets: the reason masks the value", p -> {
            Result r = run("PromptSecretGuard.java", prompt("key " + FAKE_AWS_KEY), p);
            check(r.stdout.contains("AWS access key") && !r.stdout.contains(FAKE_AWS_KEY), "masked, got: " + r.stdout);
        });
        test("prompt secrets: ordinary prompts pass", p -> {
            for (String text : List.of("Fix the typo", "Read SUPPLIER_API_KEY with System.getenv",
                    "Order 1731234567890 and 4111", "Contact me at a@example.com",
                    "DE12 3456 7890 1234 5678 90 is not a valid IBAN")) {
                isSilent(run("PromptSecretGuard.java", prompt(text), p));
            }
        });

        // Exercise 2 AgentsMdGate: PostToolUse, checks AGENTS.md
        test("gate: the repo AGENTS.md passes", p -> {
            copyCheckScript(p);
            Files.copy(ROOT.resolve("AGENTS.md"), p.resolve("AGENTS.md"));
            Result r = run("AgentsMdGate.java", edit(p.resolve("AGENTS.md").toString()), p);
            check(r.exit == 0, "exit 0, got " + r.exit + ": " + r.stderr);
        });
        test("gate: a broken rule blocks and names the reviewer", p -> {
            copyCheckScript(p);
            // the same change as in the runbook: a tool-specific line under ## Code
            String agents = Files.readString(ROOT.resolve("AGENTS.md"))
                    .replaceFirst("## Code\\R", "## Code\n\n- Use @workspace to find context.\n");
            check(agents.contains("@workspace"), "AGENTS.md has a ## Code section");
            Files.writeString(p.resolve("AGENTS.md"), agents);
            Result r = run("AgentsMdGate.java", edit(p.resolve("AGENTS.md").toString()), p);
            check(r.exit == 2, "exit 2, got " + r.exit);
            check(r.stderr.contains("@workspace"), "stderr names @workspace, got: " + r.stderr);
            check(r.stderr.contains("agents-md-reviewer"), "stderr names agents-md-reviewer");
        });
        test("gate: other files are ignored", p ->
                isSilent(run("AgentsMdGate.java", edit(p.resolve("Server.java").toString()), p)));

        // Exercise 3 HarnessGuard: PreToolUse, changes to hooks or settings ask first
        test("harness: Edit of settings.json or a hook asks", p -> {
            isDecision(run("HarnessGuard.java", edit("C:\\repo\\.claude\\settings.json"), p), "ask");
            isDecision(run("HarnessGuard.java", edit("/repo/.claude/hooks/AgentsMdGate.java"), p), "ask");
            isDecision(run("HarnessGuard.java", edit("/repo/.claude/settings.local.json"), p), "ask");
        });
        test("harness: the question says what changes and warns about a removed hook", p -> {
            String json = "{\"tool_name\": \"Edit\", \"tool_input\": {\"file_path\": \"/repo/.claude/settings.json\", "
                    + "\"old_string\": " + q("{\n  \"type\": \"command\",\n  \"command\": \"java "
                    + "\\\"$CLAUDE_PROJECT_DIR/.claude/hooks/HarnessGuard.java\\\"\",\n  \"onFailure\": \"block\"\n}")
                    + ", \"new_string\": \"\"}}";
            Result r = run("HarnessGuard.java", json, p);
            isDecision(r, "ask");
            check(r.stdout.contains("Are you sure you want Claude to change settings.json?"), "asks the question");
            check(r.stdout.contains("removes or changes a hook"), "warns about the hook, got: " + r.stdout);
            check(r.stdout.contains("loosens permissions"), "warns about onFailure");
            check(r.stdout.contains("- \\\"command\\\": \\\"java") && r.stdout.contains("HarnessGuard.java"),
                    "lists the removed line");
        });
        test("harness: Write compares with the file on disk", p -> {
            Path settings = Files.createDirectories(p.resolve(".claude")).resolve("settings.json");
            Files.writeString(settings, "{\n  \"permissions\": {\n    \"defaultMode\": \"default\"\n  }\n}\n");
            String content = "{\n  \"permissions\": {\n    \"defaultMode\": \"bypassPermissions\"\n  }\n}\n";
            Result r = run("HarnessGuard.java", write(settings.toString(), content), p);
            isDecision(r, "ask");
            check(r.stdout.contains("- \\\"defaultMode\\\": \\\"default\\\"")
                    && r.stdout.contains("+ \\\"defaultMode\\\": \\\"bypassPermissions\\\""), "old and new line");
            check(r.stdout.contains("loosens permissions"), "warns, got: " + r.stdout);
            check(!r.stdout.contains("\\\"permissions\\\": {"), "unchanged lines are left out");
        });
        test("harness: shell changes ask", p -> {
            for (String c : List.of("rm .claude/hooks/AgentsMdGate.java",
                    "echo '{}' > .claude/settings.json",
                    "cp /tmp/Evil.java .claude/hooks/",
                    "git checkout -- .claude/settings.json",
                    "sed -i 's/block/allow/' .claude/settings.json",
                    "ls && mv .claude/hooks/AgentsMdGate.java /tmp/")) {
                isDecision(run("HarnessGuard.java", bash(c), p), "ask");
            }
            isDecision(run("HarnessGuard.java", powershell("Set-Content .claude\\settings.json '{}'"), p), "ask");
            isDecision(run("HarnessGuard.java", powershell("Remove-Item .claude\\hooks\\AgentsMdGate.java"), p), "ask");
        });
        test("harness: reading and copying from the harness pass", p -> {
            for (String c : List.of("cat .claude/settings.json",
                    "cd /repo && cat .claude/settings.json; ls bin tools 2>/dev/null",
                    "mkdir -p backup && cp .claude/hooks/HookInput.java backup/",
                    "java .claude/hooks/AgentsMdGate.java < input.json > out.txt",
                    "git diff .claude/settings.json")) {
                isSilent(run("HarnessGuard.java", bash(c), p));
            }
        });
        test("harness: other files pass", p -> {
            isSilent(run("HarnessGuard.java", edit("/repo/backend/Server.java"), p));
            isSilent(run("HarnessGuard.java", edit("/repo/.claude/skills/x/SKILL.md"), p));
        });

        // Optional A Notify: Notification, desktop notice (dry run, no window in tests)
        test("notify: shows the message with the project name in the title", p -> {
            String message = "Claude needs your permission to use Bash";
            String event = "{\"hook_event_name\": \"Notification\", \"message\": " + q(message) + "}";
            Result r = run("Notify.java", event, p, Map.of("NOTIFY_DRY_RUN", "1"));
            check(r.exit == 0, "exit 0, got " + r.exit + ": " + r.stderr);
            check(r.stdout.strip().equals("Claude Code - " + p.getFileName() + " | " + message),
                    "title and message, got: " + r.stdout);
        });
        test("notify: long messages are cut, CLAUDE_NOTIFY=off is silent", p -> {
            String event = "{\"hook_event_name\": \"Notification\", \"message\": \"" + "x".repeat(500) + "\"}";
            Result r = run("Notify.java", event, p, Map.of("NOTIFY_DRY_RUN", "1"));
            check(r.stdout.strip().endsWith("...") && r.stdout.length() < 300, "message cut to 200 characters");
            isSilent(run("Notify.java", event, p, Map.of("CLAUDE_NOTIFY", "off", "NOTIFY_DRY_RUN", "1")));
        });

        System.out.println(failures == 0 ? "OK" : failures + " test(s) failed");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ---------------------------------------------------------------- hook inputs

    static String edit(String file) {
        return "{\"session_id\": \"s1\", \"hook_event_name\": \"PreToolUse\", \"tool_name\": \"Edit\", "
                + "\"tool_input\": {\"file_path\": " + q(file) + ", \"old_string\": \"a\", \"new_string\": \"b\"}}";
    }

    static String write(String file, String content) {
        return "{\"session_id\": \"s1\", \"hook_event_name\": \"PreToolUse\", \"tool_name\": \"Write\", "
                + "\"tool_input\": {\"file_path\": " + q(file) + ", \"content\": " + q(content) + "}}";
    }

    static String bash(String command) {
        return shell("Bash", command);
    }

    static String powershell(String command) {
        return shell("PowerShell", command);
    }

    static String shell(String tool, String command) {
        return "{\"session_id\": \"s1\", \"hook_event_name\": \"PreToolUse\", \"tool_name\": " + q(tool) + ", "
                + "\"tool_input\": {\"command\": " + q(command) + ", \"description\": \"Run\", \"timeout\": 120000}}";
    }

    static String prompt(String text) {
        return "{\"session_id\": \"s1\", \"hook_event_name\": \"UserPromptSubmit\", \"prompt\": " + q(text) + "}";
    }

    static String q(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                default -> out.append(c);
            }
        }
        return out.append('"').toString();
    }

    // ---------------------------------------------------------------- fixtures

    /** The gate calls the skill script below CLAUDE_PROJECT_DIR, so the test project needs it. */
    static void copyCheckScript(Path project) throws Exception {
        Path script = Path.of(".claude/skills/agents-md-review/scripts/CheckAgentsMd.java");
        Files.createDirectories(project.resolve(script).getParent());
        Files.copy(ROOT.resolve(script), project.resolve(script));
    }

    // ---------------------------------------------------------------- runner

    static Result run(String hook, String stdin, Path project) throws Exception {
        return run(hook, stdin, project, Map.of());
    }

    static Result run(String hook, String stdin, Path project, Map<String, String> env) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("java", HOOKS.resolve(hook).toString()).directory(project.toFile());
        builder.environment().put("CLAUDE_PROJECT_DIR", project.toString());
        builder.environment().putAll(env);
        Process process = builder.start();
        process.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().close();
        String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String err = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Result(process.waitFor(), out, err);
    }

    static void isDecision(Result r, String decision) {
        check(r.exit == 0, "exit 0 (JSON decision), got " + r.exit + ": " + r.stderr);
        check(r.stdout.contains("\"permissionDecision\": \"" + decision + "\""),
                "decision " + decision + ", got: " + r.stdout + r.stderr);
    }

    static void isSilent(Result r) {
        check(r.exit == 0 && r.stdout.isEmpty() && r.stderr.isEmpty(),
                "exit 0 and no output, got " + r.exit + ": " + r.stdout + r.stderr);
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static void test(String name, Body body) throws Exception {
        Path project = Files.createTempDirectory("hooks-test-");
        try {
            body.run(project);
            System.out.println("ok    " + name);
        } catch (AssertionError | Exception e) {
            failures++;
            System.out.println("FAIL  " + name + ": " + e.getMessage());
        } finally {
            try (Stream<Path> files = Files.walk(project)) {
                files.sorted(Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
            }
        }
    }
}
