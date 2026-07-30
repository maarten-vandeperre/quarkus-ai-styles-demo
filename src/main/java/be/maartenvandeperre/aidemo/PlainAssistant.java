package be.maartenvandeperre.aidemo;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * PATTERN 1 - Plain model calling.
 *
 * A single request/response against the model. No retrieval, no tools,
 * no orchestration. The model answers purely from what it learned during
 * training ("the bartender mixes from memory").
 *
 * Note the explicit opt-out of the retrieval augmentor: because the
 * Easy RAG extension is on the classpath, it would otherwise inject
 * document retrieval into EVERY AI service. Here we want the raw model.
 */
@RegisterAiService(retrievalAugmentor = RegisterAiService.NoRetrievalAugmentorSupplier.class)
public interface PlainAssistant {

    @SystemMessage("""
            You are a knowledgeable bartender. Answer the customer's question
            concisely, from your own general knowledge only. If you are not
            sure, say so instead of guessing.
            """)
    String chat(@UserMessage String question);
}
