package be.maartenvandeperre.aidemo;

import dev.langchain4j.service.decision.Choice;
import dev.langchain4j.service.decision.DecisionResult;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Comparator;
import java.util.List;

/**
 * Decision endpoint: which coffee does the customer want?
 *
 *   curl -s -X POST localhost:8080/ai/decision -H 'Content-Type: text/plain' \
 *        -d 'Something milky but not too sweet, I have a long meeting ahead'
 *
 * Returns the chosen coffee plus a probability for every item on the menu.
 */
@Path("/ai/decision")
public class DecisionResource {

    public record CoffeeProbability(String coffee, String label, double probability) {
    }

    public record DecisionResponse(String model, String text, String choice, Double confidence,
                                   List<CoffeeProbability> probabilities) {
    }

    private final CoffeeDecisionService decisions;

    public DecisionResource(CoffeeDecisionService decisions) {
        this.decisions = decisions;
    }

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    public DecisionResponse decide(String text) {
        if (text == null || text.isBlank()) {
            throw new BadRequestException("Describe what you feel like drinking.");
        }
        String customerText = text.strip();
        return toResponse(customerText, decisions.chooseCoffee(customerText));
    }

    static DecisionResponse toResponse(String text, DecisionResult<Choice<CoffeeType>> result) {
        Choice<CoffeeType> choice = result.content();
        List<CoffeeProbability> probabilities = choice.probabilities().entrySet().stream()
                .sorted(Comparator.comparingDouble((java.util.Map.Entry<CoffeeType, Double> e) -> e.getValue()).reversed())
                .map(e -> new CoffeeProbability(id(e.getKey()), e.getKey().label(), round(e.getValue())))
                .toList();
        return new DecisionResponse(result.modelName(), text, id(choice.value()), choice.confidence(), probabilities);
    }

    private static String id(CoffeeType type) {
        return type.name().toLowerCase();
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
