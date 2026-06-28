# MySQL 學習筆記

這份文件聚焦 MindBridge 如何使用 MySQL。相關檔案：

```text
src/main/resources/application.yml
src/main/resources/application-mysql.yml
docker-compose.yml
src/main/java/com/mindbridge/agent/domain
src/main/java/com/mindbridge/agent/repository
```

## MySQL 在專案中的角色

MySQL 是 durable relational database。它保存：

- users
- roles
- chat sessions
- chat messages
- knowledge chunks
- psychological reports
- Excel write records
- alert records

Redis 只是 short-term memory cache；MySQL 才是長期資料來源。

## H2 default vs MySQL profile

預設 `application.yml`：

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:h2:file:./data/mindbridge;MODE=MySQL;DATABASE_TO_LOWER=TRUE}
    username: ${DB_USERNAME:sa}
    password: ${DB_PASSWORD:}
```

啟用 `mysql` profile 後，會套用：

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/mindbridge?...}
    username: ${DB_USERNAME:mindbridge}
    password: ${DB_PASSWORD:mindbridge}
    driver-class-name: com.mysql.cj.jdbc.Driver
```

Docker Compose app container 使用：

```yaml
SPRING_PROFILES_ACTIVE: mysql
DB_URL: jdbc:mysql://mysql:3306/mindbridge?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
DB_USERNAME: mindbridge
DB_PASSWORD: mindbridge
```

## JPA Entity 如何變成 table

例子：

```java
@Entity
@Table(name = "chat_sessions")
public class ChatSession {
}
```

這會 mapping 到：

```text
chat_sessions
```

常見 mapping：

- `@Id`：primary key
- `@GeneratedValue(strategy = GenerationType.IDENTITY)`：MySQL auto increment
- `@Column(nullable = false)`：欄位不可為 null
- `@ManyToOne`：foreign key relationship
- `@Enumerated(EnumType.STRING)`：enum 用字串存
- `@Lob`：長文字欄位

## 重要 tables

### user_accounts

來自 `UserAccount`。

用途：

- login username
- encrypted password
- display name
- enabled flag

`DataInitializer` 會 seed：

```text
admin / admin123
student / student123
```

password 會用 BCrypt 儲存，不會是 plain text。

### user_account_roles

來自：

```java
@ElementCollection(fetch = FetchType.EAGER)
@CollectionTable(name = "user_account_roles", joinColumns = @JoinColumn(name = "user_id"))
```

用途：保存 roles，例如：

```text
ROLE_USER
ROLE_ADMIN
```

### chat_sessions

來自 `ChatSession`。

用途：

- 一次對話 session
- `publicId` 給 frontend 使用
- `user_id` 指向 user
- `title`
- `createdAt`
- `updatedAt`

### chat_messages

來自 `ChatMessage`。

用途：

- 每一則 user/assistant message
- `session_id`
- `user_id`
- `role`
- `content`
- `createdAt`

### knowledge_chunks

來自 `KnowledgeChunk`。

用途：

- RAG knowledge chunks
- source filename/source name
- source index
- content
- optional embedding JSON

## Hibernate ddl-auto

設定：

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update
```

`update` 代表 app startup 時 Hibernate 會嘗試讓 DB schema 跟 Entity 對齊。這對 learning/demo 很方便，但 production 通常會改用 Flyway/Liquibase migration。

## 常用 commands and syntax

### 啟動 MySQL container

用途：只啟動 MySQL。

```bash
docker compose up -d mysql
```

repo-specific：

```bash
cd D:\GitHub\MindBridge
docker compose up -d mysql
```

### 進入 MySQL CLI

用途：用 project DB user 連到 `mindbridge` database。

```bash
docker compose exec mysql mysql -umindbridge -pmindbridge mindbridge
```

注意：`-p` 後面直接接 password，中間不要空格。

### 用 root 進入 MySQL

用途：需要更高權限時使用。

```bash
docker compose exec mysql mysql -uroot -proot
```

### 顯示 databases

用途：確認 `mindbridge` database 存在。

```sql
SHOW DATABASES;
```

### 切換 database

用途：指定接下來 query 的 database。

```sql
USE mindbridge;
```

### 顯示 tables

用途：看 Hibernate 建了哪些 tables。

```sql
SHOW TABLES;
```

### 查看 table schema

用途：看欄位、型別、key。

```sql
DESCRIBE user_accounts;
DESCRIBE chat_sessions;
DESCRIBE chat_messages;
DESCRIBE knowledge_chunks;
```

也可以：

```sql
SHOW CREATE TABLE chat_messages\G
```

### 查 users

用途：確認 seed users。

```sql
SELECT id, username, display_name, enabled, created_at
FROM user_accounts;
```

不要查或貼出 password hash，除非正在 debug auth。

### 查 roles

用途：確認 admin/student 權限。

```sql
SELECT user_id, role
FROM user_account_roles
ORDER BY user_id, role;
```

### 查最近 sessions

用途：看 frontend sessionId 對應資料。

```sql
SELECT id, public_id, user_id, title, created_at, updated_at
FROM chat_sessions
ORDER BY updated_at DESC
LIMIT 10;
```

### 查最近 messages

用途：debug chat history。

```sql
SELECT id, session_id, user_id, role, LEFT(content, 120) AS preview, created_at
FROM chat_messages
ORDER BY created_at DESC
LIMIT 20;
```

### 用 JOIN 查完整對話

用途：用 `public_id` 找某個 session 的 messages。

```sql
SELECT m.created_at, u.username, m.role, m.content
FROM chat_messages m
JOIN chat_sessions s ON m.session_id = s.id
JOIN user_accounts u ON m.user_id = u.id
WHERE s.public_id = 'PUT_SESSION_PUBLIC_ID_HERE'
ORDER BY m.created_at ASC;
```

### 查 knowledge chunks

用途：確認 admin upload 或 startup ingestion。

```sql
SELECT id, source, source_index, LEFT(content, 160) AS preview, created_at
FROM knowledge_chunks
ORDER BY created_at DESC
LIMIT 20;
```

### 查特定 source

用途：看某份 knowledge file 被切成幾段。

```sql
SELECT source, COUNT(*) AS chunks
FROM knowledge_chunks
GROUP BY source
ORDER BY chunks DESC;
```

### WHERE 條件

用途：只查符合條件的 records。

```sql
SELECT *
FROM chat_messages
WHERE role = 'USER'
LIMIT 10;
```

### ORDER BY

用途：排序。

```sql
SELECT *
FROM chat_sessions
ORDER BY updated_at DESC;
```

### LIMIT

用途：避免一次查太多。

```sql
SELECT *
FROM chat_messages
LIMIT 20;
```

學習與 debug 時建議永遠先加 `LIMIT`。

### COUNT

用途：快速看資料量。

```sql
SELECT COUNT(*) FROM user_accounts;
SELECT COUNT(*) FROM chat_sessions;
SELECT COUNT(*) FROM chat_messages;
SELECT COUNT(*) FROM knowledge_chunks;
```

### LIKE

用途：模糊搜尋文字。

```sql
SELECT id, source, LEFT(content, 160) AS preview
FROM knowledge_chunks
WHERE content LIKE '%sleep%'
LIMIT 10;
```

### Safe inspection transaction

用途：只讀不修改。

```sql
START TRANSACTION READ ONLY;
SELECT COUNT(*) FROM chat_messages;
COMMIT;
```

### 新增測試資料語法

用途：學 SQL syntax。請不要隨便對 project tables 寫入，除非你知道 Entity constraints。

```sql
INSERT INTO some_table (column_a, column_b)
VALUES ('value-a', 'value-b');
```

MindBridge 建議透過 app API 建資料，避免破壞 JPA relationship。

### 更新資料語法

用途：理解 SQL。操作前一定先 SELECT 確認。

```sql
UPDATE some_table
SET column_a = 'new-value'
WHERE id = 1;
```

### 刪除資料語法

用途：理解 SQL。非常危險，先備份。

```sql
DELETE FROM some_table
WHERE id = 1;
```

學習時優先用 Docker volume reset，而不是手動 delete production-like data。

### 顯示 indexes

用途：看 table key/index。

```sql
SHOW INDEX FROM chat_sessions;
SHOW INDEX FROM user_accounts;
```

### 查看目前 MySQL connection

用途：確認使用者、database。

```sql
SELECT USER(), DATABASE(), VERSION();
```

### 從 host 測 MySQL port

用途：確認 container port exposed。

PowerShell：

```powershell
Test-NetConnection localhost -Port 3306
```

### JDBC URL syntax

用途：理解 Spring Boot DB connection string。

```text
jdbc:mysql://host:port/database?param=value&param2=value2
```

repo-specific：

```text
jdbc:mysql://mysql:3306/mindbridge?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
```

### Spring Boot MySQL environment variables

用途：覆蓋 datasource。

PowerShell：

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/mindbridge?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
$env:DB_USERNAME="mindbridge"
$env:DB_PASSWORD="mindbridge"
mvn spring-boot:run "-Dspring-boot.run.profiles=mysql"
```

## 官方參考

- MySQL Connector/J JDBC URL: <https://dev.mysql.com/doc/connector-j/en/connector-j-reference-jdbc-url-format.html>
- Spring Data JPA Query Methods: <https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html>
