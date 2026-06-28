# HTML 學習筆記

這份文件聚焦 MindBridge frontend 的 HTML 結構。主要檔案：

```text
src/main/resources/static/index.html
```

Spring Boot 會自動 serve static files，所以 browser 打開：

```text
http://localhost:8080/
```

就會看到這個 HTML 頁面。

## index.html 的角色

`index.html` 是 UI skeleton。它不負責 business logic，而是提供 DOM 結構，讓：

- `styles.css` 負責視覺與 layout
- `app.js` 負責互動、API request、資料渲染

重要引用：

```html
<link rel="stylesheet" href="/styles.css?v=20260601-admin-restore">
<link rel="icon" href="/favicon.svg" type="image/svg+xml">
<script src="/app.js?v=20260531-role-ui"></script>
```

## 頁面大結構

最外層：

```html
<main class="shell">
  <section class="workspace">
    <aside class="side">...</aside>
    <section id="mainPanel" class="chat">...</section>
  </section>
</main>
```

用途：

- `shell`：整個 app 的外框
- `workspace`：左右 layout
- `side`：登入、帳號、prompt、reports
- `chat`：聊天區或 admin dashboard

## Login form

登入區：

```html
<form id="loginForm" class="panel">
  <input id="username" name="username" value="student" autocomplete="username">
  <input id="password" name="password" value="student123" type="password">
  <button type="submit" class="primary-action">...</button>
</form>
```

`app.js` 會用：

```js
els.loginForm.addEventListener("submit", login);
```

讓 submit 事件呼叫 `login()`。

## Chat form

聊天輸入區：

```html
<form id="chatForm" class="composer">
  <textarea id="messageInput" rows="3"></textarea>
  <button id="sendButton" type="submit" class="send-action">...</button>
</form>
```

`app.js` 會用：

```js
els.chatForm.addEventListener("submit", sendMessage);
```

送出後呼叫：

```text
POST /api/chat/stream
```

## Admin dashboard

Admin dashboard 在 HTML 中預先存在，但加了 `hidden`：

```html
<section id="adminDashboard" class="admin-dashboard" hidden>
```

登入後 `app.js` 讀 `/api/profile`，如果 roles 裡有 `ROLE_ADMIN`，就顯示 dashboard，隱藏 chat form。

dashboard 內有：

- `adminStats`
- `knowledgeUploadForm`
- `adminCharts`
- `adminReportRows`
- `excelRows`
- `emailRows`

這些都是 empty containers，實際內容由 JavaScript 動態塞入。

## File upload form

知識庫上傳：

```html
<form id="knowledgeUploadForm" class="knowledge-upload">
  <input id="knowledgeFile" type="file" accept=".pdf,.md,.markdown,.txt,application/pdf,text/markdown,text/plain">
  <button type="submit" class="ghost">...</button>
</form>
```

`app.js` 使用 `FormData` 上傳到：

```text
POST /api/admin/knowledge/file
```

這個 endpoint 需要 admin account。

## Modal / Overlay

完整對話檢視：

```html
<section id="conversationOverlay" class="conversation-overlay" hidden>
  <div class="conversation-panel" role="dialog" aria-modal="true" aria-labelledby="conversationTitle">
```

重要 attributes：

- `hidden`：預設隱藏
- `role="dialog"`：告訴 assistive technology 這是 dialog
- `aria-modal="true"`：表示 modal 開啟時焦點應屬於 modal
- `aria-labelledby="conversationTitle"`：dialog 名稱來自 `conversationTitle`

## 常用 commands and syntax

### HTML document 基本骨架

用途：每個 HTML page 的基本起點。

```html
<!doctype html>
<html lang="zh-Hant">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>MindBridge</title>
</head>
<body>
  <main></main>
</body>
</html>
```

本專案目前 `index.html` 使用 `lang="zh-CN"`，如果文件與 UI 要改為繁中，可改成 `zh-Hant`，但這次 docs task 不修改 runtime UI。

### 載入 CSS

用途：讓 HTML 套用 stylesheet。

```html
<link rel="stylesheet" href="/styles.css">
```

repo-specific：

```html
<link rel="stylesheet" href="/styles.css?v=20260601-admin-restore">
```

`?v=...` 是 cache busting，讓 browser 更容易拿到新版 CSS。

### 載入 JavaScript

用途：載入 frontend behavior。

```html
<script src="/app.js"></script>
```

repo-specific：

```html
<script src="/app.js?v=20260531-role-ui"></script>
```

通常放在 `</body>` 前，確保 DOM 已經存在。

### section / aside / main

用途：建立語意化 layout。

```html
<main class="shell">
  <aside class="side"></aside>
  <section class="chat"></section>
</main>
```

在 MindBridge：

- `aside.side` 是左側登入與工具欄
- `section.chat` 是主工作區

### form

用途：處理登入、chat submit、file upload。

```html
<form id="chatForm" class="composer">
  <textarea id="messageInput"></textarea>
  <button type="submit">Send</button>
</form>
```

JavaScript 會攔截 submit：

```js
event.preventDefault();
```

避免 browser 預設整頁 reload。

### input text

用途：一般文字輸入。

```html
<input id="username" name="username" autocomplete="username">
```

常用 attributes：

- `id`：給 CSS/JS 找元素
- `name`：表單欄位名稱
- `value`：預設值
- `autocomplete`：讓 browser 知道欄位用途

### input password

用途：密碼輸入。

```html
<input id="password" name="password" type="password" autocomplete="current-password">
```

`type="password"` 會遮住輸入文字。

### textarea

用途：多行輸入，MindBridge 用於 chat message。

```html
<textarea id="messageInput" rows="3" placeholder="輸入訊息"></textarea>
```

### button

用途：表單送出或 UI action。

```html
<button type="submit">Submit</button>
<button type="button">Open</button>
```

差異：

- `type="submit"`：在 form 裡會觸發 submit
- `type="button"`：只是普通 button，要由 JS 綁 click

MindBridge prompt chips：

```html
<button type="button" class="prompt-chip" data-prompt="...">...</button>
```

### data-* attributes

用途：把少量資料掛在 HTML element 上，給 JavaScript 讀。

```html
<button data-prompt="I feel anxious">Prompt</button>
```

JavaScript：

```js
button.dataset.prompt
```

MindBridge 用於：

- `data-prompt`
- `data-admin-refresh`
- `data-scroll-target`
- `data-copy-message`
- `data-follow-up`

### hidden attribute

用途：控制初始顯示狀態。

```html
<section id="adminDashboard" hidden></section>
```

JavaScript 可切換：

```js
els.adminDashboard.hidden = false;
```

CSS 裡也有：

```css
[hidden] {
  display: none !important;
}
```

### img static asset

用途：載入圖片。

```html
<img src="/assets/mindbridge-campus-companion.png" alt="">
```

如果圖片有資訊意義，`alt` 應寫描述；如果只是裝飾，`alt=""` 可以避免 screen reader 重複朗讀。

### aria-live

用途：讓 assistive technology 知道這區內容會更新。

```html
<div id="messages" class="messages" aria-live="polite"></div>
```

MindBridge chat tokens 會動態加入這個區域。

### dialog accessibility attributes

用途：讓 modal 更容易被理解。

```html
<div role="dialog" aria-modal="true" aria-labelledby="conversationTitle">
```

常見搭配：

- `role="dialog"`
- `aria-modal="true"`
- `aria-labelledby="someHeadingId"`

### file input

用途：選擇 PDF/Markdown/txt 上傳。

```html
<input id="knowledgeFile" type="file" accept=".pdf,.md,.markdown,.txt,application/pdf,text/markdown,text/plain">
```

`accept` 只提示 browser 可選格式，不是安全驗證；backend 還是要處理檔案內容。

### HTML entity

用途：顯示特殊字元。

```html
&lt;div&gt; 代表 <div>
&amp; 代表 &
```

MindBridge 的 assistant content 不是直接塞 raw HTML，而是先 escape，再把簡單 Markdown 轉成 `<strong>` / `<br>`。

### 檢查 static page

用途：確認 HTML 被 Spring Boot serve。

```bash
curl http://localhost:8080/
```

### 在 browser 檢查元素

用途：debug HTML 結構。

```text
Right click -> Inspect
Elements tab -> 搜尋 id/class，例如 #chatForm 或 .admin-dashboard
```

### 查 HTML 中所有 id

用途：確認 `app.js` 用的 selectors 是否存在。

PowerShell：

```powershell
Select-String -Path src\main\resources\static\index.html -Pattern 'id="'
```

## 官方參考

- MDN HTML basics: <https://developer.mozilla.org/en-US/docs/Learn/HTML/Introduction_to_HTML>
- MDN forms: <https://developer.mozilla.org/en-US/docs/Learn/Forms>
