import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Warehouse logic: create items, list items, book stock.
 *
 * Deliberately without framework or database so the code can be understood in a
 * few minutes. Data lives in memory and is gone after a restart.
 */
public class Inventory {

    /** Domain error, e.g. negative stock or unknown item. */
    public static class InventoryException extends RuntimeException {
        public InventoryException(String message) {
            super(message);
        }
    }

    public static class Item {
        public final String sku;
        public final String name;
        public int quantity;

        Item(String sku, String name, int quantity) {
            this.sku = sku;
            this.name = name;
            this.quantity = quantity;
        }
    }

    private final TreeMap<String, Item> items = new TreeMap<>(); // sorted by sku

    public List<Item> listItems() {
        return new ArrayList<>(items.values());
    }

    public Item addItem(String sku, String name, int quantity) {
        if (sku == null || sku.isBlank() || name == null || name.isBlank()) {
            throw new InventoryException("sku and name are required");
        }
        if (items.containsKey(sku)) {
            throw new InventoryException("item " + sku + " already exists");
        }
        if (quantity < 0) {
            throw new InventoryException("quantity must be an integer >= 0");
        }
        Item item = new Item(sku, name, quantity);
        items.put(sku, item);
        return item;
    }

    /** Books goods in (delta > 0) or goods out (delta < 0). */
    public Item bookStock(String sku, int delta) {
        Item item = items.get(sku);
        if (item == null) {
            throw new InventoryException("item " + sku + " unknown");
        }
        if (delta == 0) {
            throw new InventoryException("delta must be a non-zero integer");
        }
        if (item.quantity + delta < 0) {
            throw new InventoryException("stock of " + sku + " must not become negative");
        }
        item.quantity += delta;
        return item;
    }

    public static Inventory demo() {
        Inventory inventory = new Inventory();
        inventory.addItem("A-100", "Apple juice 1 l", 24);
        inventory.addItem("B-200", "Wholegrain bread", 10);
        inventory.addItem("C-300", "Oat milk 1 l", 0);
        return inventory;
    }
}
