package be.maartenvandeperre.aidemo;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.service.decision.DecisionServices;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;

/**
 * CDI wiring for the decision model.
 *
 * quarkus-langchain4j adds OpenAI decision models (quarkiverse PR #3000), but
 * no release ships it yet. Until then this class builds LangChain4j's OpenAI
 * decision model itself ({@link DefaultClientOpenAiDecisionModel}), reading
 * the property names the extension will use for the named model
 * {@code decision}. Once the extension is released, delete this producer and
 * inject {@code @ModelName("decision") DecisionModel} instead; the properties stay.
 *
 * Any server that implements the OpenAI Decisions API (POST /v1/decisions)
 * works: vLLM, Granite Switch, or the decision gateway in deploy/openshift.
 */
@ApplicationScoped
public class DecisionModels {

    @Produces
    @ApplicationScoped
    DecisionModel decisionModel(
            @ConfigProperty(name = "quarkus.langchain4j.openai.decision.base-url") String baseUrl,
            @ConfigProperty(name = "quarkus.langchain4j.openai.decision.api-key") String apiKey,
            @ConfigProperty(name = "quarkus.langchain4j.openai.decision.decision-model.model-name") String modelName,
            @ConfigProperty(name = "quarkus.langchain4j.openai.decision.timeout", defaultValue = "60s") Duration timeout,
            @ConfigProperty(name = "quarkus.langchain4j.openai.decision.decision-model.log-requests", defaultValue = "false") boolean logRequests,
            @ConfigProperty(name = "quarkus.langchain4j.openai.decision.decision-model.log-responses", defaultValue = "false") boolean logResponses) {
        return new DefaultClientOpenAiDecisionModel(baseUrl, apiKey, modelName, timeout, logRequests, logResponses);
    }

    @Produces
    @ApplicationScoped
    CoffeeDecisionService coffeeDecisionService(DecisionModel decisionModel) {
        return DecisionServices.create(CoffeeDecisionService.class, decisionModel);
    }
}
