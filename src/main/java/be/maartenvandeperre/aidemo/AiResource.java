package be.maartenvandeperre.aidemo;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * One endpoint per pattern, so you can fire the SAME question at all four
 * and compare behaviour (and, with log-requests=true, compare the actual
 * prompts that go over the wire).
 *
 * Examples:
 *   curl -s -X POST localhost:8080/ai/plain -H 'Content-Type: text/plain' \
 *        -d 'Do you serve an espresso martini and what is in it?'
 *   curl -s -X POST localhost:8080/ai/rag   -d '...same question...'
 *   curl -s -X POST localhost:8080/ai/agent -d 'Order an espresso martini for table 7 if possible.'
 *   curl -s -X POST localhost:8080/ai/mcp   -d 'What is currently in the cellar according to the inventory file?'
 */
@Path("/ai")
@Consumes(MediaType.TEXT_PLAIN)
@Produces(MediaType.TEXT_PLAIN)
public class AiResource {

    private final PlainAssistant plain;
    private final RagAssistant rag;
    private final AgentAssistant agent;
    private final McpAssistant mcp;
    private final String activeProfile;

    public AiResource(PlainAssistant plain,
                      RagAssistant rag,
                      AgentAssistant agent,
                      McpAssistant mcp,
                      @ConfigProperty(name = "quarkus.profile") String activeProfile) {
        this.plain = plain;
        this.rag = rag;
        this.agent = agent;
        this.mcp = mcp;
        this.activeProfile = activeProfile;
    }

    @POST
    @Path("/plain")
    public String plain(String question) {
        return decorate(plain.chat(question));
    }

    @POST
    @Path("/rag")
    public String rag(String question) {
        return decorate(rag.chat(question));
    }

    @POST
    @Path("/agent")
    public String agent(String request) {
        return decorate(agent.handle(request));
    }

    @POST
    @Path("/mcp")
    public String mcp(String request) {
        return decorate(mcp.manage(request));
    }

    private String decorate(String answer) {
        return "[provider profile: %s]%n%s%n".formatted(activeProfile, answer);
    }
}
