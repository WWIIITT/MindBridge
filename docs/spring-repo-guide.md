# Spring Knowledge Guide for MindBridge

This guide explains the Spring knowledge used in this repo by following the actual MindBridge codebase.

MindBridge is a Spring Boot application that combines:

- Spring WebFlux for HTTP APIs and Server-Sent Events streaming
- Spring Security for login and role-based authorization
- Spring Data JPA for database persistence
- Spring AI for Ollama/OpenAI chat models and MCP tools
- Spring configuration properties for environment-driven behavior
- Static frontend serving from `src/main/resources/static`

Good starting files:

- `pom.xml`
- `src/main/java/com/mindbridge/agent/AgentApplication.java`
- `src/main/resources/application.yml`

## 1. Application Entry Point

The app starts from:

```java
@SpringBootApplication
@EnableConfigurationProperties(MindBridgeProperties.class)
public class AgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
```

`@SpringBootApplication` is the main Spring Boot switch. It enables:

- component scanning under `com.mindbridge.agent`
- auto-configuration
- Spring Boot application startup

Component scanning is why Spring automatically discovers classes annotated with:

- `@RestController`
- `@Service`
- `@Repository`
- `@Configuration`
- `@Component`

`@EnableConfigurationProperties(MindBridgeProperties.class)` tells Spring to bind the custom `mindbridge.*` YAML settings into a Java object.

## 2. Maven Dependencies

The project uses Spring Boot 3.3.5 and Java 17.

Important dependencies in `pom.xml`:

- `spring-boot-starter-webflux`: reactive HTTP APIs and SSE streaming
- `spring-boot-starter-security`: authentication and authorization
- `spring-boot-starter-data-jpa`: database access through JPA repositories
- `spring-boot-starter-data-redis`: short-term chat memory support
- `spring-boot-starter-mail`: SMTP alert support
- `spring-boot-starter-validation`: request validation with `@Valid`
- `spring-ai-ollama`: local Ollama model integration
- `spring-ai-openai`: OpenAI-compatible model integration
- `spring-ai-starter-mcp-server-webflux`: MCP server support
- `spring-ai-starter-mcp-client-webflux`: MCP client support
- `h2`: local file database
- `mysql-connector-j`: MySQL runtime driver

The repo relies heavily on Spring Boot auto-configuration. For example, adding `spring-boot-starter-data-jpa` and defining datasource settings in YAML is enough for Spring to create the JPA infrastructure.

## 3. Configuration With `application.yml`

The main config file is:

```text
src/main/resources/application.yml
```

Examples:

```yaml
server:
  port: ${SERVER_PORT:8080}
```

This means:

- use the `SERVER_PORT` environment variable if present
- otherwise use `8080`

Database config:

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:h2:file:./data/mindbridge;MODE=MySQL;DATABASE_TO_LOWER=TRUE}
    username: ${DB_USERNAME:sa}
    password: ${DB_PASSWORD:}
```

By default the app uses a local H2 database file. `application-mysql.yml` switches the datasource to MySQL.

Custom app config lives under:

```yaml
mindbridge:
  ai:
    provider: ${AI_PROVIDER:ollama}
  knowledge:
    top-k: ${RAG_TOP_K:4}
  mcp:
    excel:
      mode: ${MCP_EXCEL_MODE:local}
```

Those values are mapped into `MindBridgeProperties`.

## 4. Configuration Properties

File:

```text
src/main/java/com/mindbridge/agent/config/MindBridgeProperties.java
```

The class begins with:

```java
@ConfigurationProperties(prefix = "mindbridge")
public class MindBridgeProperties {
```

That means YAML such as:

```yaml
mindbridge:
  ai:
    provider: ollama
    temperature: 0.35
```

can be used in Java as:

```java
properties.getAi().getProvider()
properties.getAi().getTemperature()
```

This is cleaner than scattering many `@Value("${...}")` fields around the codebase.

In this repo, `MindBridgeProperties` centralizes settings for:

- AI provider/model
- chat history limit
- embedding config
- RAG knowledge retrieval
- RAG evaluation
- MCP Excel and email tools

## 5. Controllers: HTTP Entry Points

Controllers expose backend behavior as HTTP APIs.

Example:

```text
src/main/java/com/mindbridge/agent/controller/ChatController.java
```

```java
@RestController
@RequestMapping("/api/chat")
public class ChatController {
```

Meaning:

- `@RestController`: method return values become HTTP response bodies
- `@RequestMapping("/api/chat")`: all endpoints start with `/api/chat`
- `@PostMapping`: handles POST requests
- `@RequestBody`: converts JSON request body into a Java DTO
- `@Valid`: runs validation on the request DTO
- `@AuthenticationPrincipal`: injects the logged-in user

The chat stream endpoint:

```java
@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<ChatStreamEvent>> stream(...)
```

This returns a stream of Server-Sent Events to the frontend.

Other controller examples:

- `ReportController`: report/admin/conversation APIs
- `KnowledgeController`: knowledge ingestion APIs
- `AgentStatusController`: agent/tool status APIs
- `ApiExceptionHandler`: global API error handling

## 6. Services: Business Logic

Services contain the application workflow.

Example:

```text
src/main/java/com/mindbridge/agent/service/ChatService.java
```

```java
@Service
public class ChatService {
```

The controller delegates to the service:

```java
return chatService.streamChat(currentUser.getId(), request);
```

The service handles the real chat workflow:

1. sanitize user input
2. load the user
3. resolve or create a chat session
4. run the agent pipeline
5. save the user message
6. maybe save a psychological report
7. build model messages
8. stream AI tokens
9. save the assistant reply
10. trigger async tool/report handling

This is a standard Spring layering pattern:

```text
Controller -> Service -> Repository / External Client
```

Services are injected through constructors:

```java
public ChatService(
        UserAccountRepository userAccountRepository,
        ChatSessionRepository chatSessionRepository,
        AiClient aiClient
) {
    this.userAccountRepository = userAccountRepository;
    this.chatSessionRepository = chatSessionRepository;
    this.aiClient = aiClient;
}
```

You do not manually call `new ChatService(...)`. Spring creates it and supplies the dependencies.

## 7. Repositories: Database Access

Repositories are Spring Data interfaces.

Example:

```text
src/main/java/com/mindbridge/agent/repository/ChatSessionRepository.java
```

```java
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
```

`JpaRepository` gives the app common database methods automatically:

- `findById`
- `findAll`
- `save`
- `delete`
- `count`

Spring Data JPA can also generate queries from method names:

```java
Optional<ChatSession> findByPublicIdAndUser_Id(String publicId, Long userId);
```

This roughly means:

```sql
select *
from chat_sessions
where public_id = ?
and user_id = ?
```

The repo also uses:

```java
@EntityGraph(attributePaths = "user")
Optional<ChatSession> findByPublicId(String publicId);
```

`@EntityGraph` tells JPA to fetch the related `user` together with the session.

## 8. Entities: Database Tables

Entities map Java classes to database tables.

Example:

```text
src/main/java/com/mindbridge/agent/domain/ChatSession.java
```

```java
@Entity
@Table(name = "chat_sessions")
public class ChatSession {
```

Primary key:

```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;
```

Column mapping:

```java
@Column(nullable = false, unique = true, length = 64)
private String publicId;
```

Relationship mapping:

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "user_id")
private UserAccount user;
```

That means many chat sessions belong to one user.

Another example:

```text
src/main/java/com/mindbridge/agent/domain/ChatMessage.java
```

It stores messages for a session and user:

- `session`
- `user`
- `role`
- `content`
- `createdAt`

## 9. Bean Configuration

Spring beans can be created manually in `@Configuration` classes.

Example:

```text
src/main/java/com/mindbridge/agent/config/AiClientConfig.java
```

```java
@Configuration
public class AiClientConfig {

    @Bean
    public AiClient aiClient(MindBridgeProperties properties) {
        ...
    }
}
```

This method creates the app's `AiClient` implementation based on config.

If:

```yaml
mindbridge:
  ai:
    provider: ollama
```

then the app uses an Ollama-backed `SpringAiChatClient`.

If:

```yaml
mindbridge:
  ai:
    provider: openai
```

then the app uses an OpenAI-backed `SpringAiChatClient`.

Otherwise it falls back to `HeuristicAiClient`.

This is dependency inversion:

```text
ChatService depends on AiClient
AiClientConfig chooses the concrete implementation
```

## 10. MCP Tool Configuration

File:

```text
src/main/java/com/mindbridge/agent/config/McpToolConfig.java
```

This class configures tool-related beans:

```java
@Bean
public TaskExecutor mcpTaskExecutor()
```

Creates a thread pool for background MCP/tool work.

```java
@Bean
public ToolCallbackProvider mindBridgeMcpToolProvider(...)
```

Exposes Java methods as Spring AI tools.

```java
@Bean
public ExcelReportWriter excelReportWriter(...)
```

Chooses one Excel writer implementation:

- `McpExcelReportWriter`
- `HttpExcelReportWriter`
- `LocalExcelReportWriter`

```java
@Bean
public AlertNotifier alertNotifier(...)
```

Chooses one alert notifier implementation:

- `McpAlertNotifier`
- `HttpAlertNotifier`
- `SmtpAlertNotifier`
- `LogAlertNotifier`

This is a very common Spring pattern:

```text
service depends on interface
configuration chooses implementation
YAML/env controls behavior
```

## 11. Spring Security

File:

```text
src/main/java/com/mindbridge/agent/config/SecurityConfig.java
```

The app uses WebFlux Security:

```java
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
```

Security rules:

```java
.pathMatchers("/actuator/health", "/h2-console/**").permitAll()
.pathMatchers("/api/admin/**").hasRole("ADMIN")
.pathMatchers("/api/reports/**").hasRole("ADMIN")
.pathMatchers("/api/**").authenticated()
.anyExchange().permitAll()
```

Meaning:

- health check and H2 console are public
- admin APIs require `ROLE_ADMIN`
- other API routes require login
- static frontend pages are public

The app uses HTTP Basic:

```java
.httpBasic(Customizer.withDefaults())
```

Passwords use BCrypt:

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

## 12. Startup Data Initialization

File:

```text
src/main/java/com/mindbridge/agent/config/DataInitializer.java
```

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DataInitializer implements ApplicationRunner {
```

`ApplicationRunner` runs after the Spring application starts.

This initializer:

- seeds demo users if the database is empty
- ingests bundled knowledge files if needed

Seed users:

- `admin / admin123`
- `student / student123`

This is useful for local demos and development.

## 13. WebFlux and Reactor

The chat flow uses Reactor types:

- `Mono<T>`: async zero-or-one value
- `Flux<T>`: async zero-to-many values

The chat endpoint returns:

```java
Flux<ServerSentEvent<ChatStreamEvent>>
```

The service creates a stream:

```java
Flux<ServerSentEvent<ChatStreamEvent>> meta = Flux.just(...)
Flux<ServerSentEvent<ChatStreamEvent>> tokens = aiClient.stream(...)
Mono<ServerSentEvent<ChatStreamEvent>> done = Mono.fromCallable(...)
```

Then combines them:

```java
return meta.concatWith(tokens).concatWith(done);
```

Because JPA is blocking, the service moves blocking preparation work to:

```java
.subscribeOn(Schedulers.boundedElastic())
```

This keeps blocking database/file operations away from WebFlux event-loop threads.

The app is therefore a hybrid:

```text
Reactive HTTP layer + blocking JPA persistence
```

## 14. Transactions

File:

```text
src/main/java/com/mindbridge/agent/service/knowledge/KnowledgeService.java
```

Write transaction:

```java
@Transactional
public int ingest(String source, String content)
```

If the method fails, database changes can be rolled back.

Read-only transaction:

```java
@Transactional(readOnly = true)
public List<SearchResult> retrieve(String query, int topK)
```

This tells Spring/JPA that the method only reads data.

## 15. Global API Error Handling

File:

```text
src/main/java/com/mindbridge/agent/controller/ApiExceptionHandler.java
```

```java
@RestControllerAdvice
public class ApiExceptionHandler {
```

This catches exceptions from controllers and converts them to consistent JSON responses.

Examples:

```java
@ExceptionHandler(IllegalArgumentException.class)
public ResponseEntity<ApiMessage> badRequest(...)
```

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ApiMessage> validation(...)
```

This prevents every controller from needing its own repeated try/catch blocks.

## 16. Static Frontend Serving

Frontend files live in:

```text
src/main/resources/static
```

Examples:

- `index.html`
- `app.js`
- `styles.css`

Spring Boot automatically serves files from this folder.

So the app can serve the frontend at:

```text
http://localhost:8080/
```

No custom controller is required for static files.

## 17. Main Request Flow: Chat

The most useful flow to study is:

```text
Frontend app.js
-> POST /api/chat/stream
-> ChatController
-> ChatService
-> AgentRuntimeService
-> repositories
-> AiClient
-> SSE response stream
```

In Spring terms:

```text
HTTP request
-> controller method
-> service method
-> repository/database and external clients
-> response DTO or reactive stream
```

That one flow teaches most of the repo's Spring architecture.

## 18. The Core Spring Lesson

The central Spring idea in this repo is:

```text
Spring wires the application graph.
Controllers expose use cases.
Services coordinate business behavior.
Repositories persist state.
Configuration chooses environment-specific implementations.
Security wraps access around the APIs.
```

MindBridge is a good example of practical Spring Boot because it uses interfaces and configuration to keep business logic separated from infrastructure choices.

For example:

```text
ChatService does not care whether the AI provider is Ollama, OpenAI, or mock.
ToolOrchestrationService does not care whether alerts are sent by log, SMTP, HTTP, or MCP.
Repositories hide SQL behind domain-oriented method names.
Controllers stay thin and delegate real work to services.
```

## Suggested Study Order

1. `AgentApplication.java`
2. `application.yml`
3. `MindBridgeProperties.java`
4. `SecurityConfig.java`
5. `ChatController.java`
6. `ChatService.java`
7. `ChatSession.java`
8. `ChatSessionRepository.java`
9. `AiClientConfig.java`
10. `McpToolConfig.java`
11. `KnowledgeService.java`
12. `ApiExceptionHandler.java`

After that, follow one complete feature at a time:

- chat streaming
- report viewing
- knowledge ingestion
- MCP tool execution
- risk alerting

