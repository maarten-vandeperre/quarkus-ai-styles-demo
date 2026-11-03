"""OpenAI Decisions API (POST /v1/decisions) on top of a vLLM chat endpoint.

vLLM serves /v1/decisions natively from late 2026 (vllm-project/vllm#60465);
the Developer Sandbox's shared models run an older vLLM. This gateway gives
those models the same API, so the Quarkus app talks to it exactly as it
would to a newer vLLM, Granite Switch or DiffusionGemma.

How a question is answered (one backend call per question):
  * the options are labelled A, B, C, ... in the system prompt;
  * structured outputs restrict the reply to one of those letters, and
    max_tokens=1 keeps it to a single token;
  * the probability of each option is the softmax of the letters' logprobs.
So the probabilities are the model's token probabilities, not numbers the
model writes. Question types: choice, predicate (yes/no) and score.

Standard library only. Configuration (environment):
  BACKEND_URL          OpenAI-compatible base URL of the vLLM server (.../v1)
  BACKEND_MODEL        model name on that server
  BACKEND_TOKEN_FILE   bearer token file, re-read per call (default: the pod's
                       service account token, when present)
  BACKEND_API_KEY      static bearer token, used when no token file exists
  BACKEND_CA_FILE      CA bundle for https backends (default: OpenShift service CA)
  BACKEND_TIMEOUT      seconds per backend call (default 60)
  STRUCTURED_OUTPUTS   structured_outputs (vLLM >= 0.10.2), guided_choice
                       (older vLLM) or none
  MAX_OPTIONS          options per question; vLLM returns at most 20 top
                       logprobs by default (default 20)
  PORT                 listen port (default 8080)
"""
import json
import math
import os
import ssl
import string
import sys
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

SA_DIR = "/var/run/secrets/kubernetes.io/serviceaccount"

BACKEND_URL = os.environ.get("BACKEND_URL", "http://localhost:8000/v1").rstrip("/")
BACKEND_MODEL = os.environ.get("BACKEND_MODEL", "")
TOKEN_FILE = os.environ.get("BACKEND_TOKEN_FILE", SA_DIR + "/token")
API_KEY = os.environ.get("BACKEND_API_KEY", "")
CA_FILE = os.environ.get("BACKEND_CA_FILE", SA_DIR + "/service-ca.crt")
TIMEOUT = float(os.environ.get("BACKEND_TIMEOUT", "60"))
STRUCTURED_OUTPUTS = os.environ.get("STRUCTURED_OUTPUTS", "structured_outputs")
MAX_OPTIONS = min(int(os.environ.get("MAX_OPTIONS", "20")), 26)
PORT = int(os.environ.get("PORT", "8080"))

LETTERS = string.ascii_uppercase


class ApiError(Exception):
    def __init__(self, status, message, error_type="invalid_request_error"):
        super().__init__(message)
        self.status = status
        self.error_type = error_type


def ssl_context():
    if not BACKEND_URL.startswith("https"):
        return None
    if CA_FILE and os.path.exists(CA_FILE):
        return ssl.create_default_context(cafile=CA_FILE)
    return ssl.create_default_context()


SSL_CONTEXT = ssl_context()


def bearer_token():
    if TOKEN_FILE and os.path.exists(TOKEN_FILE):
        with open(TOKEN_FILE) as f:
            return f.read().strip()
    return API_KEY


def backend(method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(BACKEND_URL + path, data=data, method=method)
    request.add_header("Content-Type", "application/json")
    token = bearer_token()
    if token:
        request.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT, context=SSL_CONTEXT) as response:
            return json.loads(response.read())
    except urllib.error.HTTPError as e:
        detail = e.read().decode(errors="replace")[:500]
        raise ApiError(502, "backend returned HTTP %d: %s" % (e.code, detail), "backend_error")
    except (urllib.error.URLError, TimeoutError) as e:
        raise ApiError(502, "backend unreachable: %s" % e, "backend_error")


def input_text(value):
    """Flatten the Decisions API input (string, messages, parts or fields) to text."""
    if value is None:
        return ""
    if isinstance(value, str):
        return value
    if isinstance(value, dict):
        if "content" in value:
            return input_text(value["content"])
        if "text" in value:
            return str(value["text"])
        return "\n".join("%s: %s" % (k, input_text(v)) for k, v in value.items())
    if isinstance(value, list):
        return "\n".join(t for t in (input_text(v) for v in value) if t)
    return str(value)


def ask(text, instructions, options):
    """Return (probabilities per option index, prompt tokens) for one question."""
    if not 2 <= len(options) <= MAX_OPTIONS:
        raise ApiError(400, "questions need between 2 and %d options, got %d" % (MAX_OPTIONS, len(options)))
    letters = list(LETTERS[:len(options)])
    menu = "\n".join("%s. %s" % (letter, option) for letter, option in zip(letters, options))
    # Tested on the sandbox's Qwen3 8B: question and options in the system
    # message, input alone in the user message. With everything in one user
    # message the model drifts to "A"; without the "not shared words" line it
    # matches words from option descriptions instead of intent.
    system = (
        "You are an expert who answers multiple-choice questions about the "
        "user's message. Pick the option that best matches what the user "
        "actually means, not the one that shares the most words. Reply with "
        "the option letter only.\n\n"
        "Question: %s\n\nOptions:\n%s" % (instructions, menu)
    )
    body = {
        "model": BACKEND_MODEL,
        "messages": [{"role": "system", "content": system}, {"role": "user", "content": text}],
        "max_tokens": 1,
        "temperature": 0,
        "logprobs": True,
        "top_logprobs": len(letters),
        "chat_template_kwargs": {"enable_thinking": False},
    }
    if STRUCTURED_OUTPUTS == "structured_outputs":
        body["structured_outputs"] = {"choice": letters}
    elif STRUCTURED_OUTPUTS == "guided_choice":
        body["guided_choice"] = letters

    reply = backend("POST", "/chat/completions", body)
    try:
        top = reply["choices"][0]["logprobs"]["content"][0]["top_logprobs"]
    except (KeyError, IndexError, TypeError):
        raise ApiError(502, "backend returned no logprobs; it must support logprobs/top_logprobs", "backend_error")

    weights = [0.0] * len(letters)
    for entry in top:
        token = entry.get("token", "").strip()
        if token in letters and entry.get("logprob", -9999) > -1000:
            weights[letters.index(token)] += math.exp(entry["logprob"])
    total = sum(weights)
    if total <= 0:
        raise ApiError(502, "backend answered none of the option letters", "backend_error")
    prompt_tokens = (reply.get("usage") or {}).get("prompt_tokens", 0)
    return [w / total for w in weights], prompt_tokens


def option_line(value, description):
    # FLAT_WHITE -> "flat white": enum-style values read better as words.
    name = str(value).replace("_", " ").lower()
    return "%s: %s" % (name, description) if description else name


def answer(text, question):
    kind = question.get("type")
    name = question.get("name")
    instructions = question.get("instructions") or ""
    if kind == "choice":
        choices = question.get("choices") or []
        values = [c.get("value") for c in choices]
        if len(set(map(str, values))) != len(values):
            raise ApiError(400, "question '%s' has duplicate choice values" % name)
        probs, tokens = ask(text, instructions,
                            [option_line(c.get("value"), c.get("description")) for c in choices])
        best = max(range(len(probs)), key=probs.__getitem__)
        return {
            "type": "choice",
            "name": name,
            "choice": values[best],
            "probabilities": [{"value": v, "probability": p} for v, p in zip(values, probs)],
        }, tokens
    if kind == "predicate":
        probs, tokens = ask(text, instructions, ["yes", "no"])
        return {"type": "predicate", "name": name, "probability": probs[0]}, tokens
    if kind == "score":
        levels = question.get("levels") or []
        probs, tokens = ask(text, instructions,
                            [option_line(l.get("label"), l.get("description")) for l in levels])
        return {
            "type": "score",
            "name": name,
            "score": sum(i * p for i, p in enumerate(probs)),
            "probabilities": [{"value": i, "label": l.get("label"), "probability": p}
                              for i, (l, p) in enumerate(zip(levels, probs))],
        }, tokens
    raise ApiError(400, "unsupported question type '%s' (choice, predicate, score)" % kind)


def decide(request):
    questions = request.get("questions")
    if not isinstance(questions, list) or not questions:
        raise ApiError(400, "'questions' must be a non-empty list")
    text = input_text(request.get("input"))
    if not text.strip():
        raise ApiError(400, "'input' must not be empty")
    answers, prompt_tokens = [], 0
    for question in questions:
        result, tokens = answer(text, question)
        answers.append(result)
        prompt_tokens += tokens
    return {
        "object": "decision",
        "model": request.get("model") or BACKEND_MODEL,
        "answers": answers,
        "usage": {
            "input_tokens": prompt_tokens,
            "output_tokens": len(answers),
            "total_tokens": prompt_tokens + len(answers),
        },
    }


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def send_json(self, status, payload):
        data = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def send_error_json(self, error):
        self.send_json(error.status, {"error": {"message": str(error), "type": error.error_type}})

    def do_GET(self):
        if self.path in ("/healthz", "/health"):
            self.send_json(200, {"status": "UP", "backend": BACKEND_URL, "model": BACKEND_MODEL})
        elif self.path == "/v1/models":
            try:
                self.send_json(200, backend("GET", "/models"))
            except ApiError as e:
                self.send_error_json(e)
        else:
            self.send_json(404, {"error": {"message": "not found", "type": "invalid_request_error"}})

    def do_POST(self):
        if self.path.rstrip("/") != "/v1/decisions":
            self.send_json(404, {"error": {"message": "not found", "type": "invalid_request_error"}})
            return
        try:
            length = int(self.headers.get("Content-Length") or 0)
            try:
                request = json.loads(self.rfile.read(length) or b"{}")
            except ValueError:
                raise ApiError(400, "request body is not valid JSON")
            self.send_json(200, decide(request))
        except ApiError as e:
            self.send_error_json(e)
        except Exception as e:  # keep the server up and report the failure
            self.send_error_json(ApiError(500, "gateway error: %s" % e, "server_error"))

    def log_message(self, fmt, *args):
        sys.stderr.write("%s %s\n" % (self.address_string(), fmt % args))


if __name__ == "__main__":
    print("decision gateway on :%d -> %s (model %s)" % (PORT, BACKEND_URL, BACKEND_MODEL), flush=True)
    ThreadingHTTPServer(("", PORT), Handler).serve_forever()
