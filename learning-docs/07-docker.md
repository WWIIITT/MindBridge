# Docker 學習筆記

這份文件聚焦 MindBridge 的 Docker 設定。相關檔案：

```text
Dockerfile
docker-compose.yml
.dockerignore
```

## Docker 在專案中的角色

Docker 讓你用 containers 跑：

- Spring Boot app
- MySQL
- Redis
- Chroma
- Mailpit

這樣每個人本機可以有接近一致的 runtime environment。

## Dockerfile

MindBridge 使用 multi-stage build：

```dockerfile
FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -DskipTests package

FROM eclipse-temurin:17-jre

WORKDIR /app
COPY --from=build /workspace/target/mindbridge-agent-0.1.0.jar /app/mindbridge-agent.jar

ENV SERVER_PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/mindbridge-agent.jar"]
```

第一階段：

- 用 Maven image
- compile/package Java app
- 產生 jar

第二階段：

- 用比較小的 JRE image
- 只複製 jar
- 用 `java -jar` 啟動

好處：final image 不需要包含完整 Maven build tools。

## docker-compose.yml

Compose services：

```yaml
services:
  app:
  mysql:
  redis:
  chroma:
  mailpit:
```

`app` depends on：

```yaml
depends_on:
  - mysql
  - redis
  - chroma
```

注意：`depends_on` 代表啟動順序，不保證 MySQL 已完全 ready；Spring Boot 仍可能需要等 DB ready 或重試。

## Service-name networking

Compose 會建立 default network。Container 之間可以用 service name 連線：

```text
app -> mysql:3306
app -> redis:6379
app -> chroma:8000
app -> mailpit:1025
```

這就是為什麼 app container 的 `DB_URL` 是：

```text
jdbc:mysql://mysql:3306/mindbridge
```

而不是：

```text
jdbc:mysql://localhost:3306/mindbridge
```

在 container 內，`localhost` 指的是 app container 自己。

## Ports

Compose ports：

```yaml
ports:
  - "8080:8080"
```

意思：

```text
host port 8080 -> container port 8080
```

本專案 ports：

- app: `8080`
- MySQL: `3306`
- Redis: `6379`
- Chroma: `8000`
- Mailpit SMTP: `1025`
- Mailpit UI: `8025`

## Volumes

Compose volumes：

```yaml
volumes:
  mysql-data:
  redis-data:
  chroma-data:
```

用途：container 刪掉後資料仍保留。

app 也掛載：

```yaml
volumes:
  - ./data:/app/data
```

用途：讓 local `data/` 和 container `/app/data` 同步，例如 local Excel output。

## Environment variables

`app` service 透過 environment 設定 Spring Boot：

```yaml
SPRING_PROFILES_ACTIVE: mysql
DB_URL: jdbc:mysql://mysql:3306/mindbridge?...
DB_USERNAME: mindbridge
DB_PASSWORD: mindbridge
REDIS_HOST: redis
REDIS_PORT: 6379
USE_CHROMA: ${USE_CHROMA:-true}
CHROMA_BASE_URL: http://chroma:8000
MCP_EMAIL_MODE: ${MCP_EMAIL_MODE:-log}
```

`${AI_PROVIDER:-ollama}` 是 shell-style default：

```text
如果 host 有 AI_PROVIDER 就用它，否則用 ollama
```

## 常用 commands and syntax

### 查看 Docker Compose config

用途：檢查 compose 解析後的設定。

```bash
docker compose config
```

### 啟動所有 services

用途：build app image 並啟動整套系統。

```bash
docker compose up -d
```

repo-specific：

```bash
cd D:\GitHub\MindBridge
docker compose up -d
```

### 啟動指定 services

用途：只啟動 infrastructure，app 用 Maven 在本機跑。

```bash
docker compose up -d mysql redis chroma mailpit
```

### build app image

用途：重新建立 `mindbridge-agent:0.1.0` image。

```bash
docker compose build app
```

如果不想用 cache：

```bash
docker compose build --no-cache app
```

### 重新 build 並啟動 app

用途：Java code 或 resources 改過後更新 container。

```bash
docker compose up -d --build app
```

### 查看 running services

用途：看狀態與 port mapping。

```bash
docker compose ps
```

### 查看 logs

用途：debug startup / connection errors。

```bash
docker compose logs app
docker compose logs mysql
docker compose logs redis
```

即時追 log：

```bash
docker compose logs -f app
```

顯示最後 100 行：

```bash
docker compose logs --tail=100 app
```

### 進入 app container

用途：檢查 container 內檔案或環境變數。

```bash
docker compose exec app sh
```

### 查看 app container 環境變數

用途：確認 `SPRING_PROFILES_ACTIVE`、`DB_URL` 等。

```bash
docker compose exec app env
```

可以搭配 grep：

```bash
docker compose exec app sh -c "env | grep -E 'SPRING|DB_|REDIS|CHROMA|AI_PROVIDER'"
```

### 進入 MySQL

用途：直接查 DB。

```bash
docker compose exec mysql mysql -umindbridge -pmindbridge mindbridge
```

### 進入 Redis

用途：查 short-term memory。

```bash
docker compose exec redis redis-cli
```

### 測 app health

用途：確認 host 可以打到 app container。

```bash
curl http://localhost:8080/actuator/health
```

### 測 Mailpit UI

用途：查看 local SMTP 測試信。

```text
http://localhost:8025
```

### 停止 services

用途：停止並刪除 containers，但保留 volumes。

```bash
docker compose down
```

### 停止並刪除 volumes

用途：重置 MySQL/Redis/Chroma local data。危險。

```bash
docker compose down -v
```

使用前確認你不需要 local demo data。

### 只重啟 app

用途：DB/cache 不重啟，只重跑 backend。

```bash
docker compose restart app
```

### 查看 images

用途：確認 app image 是否存在。

```bash
docker images
```

filter：

```bash
docker images mindbridge-agent
```

### 查看 containers

用途：看 container id/name/status。

```bash
docker ps
docker ps -a
```

### Inspect container

用途：看底層設定、network、mounts。

```bash
docker inspect mindbridge-agent
```

### 查看 volumes

用途：確認 named volumes。

```bash
docker volume ls
```

inspect：

```bash
docker volume inspect mindbridge_mysql-data
```

volume 名稱可能帶 project prefix，可先用 `docker volume ls` 確認實際名字。

### 查看 compose network

用途：debug container DNS/network。

```bash
docker network ls
docker network inspect mindbridge_default
```

network 名稱可能帶目錄 prefix，可先用 `docker network ls` 確認。

### host.docker.internal

用途：讓 app container 連 host 上的 Ollama。

Compose 設定：

```yaml
extra_hosts:
  - "host.docker.internal:host-gateway"
```

app env：

```yaml
OLLAMA_BASE_URL: ${OLLAMA_BASE_URL:-http://host.docker.internal:11434}
```

### 常見問題：app 連不到 MySQL

檢查 1：MySQL container 是否 running。

```bash
docker compose ps mysql
```

檢查 2：app env 是否使用 container hostname。

```bash
docker compose exec app env | grep DB_URL
```

應該看到：

```text
jdbc:mysql://mysql:3306/mindbridge
```

檢查 3：MySQL logs。

```bash
docker compose logs --tail=100 mysql
```

### 常見問題：改 Java 後 container 沒變

原因：需要 rebuild image。

```bash
docker compose up -d --build app
```

### 常見問題：想完全重置 local data

用途：回到乾淨 demo 狀態。

```bash
docker compose down -v
docker compose up -d
```

這會刪除 MySQL/Redis/Chroma volumes。

### 常見問題：port 被占用

檢查 Windows port：

```powershell
Get-NetTCPConnection -LocalPort 8080
```

如果 `8080` 被占用，可以先停掉既有 process，或修改 compose port mapping，例如：

```yaml
ports:
  - "8081:8080"
```

然後 browser 開：

```text
http://localhost:8081
```

## 官方參考

- Docker Compose services: <https://docs.docker.com/reference/compose-file/services/>
- Docker Compose CLI: <https://docs.docker.com/reference/cli/docker/compose/>
- Spring Boot Dockerfiles: <https://docs.spring.io/spring-boot/reference/packaging/container-images/dockerfiles.html>
