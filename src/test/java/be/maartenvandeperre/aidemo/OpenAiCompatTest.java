package be.maartenvandeperre.aidemo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void singleUserMessageIsPassedThrough() {
        String prompt = ConversationPrompt.toUserPrompt(
                List.of(new ConversationPrompt.Turn("user", "  Do you serve an espresso martini?  ")),
                false);
        assertEquals("Do you serve an espresso martini?", prompt);
    }

    @Test
    void followUpIncludesTheTranscript() {
        String prompt = ConversationPrompt.toUserPrompt(List.of(
                new ConversationPrompt.Turn("system", "Be brief"),
                new ConversationPrompt.Turn("user", "What gins do you have?"),
                new ConversationPrompt.Turn("assistant", "Four bottles."),
                new ConversationPrompt.Turn("user", "Any tonic?")), false);

        assertTrue(prompt.contains("System: Be brief"));
        assertTrue(prompt.contains("Customer: What gins do you have?"));
        assertTrue(prompt.contains("You: Four bottles."));
        assertTrue(prompt.contains("Customer: Any tonic?"));
    }

    @Test
    void taskPromptIsReturnedRaw() {
        List<ConversationPrompt.Turn> turns = List.of(
                new ConversationPrompt.Turn("user", "What gins do you have?"),
                new ConversationPrompt.Turn("assistant", "Four."),
                new ConversationPrompt.Turn("user", "### Task:Generate a title"));
        assertEquals("### Task:Generate a title", ConversationPrompt.toUserPrompt(turns, false));
        assertEquals("Any tonic?", ConversationPrompt.toUserPrompt(List.of(
                new ConversationPrompt.Turn("user", "What gins do you have?"),
                new ConversationPrompt.Turn("user", "Any tonic?")), true));
    }

    @Test
    void blankMessagesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ConversationPrompt.toUserPrompt(
                List.of(new ConversationPrompt.Turn("user", "   ")), false));
    }

    @Test
    void messageContentAcceptsTextAndParts() throws Exception {
        assertEquals("Hello", OpenAiMessages.text(mapper.readTree("\"Hello\"")));
        JsonNode parts = mapper.readTree("""
                [{"type":"text","text":"Hello"},{"type":"image_url","image_url":{"url":"http://img"}}]
                """);
        assertEquals("Hello", OpenAiMessages.text(parts));
        assertEquals("", OpenAiMessages.text(null));
    }

    @Test
    void metadataTaskFlag() throws Exception {
        assertTrue(OpenAiMessages.clientTask(mapper.readTree("{\"task\":\"title_generation\"}")));
        assertFalse(OpenAiMessages.clientTask(mapper.readTree("{\"chat_id\":\"abc\"}")));
        assertFalse(OpenAiMessages.clientTask(null));
    }

    @Test
    void modelCatalogListsTheFourStylesInOrder() {
        JsonNode list = OpenAiPayloads.models(mapper, 10);
        assertEquals("list", list.get("object").asText());
        assertEquals(ChatStyle.values().length, list.get("data").size());
        for (int i = 0; i < ChatStyle.values().length; i++) {
            ChatStyle style = ChatStyle.values()[i];
            assertEquals(style.id(), list.get("data").get(i).get("id").asText());
            assertEquals(style.label(), list.get("data").get(i).get("name").asText());
            assertEquals("quarkus", list.get("data").get(i).get("owned_by").asText());
        }
        assertNull(ChatStyle.byId("gpt-5-mini"));
        assertEquals(ChatStyle.RAG, ChatStyle.byId("rag"));
    }

    @Test
    void completionAndStreamCarryTheAnswer() throws Exception {
        String answer = "Line one.\nLine two, with more words so the chunker splits the reply.";
        JsonNode completion = OpenAiPayloads.completion(mapper, "chatcmpl-1", 10, "rag", answer);
        assertEquals("chat.completion", completion.get("object").asText());
        assertEquals(answer, completion.get("choices").get(0).get("message").get("content").asText());
        assertEquals("stop", completion.get("choices").get(0).get("finish_reason").asText());

        List<String> data = OpenAiPayloads.sseData(mapper, "chatcmpl-1", 10, "rag", answer);
        assertEquals("[DONE]", data.getLast());
        JsonNode first = mapper.readTree(data.getFirst());
        assertEquals("assistant", first.get("choices").get(0).get("delta").get("role").asText());
        assertTrue(first.get("choices").get(0).get("finish_reason").isNull());

        StringBuilder streamed = new StringBuilder();
        for (String line : data.subList(0, data.size() - 1)) {
            JsonNode chunk = mapper.readTree(line);
            assertEquals("chat.completion.chunk", chunk.get("object").asText());
            JsonNode delta = chunk.get("choices").get(0).get("delta");
            if (delta.has("content")) {
                streamed.append(delta.get("content").asText());
            }
        }
        assertEquals(answer, streamed.toString());
        JsonNode last = mapper.readTree(data.get(data.size() - 2));
        assertEquals("stop", last.get("choices").get(0).get("finish_reason").asText());
        assertEquals(answer, String.join("", OpenAiPayloads.pieces(answer)));
    }

    @Test
    void providerErrorsSurfaceTheInnerMessage() {
        String raw = """
                { "error": { "message": "Incorrect API key provided: missing-key.", "type": "invalid_request_error" } }
                """;
        assertEquals("Incorrect API key provided: missing-key.", OpenAiPayloads.providerMessage(mapper, raw));
        assertEquals("The model request failed", OpenAiPayloads.providerMessage(mapper, "  "));
        assertEquals("connection reset", OpenAiPayloads.providerMessage(mapper, "connection reset"));
    }
}
