import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hook: a prompt with a secret or personal data is stopped before it reaches the model.
 *
 * Event:   UserPromptSubmit, no matcher
 * Flow:    1. Search the prompt for
 *               API keys and tokens,
 *               IBANs (checked with the mod-97 checksum, so random text does not match),
 *               payment card numbers (Visa/Mastercard/Amex prefix plus Luhn checksum),
 *               lists of e-mail addresses (MAX_EMAILS or more different addresses).
 *          2. Nothing found -> exit 0, the prompt goes through unchanged.
 *          3. Found -> JSON {"decision": "block", "reason": ...}. The prompt is not sent and not kept in the
 *             context; the user sees the reason. The reason masks the value, so it is not repeated in full.
 * Teaches: a prompt is input like any other, and the last point before data leaves the machine.
 */
public class PromptSecretGuard {

    static final int MAX_EMAILS = 3;
    static final Map<String, Pattern> KEYS = new LinkedHashMap<>();

    static {
        KEYS.put("Anthropic API key", Pattern.compile("sk-ant-[A-Za-z0-9_-]{20,}"));
        KEYS.put("OpenAI API key", Pattern.compile("sk-(proj-)?[A-Za-z0-9]{32,}"));
        KEYS.put("AWS access key", Pattern.compile("AKIA[0-9A-Z]{16}"));
        KEYS.put("Google API key", Pattern.compile("AIza[0-9A-Za-z_-]{35}"));
        KEYS.put("GitHub token", Pattern.compile("gh[pousr]_[A-Za-z0-9]{36}"));
        KEYS.put("private key", Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----"));
        KEYS.put("password or secret", Pattern.compile(
                "(?i)(api[_-]?key|secret|token|password|passwort)\\w*\\s*[:=]\\s*[\"']?[^\"'\\s]{8,}"));
    }

    static final Pattern IBAN = Pattern.compile("\\b[A-Z]{2}\\d{2}(?: ?[A-Z0-9]){11,30}\\b");
    static final Pattern CARD = Pattern.compile("\\b(?:\\d[ -]?){12,18}\\d\\b");
    static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");

    public static void main(String[] args) throws Exception {
        String prompt = HookInput.fromStdin().field("prompt");
        if (prompt == null) {
            System.exit(0);
        }

        List<String> findings = new ArrayList<>();
        for (Map.Entry<String, Pattern> key : KEYS.entrySet()) {
            Matcher m = key.getValue().matcher(prompt);
            if (m.find()) {
                findings.add(key.getKey() + " (" + mask(m.group()) + ")");
            }
        }
        Matcher m = IBAN.matcher(prompt);
        while (m.find()) {
            if (validIban(m.group())) {
                findings.add("IBAN (" + mask(m.group().replace(" ", "")) + ")");
            }
        }
        m = CARD.matcher(prompt);
        while (m.find()) {
            String digits = m.group().replaceAll("[ -]", "");
            if (digits.matches("4\\d{12}(\\d{3})?|5[1-5]\\d{14}|2[2-7]\\d{14}|3[47]\\d{13}") && luhn(digits)) {
                findings.add("payment card number (" + mask(digits) + ")");
            }
        }
        Set<String> emails = new LinkedHashSet<>();
        m = EMAIL.matcher(prompt);
        while (m.find()) {
            emails.add(m.group().toLowerCase(Locale.ROOT));
        }
        if (emails.size() >= MAX_EMAILS) {
            findings.add(emails.size() + " e-mail addresses (personal data)");
        }
        if (findings.isEmpty()) {
            System.exit(0);
        }

        String reason = "Prompt not sent: it contains " + String.join(", ", findings) + ". "
                + "Remove the value and send the prompt again. Refer to secrets by their environment variable name, "
                + "and use made-up test data instead of real personal data.";
        System.out.println("{\"decision\": \"block\", \"reason\": " + HookInput.quote(reason) + "}");
    }

    /** First 4 characters, the rest hidden. */
    static String mask(String value) {
        return value.length() <= 6 ? "***" : value.substring(0, 4) + "..." + "*".repeat(4);
    }

    /** The match may run into a following word ("DE89... AND"), so try every length from long to short. */
    static boolean validIban(String text) {
        String compact = text.replace(" ", "");
        for (int length = Math.min(34, compact.length()); length >= 15; length--) {
            if (checksum(compact.substring(0, length))) {
                return true;
            }
        }
        return false;
    }

    static boolean checksum(String iban) {
        StringBuilder digits = new StringBuilder();
        for (char c : (iban.substring(4) + iban.substring(0, 4)).toCharArray()) {
            digits.append(Character.isDigit(c) ? String.valueOf(c) : String.valueOf(c - 'A' + 10));
        }
        return new BigInteger(digits.toString()).mod(BigInteger.valueOf(97)).intValue() == 1;
    }

    static boolean luhn(String digits) {
        int sum = 0;
        boolean twice = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (twice) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            twice = !twice;
        }
        return sum % 10 == 0;
    }
}
