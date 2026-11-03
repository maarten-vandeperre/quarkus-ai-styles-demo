package be.maartenvandeperre.aidemo;

import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.openai.internal.DefaultOpenAiClient;
import dev.langchain4j.model.openai.internal.OpenAiClient;
import dev.langchain4j.model.openai.internal.decision.DecisionMapper;

import java.time.Duration;

/**
 * LangChain4j's OpenAI decision model, on LangChain4j's own HTTP client.
 *
 * {@code OpenAiDecisionModel} asks the OpenAI client SPI for its client. The
 * quarkus-langchain4j OpenAI extension registers a Quarkus client there, and
 * that client cannot call the Decisions API yet ("Decisions are not supported
 * by this client implementation"). This class builds the default client
 * instead (on the JDK HTTP client) and reuses LangChain4j's request/response mapping, so the wire
 * format and validation are exactly LangChain4j's.
 *
 * Temporary: remove it once the quarkus-langchain4j release with OpenAI
 * decision models (quarkiverse PR #3000) is out.
 */
final class DefaultClientOpenAiDecisionModel implements DecisionModel {

    private final OpenAiClient client;
    private final DecisionRequestParameters defaults;

    DefaultClientOpenAiDecisionModel(String baseUrl, String apiKey, String modelName, Duration timeout,
                                     boolean logRequests, boolean logResponses) {
        this.client = DefaultOpenAiClient.builder()
                // Explicit: the Quarkus JAX-RS client is on the classpath too.
                .httpClientBuilder(JdkHttpClient.builder())
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
        this.defaults = DecisionRequestParameters.builder().modelName(modelName).build();
    }

    @Override
    public DecisionResponse doDecide(DecisionRequest request) {
        DecisionRequest withModel = request.modelName() != null ? request
                : request.toBuilder().parameters(defaults.overrideWith(request.parameters())).build();
        return DecisionMapper.toDecisionResponse(
                client.decision(DecisionMapper.toOpenAiRequest(withModel)).execute(), withModel);
    }

    @Override
    public DecisionRequestParameters defaultRequestParameters() {
        return defaults;
    }
}
