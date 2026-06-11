# MindBridge Agent

MindBridge 是一個校園心理健康智能體

- 動態路由 RAG：先識別 `CHAT / CONSULT / RISK`，閒聊不查知識庫，諮詢與風險消息才進入檢索增強。
- SSE 流式輸出：`/api/chat/stream` 返回 `text/event-stream`，適合前端做打字機效果。
- 後臺心理狀態識別：記錄情緒標籤、情緒分數、風險等級和置信度，但學生端不展示評估結果。
- 數據閉環：諮詢/風險消息寫入數據庫，高風險先寫 Excel，再觸發郵件或 HTTP MCP 預警。
- Spring AI 模型接入：默認通過 `ollama` 調用項目模型，也可切到 `openai`；`mock` 只作爲無模型離線演示。
- 可替換知識庫：默認本地輕量檢索，可打開 Chroma 鏡像和查詢。
- 多 Agent loop：每輪輸入由 MemoryAgent、SupervisorAgent、KnowledgeAgent、RiskGuardianAgent 和回覆 Agent 協作完成；多個 Agent 共享項目微調模型，但使用不同 prompt 和工具權限。

大模型 LoRA 微調、合併、GGUF 轉換和 Ollama 接入流程見：[docs/qwen25-7b-lora-finetune-guide.md](docs/qwen25-7b-lora-finetune-guide.md)。

## 目錄

```text
src/main/java/com/mindbridge/agent
├── config                 # 配置、安全、AI/MCP Bean
├── controller             # Chat / Knowledge / Report API
├── domain                 # JPA 實體與枚舉
├── dto                    # 請求與響應對象
├── repository             # Spring Data JPA
├── security               # 當前用戶與認證查詢
└── service
    ├── ai                 # Spring AI 模型適配器、mock 客戶端與 Prompt
    ├── agent              # 多 Agent loop：記憶、路由、知識檢索、風險守護與回覆規劃
    ├── knowledge          # 切塊、檢索、Chroma 網關
    └── mcp                # Excel 與郵件/HTTP 預警工具
```

## Agent loop 與多 Agent 分工

每輪對話進入一個有限步 agent loop，最多執行 8 步，防止心理安全場景中出現無限自主循環：

```text
MemoryAgent
-> SupervisorAgent
-> KnowledgeAgent
-> RiskGuardianAgent
-> CompanionAgent / CounselorAgent
```

各 Agent 分工：

- `MemoryAgent`：讀取 Redis 短期記憶；Redis 爲空時從 MySQL 長期記憶恢復，並調用模型生成本輪記憶摘要。
- `SupervisorAgent`：調用模型判斷 `CHAT / CONSULT / RISK`，決定後續交給普通陪伴還是心理支持鏈路。
- `KnowledgeAgent`：調用模型改寫 Chroma/RAG 檢索 query，並判斷檢索結果是否足夠，不足時二次檢索。
- `RiskGuardianAgent`：調用模型做後臺心理狀態評估，同時保留高風險詞庫硬兜底。
- `CompanionAgent`：調用模型生成普通聊天回覆策略，並組裝普通助手回覆 prompt。
- `CounselorAgent`：調用模型生成心理支持回覆策略，並結合記憶、RAG、風險守護結果組裝回復 prompt。

最終回覆仍通過 Spring AI 流式調用項目模型輸出給學生端；後颱風險報告、Excel 和預警工具鏈仍按安全規則執行。

## 快速啓動

運行環境要求：

- JDK 17
- Maven 3.9+
- Ollama, only when using the local model mode

### Windows: PowerShell or WSL

The `scripts/*.sh` files are bash scripts. They work from macOS, Linux, WSL,
or Git Bash. In Windows PowerShell, run Maven directly and use PowerShell
environment variable syntax.

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

`run-dev.sh` starts the app in Ollama mode, so it requires Ollama and the
configured local model. Use mock mode first if you only want to confirm that
the server starts.
- Ollama（使用本地模型時需要）

最省事的方式是直接運行：

```bash
cd MindBridge
./scripts/run-dev.sh
```

啓動後打開：

```text
http://localhost:8080
```

如果想手動分兩步啓動，先在一個終端啓動 Ollama：

```bash
cd MindBridge
./scripts/start-ollama.sh
```

再在另一個終端運行項目：

```bash
cd MindBridge
mvn -Dmaven.repo.local=.m2/repository spring-boot:run
```

也可以先打包，再運行 jar：

```bash
cd MindBridge
mvn -Dmaven.repo.local=.m2/repository package
java -jar target/mindbridge-agent-0.1.0.jar --server.address=127.0.0.1 --server.port=8080
```

默認使用 H2 文件數據庫、Ollama 大模型、本地 Excel 文件和日誌預警。頁面左上角會顯示當前模型模式；如果本機沒有啓動 Ollama，聊天接口會提示模型連接失敗。首次啓動會創建兩個賬號：

```text
admin / admin123
student / student123
```

## 調用示例

```bash
curl -N -u student:student123 \
  -H 'Content-Type: application/json' \
  -d '{"message":"我最近很焦慮，晚上總是睡不着"}' \
  http://localhost:8080/api/chat/stream
```

高風險示例會觸發報告、Excel 寫入和預警：

```bash
curl -N -u student:student123 \
  -H 'Content-Type: application/json' \
  -d '{"message":"我不想活了，感覺撐不下去了"}' \
  http://localhost:8080/api/chat/stream
```

管理員查看後臺報告：

```bash
curl -u admin:admin123 http://localhost:8080/api/admin/reports
```

查看當前是否接入真實大模型：

```bash
curl -u student:student123 http://localhost:8080/api/agent/status
```

管理員追加知識庫：

```bash
curl -u admin:admin123 \
  -H 'Content-Type: application/json' \
  -d '{"source":"sleep-guide","content":"失眠時可先固定起牀時間，減少睡前屏幕刺激，必要時聯繫校心理中心。"}' \
  http://localhost:8080/api/admin/knowledge
```

## 接入 Ollama / LoRA 模型

默認模型配置就是本地 Ollama 路線，模型名爲：

```text
mindbridge-qwen2.5-7b-ft:latest
```

本地模型由這個 GGUF 權重創建：

```text
models/mindbridge-qwen2.5-7b-ft/mindbridge-qwen2.5-7b-ft-q4_k_m.gguf
```

首次運行或重新導入模型時執行：

```bash
cd MindBridge
./scripts/create-finetuned-model.sh
```

之後直接啓動項目：

```bash
cd MindBridge
./scripts/run-dev.sh
```

如果終端提示 `ollama: command not found`，說明只是命令鏈接沒建好；本項目腳本會自動嘗試 macOS 的 `/Applications/Ollama.app/Contents/Resources/ollama`，以及 WSL 中的 Windows 默認安裝位置 `/mnt/c/Users/<你的 Windows 用戶名>/AppData/Local/Programs/Ollama/ollama.exe`。從 WSL 調用 Windows `ollama.exe` 時，腳本會自動把 `Modelfile` 路徑轉成 Windows 路徑。如果仍找不到 Ollama，可以手動指定：

```bash
OLLAMA_BIN="/mnt/c/Users/<你的 Windows 用戶名>/AppData/Local/Programs/Ollama/ollama.exe" \
./scripts/create-finetuned-model.sh
```

沒有本地模型、只想離線演示完整業務流程時，才使用 mock：

```bash
cd MindBridge
AI_PROVIDER=mock \
mvn -Dmaven.repo.local=.m2/repository spring-boot:run
```

也可以不用腳本，手動指定本地模型啓動：

```bash
cd MindBridge
AI_PROVIDER=ollama \
OLLAMA_BASE_URL=http://localhost:11434 \
OLLAMA_MODEL=mindbridge-qwen2.5-7b-ft:latest \
JAVA_HOME="$PWD/.tools/amazon-corretto-17.jdk/Contents/Home" \
  .tools/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local=.m2/repository spring-boot:run
```

## 打包給別人運行

模型文件較大，建議單獨壓縮發送：

```text
models/mindbridge-qwen2.5-7b-ft/mindbridge-qwen2.5-7b-ft-q4_k_m.gguf
```

生成不含模型權重的應用發佈包：

```bash
cd MindBridge
./scripts/package-release.sh
```

腳本會在 `dist/` 下生成 `MindBridge-app-時間戳.tar.gz`。發佈包包含源碼、Dockerfile、docker-compose、腳本、文檔、`models/mindbridge-qwen2.5-7b-ft/Modelfile` 和 `data/lora/psychqa_synthetic.jsonl` 數據集；會排除模型權重、模型 zip、訓練數據生成腳本、運行數據庫、Excel 輸出、日誌、PDF 文檔、`target/`、`.m2/`、`.tools/`、IDE 配置等本機產物。

收到項目的人需要把模型 zip 解壓到：

```text
MindBridge/models/mindbridge-qwen2.5-7b-ft/
```

然後執行：

```bash
cd MindBridge
./scripts/create-finetuned-model.sh
./scripts/run-dev.sh
```

如果用 Docker 部署數據庫、Redis、Chroma、Mailpit：

```bash
docker compose up -d mysql redis chroma mailpit
./scripts/create-finetuned-model.sh
./scripts/run-dev.sh
```

如果不是 macOS，或 Ollama/JDK/Maven 不在默認路徑，需要先安裝 Ollama、JDK 17、Maven，並按實際路徑設置 `OLLAMA_BIN`、`JAVA_HOME`、`MAVEN_BIN`。

## 接入 OpenAI

```bash
cd MindBridge
AI_PROVIDER=openai \
OPENAI_API_KEY=你的_API_Key \
OPENAI_MODEL=gpt-4o-mini \
JAVA_HOME="$PWD/.tools/amazon-corretto-17.jdk/Contents/Home" \
  .tools/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local=.m2/repository spring-boot:run
```

## 使用 MySQL、Chroma、SMTP

啓動依賴：

```bash
docker compose up -d mysql redis chroma mailpit
```

使用 MySQL profile：

```bash
AI_PROVIDER=ollama \
USE_CHROMA=true \
MCP_EMAIL_MODE=smtp \
ALERT_MAIL_RECIPIENTS=counselor@example.com \
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

Mailpit 管理頁面：`http://localhost:8025`

## MCP 工具模式

Excel 工具：

- `MCP_EXCEL_MODE=local`：默認寫入 `./data/mindbridge-reports.xlsx`
- `MCP_EXCEL_MODE=http`：調用 `MCP_EXCEL_URL/write`
- `MCP_EXCEL_MODE=mcp`：通過標準 Model Context Protocol Client 調用 MCP Server 暴露的 `mindbridge_write_excel_report` 工具

郵件工具：

- `MCP_EMAIL_MODE=log`：默認只記錄日誌，便於本地演示
- `MCP_EMAIL_MODE=smtp`：使用 Spring Mail 發送
- `MCP_EMAIL_MODE=http`：調用 `MCP_EMAIL_URL/send`
- `MCP_EMAIL_MODE=mcp`：通過標準 Model Context Protocol Client 調用 MCP Server 暴露的 `mindbridge_send_risk_alert` 工具

標準 MCP：

- `MCP_SERVER_ENABLED=true`：啓用 Spring AI MCP WebFlux Server，默認 SSE 端點爲 `/sse`，消息端點爲 `/mcp/messages`
- `MCP_CLIENT_ENABLED=true`：啓用 Spring AI MCP WebFlux Client，默認連接 `MCP_SERVER_URL`
- `MCP_EMAIL_SERVER_DELIVERY_MODE=log|smtp`：MCP Server 收到郵件工具調用後的實際投遞方式

高風險鏈路按文檔實現爲：寫入報告 -> 寫入 Excel -> Excel 成功後發送預警 -> 更新狀態。

## RAG 評測指標

項目內置 RAG 檢索評測模塊，可基於標註評測集統計：

- `Recall@K`：相關知識是否出現在 TopK 檢索結果中
- `Precision@K`：TopK 返回片段中相關片段佔比
- `MRR`：第一個相關片段的平均倒數排名
- `nDCG@K`：考慮排序位置的歸一化檢索質量
- `Hit Rate`：至少命中一個相關片段的問題佔比

運行評測：

```bash
AI_PROVIDER=mock \
USE_CHROMA=false \
RAG_EVAL_ENABLED=true \
RAG_EVAL_EXIT_AFTER_RUN=true \
mvn spring-boot:run
```

默認評測集：`src/main/resources/rag-eval/mindbridge-rag-eval.json`

默認輸出報告：`target/rag-eval-report.json`

評測集中的每條樣本包含：

- `question`：待檢索問題
- `expectedSources`：應該命中的知識庫來源文件
- `expectedTerms`：可輔助判定相關性的關鍵詞
