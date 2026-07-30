package be.maartenvandeperre.aidemo;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * PATTERN 2 - Retrieval Augmented Generation (RAG).
 *
 * Before the model is called, the question is embedded, matched against
 * the documents in src/main/resources/rag-docs (ingested at startup by
 * the Easy RAG extension), and the most relevant segments are appended
 * to the prompt. The model "knows nothing but can find almost anything":
 * it answers from YOUR documents, not from its training data.
 *
 * Because Easy RAG registers a default retrieval augmentor, this service
 * needs no extra wiring - not opting out IS opting in.
 */
@RegisterAiService
public interface RagAssistant {

    @SystemMessage("""
            You are the bartender of 'The Quarkus Tap'. Answer ONLY based on
            the house documents provided in the context (menu, house rules).
            If the answer is not in the context, say that it is not on the
            menu. Always mention which document you based your answer on.
            """)
    String chat(@UserMessage String question);
}
