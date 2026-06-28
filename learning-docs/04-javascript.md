# JavaScript 學習筆記

這份文件聚焦 MindBridge frontend 的 JavaScript。主要檔案：

```text
src/main/resources/static/app.js
```

它負責：

- login state
- DOM references
- HTTP requests
- Basic Auth
- chat SSE streaming
- admin dashboard data loading
- file upload
- UI render/update
- error handling

## state object

檔案開頭：

```js
const state = {
  auth: {
    username: "student",
    password: "student123"
  },
  sessionId: null,
  sending: false,
  modelName: "mindbridge-qwen2.5-7b-ft:latest",
  isAdmin: false
};
```

這是 frontend 的 in-memory state，不會永久保存。重新整理頁面後會回到預設 demo account。

## DOM references

`els` 集中存 DOM elements：

```js
const els = {
  loginForm: document.querySelector("#loginForm"),
  username: document.querySelector("#username"),
  chatForm: document.querySelector("#chatForm"),
  messageInput: document.querySelector("#messageInput")
};
```

好處是後面不用一直重複 `document.querySelector(...)`。

## Basic Auth

MindBridge frontend 用 demo username/password 產生 `Authorization` header：

```js
function authHeader() {
  const token = btoa(`${state.auth.username}:${state.auth.password}`);
  return `Basic ${token}`;
}
```

所有 `/api/*` request 都透過：

```js
async function api(path, options = {}) {
  const headers = {
    Authorization: authHeader(),
    ...(options.headers || {})
  };
  const response = await fetch(path, { ...options, headers });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `${response.status} ${response.statusText}`);
  }
  return response;
}
```

## Login flow

`login()` 做：

```text
讀 form username/password
  -> GET /api/profile
  -> GET /api/agent/status
  -> 如果 admin，載入 dashboard data
  -> 如果 student，顯示 chat UI
```

重要 endpoint：

```text
GET /api/profile
GET /api/agent/status
```

## Chat stream flow

送訊息：

```js
const response = await api("/api/chat/stream", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ sessionId: state.sessionId, message })
});
```

讀 stream：

```js
const reader = response.body.getReader();
const decoder = new TextDecoder();
```

迴圈讀 chunk：

```js
while (true) {
  const { value, done } = await reader.read();
  if (done) break;
  buffer += decoder.decode(value, { stream: true });
  buffer = parseSse(buffer, onEvent);
}
```

MindBridge SSE event type：

- `meta`：拿到 `sessionId`
- `token`：追加 assistant response
- `error`：顯示錯誤訊息
- `done`：backend 表示完成

## Admin dashboard

Admin dashboard 同時載入三類資料：

```js
const [reports, excelRecords, alerts] = await Promise.all([
  loadReports(),
  loadExcelRecords(),
  loadAlertRecords()
]);
```

對應 endpoints：

```text
GET /api/admin/reports
GET /api/admin/excel-records
GET /api/admin/alerts
```

`Promise.all` 讓三個 request 並行，dashboard 比依序載入更快。

## File upload

Knowledge upload 使用 `FormData`：

```js
const body = new FormData();
body.append("file", file);

await api("/api/admin/knowledge/file", {
  method: "POST",
  body
});
```

注意：使用 `FormData` 時不要手動設定 `Content-Type`，browser 會自動加 multipart boundary。

## 常用 commands and syntax

### 變數宣告

用途：宣告 value。

```js
const fixed = "cannot reassign";
let count = 0;
```

規則：

- `const`：變數不能重新指向其他值
- `let`：可以重新指定
- 避免使用 `var`

repo-specific：

```js
const state = { sending: false };
let buffer = "";
```

### Object syntax

用途：儲存 key-value。

```js
const user = {
  username: "student",
  password: "student123"
};
```

讀取：

```js
user.username
user["username"]
```

### Array syntax

用途：儲存 list。

```js
const roles = ["ROLE_USER", "ROLE_ADMIN"];
```

常用：

```js
roles.some((role) => role === "ROLE_ADMIN");
items.map((item) => item.name);
items.filter((item) => item.status === "SUCCESS");
```

### Template string

用途：組字串。

```js
const token = `${state.auth.username}:${state.auth.password}`;
```

repo-specific：

```js
return `Basic ${token}`;
```

### Function declaration

用途：定義可重用邏輯。

```js
function authHeader() {
  return "Basic ...";
}
```

### Arrow function

用途：常用於 callback。

```js
items.forEach((item) => {
  console.log(item);
});
```

repo-specific：

```js
reports.filter((item) => item.riskLevel === "HIGH");
```

### async / await

用途：寫非同步 request。

```js
async function loadProfile() {
  const response = await api("/api/profile");
  return response.json();
}
```

### try / catch / finally

用途：處理錯誤並恢復 UI state。

```js
try {
  await send();
} catch (error) {
  console.error(error);
} finally {
  state.sending = false;
}
```

MindBridge 在 `sendMessage()` 裡用 `finally` 重新 enable send button。

### DOM query

用途：找到 HTML element。

```js
document.querySelector("#chatForm");
document.querySelector(".reports-panel");
```

id 用 `#`，class 用 `.`。

### 修改 text

用途：更新畫面文字。

```js
els.serviceState.textContent = "服務正常";
```

### 修改 HTML

用途：插入 markup。要小心 XSS。

```js
element.innerHTML = "<strong>Hello</strong>";
```

MindBridge 對 assistant content 先做 `escapeHtml()`，再做簡單 markdown render。

### 建立 element

用途：動態 render list/card。

```js
const card = document.createElement("article");
card.className = "report";
card.textContent = "Report";
parent.append(card);
```

### classList

用途：切換 UI state。

```js
element.classList.add("danger");
element.classList.remove("ok", "warn");
element.classList.toggle("admin-view", true);
```

repo-specific：

```js
document.body.classList.add("admin-view");
els.mainPanel.classList.add("admin-mode");
```

### hidden property

用途：顯示/隱藏 element。

```js
els.adminDashboard.hidden = false;
els.chatForm.hidden = true;
```

### Event listener

用途：處理 click/submit/keydown。

```js
els.chatForm.addEventListener("submit", sendMessage);
```

submit handler 通常要：

```js
event.preventDefault();
```

### Event delegation

用途：在 document 上監聽 click，再判斷點到誰。

```js
document.addEventListener("click", (event) => {
  const chip = event.target.closest(".prompt-chip");
  if (!chip) return;
  usePrompt(chip.dataset.prompt);
});
```

適合動態生成的 elements。

### fetch GET

用途：呼叫 API。

```js
const response = await fetch("/api/profile", {
  headers: { Authorization: authHeader() }
});
const body = await response.json();
```

### fetch POST JSON

用途：送 JSON body。

```js
const response = await fetch("/api/chat/stream", {
  method: "POST",
  headers: {
    Authorization: authHeader(),
    "Content-Type": "application/json"
  },
  body: JSON.stringify({ message: "hello" })
});
```

### JSON.stringify / response.json

用途：JavaScript object 和 JSON string 互轉。

```js
JSON.stringify({ message: "hello" });
const data = await response.json();
```

### Basic Auth header

用途：呼叫 Spring Security protected APIs。

```js
const token = btoa("student:student123");
const header = `Basic ${token}`;
```

### FormData

用途：上傳 file。

```js
const body = new FormData();
body.append("file", file);
await fetch("/api/admin/knowledge/file", {
  method: "POST",
  headers: { Authorization: authHeader() },
  body
});
```

### ReadableStream

用途：讀 streaming response。

```js
const reader = response.body.getReader();
const decoder = new TextDecoder();
const { value, done } = await reader.read();
```

MindBridge 用於 `SSE` token streaming。

### SSE parsing

用途：把 server 回傳的 chunks 切成 events。

```js
const blocks = buffer.split("\n\n");
const rest = blocks.pop() || "";
```

SSE 常見格式：

```text
event: token
data: {"type":"token","content":"hello"}
```

MindBridge parser 主要讀 `data:` line。

### Promise.all

用途：並行多個 async operations。

```js
const [reports, excelRecords, alerts] = await Promise.all([
  loadReports(),
  loadExcelRecords(),
  loadAlertRecords()
]);
```

### Browser console debug

用途：在 DevTools 測 selector 或 state。

```js
document.querySelector("#chatForm")
document.querySelectorAll(".prompt-chip").length
```

### Network tab debug

用途：檢查 API request/response。

```text
DevTools -> Network -> Fetch/XHR -> 點 /api/profile 或 /api/chat/stream
```

要看：

- status code
- request headers
- response body
- timing

### 檢查 app.js 是否被 serve

用途：確認 Spring Boot static resource。

```bash
curl http://localhost:8080/app.js
```

### 搜尋 endpoint 使用位置

用途：找 frontend 呼叫哪些 API。

```powershell
Select-String -Path src\main\resources\static\app.js -Pattern "/api/"
```

## 官方參考

- MDN Fetch API: <https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API/Using_Fetch>
- MDN Server-Sent Events: <https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events>
- MDN FormData: <https://developer.mozilla.org/en-US/docs/Web/API/FormData>
