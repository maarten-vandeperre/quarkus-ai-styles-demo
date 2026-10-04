package be.maartenvandeperre.aidemo;

/**
 * The four interaction patterns, exposed to a chat UI as model ids.
 * Selecting {@code rag} in the UI is the same assistant as {@code POST /ai/rag}.
 */
public enum ChatStyle {
    PLAIN("plain", "Plain calling"),
    RAG("rag", "RAG"),
    AGENT("agent", "Agent"),
    MCP("mcp", "MCP");

    private final String id;
    private final String label;

    ChatStyle(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static ChatStyle byId(String id) {
        if (id == null) {
            return null;
        }
        for (ChatStyle style : values()) {
            if (style.id.equals(id)) {
                return style;
            }
        }
        return null;
    }
}
