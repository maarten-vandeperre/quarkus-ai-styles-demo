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
| `POST /v1/chat/completions` | Same four patterns, as a chat | `OpenAiCompatResource` |
| `POST /ai/decision` | Decision model (coffee + probabilities) | `DecisionResource` + `CoffeeDecisionService` |

The chat endpoint speaks the OpenAI API, so [Open WebUI](#chat-ui-open-webui)
can sit in front of the app. The model you pick (`plain`, `rag`, `agent`, `mcp`)
selects the row above.

> **Windows?** See [README-windows.md](README-windows.md) for the same steps in PowerShell.

## Prerequisites

- Java 25 (the Gradle toolchain will download one if needed)
- Node.js 18+ (only for the `/ai/mcp` endpoint: it launches the reference
  filesystem MCP server via `npx @modelcontextprotocol/server-filesystem`)
- An API key for at least one of the cloud providers, **or** a local
  [Ollama](https://ollama.com) install, **or** [Podman Desktop](https://podman-desktop.io)
  with the AI Lab extension serving Qwen (no key needed)
- Docker or Podman, only for the Open WebUI chat (the curl endpoints run without it)

## Switching providers

Open `src/main/resources/application.properties` and change **one line**:

```properties
quarkus.profile=openai   # openai | anthropic | mistral | kimi | gemini | ollama | qwen
```

Each value activates a Quarkus configuration profile that sets the LangChain4j
provider, model name and (for Kimi, Gemini and Qwen) an OpenAI-compatible `base-url`.
In dev mode (`./gradlew quarkusDev`) the change is picked up on live reload.
For a production build, the provider is a build-time choice:

```bash
./gradlew build -Dquarkus.profile=anthropic
```

Design note: OpenAI, Kimi, Gemini and Qwen all go through the `quarkus-langchain4j-openai`
extension (Kimi, Gemini and Podman AI Lab expose OpenAI-compatible endpoints), while Anthropic,
Mistral and Ollama each use their own native extension. Your application code never
changes — the AI service interfaces are provider-agnostic. That is the whole point.

## Setting up providers

Export the key for the provider you selected; the properties file reads them
from environment variables so nothing sensitive lands in git.
Ollama and Qwen on Podman AI Lab run locally and need no API key — see the
dedicated sections below.

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

### Ollama (local — no API key needed)

Ollama lets you run open models entirely on your machine.
This demo is pre-configured for **Gemma 4 12B** (~7.6 GB download, runs on 16 GB RAM).

1. Install Ollama from <https://ollama.com> (macOS, Linux, Windows).
2. Pull the model:
   ```bash
   ollama pull gemma4:12b
   ```
3. Verify it is running:
   ```bash
   ollama list          # should show gemma4:12b
   ollama run gemma4:12b "Hello!"   # quick smoke test, then /bye to exit
   ```
4. Set the profile: `quarkus.profile=ollama` — no environment variable needed.
5. The Ollama server starts automatically on `http://localhost:11434` when you
   run any `ollama` command. If Quarkus cannot connect, make sure the server is
   up (`ollama serve` in a separate terminal).

To use a different model, change `%ollama.quarkus.langchain4j.ollama.chat-model.model-name`
in `application.properties`. Any model from the [Ollama library](https://ollama.com/library)
works (e.g. `llama4:scout`, `qwen3:8b`, `mistral:latest`).

> **Tip — Apple Silicon**: if you are on an M-series Mac, the MLX-optimised
> variant `gemma4:12b-mlx` may give better throughput. Pull it with
> `ollama pull gemma4:12b-mlx` and update the model name accordingly.

### Qwen (Podman Desktop AI Lab — local, no API key needed)

[Podman AI Lab](https://podman-desktop.io/docs/ai-lab/start-inference-server) runs a
model in a container and exposes an OpenAI-compatible chat API. This profile
is set up for **Qwen3 4B** (`qwen/qwen3-4b-GGUF`) on port **35000**.

1. Install [Podman Desktop](https://podman-desktop.io) and, from its Extensions
   catalog, install **Podman AI Lab**.
2. In AI Lab, open **Catalog**, download **Qwen3 4B** (`qwen/qwen3-4b-GGUF`).
3. Open **Services → New Model Service**, select that model, set the port to
   `35000`, and create the service. Wait until the service details page shows
   the inference URL.
4. Set the profile: `quarkus.profile=qwen` — no environment variable needed.
   The OpenAI client still sends a key; the properties file uses the dummy
   value `sk-podman`, which the local server accepts.

If Service Details shows a different port, point the app at it without editing
the file:

```bash
export PODMAN_AI_BASE_URL=http://localhost:<port>/v1
```

Podman AI Lab serves one model at a time, whichever service is running. To
use another catalog model (for example the Qwen3 4B thinking variant), change
`%qwen.quarkus.langchain4j.openai.chat-model.model-name` to the name shown in
Service Details. Local inference is slower than the cloud providers, so the
profile waits up to 180 seconds per model call.

> **Tip — macOS**: creating the model service may offer a GPU-enabled Podman
> machine. Use it when you can; otherwise the model runs on CPU and the first
> reply takes longer.

## Running

```bash
export OPENAI_API_KEY=sk-...        # or the key matching your chosen profile
./gradlew quarkusDev
```

Then, in another terminal, run the four requests one by one (each block runs
on its own from IntelliJ's Markdown preview). Windows users: see
[README-windows.md](README-windows.md) for the PowerShell versions.

**1. Plain**: answers from training data ("bartender's memory")

```bash
curl -s -X POST localhost:8080/ai/plain -H 'Content-Type: text/plain' \
     -d 'Do you serve an espresso martini, and what is in it?'
```

**2. RAG**: answers from the docs in `src/main/resources/rag-docs`

```bash
curl -s -X POST localhost:8080/ai/rag -H 'Content-Type: text/plain' \
     -d 'Do you serve an espresso martini, and what is in it?'
```

**3. Agent**: the model calls Java tools (check stock, place order)

```bash
curl -s -X POST localhost:8080/ai/agent -H 'Content-Type: text/plain' \
     -d 'A customer at table 7 wants an espresso martini. Handle it.'
```

**4. MCP**: the model uses tools from an external MCP server (filesystem)

```bash
curl -s -X POST localhost:8080/ai/mcp -H 'Content-Type: text/plain' \
     -d 'How many bottles of gin are in the cellar? Check the inventory file.'
```

Compare answers: `/ai/plain` gives you a generic espresso martini recipe,
`/ai/rag` quotes the house menu (Supersonic, EUR 12.50), `/ai/agent` discovers
vodka is out of stock and refuses/substitutes, and `/ai/mcp` reads
`playground/cellar-inventory.txt` through the MCP filesystem server.

## Decision endpoint (decision models)

`POST /ai/decision` takes free text and returns the coffee the customer most
likely wants, with a probability for every item on the menu:

```bash
curl -s -X POST localhost:8080/ai/decision -H 'Content-Type: text/plain' \
     -d 'Something milky but not too sweet, I have a long meeting ahead'
```

```json
{"model":"qwen3-8b","text":"Something milky but not too sweet, I have a long meeting ahead",
 "choice":"latte","confidence":null,
 "probabilities":[{"coffee":"latte","label":"Latte","probability":0.961},
                  {"coffee":"flat_white","label":"Flat white","probability":0.018}, ...]}
```

This is a **decision model**, not a chat model. A decision model answers typed
questions (choice, yes/no, score) and returns a probability for each option,
taken from the model's token probabilities. Nothing is parsed out of
generated text. It speaks the OpenAI Decisions API (`POST /v1/decisions`).

| Piece | What it does |
|-------|--------------|
| `CoffeeDecisionService` | Declarative LangChain4j decision service: `@Decide` + `Choice<CoffeeType>` |
| `CoffeeType` | The menu; each constant is an option, its `@Description` tells the model what it is |
| `DecisionModels` | CDI producer for the `DecisionModel` and the decision service |
| `DefaultClientOpenAiDecisionModel` | LangChain4j's OpenAI decision model on LangChain4j's own HTTP client (see below) |

### Versions: why the build is ahead of the platform

Decision models arrived in LangChain4j 1.22.0 (`DecisionModel`,
`OpenAiDecisionModel`, `DecisionServices`). The quarkus-langchain4j
integration for OpenAI decision models
([quarkiverse PR #3000](https://github.com/quarkiverse/quarkus-langchain4j/pull/3000))
is merged but not released yet. So the build uses Quarkus 3.40.1 with
quarkus-langchain4j 1.15.0.CR1, and lifts LangChain4j to 1.22.0
(`gradle.properties`).

Until that release:

- `DecisionModels` builds the decision model by hand. It reads the property
  names the extension will use for a named model `decision`
  (`quarkus.langchain4j.openai.decision.*`). Quarkus logs "Unrecognized
  configuration key" for the `decision-model.*` ones; that is expected.
- `DefaultClientOpenAiDecisionModel` exists because the Quarkus OpenAI client,
  which the extension registers for every LangChain4j OpenAI model, does not
  implement the Decisions API yet.

After the release, delete both classes and inject
`@ModelName("decision") DecisionModel`. The properties stay the same.

### Where the decision model runs

| Setting | Default | Meaning |
|---------|---------|---------|
| `DECISION_BASE_URL` | `http://localhost:8000/v1` | Decisions API base URL |
| `DECISION_MODEL` | `decision` | model name sent in the request |
| `DECISION_API_KEY` | `sk-none` | bearer token |

Any server with `POST /v1/decisions` works: a recent vLLM (native since
[vllm#60465](https://github.com/vllm-project/vllm/pull/60465)), the
[Granite Switch decision plugin](https://github.com/generative-computing/granite-switch-decision),
OpenAI, or the **decision gateway** in this repo.

`deploy/openshift/decision-gateway/gateway.py` provides `/v1/decisions` for
vLLM servers that don't have it, such as the Developer Sandbox's shared
models (vLLM 0.11). Per question, it labels the options A, B, C, and so on.
It restricts the reply to one of those letters with structured outputs, then
turns the letters' logprobs into probabilities. It uses only the Python
standard library and runs from a ConfigMap on the UBI Python image, so it
needs no image build.

## Running on OpenShift

`deploy/openshift/` contains kustomize bases and two overlays. All
configuration is in the manifests, so `oc apply -k` and Argo CD
(`deploy/argocd/application-*.yaml`) deploy the same thing.

| Overlay | Decision model | Needs |
|---------|----------------|-------|
| `sandbox` | gateway → shared **Qwen3 8B** (`sandbox-shared-models`) | Red Hat Developer Sandbox, no GPU |
| `gpu` | **DiffusionGemma 26B-A4B** on vLLM (OpenShift AI, KServe) | one GPU with >= 40 GB |

Both overlays also deploy the app. A `BuildConfig` builds
`src/main/docker/Dockerfile.multistage` from Git; the app is exposed through
a Route.

The deploy scripts check for an `oc` login and a current project (or `-n`).
The `gpu` overlay also checks for KServe and a GPU quota. Each script then
applies the overlay and waits for the build, the rollouts and the model:

```bash
deploy/scripts/deploy.sh sandbox [-n <ns>] [--local-source]         # macOS / Linux
deploy\scripts\deploy.cmd sandbox [-Namespace <ns>] [-LocalSource]  # Windows (wraps deploy.ps1)
```

- `--local-source` / `-LocalSource` builds your working tree instead of `main` on GitHub.
- `--delete` / `-Delete` removes everything the overlay created.
- `gpu` instead of `sandbox` deploys DiffusionGemma as well.

To run the app locally against the sandbox gateway:

```bash
oc port-forward svc/decision-gateway 8000:8080   # keeps the default DECISION_BASE_URL
./gradlew quarkusDev
```

The chat patterns (`/ai/plain`, ...) in the deployed app use the provider
baked into the build (`quarkus.profile`). Give them a key with
`oc create secret generic ai-provider-keys --from-literal=OPENAI_API_KEY=sk-...`.
`/ai/mcp` needs `npx`, which the runtime image does not contain.

### DiffusionGemma (gpu overlay)

[DiffusionGemma](https://huggingface.co/google/diffusiongemma-26B-A4B-it)
is a 26B MoE (3.8B active) text-diffusion model. vLLM supports it natively.
The overlay serves `RedHatAI/diffusiongemma-26B-A4B-it-FP8-dynamic` with a vLLM
`ServingRuntime` (`vllm/vllm-openai:nightly`; pin a digest) and a KServe
`InferenceService` in RawDeployment mode. The app calls vLLM's own
`/v1/decisions`.

> **Not tested on a GPU yet.** vLLM returns 501 on `/v1/decisions` for models
> without a decision read strategy. If DiffusionGemma does, set
> `DECISION_BASE_URL=http://decision-gateway:8080/v1` in
> `deploy/openshift/overlays/gpu`. The gateway is already deployed there and
> points at DiffusionGemma's chat endpoint. That path relies on logprobs from
> vLLM's diffusion sampler, which is not documented either.

> **Why not on the sandbox.** User namespaces have a GPU quota of 0, the
> shared GPUs are 24 GB L4s, and the 30 Gi memory quota is too small for
> vLLM's CPU backend. The `gpu` overlay script stops on a zero GPU quota.

## Chat UI (Open WebUI)

Open WebUI is a self-hosted chat window. This repo runs it in a container
(Podman or Docker) and points
it at an OpenAI-compatible API on the Quarkus app (`/v1/models` and
`/v1/chat/completions`). Each pattern is a model:

| Model | Same assistant as |
|-------|-------------------|
| `plain` | `POST /ai/plain` |
| `rag` | `POST /ai/rag` |
| `agent` | `POST /ai/agent` |
| `mcp` | `POST /ai/mcp` |

The active `quarkus.profile` is the provider behind every model. Replies are
prefixed with that profile name, same as the curl endpoints. A follow-up
message includes the conversation so far. Open WebUI's own side tasks (chat
title, tags, follow-up suggestions) go to a small utility prompt, so they stay
out of the menu, the stock tools, and the cellar files.

1. Start Quarkus and leave it on port 8080:

   ```bash
   ./gradlew quarkusDev
   ```

2. In another terminal, from this directory:

   ```bash
   podman compose up -d
   ```

   With Docker: `docker compose up -d`. `podman compose` needs a compose
   provider (`docker-compose` or `podman-compose`); Podman Desktop offers to
   install one under **Settings → Resources → Compose**.

3. Open <http://localhost:3000>, choose `plain`, `rag`, `agent`, or `mcp`,
   and ask the same question you would send with curl.

The container calls the host at `http://host.docker.internal:8080/v1`
(Podman resolves that name too). Quarkus only accepts requests whose `Host`
header is a localhost name when it listens on localhost, so
`application.properties` adds the container host names to
`quarkus.http.host-validation.allowed-hosts`. Without that line, Open WebUI
gets an empty `400` from `/v1/models` and the model list stays empty.
If Quarkus is on another port, set `QUARKUS_OPENAI_BASE_URL` before `podman compose up`
(for example `http://host.docker.internal:8081/v1`).
The key in `compose.yaml` (`sk-local`) is a placeholder; the shim accepts any
key. Authentication on the chat page is off so a fresh volume opens straight
into the conversation. Keep ports 3000 and 8080 on your machine — the chat
endpoint does not check credentials.

`agent` and `mcp` finish their tool calls before the first token shows up;
the reply is then written out as a stream. Open WebUI built-in tools (web
search and the like) are separate from `BarTools` and the cellar MCP server.

Settings from `compose.yaml` are copied into the `open-webui` volume on first
start. After you edit them, recreate that volume:

```bash
podman compose down -v
podman compose up -d
```

The same API works without the UI:

```bash
curl -s localhost:8080/v1/models
```

```bash
curl -s localhost:8080/v1/chat/completions \
     -H 'Content-Type: application/json' \
     -d '{"model":"rag","messages":[{"role":"user","content":"Do you serve an espresso martini, and what is in it?"}]}'
```

## Notes & troubleshooting

- **Versions**: built against Quarkus 3.40.1, quarkus-langchain4j 1.15.0.CR1
  and LangChain4j 1.22.0 (see `gradle.properties`, `build.gradle` and
  [Versions](#versions-why-the-build-is-ahead-of-the-platform)). If a newer platform renames a config key, the extension
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
- **"Model '' was not found" when you send a message**: no model was
  selected, because the model list was empty when the page loaded. That
  happens when Quarkus was not running (or not reachable) at that moment.
  Start `./gradlew quarkusDev`, check that the model menu at the top left shows
  `plain`, `rag`, `agent` and `mcp`, then reload the page (or start a new chat)
  and pick one.
- **Open WebUI shows no models**: it loads them from
  `http://host.docker.internal:8080/v1/models`. Start `./gradlew quarkusDev`
  first, then `podman compose restart` (or `docker compose restart`). The
  container logs (`podman logs quarkus-ai-open-webui`) say why:
  - `Connection error: 400, message='Attempt to decode JSON with unexpected mimetype'`:
    Quarkus rejected the `Host` header. Check that
    `quarkus.http.host-validation.allowed-hosts` is still in
    `application.properties`.
  - Connection refused or timeout: the container cannot reach Quarkus on
    the host. On Linux and on Podman with WSL, the host name points at the
    host's network address, not its loopback. Start Quarkus with
    `QUARKUS_HTTP_HOST=0.0.0.0 ./gradlew quarkusDev`. That opens port 8080
    to your network, so only do it on a trusted one.
  - If you changed `compose.yaml` and the UI still has the old connection,
    reset the volume with `podman compose down -v`.
- **Chat title stays "New Chat"**: the title task expects a raw JSON object.
  The conversation itself still works when the model adds extra text around it.
- **Tool calling quality differs per provider/model**: smaller or older models
  are noticeably worse at deciding when and how to call tools. If `/ai/agent`
  misbehaves on one provider, try the same request on another — that contrast
  is itself a useful demo.
