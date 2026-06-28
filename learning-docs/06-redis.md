# Redis 學習筆記

這份文件聚焦 MindBridge 如何使用 Redis。相關檔案：

```text
src/main/java/com/mindbridge/agent/service/memory/ShortTermMemoryService.java
src/main/resources/application.yml
docker-compose.yml
```

## Redis 在專案中的角色

Redis 在 MindBridge 裡是 short-term memory cache。它不是主要 database。

主要用途：

- 保存最近幾輪 chat message
- 讓 agent 在處理下一輪訊息時可以快速拿到 short memory
- 設定 TTL，過期後自動清除
- Redis 不可用時不阻斷 chat flow

長期對話資料仍存在 MySQL 的 `chat_sessions` 和 `chat_messages`。

## Redis configuration

`application.yml`：

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: ${REDIS_DATABASE:0}
      timeout: ${REDIS_TIMEOUT:2s}
```

Docker Compose app container：

```yaml
REDIS_HOST: redis
REDIS_PORT: 6379
```

跟 MySQL 一樣，`redis` 是 Docker Compose service name。

## ShortTermMemoryService

主要 class：

```text
src/main/java/com/mindbridge/agent/service/memory/ShortTermMemoryService.java
```

key prefix：

```java
private static final String KEY_PREFIX = "mindbridge:chat:short-memory:";
```

完整 key：

```text
mindbridge:chat:short-memory:{sessionId}
```

每筆 memory message 會被 serialize 成 JSON string：

```java
objectMapper.writeValueAsString(new MemoryMessage(role, content))
```

## Redis list pattern

append 時：

```java
redisTemplate.opsForList().rightPush(key, value);
redisTemplate.opsForList().trim(key, -messageLimit(), -1);
redisTemplate.expire(key, ttl());
```

意思：

1. `rightPush`：把新 message 加到 list 尾端
2. `trim`：只保留最後 N 則
3. `expire`：刷新 TTL

讀取時：

```java
redisTemplate.opsForList().range(key(sessionId), 0, -1);
```

意思：讀整個 list。

## TTL

TTL 由這個設定控制：

```yaml
mindbridge:
  chat:
    short-memory-ttl-hours: ${CHAT_SHORT_MEMORY_TTL_HOURS:24}
```

程式中：

```java
Duration.ofHours(Math.max(1, properties.getChat().getShortMemoryTtlHours()))
```

最少 1 小時。

## Failure behavior

`ShortTermMemoryService` 幾乎每個 Redis operation 都包在 `try/catch`。

如果 Redis 掛掉：

- append skipped
- read returns empty list
- refresh skipped
- app 不會因 Redis memory failure 直接壞掉

這是 cache 的常見設計：cache fail 不應該讓核心資料流程 fail。

## 常用 commands and syntax

### 啟動 Redis container

用途：只啟動 Redis。

```bash
docker compose up -d redis
```

repo-specific：

```bash
cd D:\GitHub\MindBridge
docker compose up -d redis
```

### 進入 redis-cli

用途：直接操作 Redis。

```bash
docker compose exec redis redis-cli
```

### PING

用途：確認 Redis 有回應。

```redis
PING
```

expected：

```text
PONG
```

### SELECT database

用途：切換 Redis logical database。MindBridge 預設 database 是 `0`。

```redis
SELECT 0
```

### 掃描 project keys

用途：安全列出 matching keys。比 `KEYS *` 更適合資料多時使用。

```redis
SCAN 0 MATCH mindbridge:chat:short-memory:* COUNT 100
```

### KEYS

用途：local 小資料量快速查 key。production 不建議。

```redis
KEYS mindbridge:chat:short-memory:*
```

### TYPE

用途：確認 key 的資料型別。

```redis
TYPE mindbridge:chat:short-memory:PUT_SESSION_ID_HERE
```

expected：

```text
list
```

### LRANGE

用途：讀 Redis list 內容。

```redis
LRANGE mindbridge:chat:short-memory:PUT_SESSION_ID_HERE 0 -1
```

MindBridge list 裡每個 item 是 JSON string，通常像：

```json
{"role":"USER","content":"..."}
```

### LLEN

用途：查看 list 長度。

```redis
LLEN mindbridge:chat:short-memory:PUT_SESSION_ID_HERE
```

### RPUSH

用途：從右邊加入 list。對應 Java 的 `rightPush`。

```redis
RPUSH mindbridge:chat:short-memory:test-session '{"role":"USER","content":"hello"}'
```

### LTRIM

用途：裁切 list，只保留指定範圍。對應 Java 的 `trim`。

```redis
LTRIM mindbridge:chat:short-memory:test-session -20 -1
```

意思：只保留最後 20 筆。

### EXPIRE

用途：設定 key 過期秒數。

```redis
EXPIRE mindbridge:chat:short-memory:test-session 86400
```

`86400` 秒 = 24 小時。

### TTL

用途：查看 key 剩餘生命。

```redis
TTL mindbridge:chat:short-memory:test-session
```

常見結果：

- 正數：剩餘秒數
- `-1`：key 存在但沒有 TTL
- `-2`：key 不存在

### DEL

用途：刪除某個 session memory。

```redis
DEL mindbridge:chat:short-memory:test-session
```

### FLUSHDB

用途：清空目前 Redis database。危險，只適合 local demo。

```redis
FLUSHDB
```

如果只想清 MindBridge keys，優先用 `SCAN` 找 key 再逐個 `DEL`。

### 從 host 測 Redis port

用途：確認 container port exposed。

PowerShell：

```powershell
Test-NetConnection localhost -Port 6379
```

### Spring Boot Redis env vars

用途：改 Spring Boot 連線位置。

PowerShell：

```powershell
$env:REDIS_HOST="localhost"
$env:REDIS_PORT="6379"
mvn spring-boot:run
```

Docker Compose app container 內則使用：

```text
REDIS_HOST=redis
REDIS_PORT=6379
```

### Java StringRedisTemplate list syntax

用途：讀懂 `ShortTermMemoryService`。

```java
redisTemplate.opsForList().rightPush(key, value);
redisTemplate.opsForList().range(key, 0, -1);
redisTemplate.opsForList().trim(key, -messageLimit(), -1);
redisTemplate.expire(key, ttl());
redisTemplate.delete(key);
```

對應 Redis commands：

```text
RPUSH
LRANGE
LTRIM
EXPIRE
DEL
```

### 產生一次 Redis memory 的實際方式

用途：透過 app 建立真實 key。

1. 啟動 Redis 和 app。
2. 用 student 送 chat：

```bash
curl -N -u student:student123 \
  -H "Content-Type: application/json" \
  -d "{\"message\":\"hello\"}" \
  http://localhost:8080/api/chat/stream
```

3. 查 Redis keys：

```bash
docker compose exec redis redis-cli KEYS "mindbridge:chat:short-memory:*"
```

## 官方參考

- Spring Data Redis Template: <https://docs.spring.io/spring-data/redis/reference/redis/template.html>
- Redis commands: <https://redis.io/commands/>
