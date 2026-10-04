package be.maartenvandeperre.aidemo;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * Neutral completion for chat-UI side tasks (titles, tags, follow-up
 * suggestions). Those prompts ask for raw JSON. The bartender, menu, and
 * tool prompts would compete with that format, so side tasks stay here.
 */
@RegisterAiService(retrievalAugmentor = RegisterAiService.NoRetrievalAugmentorSupplier.class)
public interface TaskAssistant {

    @SystemMessage("""
            Follow the user's instructions exactly.
            When the user asks for JSON, reply with that JSON only:
            no markdown fences, no role-play, and no extra text.
            """)
    String complete(@UserMessage String prompt);
}
