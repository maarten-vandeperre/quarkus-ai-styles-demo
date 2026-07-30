package be.maartenvandeperre.aidemo;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * PATTERN 3 - Agentic AI (tool calling / function calling).
 *
 * The model is allowed to ACT: it decides which of the BarTools methods
 * to call, in which order, interprets the results and loops until it can
 * answer. "Before you sit down, she has already checked the kitchen and
 * ordered your sparkling water."
 *
 * Guardrails matter here: keep the tool surface small, validate inputs,
 * and set an upper bound on the number of tool-call iterations.
 */
@RegisterAiService(
        tools = BarTools.class,
        retrievalAugmentor = RegisterAiService.NoRetrievalAugmentorSupplier.class)
public interface AgentAssistant {

    @SystemMessage("""
            You are the operations assistant of 'The Quarkus Tap' bar.
            Use the available tools to check stock and place orders.
            Never place an order when an ingredient is out of stock;
            propose an alternative instead. Always report which tool
            calls you made and why.
            """)
    String handle(@UserMessage String request);
}
