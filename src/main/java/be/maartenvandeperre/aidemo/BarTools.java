package be.maartenvandeperre.aidemo;

import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tools the agent can call. In a real system these would hit your
 * inventory service, POS system, Kafka topics, Camel routes, ...
 * Here they are in-memory so the demo runs anywhere.
 *
 * The model never executes code itself: LangChain4j sends the tool
 * signatures along with the prompt, the model responds with "call
 * checkStock('gin')", the framework executes the Java method and feeds
 * the result back into the conversation until the model produces a
 * final answer.
 */
@ApplicationScoped
public class BarTools {

    private final Map<String, Integer> stock = new ConcurrentHashMap<>(Map.of(
            "gin", 4,
            "tonic", 12,
            "lime", 2,
            "espresso", 30,
            "vodka", 0,
            "coffee liqueur", 1
    ));

    private final List<String> orders = new java.util.concurrent.CopyOnWriteArrayList<>();

    @Tool("Check how many units of an ingredient are in stock. Returns the amount, 0 means out of stock.")
    public int checkStock(String ingredient) {
        return stock.getOrDefault(ingredient.toLowerCase(), 0);
    }

    @Tool("Place an order for a drink for a given table number. Only do this after confirming all ingredients are in stock.")
    public String placeOrder(String drink, int tableNumber) {
        var order = "Order #%d: %s for table %d".formatted(orders.size() + 1, drink, tableNumber);
        orders.add(order);
        return order + " -> confirmed";
    }

    @Tool("List all orders placed so far this evening.")
    public List<String> listOrders() {
        return List.copyOf(orders);
    }
}
