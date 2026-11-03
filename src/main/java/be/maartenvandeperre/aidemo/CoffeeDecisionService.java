package be.maartenvandeperre.aidemo;

import dev.langchain4j.service.decision.Choice;
import dev.langchain4j.service.decision.Decide;
import dev.langchain4j.service.decision.DecisionResult;

/**
 * Decision service: the declarative counterpart of an AI service, but for a
 * decision model. LangChain4j turns the enum into a choice question, sends
 * it to the model's Decisions API, and returns a probability per coffee.
 *
 * There is no prompt and no JSON to parse: the probabilities come from the
 * model (token logprobs), not from text the model writes.
 * Wired in {@link DecisionModels}.
 */
public interface CoffeeDecisionService {

    @Decide("Which coffee from the menu does the customer want?")
    DecisionResult<Choice<CoffeeType>> chooseCoffee(String customerText);
}
