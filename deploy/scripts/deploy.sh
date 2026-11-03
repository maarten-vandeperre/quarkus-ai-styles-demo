#!/usr/bin/env bash
# Deploy the demo to OpenShift with the active `oc` login. macOS and Linux.
# Windows: deploy.ps1 (or deploy.cmd)
#
# Usage: deploy/scripts/deploy.sh <sandbox|gpu> [-n namespace] [--local-source] [--delete] [--force] [--no-wait]
#   sandbox          decision gateway + app, using the Developer Sandbox's shared models
#   gpu              DiffusionGemma (needs a >= 40 GB GPU) + decision gateway + app
#   -n, --namespace  target project (default: the current `oc project`)
#   --local-source   build the app from this working tree instead of Git
#   --delete         remove everything the overlay created
#   --force          apply the gpu overlay even when the namespace has no GPU quota
#   --no-wait        do not wait for builds, rollouts and the model
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
APP=quarkus-ai-styles-demo
OVERLAY=""
NAMESPACE=""
ACTION=apply
LOCAL_SOURCE=false
FORCE=false
WAIT=true

while [ $# -gt 0 ]; do
  case "$1" in
    sandbox|gpu) OVERLAY="$1"; shift ;;
    -n|--namespace) NAMESPACE="${2:-}"; shift 2 ;;
    --local-source) LOCAL_SOURCE=true; shift ;;
    --delete) ACTION=delete; shift ;;
    --force) FORCE=true; shift ;;
    --no-wait) WAIT=false; shift ;;
    -h|--help) sed -n '2,14p' "$0"; exit 0 ;;
    *) echo "Unknown argument: $1 (see --help)" >&2; exit 2 ;;
  esac
done

fail() { echo "ERROR: $*" >&2; exit 1; }

[ -n "$OVERLAY" ] || fail "Choose an overlay: sandbox or gpu (see --help)."
MANIFESTS="$REPO_ROOT/deploy/openshift/overlays/$OVERLAY"

command -v oc >/dev/null 2>&1 || fail "'oc' not found on PATH. Install the OpenShift CLI first."
USER_NAME="$(oc whoami 2>/dev/null)" || fail "No active oc session. Run: oc login --token=<token> --server=<api url>"
if [ -z "$NAMESPACE" ]; then
  NAMESPACE="$(oc project -q 2>/dev/null)" || fail "No current project. Pass -n <namespace> or run: oc project <namespace>"
fi
oc get project "$NAMESPACE" >/dev/null 2>&1 || fail "Project '$NAMESPACE' does not exist or is not visible to $USER_NAME."

echo "Cluster:   $(oc whoami --show-server)"
echo "User:      $USER_NAME"
echo "Namespace: $NAMESPACE"
echo "Overlay:   $OVERLAY"

if [ "$ACTION" = delete ]; then
  oc delete -k "$MANIFESTS" -n "$NAMESPACE" --ignore-not-found
  exit 0
fi

if [ "$OVERLAY" = gpu ]; then
  oc api-resources --api-group=serving.kserve.io -o name 2>/dev/null | grep -q '^inferenceservices' \
    || fail "KServe (OpenShift AI model serving) is not installed on this cluster."
  # Every ResourceQuota that caps GPUs must leave room for one.
  GPU_LIMITS="$(oc get resourcequota -n "$NAMESPACE" \
    -o jsonpath='{range .items[*]}{.spec.hard.requests\.nvidia\.com/gpu}{"\n"}{end}' 2>/dev/null || true)"
  if printf '%s\n' "$GPU_LIMITS" | grep -qx '0'; then
    echo "WARNING: namespace '$NAMESPACE' has a GPU quota of 0 (requests.nvidia.com/gpu=0)." >&2
    echo "         DiffusionGemma needs one GPU with >= 40 GB; its pod would never start." >&2
    echo "         On the Developer Sandbox, use the 'sandbox' overlay." >&2
    [ "$FORCE" = true ] || fail "Aborting. Re-run with --force to apply anyway."
  fi
fi

oc apply -k "$MANIFESTS" -n "$NAMESPACE"

if [ "$LOCAL_SOURCE" = true ]; then
  command -v git >/dev/null 2>&1 || fail "--local-source needs git (it uploads the files git tracks or would track)."
  SOURCE_DIR="$(mktemp -d)"
  echo "Copying the working tree (minus .gitignore'd files) to $SOURCE_DIR ..."
  (cd "$REPO_ROOT" && git ls-files --cached --others --exclude-standard -z \
    | while IFS= read -r -d '' f; do
        [ -f "$f" ] || continue
        mkdir -p "$SOURCE_DIR/$(dirname "$f")" && cp -p "$f" "$SOURCE_DIR/$f"
      done)
  oc cancel-build "bc/$APP" -n "$NAMESPACE" >/dev/null 2>&1 || true
  oc start-build "$APP" -n "$NAMESPACE" --from-dir="$SOURCE_DIR" --follow --wait
  rm -rf "$SOURCE_DIR"
fi

if [ "$WAIT" = true ]; then
  oc rollout status deployment/decision-gateway -n "$NAMESPACE" --timeout=5m
  if [ "$LOCAL_SOURCE" = false ]; then
    echo "Waiting for the app image (Git build, a few minutes; oc logs -f bc/$APP to follow)..."
    for _ in $(seq 1 180); do
      oc get istag "$APP:latest" -n "$NAMESPACE" >/dev/null 2>&1 && break
      PHASE="$(oc get builds -n "$NAMESPACE" -l "buildconfig=$APP" -o jsonpath='{.items[-1:].status.phase}' 2>/dev/null || true)"
      case "$PHASE" in Failed|Error|Cancelled) fail "Build $PHASE: oc logs bc/$APP -n $NAMESPACE" ;; esac
      sleep 10
    done
  fi
  oc rollout status "deployment/$APP" -n "$NAMESPACE" --timeout=15m
  if [ "$OVERLAY" = gpu ]; then
    echo "Waiting for InferenceService/diffusiongemma (weights are ~26 GB)..."
    oc wait inferenceservice/diffusiongemma -n "$NAMESPACE" --for=condition=Ready --timeout=45m
  fi
fi

HOST="$(oc get route "$APP" -n "$NAMESPACE" -o jsonpath='{.spec.host}' 2>/dev/null || true)"
echo
echo "App:  https://$HOST"
echo "Try:  curl -s -X POST https://$HOST/ai/decision -H 'Content-Type: text/plain' -d 'Something milky, not too sweet'"
