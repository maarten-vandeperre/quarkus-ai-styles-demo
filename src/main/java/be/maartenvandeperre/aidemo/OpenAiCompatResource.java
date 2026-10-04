package be.maartenvandeperre.aidemo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.smallrye.common.annotation.Blocking;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * OpenAI-compatible API so a chat UI (Open WebUI) can talk to the four
 * demo assistants. Each {@link ChatStyle} is a model id:
 *
 * <pre>
 *   GET  /v1/models
 *   POST /v1/chat/completions   body.model = plain | rag | agent | mcp
 * </pre>
 *
 * The reply comes from the same assistant as {@code POST /ai/{style}}.
 * Tool calls and MCP round-trips finish before any tokens are written, then
 * the text is flushed as server-sent events when the client sets
 * {@code "stream": true} (Open WebUI does this by default).
 *
 * <p>Any API key is accepted. This endpoint is a local demo shim — keep
 * port 8080 on your own machine.
 */
@Path("/v1")
public class OpenAiCompatResource {

    private static final Logger LOG = Logger.getLogger(OpenAiCompatResource.class);

    private final PlainAssistant plain;
    private final RagAssistant rag;
    private final AgentAssistant agent;
    private final McpAssistant mcp;
    private final TaskAssistant tasks;
    private final ObjectMapper mapper;
    private final String activeProfile;

    public OpenAiCompatResource(PlainAssistant plain,
                                RagAssistant rag,
                                AgentAssistant agent,
                                McpAssistant mcp,
                                TaskAssistant tasks,
                                ObjectMapper mapper,
                                @ConfigProperty(name = "quarkus.profile") String activeProfile) {
        this.plain = plain;
        this.rag = rag;
        this.agent = agent;
        this.mcp = mcp;
        this.tasks = tasks;
        this.mapper = mapper;
        this.activeProfile = activeProfile;
    }

    @GET
    @Path("/models")
    @Produces(MediaType.APPLICATION_JSON)
    public ObjectNode models() {
        return OpenAiPayloads.models(mapper, now());
    }

    @GET
    @Path("/models/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response model(@PathParam("id") String id) {
        ChatStyle style = ChatStyle.byId(id);
        if (style == null) {
            return error(404, "unknown model: " + id, "invalid_request_error", "model_not_found");
        }
        return Response.ok(OpenAiPayloads.model(mapper, style, now())).build();
    }

    @POST
    @Path("/chat/completions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Blocking
    public Response chat(OpenAiMessages.ChatCompletionRequest request) {
        if (request == null || request.model() == null || request.model().isBlank()) {
            return error(400, "model is required", "invalid_request_error", "model_required");
        }
        String model = request.model().strip();
        ChatStyle style = ChatStyle.byId(model);
        if (style == null) {
            return error(404, "unknown model: " + model, "invalid_request_error", "model_not_found");
        }

        boolean task = OpenAiMessages.clientTask(request.metadata());
        String prompt;
        try {
            prompt = ConversationPrompt.toUserPrompt(OpenAiMessages.turns(request.messages()), task);
        } catch (IllegalArgumentException e) {
            return error(400, e.getMessage(), "invalid_request_error", "invalid_messages");
        }
        task = task || prompt.stripLeading().startsWith("### Task:");

        boolean stream = Boolean.TRUE.equals(request.stream());
        LOG.infof("Chat completion model=%s task=%s stream=%s", model, task, stream);

        String answer;
        try {
            answer = task ? tasks.complete(prompt) : decorate(dispatch(style, prompt));
        } catch (RuntimeException e) {
            LOG.errorf(e, "Chat completion failed for model %s", model);
            return error(502, OpenAiPayloads.providerMessage(mapper, e.getMessage()), "server_error", "model_error");
        }
        if (answer == null) {
            answer = "";
        }

        String id = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "");
        long created = now();
        if (stream) {
            return sse(OpenAiPayloads.sseData(mapper, id, created, model, answer));
        }
        return Response.ok(OpenAiPayloads.completion(mapper, id, created, model, answer))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    private String dispatch(ChatStyle style, String prompt) {
        return switch (style) {
            case PLAIN -> plain.chat(prompt);
            case RAG -> rag.chat(prompt);
            case AGENT -> agent.handle(prompt);
            case MCP -> mcp.manage(prompt);
        };
    }

    private String decorate(String answer) {
        return "[provider profile: %s]%n%s%n".formatted(activeProfile, answer == null ? "" : answer);
    }

    private Response sse(List<String> dataLines) {
        StreamingOutput body = output -> {
            Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
            for (String line : dataLines) {
                writer.write("data: ");
                writer.write(line);
                writer.write("\n\n");
                writer.flush();
            }
        };
        return Response.ok(body)
                .type(MediaType.SERVER_SENT_EVENTS + ";charset=UTF-8")
                .header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no")
                .build();
    }

    private Response error(int status, String message, String type, String code) {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode body = root.putObject("error");
        body.put("message", message);
        body.put("type", type);
        body.putNull("param");
        if (code == null) {
            body.putNull("code");
        } else {
            body.put("code", code);
        }
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(root).build();
    }

    private static long now() {
        return Instant.now().getEpochSecond();
    }
}
