package be.maartenvandeperre.aidemo;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.FieldsInput;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.service.decision.DecisionServices;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoffeeDecisionServiceTest {

    /** Records the request and answers with fixed probabilities. */
    static class FakeDecisionModel implements DecisionModel {
        DecisionRequest lastRequest;

        @Override
        public DecisionResponse doDecide(DecisionRequest request) {
            lastRequest = request;
            String name = request.questions().keySet().iterator().next();
            ChoiceQuestion question = (ChoiceQuestion) request.questions().get(name);
            Map<String, Double> probabilities = new LinkedHashMap<>();
            question.options().keySet().forEach(option -> probabilities.put(option, 0.0));
            probabilities.put(key(question, "LATTE"), 0.6);
            probabilities.put(key(question, "FLAT_WHITE"), 0.3);
            probabilities.put(key(question, "CAPPUCCINO"), 0.1);
            return DecisionResponse.builder()
                    .modelName("fake")
                    .answer(name, ChoiceAnswer.builder()
                            .value(key(question, "LATTE"))
                            .options(question.options().keySet())
                            .probabilities(probabilities)
                            .build())
                    .build();
        }

        private static String key(ChoiceQuestion question, String constant) {
            return question.options().keySet().stream()
                    .filter(option -> option.equalsIgnoreCase(constant))
                    .findFirst().orElseThrow();
        }
    }

    private final FakeDecisionModel model = new FakeDecisionModel();
    private final CoffeeDecisionService service = DecisionServices.create(CoffeeDecisionService.class, model);

    @Test
    void everyCoffeeIsAnOptionWithItsDescription() {
        service.chooseCoffee("Something milky");

        // Method parameters are sent as named input fields.
        assertEquals(FieldsInput.from(Map.of("customerText", "Something milky")), model.lastRequest.input());
        ChoiceQuestion question = (ChoiceQuestion) model.lastRequest.questions().values().iterator().next();
        assertEquals(CoffeeType.values().length, question.options().size());
        assertTrue(question.options().values().stream().anyMatch(d -> d != null && d.contains("velvety milk")),
                "enum @Description should reach the model");
    }

    @Test
    void responseIsSortedByProbability() {
        var response = DecisionResource.toResponse("Something milky", service.chooseCoffee("Something milky"));

        assertEquals("latte", response.choice());
        assertEquals("fake", response.model());
        assertEquals(CoffeeType.values().length, response.probabilities().size());
        assertEquals("latte", response.probabilities().get(0).coffee());
        assertEquals("Latte", response.probabilities().get(0).label());
        assertEquals(0.6, response.probabilities().get(0).probability());
        assertEquals("flat_white", response.probabilities().get(1).coffee());
    }
}
