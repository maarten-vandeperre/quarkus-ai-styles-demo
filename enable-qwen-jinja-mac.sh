#Jinja is basically a template engine: it takes the structured conversation—your messages, system prompt,
#and available tools—and formats them into the exact text structure the model expects.
#For tool calling, it is like the translator that turns “here are 3 functions you may call”
#into Qwen’s special <tools> / <tool_call> prompt format. Without Jinja, the model may understand normal chat,
#but the server does not know how to correctly package tools into the prompt, which is why llama.cpp complains that tools requires --jinja.

#!/usr/bin/env bash
set -euo pipefail

# qwen-tools-mac.sh
#
# macOS helper for Podman Desktop / AI Lab:
# - ensures Podman machine is running
# - auto-detects the Qwen/llama.cpp model-service container
# - inspects its image, mounts, ports and command
# - stops the original service
# - recreates a sibling service with BOTH:
#       --jinja
#       LLAMA_ARG_JINJA=true
# - verifies the process and endpoint
# - runs a real OpenAI-compatible tools request
#
# No arguments are required for the common case.
#
# Optional environment variables:
#   QWEN_CONTAINER   force the source container name
#   JINJA_NAME       force the new container name
#
# Rollback:
#   podman rm -f <jinja-container>
#   podman start <original-container>

log()  { printf '\n\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\n\033[1;33mWARN:\033[0m %s\n' "$*" >&2; }
fail() { printf '\n\033[1;31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }

command -v podman >/dev/null 2>&1 || fail "podman is not installed or not in PATH."
command -v python3 >/dev/null 2>&1 || fail "python3 is required."
command -v curl >/dev/null 2>&1 || fail "curl is required."

log "Checking Podman machine"

if ! podman info >/dev/null 2>&1; then
  podman machine start >/dev/null 2>&1 || fail "Could not start the Podman machine."
fi

podman info >/dev/null 2>&1 || fail "Podman is still not reachable."

find_container() {
  python3 - <<'PY'
import json, subprocess, os, sys

forced = os.environ.get("QWEN_CONTAINER")
if forced:
    print(forced)
    raise SystemExit

p = subprocess.run(
    ["podman", "ps", "-a", "--format", "json"],
    capture_output=True, text=True, check=True
)
items = json.loads(p.stdout or "[]")

def name_of(x):
    n = x.get("Names") or ""
    if isinstance(n, list):
        return n[0] if n else ""
    return str(n)

def haystack(x):
    return " ".join([
        name_of(x),
        str(x.get("Image", "")),
        str(x.get("Command", "")),
    ]).lower()

groups = [
    ("qwen",),
    ("llama-server", "llama.cpp", "ramalama"),
    ("model", "inference"),
]

for keywords in groups:
    matches = []
    for x in items:
        h = haystack(x)
        if any(k in h for k in keywords):
            n = name_of(x)
            if n:
                matches.append(n)
    if len(matches) == 1:
        print(matches[0])
        raise SystemExit
    if len(matches) > 1:
        running = []
        for n in matches:
            q = subprocess.run(
                ["podman", "inspect", n, "--format", "{{.State.Running}}"],
                capture_output=True, text=True
            )
            if q.stdout.strip() == "true":
                running.append(n)
        if len(running) == 1:
            print(running[0])
            raise SystemExit

raise SystemExit(3)
PY
}

if ! SOURCE="${QWEN_CONTAINER:-$(find_container 2>/dev/null)}"; then
  echo
  echo "Could not uniquely identify the Qwen model-service container."
  echo "Available containers:"
  podman ps -a --format '  {{.Names}}  {{.Image}}  {{.Command}}'
  echo
  echo "Re-run with:"
  echo "  QWEN_CONTAINER=<container-name> ./qwen-tools-mac.sh"
  exit 1
fi

podman container exists "$SOURCE" || fail "Container '$SOURCE' does not exist."

TARGET="${JINJA_NAME:-${SOURCE}-jinja}"

log "Using source container: $SOURCE"
echo "Target container: $TARGET"

TMP_JSON="$(mktemp)"
TMP_CMD="$(mktemp)"
TMP_TEST="$(mktemp)"
trap 'rm -f "$TMP_JSON" "$TMP_CMD" "$TMP_TEST"' EXIT

podman inspect "$SOURCE" > "$TMP_JSON"

python3 - "$TMP_JSON" "$TARGET" > "$TMP_CMD" <<'PY'
import json, shlex, sys

inspect_path, target = sys.argv[1], sys.argv[2]

with open(inspect_path, encoding="utf-8") as f:
    x = json.load(f)[0]

cfg = x.get("Config") or {}
host = x.get("HostConfig") or {}
mounts = x.get("Mounts") or []

image = cfg.get("Image") or x.get("ImageName")
if not image:
    raise SystemExit("Could not determine source image")

run = ["podman", "run", "-d", "--name", target]

# Preserve restart policy if meaningful.
rp = (host.get("RestartPolicy") or {}).get("Name") or ""
if rp and rp != "no":
    run += ["--restart", rp]

# Preserve env, except runtime-generated values and any previous Jinja setting.
for e in cfg.get("Env") or []:
    if "=" not in e:
        continue
    k, v = e.split("=", 1)
    if k in {"PATH", "HOSTNAME", "container", "LLAMA_ARG_JINJA"}:
        continue
    run += ["-e", f"{k}={v}"]

# Force Jinja through environment as well as CLI.
run += ["-e", "LLAMA_ARG_JINJA=true"]

# Preserve mounts.
for m in mounts:
    typ = m.get("Type")
    dst = m.get("Destination")
    if not dst:
        continue

    if typ == "volume":
        src = m.get("Name") or m.get("Source")
    else:
        src = m.get("Source")

    if not src:
        continue

    spec = f"{src}:{dst}"
    opts = []
    if not m.get("RW", True):
        opts.append("ro")
    if opts:
        spec += ":" + ",".join(opts)
    run += ["-v", spec]

# Preserve published ports.
bindings = host.get("PortBindings") or {}
for ckey, vals in bindings.items():
    cport, _, proto = ckey.partition("/")
    proto = proto or "tcp"
    for b in vals or []:
        hp = b.get("HostPort")
        hip = b.get("HostIp") or ""
        if not hp:
            continue
        if hip and hip not in {"0.0.0.0", "::"}:
            p = f"{hip}:{hp}:{cport}"
        else:
            p = f"{hp}:{cport}"
        if proto != "tcp":
            p += f"/{proto}"
        run += ["-p", p]

if cfg.get("WorkingDir"):
    run += ["-w", cfg["WorkingDir"]]
if cfg.get("User"):
    run += ["--user", cfg["User"]]

ep = cfg.get("Entrypoint")
if ep:
    if isinstance(ep, list) and len(ep) == 1:
        run += ["--entrypoint", ep[0]]
    elif isinstance(ep, str):
        run += ["--entrypoint", ep]
    else:
        run += ["--entrypoint", json.dumps(ep)]

run.append(image)

cmd = list(cfg.get("Cmd") or [])

# Remove duplicate prior Jinja flags, then append one.
cleaned = []
skip_next = False
for i, arg in enumerate(cmd):
    if skip_next:
        skip_next = False
        continue
    if arg == "--jinja":
        continue
    cleaned.append(arg)

cleaned.append("--jinja")
run.extend(cleaned)

print(" ".join(shlex.quote(a) for a in run))
PY

GENERATED="$(cat "$TMP_CMD")"

log "Detected current model-service configuration"
echo "Image:"
podman inspect "$SOURCE" --format '  {{.Config.Image}}'
echo "Command:"
podman inspect "$SOURCE" --format '  {{json .Config.Cmd}}'
echo "Ports:"
podman port "$SOURCE" 2>/dev/null || true
echo "Mounts:"
podman inspect "$SOURCE" --format '{{range .Mounts}}  {{.Type}}  {{.Source}} -> {{.Destination}}{{println}}{{end}}'

log "Preparing Jinja-enabled service"

if podman container exists "$TARGET"; then
  warn "Removing previous '$TARGET' container."
  podman rm -f "$TARGET" >/dev/null
fi

if [[ "$(podman inspect "$SOURCE" --format '{{.State.Running}}')" == "true" ]]; then
  log "Stopping original service to free its host port"
  podman stop "$SOURCE" >/dev/null
fi

echo
echo "Starting:"
echo "$GENERATED"
echo

eval "$GENERATED" >/dev/null

sleep 3

if [[ "$(podman inspect "$TARGET" --format '{{.State.Running}}')" != "true" ]]; then
  warn "The Jinja-enabled container exited."
  echo
  podman logs --tail 100 "$TARGET" || true
  echo
  echo "Rollback:"
  echo "  podman rm -f '$TARGET'"
  echo "  podman start '$SOURCE'"
  exit 1
fi

log "Container is running"

podman ps --filter "name=^${TARGET}$" \
  --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}\t{{.Image}}'

echo
echo "Effective command:"
podman inspect "$TARGET" --format '  {{json .Config.Cmd}}'

echo
echo "Jinja environment:"
podman inspect "$TARGET" --format '{{range .Config.Env}}{{println .}}{{end}}' | grep '^LLAMA_ARG_JINJA=' || true

echo
echo "Running process:"
podman top "$TARGET" || true

# Determine published host port.
HOST_PORT="$(podman port "$TARGET" 2>/dev/null | awk -F: 'NR==1{print $NF}' | tr -d '[:space:]')"

if [[ -z "$HOST_PORT" ]]; then
  warn "Could not determine published host port automatically."
  echo "The container is running, but the endpoint test will be skipped."
else
  BASE_URL="http://localhost:${HOST_PORT}"

  log "Checking /props"
  if curl -fsS "${BASE_URL}/props" > "$TMP_TEST"; then
    python3 - "$TMP_TEST" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as f:
    d = json.load(f)
print("model_path:", d.get("model_path"))
print("build_info:", d.get("build_info"))
tpl = d.get("chat_template") or ""
print("chat_template_present:", bool(tpl))
print("tool_template_present:", "tools" in tpl and "tool_call" in tpl)
PY
  else
    warn "/props endpoint was not reachable."
  fi

  log "Running real OpenAI-compatible tool-call test"

  HTTP_CODE="$(curl -sS -o "$TMP_TEST" -w '%{http_code}' \
    "${BASE_URL}/v1/chat/completions" \
    -H "Content-Type: application/json" \
    -d '{
      "model": "qwen/qwen3-4b-GGUF",
      "messages": [
        {
          "role": "user",
          "content": "Use the get_inventory tool to check how many bottles of gin are in the cellar."
        }
      ],
      "tools": [
        {
          "type": "function",
          "function": {
            "name": "get_inventory",
            "description": "Look up an item in the inventory.",
            "parameters": {
              "type": "object",
              "properties": {
                "item": {
                  "type": "string"
                }
              },
              "required": ["item"]
            }
          }
        }
      ]
    }' || true)"

  echo "HTTP status: $HTTP_CODE"
  cat "$TMP_TEST"
  echo

  if grep -q 'tools param requires --jinja flag' "$TMP_TEST"; then
    warn "The server still reports that Jinja is disabled."
    warn "This strongly indicates the RamaLama/llama.cpp image itself is not honoring Jinja."
    echo
    echo "Next recommended step:"
    echo "  use a newer upstream llama.cpp server image with the same GGUF."
    echo
    echo "The current container has BOTH:"
    echo "  --jinja"
    echo "  LLAMA_ARG_JINJA=true"
    echo
    echo "so if the endpoint still rejects tools, this is no longer a script/config omission."
  elif [[ "$HTTP_CODE" =~ ^2 ]]; then
    echo
    echo "SUCCESS: tool-calling endpoint accepted the tools request."
  else
    warn "The endpoint returned a non-2xx response. Inspect the response and logs."
  fi
fi

echo
echo "Recent logs:"
podman logs --tail 40 "$TARGET" || true

echo
echo "============================================================"
echo "DONE"
echo "============================================================"
echo
echo "Original service:"
echo "  $SOURCE"
echo
echo "Jinja-enabled service:"
echo "  $TARGET"
echo
echo "Rollback:"
echo "  podman rm -f '$TARGET'"
echo "  podman start '$SOURCE'"
echo
