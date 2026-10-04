package be.maartenvandeperre.aidemo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI chat-completions JSON: the model list, a single completion, and
 * the server-sent events used when the client asks for {@code stream: true}.
 */
final class OpenAiPayloads {

    private OpenAiPayloads() {}

    static ObjectNode models(ObjectMapper mapper, long created) {
        ObjectNode root = mapper.createObjectNode();
        root.put("object", "list");
        var data = root.putArray("data");
        for (ChatStyle style : ChatStyle.values()) {
            data.add(model(mapper, style, created));
        }
        return root;
    }

    static ObjectNode model(ObjectMapper mapper, ChatStyle style, long created) {
        ObjectNode card = mapper.createObjectNode();
        card.put("id", style.id());
        card.put("object", "model");
        card.put("created", created);
        card.put("owned_by", "quarkus");
        card.put("name", style.label());
        return card;
    }

    static ObjectNode completion(ObjectMapper mapper, String id, long created, String model, String content) {
        ObjectNode root = mapper.createObjectNode();
        root.put("id", id);
        root.put("object", "chat.completion");
        root.put("created", created);
        root.put("model", model);
        ObjectNode choice = root.putArray("choices").addObject();
        choice.put("index", 0);
        ObjectNode message = choice.putObject("message");
        message.put("role", "assistant");
        message.put("content", content);
        choice.put("finish_reason", "stop");
        return root;
    }

    /**
     * SSE {@code data:} payloads. The last entry is the literal {@code [DONE]}.
     * The assistant text is split into small pieces so a chat UI can render
     * it as it arrives; the pieces concatenate back to {@code content}.
     */
    static List<String> sseData(ObjectMapper mapper, String id, long created, String model, String content) {
        List<String> lines = new ArrayList<>();
        lines.add(write(mapper, chunk(mapper, id, created, model, "assistant", "", null)));
        for (String piece : pieces(content)) {
            lines.add(write(mapper, chunk(mapper, id, created, model, null, piece, null)));
        }
        lines.add(write(mapper, chunk(mapper, id, created, model, null, null, "stop")));
        lines.add("[DONE]");
        return lines;
    }

    private static ObjectNode chunk(ObjectMapper mapper, String id, long created, String model,
                                    String role, String content, String finishReason) {
        ObjectNode root = mapper.createObjectNode();
        root.put("id", id);
        root.put("object", "chat.completion.chunk");
        root.put("created", created);
        root.put("model", model);
        ObjectNode choice = root.putArray("choices").addObject();
        choice.put("index", 0);
        ObjectNode delta = choice.putObject("delta");
        if (role != null) {
            delta.put("role", role);
        }
        if (content != null) {
            delta.put("content", content);
        }
        if (finishReason == null) {
            choice.putNull("finish_reason");
        } else {
            choice.put("finish_reason", finishReason);
        }
        return root;
    }

    private static String write(ObjectMapper mapper, ObjectNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Could not encode a chat chunk", e);
        }
    }

    /**
     * Provider failures often arrive as a JSON blob. The chat UI shows this
     * string directly, so prefer the inner OpenAI {@code error.message}.
     */
    static String providerMessage(ObjectMapper mapper, String raw) {
        if (raw == null || raw.isBlank()) {
            return "The model request failed";
        }
        String flattened = raw.strip().replaceAll("\\s+", " ");
        int start = flattened.indexOf('{');
        if (start >= 0) {
            try {
                JsonNode inner = mapper.readTree(flattened.substring(start)).path("error").path("message");
                if (inner.isTextual() && !inner.asText().isBlank()) {
                    flattened = inner.asText().strip();
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
                // keep the flattened provider text
            }
        }
        return flattened.length() > 500 ? flattened.substring(0, 500) + "…" : flattened;
    }

    static List<String> pieces(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int words = 0;
        for (String token : text.split("(?<=\\s)", -1)) {
            if (token.isEmpty()) {
                continue;
            }
            current.append(token);
            if (!token.isBlank()) {
                words++;
            }
            if (words >= 8 || current.length() >= 48) {
                parts.add(current.toString());
                current.setLength(0);
                words = 0;
            }
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }
}
