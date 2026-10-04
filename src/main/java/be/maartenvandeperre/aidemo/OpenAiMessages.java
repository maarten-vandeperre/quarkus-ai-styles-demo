package be.maartenvandeperre.aidemo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * The slice of the OpenAI chat-completions request this demo understands.
 * Extra fields Open WebUI sends (temperature, tools, user id, ...) are ignored.
 */
public final class OpenAiMessages {

    private OpenAiMessages() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatCompletionRequest(
            String model,
            List<ChatMessage> messages,
            Boolean stream,
            JsonNode metadata) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatMessage(String role, JsonNode content) {}

    static List<ConversationPrompt.Turn> turns(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<ConversationPrompt.Turn> turns = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (message == null) {
                continue;
            }
            turns.add(new ConversationPrompt.Turn(message.role(), text(message.content())));
        }
        return turns;
    }

    static boolean clientTask(JsonNode metadata) {
        if (metadata == null || !metadata.isObject()) {
            return false;
        }
        JsonNode task = metadata.get("task");
        if (task == null || task.isNull()) {
            return false;
        }
        return !task.isTextual() || !task.asText().isBlank();
    }

    /**
     * OpenAI {@code content} is either a string or an array of parts
     * ({@code {"type":"text","text":"..."}}). Image parts are skipped;
     * this demo is text-only.
     */
    static String text(JsonNode content) {
        if (content == null || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return content.asText();
        }
        if (content.isArray()) {
            StringBuilder text = new StringBuilder();
            for (JsonNode part : content) {
                String piece = partText(part);
                if (piece.isEmpty()) {
                    continue;
                }
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(piece);
            }
            return text.toString();
        }
        if (content.hasNonNull("text")) {
            return content.get("text").asText();
        }
        return "";
    }

    private static String partText(JsonNode part) {
        if (part == null || part.isNull()) {
            return "";
        }
        if (part.isTextual()) {
            return part.asText();
        }
        if (part.hasNonNull("text")) {
            return part.get("text").asText();
        }
        return "";
    }
}
