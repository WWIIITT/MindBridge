# MindBridge Architecture Overview

這份文件用 MindBridge 這個 repository 來理解整體系統。重點不是背框架名詞，而是看清楚一個 request 如何從 browser 走到 Spring Boot，再走到 MySQL、Redis、Chroma、Mailpit 和 AI provider。

## 系統總覽

MindBridge 是一個 campus mental-health assistant。前端是 `HTML + CSS + JavaScript`，直接放在 Spring Boot 的 static resources：

- `src/main/resources/static/index.html`
- `src/main/resources/static/styles.css`
- `src/main/resources/static/app.js`
- `src/main/resources/static/assets/mindbridge-campus-companion.png`

後端是 Java 17 + Spring Boot 3.3.5。主要 package 在：

```text
src/main/java/com/mindbridge/agent
```

核心分層如下：

```text
Browser
  -> static files: index.html / styles.css / app.js
  -> HTTP Basic Auth
  -> /api/*
  -> Controller
  -> Service
  -> Repository / External Client
  -> MySQL / Redis / Chroma / Ollama / Mailpit
```

Docker Compose 版本則把各服務放成 containers：

```text
app container
  -> mysql:3306
  -> redis:6379
  -> chroma:8000
  -> mailpit:1025
  -> host.docker.internal:11434 for Ollama
```

## Frontend 到 Backend

Spring Boot 會自動 serve `src/main/resources/static` 裡的檔案，所以 browser 開：

```text
http://localhost:8080/
```

會拿到 `index.html`，然後載入：

```html
<link rel="stylesheet" href="/styles.css?v=20260601-admin-restore">
<script src="/app.js?v=20260531-role-ui"></script>
```

`app.js` 負責：

- 儲存登入狀態：`state.auth.username` / `state.auth.password`
- 用 `fetch` 呼叫 `/api/*`
- 用 `Authorization: Basic ...` 傳 demo account
- 解析 `POST /api/chat/stream` 回傳的 `SSE`
- 根據 user role 切換 student chat UI 或 admin dashboard

最重要的 frontend request flow：

```text
sendMessage()
  -> fetch("/api/chat/stream", { method: "POST", body: JSON.stringify(...) })
  -> response.body.getReader()
  -> parseSse()
  -> append token to assistant bubble
```

## Backend 主要流程

Spring Boot entry point 是：

```text
src/main/java/com/mindbridge/agent/AgentApplication.java
```

`@SpringBootApplication` 讓 Spring 掃描 `com.mindbridge.agent` 底下的 classes，例如：

- `@RestController`
- `@Service`
- `@Repository`
- `@Configuration`
- `@Component`

核心 chat request：

```text
app.js
  -> POST /api/chat/stream
  -> ChatController.stream()
  -> ChatService.streamChat()
  -> AgentRuntimeService.run()
  -> AiClient.stream()
  -> ServerSentEvent<ChatStreamEvent>
```

`ChatController` 只負責 HTTP entry point 和 basic authorization 檢查；`ChatService` 才負責實際 workflow：

- sanitize input
- 找 user
- 建立或讀取 `ChatSession`
- 執行 multi-agent pipeline
- 儲存 user message
- 必要時建立 `PsychologicalReport`
- 呼叫 AI model stream
- 儲存 assistant reply
- 高風險時觸發 Excel / alert tool

## MySQL 在這個專案的角色

預設 local run 使用 H2 file database：

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:h2:file:./data/mindbridge;MODE=MySQL;DATABASE_TO_LOWER=TRUE}
```

啟用 `mysql` profile 後，會套用：

```text
src/main/resources/application-mysql.yml
```

裡面的 MySQL driver：

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
```

Docker Compose 裡的 app container 用這個 `DB_URL` 連到 MySQL service：

```text
jdbc:mysql://mysql:3306/mindbridge?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
```

注意 `mysql` 在這裡不是 hostname magic，而是 Docker Compose service name。Compose 會建立 network DNS，讓 `app` 可以用 `mysql` 找到 MySQL container。

JPA entities 會被 Hibernate mapping 成資料表，例如：

- `UserAccount` -> `user_accounts`
- `ChatSession` -> `chat_sessions`
- `ChatMessage` -> `chat_messages`
- `KnowledgeChunk` -> `knowledge_chunks`
- `PsychologicalReport` -> psychological report table
- `AlertRecord` -> alert record table

## Redis 在這個專案的角色

Redis 是 short-term memory cache，不是主要資料庫。

主要 class：

```text
src/main/java/com/mindbridge/agent/service/memory/ShortTermMemoryService.java
```

它用 `StringRedisTemplate` 操作 Redis list：

```java
redisTemplate.opsForList().rightPush(key, value);
redisTemplate.opsForList().trim(key, -messageLimit(), -1);
redisTemplate.expire(key, ttl());
```

key pattern：

```text
mindbridge:chat:short-memory:{sessionId}
```

資料會有 TTL，預設由：

```yaml
mindbridge:
  chat:
    short-memory-ttl-hours: ${CHAT_SHORT_MEMORY_TTL_HOURS:24}
```

控制。Redis 掛掉時，程式會 catch exception 並略過 short-term memory，完整 chat history 仍然存在 MySQL。

## Chroma 在這個專案的角色

Chroma 是 optional vector store，用在 RAG retrieval。主要 class：

```text
src/main/java/com/mindbridge/agent/service/knowledge/ChromaGateway.java
src/main/java/com/mindbridge/agent/service/knowledge/KnowledgeService.java
```

`KnowledgeService.ingest()` 會：

1. 把文件切成 chunks
2. 刪掉同 source 的舊 chunks
3. 儲存 chunks 到 MySQL
4. 如果 `USE_CHROMA=true`，同步 mirror 到 Chroma

retrieval 優先順序：

```text
Chroma query
  -> stored embedding in MySQL
  -> local TokenVectorizer fallback
```

所以 Chroma 是搜尋加速與向量檢索層，MySQL 仍是主要持久化來源。

## Mailpit 和 Tool Services

Mailpit 是 local email testing service。Compose expose：

```text
SMTP: 1025
Web UI: 8025
```

專案中的 alert mode 可用：

- `MCP_EMAIL_MODE=log`
- `MCP_EMAIL_MODE=smtp`
- `MCP_EMAIL_MODE=http`
- `MCP_EMAIL_MODE=mcp`

local demo 預設是 `log`，如果要看 SMTP 測試信，可以改成 `smtp` 並開：

```text
http://localhost:8025
```

## Docker Container 溝通圖

`docker-compose.yml` 定義：

```text
app       -> Spring Boot application
mysql     -> durable relational database
redis     -> short-term memory cache
chroma    -> optional vector database
mailpit   -> local SMTP test inbox
```

ports：

```text
8080:8080  app
3306:3306  mysql
6379:6379  redis
8000:8000  chroma
1025:1025  mailpit SMTP
8025:8025  mailpit UI
```

volumes：

```text
mysql-data   -> /var/lib/mysql
redis-data   -> /data
chroma-data  -> /chroma/chroma
./data       -> /app/data
```

## 常用 commands and syntax

### 啟動整套 Docker services

用途：啟動 app、MySQL、Redis、Chroma、Mailpit。

```bash
docker compose up -d
```

repo-specific example：

```bash
cd D:\GitHub\MindBridge
docker compose up -d
```

### 只啟動 infrastructure，不啟動 app container

用途：本機用 Maven 跑 Spring Boot，但 database/cache/vector/email 用 Docker。

```bash
docker compose up -d mysql redis chroma mailpit
```

### 用 mock AI 啟動 Spring Boot

用途：不依賴 Ollama 或 OpenAI，先確認 backend 和 frontend 可跑。

PowerShell：

```powershell
$env:AI_PROVIDER="mock"
$env:USE_CHROMA="false"
mvn "-Dmaven.repo.local=.m2/repository" spring-boot:run
```

### 用 MySQL profile 啟動 Spring Boot

用途：讓 Spring Boot 使用 `application-mysql.yml`。

PowerShell：

```powershell
$env:AI_PROVIDER="mock"
$env:USE_CHROMA="false"
mvn "-Dmaven.repo.local=.m2/repository" spring-boot:run "-Dspring-boot.run.profiles=mysql"
```

### 檢查 Spring Boot health

用途：確認 app 是否啟動。

```bash
curl http://localhost:8080/actuator/health
```

expected response：

```json
{"status":"UP"}
```

### 用 student account 呼叫 chat stream

用途：測試主要 chat endpoint。

```bash
curl -N -u student:student123 \
  -H "Content-Type: application/json" \
  -d "{\"message\":\"I feel anxious and cannot sleep.\"}" \
  http://localhost:8080/api/chat/stream
```

### 用 admin account 查 reports

用途：測試 admin endpoint 和 `ROLE_ADMIN` authorization。

```bash
curl -u admin:admin123 http://localhost:8080/api/admin/reports
```

### 查看目前 containers

用途：確認 service 名稱、狀態、ports。

```bash
docker compose ps
```

### 查看 app logs

用途：debug Spring Boot startup、DB connection、AI provider、SSE error。

```bash
docker compose logs -f app
```

### 進入 MySQL container

用途：直接檢查 durable data。

```bash
docker compose exec mysql mysql -umindbridge -pmindbridge mindbridge
```

### 進入 Redis container

用途：檢查 short-term memory keys。

```bash
docker compose exec redis redis-cli
```

### 停止 containers

用途：停止但保留 volumes。

```bash
docker compose down
```

用途：停止並刪除 volumes，會清掉 MySQL/Redis/Chroma data。

```bash
docker compose down -v
```

使用 `-v` 前要小心，這會刪除 local demo data。

## 官方參考

- Spring Boot Externalized Configuration: <https://docs.spring.io/spring-boot/reference/features/external-config.html>
- Spring Boot Profiles: <https://docs.spring.io/spring-boot/reference/features/profiles.html>
- Docker Compose services: <https://docs.docker.com/reference/compose-file/services/>
- MDN Fetch API: <https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API/Using_Fetch>
- MDN Server-Sent Events: <https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events>
