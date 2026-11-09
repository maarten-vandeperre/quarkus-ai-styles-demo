# AI Styles Demo on Windows (PowerShell)

This is the Windows version of the run instructions in [README.md](README.md).
Every command here is PowerShell. They work in Windows PowerShell 5.1 and in
PowerShell 7 (`pwsh`). For what each pattern does, how to get API keys, and
the OpenShift deployment, see the main README.

Each command is in its own code block, so you can run them one at a time from
IntelliJ's Markdown preview or paste them into a terminal.

## Prerequisites

- Java 25 (the Gradle toolchain downloads one if needed)
- Node.js 18+, only for the `/ai/mcp` endpoint. Install it with
  `winget install OpenJS.NodeJS.LTS` or from <https://nodejs.org>.
- An API key for one cloud provider, **or** [Ollama for Windows](https://ollama.com/download/windows),
  **or** [Podman Desktop](https://podman-desktop.io) with the AI Lab extension serving Qwen
- Podman Desktop or Docker Desktop, only for the Open WebUI chat

Check the tools from a PowerShell window:

```powershell
java -version
node --version
npx --version
```

> **`curl` in PowerShell.** In Windows PowerShell 5.1, `curl` is an alias for
> `Invoke-WebRequest` and does not accept curl's flags. This guide uses
> `Invoke-RestMethod`, which works in every PowerShell version and prints the
> JSON as an object. If you prefer real curl, call `curl.exe` (included with
> Windows 10 and later).

## Fully local demo (Ollama, Open WebUI, no API key)

Gemma 4 on Ollama for the four chat patterns, Gemma 4 or kev on Ollama for
the decision endpoint, Open WebUI as the chat window. Needs Ollama for
Windows, Podman Desktop or Docker Desktop, and Node.js (for `mcp`).

1. Pull the models (once):

   ```powershell
   ollama pull gemma4:12b
   ollama pull gemma4
   ollama pull kev:0.8b-q8_0
   ```

   `gemma4:12b` is the chat model; `gemma4` and `kev:0.8b-q8_0` are the two
   decision models.

2. In `src\main\resources\application.properties`, set:

   ```properties
   quarkus.profile=ollama
   decision.provider=gemma
   ```

   Use `decision.provider=kev` for kev.

3. Start Open WebUI and the local decision gateway (Gemma needs it, kev not).
   Stop any `oc port-forward` on port 8000 first:

   ```powershell
   podman compose up -d
   ```

   With Docker Desktop: `docker compose up -d`.

4. Start the app in its own window and leave it open:

   ```powershell
   .\gradlew.bat quarkusDev
   ```

5. Chat: open <http://localhost:3000> and pick `plain`, `rag`, `agent` or `mcp`.
   Decision (not in Open WebUI), from a second window:

   ```powershell
   Invoke-RestMethod -Method Post -Uri http://localhost:8080/ai/decision -ContentType 'text/plain' -Body 'Something milky but not too sweet, I have a long meeting ahead' | ConvertTo-Json -Depth 5
   ```

   The `model` field shows which decision model answered. Change
   `decision.provider` and save to switch; dev mode reloads on the next request.

## Choose a provider

Open `src\main\resources\application.properties` and change one line:

```properties
quarkus.profile=openai   # openai | anthropic | mistral | kimi | gemini | ollama | qwen
```

Set the API key for that provider in the same PowerShell window you start
Quarkus from. The variable lasts until you close the window.

```powershell
$env:OPENAI_API_KEY = 'sk-...'
```

The other providers use `ANTHROPIC_API_KEY`, `MISTRAL_API_KEY`, `KIMI_API_KEY`
or `GEMINI_API_KEY`. To keep a key across sessions, store it for your user
account (open a new window afterwards):

```powershell
[Environment]::SetEnvironmentVariable('OPENAI_API_KEY', 'sk-...', 'User')
```

The key is only for the four chat patterns. The decision endpoint
(`/ai/decision`) with `decision.provider=gemma` or `kev` runs on the local
Ollama and needs no key.

### Ollama (local, no key)

```powershell
ollama pull gemma4:12b
```

```powershell
ollama list
```

Then set `quarkus.profile=ollama`. Ollama for Windows runs as a background
service on `http://localhost:11434`.

### Qwen on Podman AI Lab (local, no key)

Follow the Podman Desktop steps in the main README (download
`qwen/qwen3-4b-GGUF`, create a model service on port `35000`), then set
`quarkus.profile=qwen`. If the service runs on another port:

```powershell
$env:PODMAN_AI_BASE_URL = 'http://localhost:<port>/v1'
```

## Run the app

```powershell
.\gradlew.bat quarkusDev
```

Leave this window open. Quarkus listens on <http://localhost:8080>.

### MCP on Windows

The MCP client starts the filesystem server with `npx`. On Windows, `npx` is
a batch file (`npx.cmd`), and Java cannot start a batch file by its short
name. The app detects Windows and uses `npx.cmd` automatically
(`WindowsNpxConfigSource`), so no extra setup is needed.

If the server still cannot start, the MCP client is skipped without an error
and the model answers without tools, for example *"I don't currently have
direct access to your cellar files"*. Check the Quarkus log for the MCP
startup error, and point the app at `npx` explicitly if needed:

```powershell
$env:CELLAR_NPX = 'C:\Program Files\nodejs\npx.cmd'
.\gradlew.bat quarkusDev
```

## Try the four patterns

Open a second PowerShell window in the project folder and run these one at a
time.

**1. Plain**: answers from training data ("bartender's memory")

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/ai/plain -ContentType 'text/plain' -Body 'Do you serve an espresso martini, and what is in it?'
```

**2. RAG**: answers from the docs in `src\main\resources\rag-docs`

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/ai/rag -ContentType 'text/plain' -Body 'Do you serve an espresso martini, and what is in it?'
```

**3. Agent**: the model calls Java tools (check stock, place order)

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/ai/agent -ContentType 'text/plain' -Body 'A customer at table 7 wants an espresso martini. Handle it.'
```

**4. MCP**: the model uses tools from an external MCP server (filesystem)

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/ai/mcp -ContentType 'text/plain' -Body 'How many bottles of gin are in the cellar? Check the inventory file.'
```

`/ai/plain` gives a generic espresso martini recipe, `/ai/rag` quotes the
house menu (Supersonic, EUR 12.50), `/ai/agent` finds that vodka is out of
stock and refuses or substitutes, and `/ai/mcp` reads
`playground\cellar-inventory.txt` through the MCP server.

## Decision endpoint

`decision.provider` in `application.properties` picks the decision model; see
[Choosing the decision model](README.md#choosing-the-decision-model). The
default is `gemma`: Gemma 4 on Ollama for Windows, through the local decision
gateway on port 8000. Start it before Quarkus:

```powershell
ollama pull gemma4
podman compose up -d decision-gateway
```

With Docker Desktop: `docker compose up -d decision-gateway`.

Or use kev, which Ollama answers itself (no gateway):

```powershell
ollama pull kev:0.8b-q8_0
$env:DECISION_PROVIDER = 'kev'
.\gradlew.bat quarkusDev
```

Or the Developer Sandbox gateway (stop the local gateway first, both use port 8000):

```powershell
oc port-forward svc/decision-gateway 8000:8080
```

and in the Quarkus window:

```powershell
$env:DECISION_PROVIDER = 'openai'
$env:DECISION_MODEL = 'qwen3-8b'
.\gradlew.bat quarkusDev
```

If the sandbox gateway answers `backend unreachable: ... Name or service not
known`, this sandbox cluster does not serve the shared models
(`oc get inferenceservice -n sandbox-shared-models` is empty): use `gemma` or
`kev`.

Then:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/ai/decision -ContentType 'text/plain' -Body 'Something milky but not too sweet, I have a long meeting ahead' | ConvertTo-Json -Depth 5
```

## Chat UI (Open WebUI)

1. Start Quarkus (`.\gradlew.bat quarkusDev`) and leave it on port 8080.
2. Make sure the Podman machine is running. In Podman Desktop, check the
   dashboard, or run:

   ```powershell
   podman machine start
   ```

3. `podman compose` needs a compose provider. Podman Desktop installs one
   under **Settings → Resources → Compose**. Check it:

   ```powershell
   podman compose version
   ```

4. Start Open WebUI from the project folder:

   ```powershell
   podman compose up -d
   ```

   With Docker Desktop: `docker compose up -d`.

5. Open <http://localhost:3000> and pick `plain`, `rag`, `agent` or `mcp`.

If Quarkus runs on another port:

```powershell
$env:QUARKUS_OPENAI_BASE_URL = 'http://host.docker.internal:8081/v1'
podman compose up -d
```

Open WebUI copies its settings into the `open-webui` volume on first start.
After you change `compose.yaml`, recreate the volume:

```powershell
podman compose down -v
podman compose up -d
```

The same API without the UI:

```powershell
Invoke-RestMethod -Uri http://localhost:8080/v1/models | Select-Object -ExpandProperty data
```

```powershell
$body = @{
    model    = 'rag'
    messages = @(@{ role = 'user'; content = 'Do you serve an espresso martini, and what is in it?' })
} | ConvertTo-Json -Depth 5
(Invoke-RestMethod -Method Post -Uri http://localhost:8080/v1/chat/completions -ContentType 'application/json' -Body $body).choices[0].message.content
```

## Deploy to OpenShift

Log in with `oc login` first. From the project folder:

```powershell
.\deploy\scripts\deploy.ps1 sandbox
```

Options: `-Namespace <ns>`, `-LocalSource` (build your working tree instead
of `main` on GitHub), `-Delete`, and `gpu` instead of `sandbox`. If the
execution policy blocks the script, use the wrapper, which runs it with
`-ExecutionPolicy Bypass`:

```powershell
.\deploy\scripts\deploy.cmd sandbox
```

## Troubleshooting

- **"Model '' was not found" when you send a message**: no model was
  selected, because the model list was empty when the page loaded. That
  happens when Quarkus was not running (or not reachable) at that moment.
  Start `.\gradlew.bat quarkusDev`, check that the model menu at the top left shows
  `plain`, `rag`, `agent` and `mcp`, then reload the page (or start a new chat)
  and pick one.
- **Open WebUI shows no models.** Read the container log:

  ```powershell
  podman logs quarkus-ai-open-webui --tail 50
  ```

  - `Connection error: 400 ... unexpected mimetype`: Quarkus rejected the
    `Host` header. `quarkus.http.host-validation.allowed-hosts` in
    `application.properties` must list `host.docker.internal` and
    `host.containers.internal`.
  - Connection refused or timeout: the container cannot reach Quarkus. With
    Podman on WSL, `host.docker.internal` can point at the Windows host's
    network address instead of its loopback, and Quarkus only listens on
    loopback. Restart Quarkus on all interfaces and allow Java through the
    Windows Firewall prompt. This opens port 8080 to your network, so only
    do it on a trusted one.

    ```powershell
    $env:QUARKUS_HTTP_HOST = '0.0.0.0'
    .\gradlew.bat quarkusDev
    ```

  Then restart Open WebUI:

  ```powershell
  podman compose restart
  ```

- **`gradlew.bat` is slow on the first run.** It downloads Gradle and a Java
  25 toolchain. Later runs are fast.
- **First RAG request is slow.** The ONNX embedding model loads on first use.
