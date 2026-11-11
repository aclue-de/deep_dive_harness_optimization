/**
 * Tests for Inventory, without a test framework.
 * Run: java backend/InventoryTest.java   (exit code 0 = all green)
 */
public class InventoryTest {

    static int failures = 0;

    public static void main(String[] args) {
        // Chatty on purpose (slides 48/49): logs every booking, about 250 lines (~20,000 characters) per run
        test("bulk goods in and out", () -> {
            Inventory inventory = setUp();
            for (int i = 1; i <= 250; i++) {
                int delta = i % 2 == 1 ? 3 : -2;
                int stock = inventory.bookStock("A-100", delta).quantity;
                System.out.printf("[debug] booking %03d: A-100 Apple juice %+d -> stock %d (warehouse WH-1, shelf 04-B)%n",
                        i, delta, stock);
            }
            check(inventory.listItems().get(0).quantity == 130, "stock is 130");
        });
        test("add item", () -> {
            Inventory inventory = setUp();
            check(inventory.addItem("B-200", "Bread", 3).quantity == 3, "quantity is 3");
            check(inventory.listItems().size() == 2, "two items");
        });
        test("duplicate sku is rejected", () -> expectError(() -> setUp().addItem("A-100", "Apple juice", 1)));
        test("goods in and out", () -> {
            Inventory inventory = setUp();
            inventory.bookStock("A-100", 10);
            check(inventory.bookStock("A-100", -15).quantity == 0, "stock is 0");
        });
        test("stock never negative", () -> expectError(() -> setUp().bookStock("A-100", -6)));
        test("unknown item is rejected", () -> expectError(() -> setUp().bookStock("X-999", 1)));

        System.out.println(failures == 0 ? "OK" : failures + " test(s) failed");
        System.exit(failures == 0 ? 0 : 1);
    }

    static Inventory setUp() {
        Inventory inventory = new Inventory();
        inventory.addItem("A-100", "Apple juice", 5);
        return inventory;
    }

    static void test(String name, Runnable body) {
        try {
            body.run();
            System.out.println("ok    " + name);
        } catch (AssertionError | RuntimeException e) {
            failures++;
            System.out.println("FAIL  " + name + ": " + e.getMessage());
        }
    }

    static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError("expected " + what);
        }
    }

    static void expectError(Runnable action) {
        try {
            action.run();
        } catch (Inventory.InventoryException expected) {
            return;
        }
        throw new AssertionError("expected an InventoryException");
    }
}
