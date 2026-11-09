package be.maartenvandeperre.aidemo;

import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.ollama.OllamaDecisionModel;
import dev.langchain4j.service.decision.DecisionServices;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;

/**
 * CDI wiring for the decision model. {@code decision.provider} picks one:
 *
 * <ul>
 *   <li>{@code openai} - any server with the OpenAI Decisions API
 *       (POST /v1/decisions): the Developer Sandbox gateway, vLLM, Granite
 *       Switch, OpenAI. Settings: {@code quarkus.langchain4j.openai.decision.*}.</li>
 *   <li>{@code gemma} - Gemma 4 on the local Ollama. Gemma is a chat model, so
 *       it goes through the decision gateway (compose service
 *       {@code decision-gateway}), which also speaks /v1/decisions.
 *       Settings: {@code decision.gemma.*}.</li>
 *   <li>{@code kev} - kev on the local Ollama. kev is a native decision model;
 *       LangChain4j's {@link OllamaDecisionModel} calls Ollama's decision API
 *       (POST /v1/systemone) directly, no gateway. Settings: {@code decision.kev.*}.</li>
 * </ul>
 *
 * The OpenAI variant is built by hand ({@link DefaultClientOpenAiDecisionModel})
 * because no quarkus-langchain4j release ships decision models yet (quarkiverse
 * PR #3000). Both variants use LangChain4j's JDK HTTP client: the Quarkus
 * clients on the classpath cannot call the decision APIs.
 */
@ApplicationScoped
public class DecisionModels {

    @ConfigProperty(name = "decision.provider", defaultValue = "openai")
    String provider;

    @ConfigProperty(name = "quarkus.langchain4j.openai.decision.base-url")
    String openAiBaseUrl;
    @ConfigProperty(name = "quarkus.langchain4j.openai.decision.api-key")
    String openAiApiKey;
    @ConfigProperty(name = "quarkus.langchain4j.openai.decision.decision-model.model-name")
    String openAiModel;

    @ConfigProperty(name = "decision.gemma.base-url", defaultValue = "http://localhost:8000/v1")
    String gemmaBaseUrl;
    @ConfigProperty(name = "decision.gemma.model-name", defaultValue = "gemma4:latest")
    String gemmaModel;

    @ConfigProperty(name = "decision.kev.base-url", defaultValue = "http://localhost:11434")
    String kevBaseUrl;
    @ConfigProperty(name = "decision.kev.model-name", defaultValue = "kev:0.8b-q8_0")
    String kevModel;

    @ConfigProperty(name = "quarkus.langchain4j.openai.decision.timeout", defaultValue = "60s")
    Duration timeout;
    @ConfigProperty(name = "quarkus.langchain4j.openai.decision.decision-model.log-requests", defaultValue = "false")
    boolean logRequests;
    @ConfigProperty(name = "quarkus.langchain4j.openai.decision.decision-model.log-responses", defaultValue = "false")
    boolean logResponses;

    @Produces
    @ApplicationScoped
    DecisionModel decisionModel() {
        String choice = provider.strip().toLowerCase();
        DecisionModel model = switch (choice) {
            case "openai" -> openAi(openAiBaseUrl, openAiApiKey, openAiModel);
            // The local gateway needs no key; it forwards the model name to Ollama.
            case "gemma" -> openAi(gemmaBaseUrl, "sk-none", gemmaModel);
            case "kev" -> OllamaDecisionModel.builder()
                    .httpClientBuilder(JdkHttpClient.builder())
                    .baseUrl(kevBaseUrl)
                    .modelName(kevModel)
                    .timeout(timeout)
                    .logRequests(logRequests)
                    .logResponses(logResponses)
                    .build();
            default -> throw new IllegalStateException(
                    "decision.provider must be openai, gemma or kev, not '" + provider + "'");
        };
        Log.infof("Decision model: %s (%s)", choice, model.defaultRequestParameters().modelName());
        return model;
    }

    private DecisionModel openAi(String baseUrl, String apiKey, String modelName) {
        Log.debugf("OpenAI Decisions API at %s", baseUrl);
        return new DefaultClientOpenAiDecisionModel(baseUrl, apiKey, modelName, timeout, logRequests, logResponses);
    }

    @Produces
    @ApplicationScoped
    CoffeeDecisionService coffeeDecisionService(DecisionModel decisionModel) {
        return DecisionServices.create(CoffeeDecisionService.class, decisionModel);
    }
}
