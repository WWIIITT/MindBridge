# MindBridge Agent

<p>
  <a href="README.zh-Hant.md">
    <img alt="Traditional Chinese README" src="https://img.shields.io/badge/README-%E7%B9%81%E9%AB%94%E4%B8%AD%E6%96%87-blue">
  </a>
</p>

MindBridge is a campus mental-health assistant.

- Dynamic routed RAG: first classifies each turn as `CHAT`, `CONSULT`, or `RISK`; casual chat does not query the knowledge base, while consultation and risk messages enter retrieval-augmented generation.
- SSE streaming output: `POST /api/chat/stream` returns `text/event-stream`, so any frontend can show a typewriter-style response.
- Post-response psychological-state assessment: records emotion labels, emotion score, risk level, and confidence without exposing the assessment result to the student UI.
- Data loop: consultation and risk messages are written to the database; high-risk cases are written to Excel first, then trigger email or HTTP MCP alerts.
- Spring AI model integration: the default provider is `ollama` for the project model; `openai` is also supported, and `mock` is available for offline demos.
- Replaceable knowledge base: local vector-style retrieval is enabled by default, with optional Chroma vector storage and search.
- Multi-agent loop: each input is handled by `MemoryAgent`, `SupervisorAgent`, `KnowledgeAgent`, `RiskGuardianAgent`, and a response agent. Agents share the project model but use different prompts and tool permissions.

For the large-model LoRA fine-tuning, merge, GGUF conversion, and Ollama integration workflow, see [docs/qwen25-7b-lora-finetune-guide.md](docs/qwen25-7b-lora-finetune-guide.md).

## Project Layout

```text
src/main/java/com/mindbridge/agent
|-- config                 # Configuration, security, AI, and MCP beans
|-- controller             # Chat / Knowledge / Report APIs
|-- domain                 # JPA entities and enums
|-- dto                    # Request and response DTOs
|-- repository             # Spring Data JPA repositories
|-- security               # Current user and authentication lookup
`-- service
    |-- ai                 # Spring AI adapters, mock client, and prompts
    |-- agent              # Multi-agent loop: memory, routing, retrieval, risk guard, and response planning
    |-- knowledge          # Chunking, retrieval, and Chroma integration
    `-- mcp                # Excel and email/HTTP alerting tools
```

## Agent Loop And Agent Responsibilities

Each chat turn enters a bounded agent loop. The loop runs at most 8 steps to avoid unbounded autonomous cycles in a psychological-safety scenario:

```text
MemoryAgent
-> SupervisorAgent
-> KnowledgeAgent
-> RiskGuardianAgent
-> CompanionAgent / CounselorAgent
```

Agent responsibilities:

- `MemoryAgent`: reads short-term memory from Redis; if Redis is empty, falls back to long-term memory in MySQL; then asks the model to summarize memory for the current turn.
- `SupervisorAgent`: asks the model to classify the input as `CHAT`, `CONSULT`, or `RISK`, deciding whether the next step should be general companionship or psychological support.
- `KnowledgeAgent`: asks the model to rewrite the Chroma/RAG retrieval query, evaluates whether the retrieved context is sufficient, and can perform a second retrieval when needed.
- `RiskGuardianAgent`: asks the model to perform post-response psychological-state assessment while keeping a rule-based high-risk fallback.
- `CompanionAgent`: asks the model to generate a normal chat response strategy and wraps it into the general assistant response prompt.
- `CounselorAgent`: asks the model to generate a psychological-support response strategy, combining intent, RAG context, and risk-guard results into the final response prompt.

The final response is streamed to the student through Spring AI. High-risk reports, Excel writes, and alert tools still run according to the safety rules.

## Quick Start

Runtime requirements:

- JDK 17
- Maven 3.9+
- Ollama, only when using local model mode

### Windows: PowerShell Or WSL

The `scripts/*.sh` files are bash scripts. They work from macOS, Linux, WSL, or Git Bash. In Windows PowerShell, run Maven directly and use PowerShell environment variable syntax.

PowerShell smoke test without Ollama or OpenAI:

```powershell
cd D:\GitHub\MindBridge

$env:AI_PROVIDER="mock"
$env:USE_CHROMA="false"

mvn "-Dmaven.repo.local=.m2/repository" spring-boot:run
```

WSL equivalent:

```bash
cd /mnt/d/GitHub/MindBridge
AI_PROVIDER=mock USE_CHROMA=false mvn -Dmaven.repo.local=.m2/repository spring-boot:run
```

To use the bash scripts from WSL or Git Bash:

```bash
cd /mnt/d/GitHub/MindBridge
./scripts/run-dev.sh
```

`run-dev.sh` starts the app in Ollama mode, so it requires Ollama and the configured local model. Use mock mode first if you only want to confirm that the server starts.

The shortest local-model startup path is:

```bash
cd MindBridge
./scripts/run-dev.sh
```

After startup, open:

```text
http://localhost:8080
```

If you want to start services manually, start Ollama in one terminal:

```bash
cd MindBridge
./scripts/start-ollama.sh
```

Then run the project in another terminal:

```bash
cd MindBridge
mvn -Dmaven.repo.local=.m2/repository spring-boot:run
```

You can also build first and run the jar:

```bash
cd MindBridge
mvn -Dmaven.repo.local=.m2/repository package
java -jar target/mindbridge-agent-0.1.0.jar --server.address=127.0.0.1 --server.port=8080
```

By default, the app uses an H2 file database, Ollama, a local Excel file, and log-based alerts. The top-left of the page shows the current model mode. If Ollama is not running locally, the chat API reports a model connection failure. On first startup, the app creates two test accounts:

```text
admin / admin123
student / student123
```

## API Examples

```bash
curl -N -u student:student123 \
  -H 'Content-Type: application/json' \
  -d '{"message":"I have been anxious lately and cannot sleep at night."}' \
  http://localhost:8080/api/chat/stream
```

A high-risk example triggers report creation, Excel write, and alerting:

```bash
curl -N -u student:student123 \
  -H 'Content-Type: application/json' \
  -d '{"message":"I do not want to live anymore. I feel like I cannot keep going."}' \
  http://localhost:8080/api/chat/stream
```

Admin report lookup:

```bash
curl -u admin:admin123 http://localhost:8080/api/admin/reports
```

Check whether the app is connected to a real model:

```bash
curl -u student:student123 http://localhost:8080/api/agent/status
```

Admin knowledge-base ingestion:

```bash
curl -u admin:admin123 \
  -H 'Content-Type: application/json' \
  -d '{"source":"sleep-guide","content":"For insomnia, first keep a consistent wake-up time, reduce screen stimulation before bed, and contact the campus counseling center when needed."}' \
  http://localhost:8080/api/admin/knowledge
```

## Ollama / LoRA Model Integration

The default local model name is:

```text
mindbridge-qwen2.5-7b-ft:latest
```

The local model is created from this GGUF weight file:

```text
models/mindbridge-qwen2.5-7b-ft/mindbridge-qwen2.5-7b-ft-q4_k_m.gguf
```

Create or re-import the model:

```bash
cd MindBridge
./scripts/create-finetuned-model.sh
```

Then start the project:

```bash
cd MindBridge
./scripts/run-dev.sh
```

If you are developing in WSL, install and use Ollama inside WSL when possible. This avoids Windows/WSL port and model-store mismatches. `create-finetuned-model.sh`, `run-dev.sh`, and `start-ollama.sh` target the Ollama process that is reachable from the current environment; they do not automatically configure a Windows Ollama instance.

```bash
curl -fsSL https://ollama.com/install.sh | sh
ollama serve
```

In another WSL terminal, create and verify the model:

```bash
cd MindBridge
OLLAMA_BASE_URL=http://127.0.0.1:11434 ./scripts/create-finetuned-model.sh
curl http://127.0.0.1:11434/api/tags
```

The response should include `mindbridge-qwen2.5-7b-ft:latest`. Ollama stores imported models in its own model store. In a service-style WSL install, that is usually:

```text
/usr/share/ollama/.ollama/models
```

If `ollama serve` reports `bind: address already in use`, check whether an Ollama service is already running:

```bash
curl http://127.0.0.1:11434/api/tags
sudo ss -ltnp | grep 11434
```

If the port is already used by Ollama, you can use the existing service. If you need to stop it:

```bash
sudo pkill ollama
```

To use a different local port, for example `11435`:

```bash
OLLAMA_HOST=127.0.0.1:11435 ollama serve
OLLAMA_BASE_URL=http://127.0.0.1:11435 ./scripts/create-finetuned-model.sh
OLLAMA_BASE_URL=http://127.0.0.1:11435 ./scripts/run-dev.sh
```

If you do not have a local model and only want to demo the full workflow, use mock mode:

```bash
cd MindBridge
AI_PROVIDER=mock \
mvn -Dmaven.repo.local=.m2/repository spring-boot:run
```

You can also start the local model manually without scripts:

```bash
cd MindBridge
AI_PROVIDER=ollama \
OLLAMA_BASE_URL=http://localhost:11434 \
OLLAMA_MODEL=mindbridge-qwen2.5-7b-ft:latest \
JAVA_HOME="$PWD/.tools/amazon-corretto-17.jdk/Contents/Home" \
  .tools/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local=.m2/repository spring-boot:run
```

## Package For Distribution

The model file is large, so distribute it separately when possible:

```text
models/mindbridge-qwen2.5-7b-ft/mindbridge-qwen2.5-7b-ft-q4_k_m.gguf
```

Generate an application release archive without model weights:

```bash
cd MindBridge
./scripts/package-release.sh
```

The script writes a timestamped `MindBridge-app-*.tar.gz` archive under `dist/`. The archive includes source code, `Dockerfile`, `docker-compose.yml`, scripts, documentation, `models/mindbridge-qwen2.5-7b-ft/Modelfile`, and `data/lora/psychqa_synthetic.jsonl`. It excludes model weights, model zips, generated cache/script artifacts, runtime data, Excel output, logs, PDF files, `target/`, `.m2/`, `.tools/`, and IDE configuration.

Recipients should extract the model zip to:

```text
MindBridge/models/mindbridge-qwen2.5-7b-ft/
```

Then run:

```bash
cd MindBridge
./scripts/create-finetuned-model.sh
./scripts/run-dev.sh
```

To deploy MySQL, Redis, Chroma, and Mailpit with Docker:

```bash
docker compose up -d mysql redis chroma mailpit
./scripts/run-dev.sh
```

`run-dev.sh` checks `mindbridge-qwen2.5-7b-ft:latest` through the actual `OLLAMA_BASE_URL`. If the model does not exist and the local `Modelfile` is available, the script creates the model with the normal Ollama CLI. You can also run `create-finetuned-model.sh` manually first.

If WSL reports `The command 'docker' could not be found in this WSL 2 distro`, open Docker Desktop and enable the current WSL distro under `Settings -> Resources -> WSL Integration`, then reopen the WSL terminal.

If you are not on macOS, or Ollama/JDK/Maven are not on the default path, install Ollama, JDK 17, and Maven first, then configure `OLLAMA_BIN`, `JAVA_HOME`, and `MAVEN_BIN` with their actual paths.

## OpenAI Integration

```bash
cd MindBridge
AI_PROVIDER=openai \
OPENAI_API_KEY=your_api_key \
OPENAI_MODEL=gpt-4o-mini \
JAVA_HOME="$PWD/.tools/amazon-corretto-17.jdk/Contents/Home" \
  .tools/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local=.m2/repository spring-boot:run
```

## MySQL, Chroma, And SMTP

Start dependencies:

```bash
docker compose up -d mysql redis chroma mailpit
```

Run with the MySQL profile:

```bash
AI_PROVIDER=ollama \
USE_CHROMA=true \
MCP_EMAIL_MODE=smtp \
ALERT_MAIL_RECIPIENTS=counselor@example.com \
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

Mailpit admin UI:

```text
http://localhost:8025
```

## MCP Tool Modes

Excel tool:

- `MCP_EXCEL_MODE=local`: writes to `./data/mindbridge-reports.xlsx` by default.
- `MCP_EXCEL_MODE=http`: calls `MCP_EXCEL_URL/write`.
- `MCP_EXCEL_MODE=mcp`: uses the Spring AI Model Context Protocol client to call the MCP server tool `mindbridge_write_excel_report`.

Email tool:

- `MCP_EMAIL_MODE=log`: logs only by default, useful for local demos.
- `MCP_EMAIL_MODE=smtp`: sends with Spring Mail.
- `MCP_EMAIL_MODE=http`: calls `MCP_EMAIL_URL/send`.
- `MCP_EMAIL_MODE=mcp`: uses the Spring AI Model Context Protocol client to call the MCP server tool `mindbridge_send_risk_alert`.

Spring MCP:

- `MCP_SERVER_ENABLED=true`: enables the Spring AI MCP WebFlux server. The default SSE endpoint is `/sse`, and the message endpoint is `/mcp/messages`.
- `MCP_CLIENT_ENABLED=true`: enables the Spring AI MCP WebFlux client. It connects to `MCP_SERVER_URL` by default.
- `MCP_EMAIL_SERVER_DELIVERY_MODE=log|smtp`: controls how the MCP server delivers email-tool calls.

The high-risk path follows the documented safety order: create the report, write to Excel, send the alert after the Excel write succeeds, and then update state.

## RAG Evaluation Metrics

The project includes a RAG retrieval evaluation module based on a labeled evaluation dataset:

- `Recall@K`: whether relevant knowledge appears in the Top-K retrieval results.
- `Precision@K`: the proportion of relevant chunks among the Top-K results.
- `MRR`: the mean reciprocal rank of the first relevant chunk.
- `nDCG@K`: normalized ranking quality that accounts for result order.
- `Hit Rate`: the proportion of questions with at least one relevant retrieved chunk.

Run evaluation:

```bash
AI_PROVIDER=mock \
USE_CHROMA=false \
RAG_EVAL_ENABLED=true \
RAG_EVAL_EXIT_AFTER_RUN=true \
mvn spring-boot:run
```

Default evaluation dataset:

```text
src/main/resources/rag-eval/mindbridge-rag-eval.json
```

Default output report:

```text
target/rag-eval-report.json
```

Each evaluation case contains:

- `question`: the query to retrieve against.
- `expectedSources`: knowledge-base source files that should be hit.
- `expectedTerms`: keywords that help judge relevance.
