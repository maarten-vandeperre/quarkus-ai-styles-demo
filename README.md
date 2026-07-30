# AI Styles Demo — Plain calling vs. RAG vs. Agentic AI vs. MCP

A Quarkus (Java 25, Gradle) demo application accompanying the blog post
*"Four Ways to Serve a Drink: Plain Model Calling, RAG, Agentic AI and MCP in Quarkus"*.

The same bar-themed use case is implemented four times, once per interaction pattern,
so you can fire the same question at each endpoint and compare what actually happens
(enable `quarkus.langchain4j.log-requests=true` — it already is — and watch the prompts).

| Endpoint     | Pattern              | Class                              |
|--------------|----------------------|------------------------------------|
| `POST /ai/plain` | Plain model calling  | `PlainAssistant`               |
| `POST /ai/rag`   | RAG (Easy RAG)       | `RagAssistant`                 |
| `POST /ai/agent` | Agentic / tool calls | `AgentAssistant` + `BarTools`  |
| `POST /ai/mcp`   | MCP client           | `McpAssistant`                 |

## Prerequisites

- Java 25 (the Gradle toolchain will download one if needed)
- Node.js 18+ (only for the `/ai/mcp` endpoint: it launches the reference
  filesystem MCP server via `npx @modelcontextprotocol/server-filesystem`)
- An API key for at least one of the five supported providers

## Switching providers

Open `src/main/resources/application.properties` and change **one line**:

```properties
quarkus.profile=openai   # openai | anthropic | mistral | kimi | gemini
```

Each value activates a Quarkus configuration profile that sets the LangChain4j
provider, model name and (for Kimi and Gemini) an OpenAI-compatible `base-url`.
In dev mode (`./gradlew quarkusDev`) the change is picked up on live reload.
For a production build, the provider is a build-time choice:

```bash
./gradlew build -Dquarkus.profile=anthropic
```

Design note: OpenAI, Kimi and Gemini all go through the `quarkus-langchain4j-openai`
extension (Kimi and Gemini expose OpenAI-compatible endpoints), while Anthropic and
Mistral use their native extensions. Your application code never changes — the AI
service interfaces are provider-agnostic. That is the whole point.

## Getting the API keys

Export the key for the provider you selected; the properties file reads them
from environment variables so nothing sensitive lands in git.

### OpenAI
1. Go to <https://platform.openai.com>, sign in, open **Settings → API keys**.
2. Create a key and add billing credit (pay-as-you-go).
3. `export OPENAI_API_KEY=sk-...`
4. Model configured: `gpt-5-mini` (change via `%openai.quarkus.langchain4j.openai.chat-model.model-name`).

### Anthropic (Claude)
1. Go to <https://console.anthropic.com>, sign in, open **API keys**.
2. Create a key and add credit.
3. `export ANTHROPIC_API_KEY=sk-ant-...`
4. Model configured: `claude-sonnet-4-6`.

### Mistral AI
1. Go to <https://console.mistral.ai>, create an account, open **API keys**.
2. Create a key (Mistral has a free experiment tier with rate limits).
3. `export MISTRAL_API_KEY=...`
4. Model configured: `mistral-small-latest`.

### Kimi (Moonshot AI)
1. Go to <https://platform.moonshot.ai>, register, open **API keys**.
2. Create a key and top up the balance.
3. `export KIMI_API_KEY=sk-...`
4. Model configured: `kimi-k2-0711-preview` — check the platform's model list,
   Moonshot rotates preview model names regularly.

### Gemini (Google)
1. Go to <https://aistudio.google.com> and click **Get API key** (free tier available).
2. `export GEMINI_API_KEY=AIza...`
3. Model configured: `gemini-2.5-flash`. We use Gemini's OpenAI-compatible
   endpoint (`.../v1beta/openai`), so no Google Cloud project setup is required.

## Running

```bash
export OPENAI_API_KEY=sk-...        # or the key matching your chosen profile
./gradlew quarkusDev
```

Then, in another terminal:

```bash
# 1. Plain: answers from training data ("bartender's memory")
curl -s -X POST localhost:8080/ai/plain -H 'Content-Type: text/plain' \
     -d 'Do you serve an espresso martini, and what is in it?'

# 2. RAG: answers from the docs in src/main/resources/rag-docs
curl -s -X POST localhost:8080/ai/rag -H 'Content-Type: text/plain' \
     -d 'Do you serve an espresso martini, and what is in it?'

# 3. Agent: the model calls Java tools (check stock, place order)
curl -s -X POST localhost:8080/ai/agent -H 'Content-Type: text/plain' \
     -d 'A customer at table 7 wants an espresso martini. Handle it.'

# 4. MCP: the model uses tools from an external MCP server (filesystem)
curl -s -X POST localhost:8080/ai/mcp -H 'Content-Type: text/plain' \
     -d 'How many bottles of gin are in the cellar? Check the inventory file.'
```

Compare answers: `/ai/plain` gives you a generic espresso martini recipe,
`/ai/rag` quotes the house menu (Supersonic, EUR 12.50), `/ai/agent` discovers
vodka is out of stock and refuses/substitutes, and `/ai/mcp` reads
`playground/cellar-inventory.txt` through the MCP filesystem server.

## Notes & troubleshooting

- **Versions**: built against Quarkus 3.37.3 with quarkus-langchain4j versions
  managed by the `quarkus-langchain4j-bom` platform BOM (see `gradle.properties`
  and `build.gradle`). If a newer platform renames a config key, the extension
  docs are authoritative: <https://docs.quarkiverse.io/quarkus-langchain4j/dev/>.
  This project was generated as sample code and has not been compiled in your
  environment — run `gradle build` once before demoing it live.
- **Easy RAG opt-out**: with `quarkus-langchain4j-easy-rag` on the classpath,
  every `@RegisterAiService` gets retrieval by default. The plain, agent and MCP
  services explicitly opt out via `RegisterAiService.NoRetrievalAugmentorSupplier`.
- **First RAG request is slow**: the ONNX embedding model is loaded in-process
  on first use; subsequent requests are fast.
- **MCP endpoint fails**: make sure `npx` is on the PATH of the JVM process and
  that the `playground` folder exists relative to the working directory.
- **Tool calling quality differs per provider/model**: smaller or older models
  are noticeably worse at deciding when and how to call tools. If `/ai/agent`
  misbehaves on one provider, try the same request on another — that contrast
  is itself a useful demo.
