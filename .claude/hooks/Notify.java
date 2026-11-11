import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Hook: desktop notification when Claude waits for you (a permission prompt, or idle for input).
 *
 * Event:   Notification, no matcher, "async": true (showing the notice takes a few seconds; Claude must not wait)
 * Flow:    1. Read "message" (and "title" if present) from the event. Title: "Claude Code - <project folder>",
 *             so you know which of several sessions is waiting.
 *          2. Show it the way the OS supports it:
 *               Windows -> Java AWT tray notification (pure Java, no extra tool)
 *               macOS   -> osascript "display notification" (built into macOS; text passed as arguments, not code)
 *               Linux   -> notify-send, if installed
 *             If none works: a system beep.
 *          3. Never blocks, never decides: exit 0.
 * Config:  CLAUDE_NOTIFY=off disables it. NOTIFY_DRY_RUN=1 prints "title | message" instead (used by the tests).
 */
public class Notify {

    static final int MAX_LENGTH = 200;
    static final int SHOW_MILLIS = 6000;

    public static void main(String[] args) throws Exception {
        HookInput input = HookInput.fromStdin();
        if ("off".equalsIgnoreCase(System.getenv("CLAUDE_NOTIFY"))) {
            System.exit(0);
        }

        String project = Path.of(System.getenv().getOrDefault("CLAUDE_PROJECT_DIR", "."))
                .toAbsolutePath().normalize().getFileName().toString();
        String title = input.field("title") != null ? input.field("title") : "Claude Code - " + project;
        String message = input.field("message") != null ? input.field("message") : "Claude is waiting for you";
        if (message.length() > MAX_LENGTH) {
            message = message.substring(0, MAX_LENGTH - 3) + "...";
        }

        if ("1".equals(System.getenv("NOTIFY_DRY_RUN"))) {
            System.out.println(title + " | " + message);
            System.exit(0);
        }

        String os = System.getProperty("os.name").toLowerCase();
        boolean shown;
        if (os.contains("mac")) {
            shown = run("osascript", "-e", "on run argv", "-e",
                    "display notification (item 2 of argv) with title (item 1 of argv)", "-e", "end run",
                    title, message);
        } else if (os.contains("win")) {
            shown = tray(title, message);
        } else {
            shown = Files.isExecutable(Path.of("/usr/bin/notify-send")) && run("notify-send", title, message);
        }
        if (!shown) {
            beep();
        }
        System.exit(0); // AWT keeps the JVM alive otherwise
    }

    /** Windows: balloon/toast through the system tray, shown for a few seconds, then removed again. */
    static boolean tray(String title, String message) {
        try {
            if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) {
                return false;
            }
            BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0xD9, 0x77, 0x57));
            g.fillOval(1, 1, 14, 14);
            g.dispose();
            TrayIcon icon = new TrayIcon(image, title);
            icon.setImageAutoSize(true);
            SystemTray.getSystemTray().add(icon);
            icon.displayMessage(title, message, TrayIcon.MessageType.INFO);
            Thread.sleep(SHOW_MILLIS);
            SystemTray.getSystemTray().remove(icon);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean run(String... command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    static void beep() {
        try {
            Toolkit.getDefaultToolkit().beep();
        } catch (Exception | Error e) {
            // headless: nothing we can do
        }
    }
}
