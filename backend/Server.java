import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Warehouse app: JSON API plus the static page from frontend/ (JDK only).
 * Start from the repo root: java backend/Server.java  ->  http://localhost:8000
 *
 * GET  /api/items                 all items
 * POST /api/items                 create item   {"sku", "name", "quantity"}
 * POST /api/items/<sku>/stock     book stock    {"delta"}
 */
public class Server {

    // Key for the supplier API (reorders are not implemented yet)
    static final String SUPPLIER_API_KEY = "demo-supplier-key-0123456789";

    static final Inventory INVENTORY = Inventory.demo();
    static final Pattern STOCK_PATH = Pattern.compile("/api/items/([^/]+)/stock");
    static final Path FRONTEND = Path.of("frontend").toAbsolutePath().normalize();
    static final Map<String, String> TYPES = Map.of(
            "html", "text/html", "js", "text/javascript", "css", "text/css");

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8000"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/", Server::api);
        server.createContext("/", Server::page);
        server.start();
        System.out.println("Warehouse app running on http://localhost:" + port);
    }

    static void api(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        try {
            if (method.equals("GET") && path.equals("/api/items")) {
                send(exchange, 200, "application/json", toJson(INVENTORY.listItems()));
            } else if (method.equals("POST") && path.equals("/api/items")) {
                String body = readBody(exchange);
                Integer quantity = intField(body, "quantity");
                Inventory.Item item = INVENTORY.addItem(
                        stringField(body, "sku"), stringField(body, "name"), quantity == null ? 0 : quantity);
                send(exchange, 201, "application/json", toJson(item));
            } else if (method.equals("POST") && STOCK_PATH.matcher(path).matches()) {
                Matcher matcher = STOCK_PATH.matcher(path);
                matcher.matches();
                Integer delta = intField(readBody(exchange), "delta");
                if (delta == null) {
                    throw new Inventory.InventoryException("delta must be a non-zero integer");
                }
                send(exchange, 200, "application/json", toJson(INVENTORY.bookStock(matcher.group(1), delta)));
            } else {
                send(exchange, 404, "application/json", error("not found"));
            }
        } catch (Inventory.InventoryException e) {
            send(exchange, 400, "application/json", error(e.getMessage()));
        }
    }

    static void page(HttpExchange exchange) throws IOException {
        String name = exchange.getRequestURI().getPath().equals("/") ? "index.html" : exchange.getRequestURI().getPath();
        Path file = FRONTEND.resolve(name.replaceFirst("^/", "")).normalize();
        if (!file.startsWith(FRONTEND) || !Files.isRegularFile(file)) {
            send(exchange, 404, "text/plain", "not found");
            return;
        }
        String extension = file.toString().substring(file.toString().lastIndexOf('.') + 1);
        send(exchange, 200, TYPES.getOrDefault(extension, "text/plain"), Files.readString(file));
    }

    // --- minimal JSON: enough for flat objects with strings and integers ---

    static String stringField(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(json);
        return m.find() ? m.group(1).replace("\\\"", "\"").replace("\\\\", "\\") : null;
    }

    static Integer intField(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*([^,}\\s]+)").matcher(json);
        if (!m.find()) {
            return null;
        }
        try {
            return Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            throw new Inventory.InventoryException(name + " must be an integer");
        }
    }

    static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    static String toJson(Inventory.Item item) {
        return "{\"sku\": " + quote(item.sku) + ", \"name\": " + quote(item.name)
                + ", \"quantity\": " + item.quantity + "}";
    }

    static String toJson(List<Inventory.Item> items) {
        return items.stream().map(Server::toJson).collect(Collectors.joining(", ", "[", "]"));
    }

    static String error(String message) {
        return "{\"error\": " + quote(message) + "}";
    }

    static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    static void send(HttpExchange exchange, int status, String type, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        exchange.sendResponseHeaders(status, data.length);
        exchange.getResponseBody().write(data);
        exchange.close();
    }
}
