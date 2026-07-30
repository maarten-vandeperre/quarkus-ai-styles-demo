package be.maartenvandeperre.aidemo;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mcp.runtime.McpToolBox;

/**
 * PATTERN 4 - MCP (Model Context Protocol).
 *
 * Conceptually this is STILL agentic AI - the model calls tools. The
 * difference is where the tools live and how they are described. With
 * plain tool calling (AgentAssistant) the tools are Java methods inside
 * this application. With MCP the tools are exposed by an external MCP
 * SERVER over a standardized protocol; any MCP-capable client can use
 * them without custom glue code. MCP is the standardized service hatch
 * between the bar and the kitchen, not a new kind of bartender.
 *
 * Here we connect to the reference filesystem MCP server (configured in
 * application.properties as the "cellar" client), which exposes
 * list/read/write tools for the ./playground folder.
 */
@RegisterAiService(retrievalAugmentor = RegisterAiService.NoRetrievalAugmentorSupplier.class)
public interface McpAssistant {

    @SystemMessage("""
            You are the cellar manager of 'The Quarkus Tap'. The cellar
            administration lives in text files that you can access through
            your tools. Read files before answering questions about the
            cellar, and update files when asked to register deliveries.
            Mention which files you consulted.
            """)
    @McpToolBox("cellar")
    String manage(@UserMessage String request);
}
