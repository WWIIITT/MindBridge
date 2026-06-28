# Java / Spring Boot 學習筆記

這份文件聚焦 MindBridge 的 Java backend。請一邊看這份文件，一邊打開：

```text
pom.xml
src/main/java/com/mindbridge/agent/AgentApplication.java
src/main/resources/application.yml
src/main/resources/application-mysql.yml
```

## Backend 在專案中的位置

MindBridge backend 使用：

- Java 17
- Spring Boot 3.3.5
- Spring WebFlux
- Spring Security
- Spring Data JPA
- Spring Data Redis
- Spring AI
- Maven

主要 package：

```text
com.mindbridge.agent
```

常見分層：

```text
Controller -> Service -> Repository -> Entity -> Database
```

另外也有外部 client：

```text
Service -> AiClient / ChromaGateway / AlertNotifier / ExcelReportWriter
```

## AgentApplication

入口檔案：

```text
src/main/java/com/mindbridge/agent/AgentApplication.java
```

核心概念：

```java
@SpringBootApplication
@EnableConfigurationProperties(MindBridgeProperties.class)
public class AgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
```

`@SpringBootApplication` 會啟用：

- component scanning
- auto-configuration
- Spring Boot startup

`@EnableConfigurationProperties` 讓 `application.yml` 中的 `mindbridge.*` 可以 binding 到 `MindBridgeProperties`。

## Controller

`Controller` 是 HTTP API 入口。

例子：

```text
src/main/java/com/mindbridge/agent/controller/ChatController.java
```

它定義：

```java
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatStreamEvent>> stream(...)
}
```

意思：

- `@RestController`：回傳值會被序列化成 HTTP response body
- `@RequestMapping("/api/chat")`：class 底下 API 都以 `/api/chat` 開頭
- `@PostMapping("/stream")`：處理 `POST /api/chat/stream`
- `produces = MediaType.TEXT_EVENT_STREAM_VALUE`：回傳 `SSE`
- `@RequestBody`：把 JSON body 轉成 Java DTO
- `@Valid`：執行 DTO validation
- `@AuthenticationPrincipal`：取得目前登入 user

## Service

`Service` 是 business workflow 的地方。

例子：

```text
src/main/java/com/mindbridge/agent/service/ChatService.java
```

`ChatService.streamChat()` 做的事情包含：

1. sanitize 使用者輸入
2. 讀取 `UserAccount`
3. 建立或讀取 `ChatSession`
4. 執行 agent runtime
5. 儲存 user message
6. 必要時建立 psychological report
7. 組 AI prompt messages
8. streaming AI tokens
9. 儲存 assistant reply
10. 背景處理 Excel / alert tools

這就是為什麼 Controller 要保持薄，真正流程放 Service。

## Repository 和 Entity

`Entity` 代表 database table。例子：

```java
@Entity
@Table(name = "chat_sessions")
public class ChatSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
}
```

`Repository` 是資料庫 access interface：

```java
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    Optional<ChatSession> findByPublicIdAndUser_Id(String publicId, Long userId);
}
```

Spring Data JPA 會根據 method name 產生 query。這個 method 大概等同：

```sql
select *
from chat_sessions
where public_id = ?
and user_id = ?
```

## DTO 和 Validation

DTO 放在：

```text
src/main/java/com/mindbridge/agent/dto
```

它們是 API input/output shape。例如 `ChatRequest` 代表 frontend 送來的 chat JSON。

Controller 使用：

```java
@Valid @RequestBody ChatRequest request
```

如果 DTO 欄位有 validation annotations，Spring 會自動檢查，錯誤會交給 `ApiExceptionHandler`。

## Security

Security config：

```text
src/main/java/com/mindbridge/agent/config/SecurityConfig.java
```

重要規則：

```java
.pathMatchers("/actuator/health", "/h2-console/**").permitAll()
.pathMatchers("/api/admin/**").hasRole("ADMIN")
.pathMatchers("/api/reports/**").hasRole("ADMIN")
.pathMatchers("/api/**").authenticated()
.anyExchange().permitAll()
```

意思：

- `/actuator/health` public
- `/api/admin/**` 需要 `ROLE_ADMIN`
- 其他 `/api/**` 需要登入
- static frontend public

demo users 由 `DataInitializer` 建立：

```text
admin / admin123
student / student123
```

## ConfigurationProperties

設定檔：

```text
src/main/resources/application.yml
```

自訂設定集中在：

```yaml
mindbridge:
  ai:
    provider: ${AI_PROVIDER:ollama}
  chat:
    history-limit: ${CHAT_HISTORY_LIMIT:10}
  knowledge:
    use-chroma: ${USE_CHROMA:true}
```

Java binding class：

```text
src/main/java/com/mindbridge/agent/config/MindBridgeProperties.java
```

好處是 Service 或 Config 可以注入 `MindBridgeProperties`，不用到處散落 `@Value("${...}")`。

## WebFlux 和 SSE

這個 project 使用 `spring-boot-starter-webflux`。Chat stream 回傳：

```java
Flux<ServerSentEvent<ChatStreamEvent>>
```

你可以把 `Flux<T>` 理解成：

```text
一段時間內陸續產生多個 T
```

在 chat 這裡，`T` 是 SSE event。Frontend 每收到一個 token event，就更新 assistant bubble。

因為 JPA 是 blocking I/O，`ChatService` 用：

```java
subscribeOn(Schedulers.boundedElastic())
```

把 blocking preparation 和 done save 移到適合 blocking work 的 scheduler。

## MySQL profile

預設 `application.yml` 使用 H2。要切到 MySQL：

```text
src/main/resources/application-mysql.yml
```

啟動 profile：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

Docker Compose 裡 app container 則用：

```yaml
SPRING_PROFILES_ACTIVE: mysql
DB_URL: jdbc:mysql://mysql:3306/mindbridge?...
```

## 常用 commands and syntax

### Maven: 啟動 app

用途：local development 啟動 Spring Boot。

```bash
mvn spring-boot:run
```

repo-specific PowerShell：

```powershell
mvn "-Dmaven.repo.local=.m2/repository" spring-boot:run
```

### Maven: mock AI 啟動

用途：不連 Ollama/OpenAI，先測 backend、frontend、DB。

```powershell
$env:AI_PROVIDER="mock"
$env:USE_CHROMA="false"
mvn "-Dmaven.repo.local=.m2/repository" spring-boot:run
```

### Maven: 使用 MySQL profile

用途：套用 `application-mysql.yml`。

```powershell
$env:AI_PROVIDER="mock"
$env:USE_CHROMA="false"
mvn "-Dmaven.repo.local=.m2/repository" spring-boot:run "-Dspring-boot.run.profiles=mysql"
```

### Maven: package jar

用途：建立 executable jar。

```bash
mvn package
```

repo-specific：

```powershell
mvn "-Dmaven.repo.local=.m2/repository" package
```

### Maven: skip tests package

用途：快速 package，Dockerfile build stage 也是用類似方式。

```bash
mvn -DskipTests package
```

### Maven: run tests

用途：執行 `src/test/java` 裡的 tests。

```bash
mvn test
```

### Run jar

用途：不透過 Maven plugin，直接跑 packaged jar。

```bash
java -jar target/mindbridge-agent-0.1.0.jar --server.port=8080
```

### Spring annotation syntax

用途：辨識專案中最常見的 Spring annotations。

```java
@RestController
@RequestMapping("/api/chat")
public class ChatController {
}
```

```java
@Service
public class ChatService {
}
```

```java
@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
}
```

```java
@Configuration
public class AiClientConfig {
    @Bean
    public AiClient aiClient(MindBridgeProperties properties) {
        return new HeuristicAiClient();
    }
}
```

### REST endpoint syntax

用途：定義 API route。

```java
@GetMapping("/api/profile")
public Map<String, Object> profile() {
    return Map.of("status", "ok");
}
```

MindBridge 實際使用的 pattern：

```java
@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
```

### Request body syntax

用途：接收 frontend JSON。

```java
public Response method(@Valid @RequestBody ChatRequest request) {
    return response;
}
```

Frontend 送：

```json
{"sessionId":"abc","message":"hello"}
```

### Authentication principal syntax

用途：取得登入 user。

```java
public List<ReportResponse> myReports(@AuthenticationPrincipal CurrentUser currentUser) {
    return reportService.myReports(currentUser.getId()).stream()
            .map(ReportResponse::from)
            .toList();
}
```

### JPA Entity syntax

用途：把 Java class mapping 到 database table。

```java
@Entity
@Table(name = "chat_messages")
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageRole role;
}
```

### JPA relationship syntax

用途：表示 many messages belong to one session。

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "session_id")
private ChatSession session;
```

### Repository query method syntax

用途：不用寫 SQL，用 method name 建 query。

```java
List<ChatMessage> findBySession_PublicIdOrderByCreatedAtAsc(String publicId);
```

可讀成：

```text
find chat_messages by session.publicId, order by createdAt ascending
```

### Transaction syntax

用途：資料庫寫入失敗時 rollback。

```java
@Transactional
public int ingest(String source, String content) {
    return chunks;
}
```

read-only query：

```java
@Transactional(readOnly = true)
public List<SearchResult> retrieve(String query, int topK) {
    return results;
}
```

### Configuration placeholder syntax

用途：環境變數覆蓋預設值。

```yaml
server:
  port: ${SERVER_PORT:8080}
```

意思：

```text
如果有 SERVER_PORT，就用環境變數；否則用 8080
```

### curl: health check

用途：確認 app alive。

```bash
curl http://localhost:8080/actuator/health
```

### curl: chat stream

用途：直接測 `POST /api/chat/stream`。

```bash
curl -N -u student:student123 \
  -H "Content-Type: application/json" \
  -d "{\"message\":\"I have been anxious lately.\"}" \
  http://localhost:8080/api/chat/stream
```

### curl: admin reports

用途：確認 admin authorization 和 report API。

```bash
curl -u admin:admin123 http://localhost:8080/api/admin/reports
```

### Java collection syntax 常見片段

用途：讀懂 Service 中 stream/map/toList。

```java
return reportService.latestReports().stream()
        .map(ReportResponse::from)
        .toList();
```

意思：

```text
把 reports list 轉成 stream，每個 item 用 ReportResponse.from() 轉換，最後收回 List
```

### Constructor injection syntax

用途：Spring 建立 bean 時自動注入 dependencies。

```java
public ChatService(
        UserAccountRepository userAccountRepository,
        AiClient aiClient
) {
    this.userAccountRepository = userAccountRepository;
    this.aiClient = aiClient;
}
```

不用自己 `new ChatService(...)`。

## 官方參考

- Spring Boot Externalized Configuration: <https://docs.spring.io/spring-boot/reference/features/external-config.html>
- Spring Boot Profiles: <https://docs.spring.io/spring-boot/reference/features/profiles.html>
- Spring Data JPA Query Methods: <https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html>
