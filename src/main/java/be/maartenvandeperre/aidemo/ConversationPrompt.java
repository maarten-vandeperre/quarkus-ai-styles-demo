package be.maartenvandeperre.aidemo;

import java.util.List;

/**
 * Turns an OpenAI {@code messages} array into the single user string the
 * demo assistants accept.
 *
 * <p>A lone user message is passed through unchanged, so the first turn in
 * the chat matches {@code POST /ai/*}. Later turns include the transcript.
 * Open WebUI task prompts (titles, tags, follow-ups) are returned as the
 * raw user text, because that text already contains the instructions and
 * the excerpt of the chat they need.
 */
public final class ConversationPrompt {

    public record Turn(String role, String text) {}

    private ConversationPrompt() {}

    public static String toUserPrompt(List<Turn> turns, boolean clientTask) {
        List<Turn> meaningful = turns.stream()
                .filter(turn -> turn.text() != null && !turn.text().isBlank())
                .toList();
        if (meaningful.isEmpty()) {
            throw new IllegalArgumentException("messages must include non-empty text");
        }
        if (clientTask || isTaskPrompt(meaningful)) {
            String task = lastUserText(meaningful);
            if (task == null) {
                throw new IllegalArgumentException("task request has no user message");
            }
            return task.strip();
        }
        if (meaningful.size() == 1 && "user".equals(meaningful.get(0).role())) {
            return meaningful.get(0).text().strip();
        }
        StringBuilder transcript = new StringBuilder();
        transcript.append("Continue this conversation. Answer the latest customer message.\n\n");
        for (Turn turn : meaningful) {
            transcript.append(label(turn.role()))
                    .append(": ")
                    .append(turn.text().strip())
                    .append("\n\n");
        }
        return transcript.toString().strip();
    }

    private static boolean isTaskPrompt(List<Turn> turns) {
        String lastUser = lastUserText(turns);
        return lastUser != null && lastUser.stripLeading().startsWith("### Task:");
    }

    private static String lastUserText(List<Turn> turns) {
        String last = null;
        for (Turn turn : turns) {
            if ("user".equals(turn.role())) {
                last = turn.text();
            }
        }
        return last;
    }

    private static String label(String role) {
        return switch (role == null ? "" : role) {
            case "user" -> "Customer";
            case "assistant" -> "You";
            case "system" -> "System";
            case "tool" -> "Tool";
            default -> role.isBlank() ? "Message" : role;
        };
    }
}
