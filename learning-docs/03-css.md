# CSS 學習筆記

這份文件聚焦 MindBridge frontend 的 CSS。主要檔案：

```text
src/main/resources/static/styles.css
```

CSS 負責：

- layout
- spacing
- colors
- responsive design
- hover/focus states
- admin dashboard 視覺
- chat bubbles

## CSS 在 MindBridge 的工作方式

HTML 提供 class：

```html
<main class="shell">
<section class="workspace">
<aside class="side">
<section id="mainPanel" class="chat">
```

CSS 用 selector 找到它們：

```css
.workspace {
  display: grid;
  grid-template-columns: minmax(320px, 380px) minmax(0, 1fr);
  gap: 18px;
}
```

JavaScript 會切換 class 或 hidden state，例如：

```js
document.body.classList.add("admin-view");
els.mainPanel.classList.add("admin-mode");
```

CSS 再根據這些 state 改 layout：

```css
body.admin-view .brand-card .brand-visual {
  display: none;
}
```

## CSS variables

檔案開頭定義 design tokens：

```css
:root {
  --ink: #18252b;
  --muted: #65737a;
  --line: #dce7e4;
  --accent: #237b71;
  --risk: #a13d57;
}
```

使用方式：

```css
color: var(--ink);
background: var(--accent);
```

好處：同一個顏色只定義一次，整個 UI 風格比較一致。

## Layout pattern

主要 layout 是 CSS Grid：

```css
.workspace {
  display: grid;
  grid-template-columns: minmax(320px, 380px) minmax(0, 1fr);
}
```

意思：

- 左側 sidebar 寬度在 `320px` 到 `380px` 間
- 右側主區域吃剩餘空間

sidebar 內部常用 Flex：

```css
.side {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
```

## Chat bubbles

assistant bubble：

```css
.bubble.assistant {
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid var(--line);
}
```

user bubble：

```css
.bubble.user {
  margin-left: auto;
  background: linear-gradient(135deg, #237b71, #1f6e84);
  color: #fff;
}
```

`margin-left: auto` 讓 user message 靠右。

## Admin dashboard

Admin dashboard 使用多個 grid 區塊：

```css
.admin-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}
```

mobile 時改成一欄：

```css
@media (max-width: 820px) {
  .admin-stats,
  .admin-visuals,
  .admin-sections {
    grid-template-columns: 1fr;
  }
}
```

## State classes

MindBridge 用 `.ok`、`.warn`、`.danger` 代表狀態：

```css
.status-pill.ok {
  background: var(--accent-soft);
  color: var(--accent-dark);
}

.status-pill.danger {
  background: var(--risk-soft);
  color: var(--risk);
}
```

JavaScript 會切 class：

```js
element.classList.remove("ok", "warn", "danger");
element.classList.add(tone);
```

## 常用 commands and syntax

### Selector: class

用途：選到所有 `class="panel"` 的 elements。

```css
.panel {
  padding: 16px;
}
```

repo-specific：

```css
.reports-panel {
  display: grid;
}
```

### Selector: id

用途：選到單一 id element。

```css
#messages {
  overflow: auto;
}
```

本專案多數 styling 用 class，id 多給 JavaScript 使用。

### Selector: element

用途：選所有同類 tag。

```css
button {
  border: 0;
  border-radius: 8px;
}
```

### Selector: combined class

用途：只選同時有兩個 class 的 element。

```css
.bubble.user {
  margin-left: auto;
}
```

意思：選 `class="bubble user"`。

### Selector: descendant

用途：選某元素裡面的子孫。

```css
.brand-visual img {
  width: 100%;
  height: 100%;
}
```

### Selector: attribute

用途：選有某 attribute 的 element。

```css
[hidden] {
  display: none !important;
}
```

MindBridge 用這個確保 HTML `hidden` 一定隱藏。

### CSS variable syntax

用途：集中管理顏色與陰影。

```css
:root {
  --accent: #237b71;
}

button {
  background: var(--accent);
}
```

### Box sizing

用途：讓 width/height 包含 padding 和 border，比較好算 layout。

```css
* {
  box-sizing: border-box;
}
```

### Grid container

用途：建立二維 layout。

```css
.workspace {
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: 18px;
}
```

常見語法：

```css
grid-template-columns: repeat(4, minmax(0, 1fr));
grid-template-rows: auto minmax(0, 1fr) auto;
```

### Flex container

用途：一維排列，例如 sidebar 垂直堆疊。

```css
.side {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
```

常見語法：

```css
align-items: center;
justify-content: space-between;
```

### Spacing

用途：控制內外距。

```css
.panel {
  padding: 16px;
}

.report {
  margin-bottom: 10px;
}
```

差異：

- `padding`：元素裡面的空間
- `margin`：元素外面的空間
- `gap`：grid/flex child 之間的距離

### Border radius

用途：圓角。

```css
border-radius: 8px;
```

本專案大多使用 `8px`，讓 cards/buttons/bubbles 保持一致。

### Background

用途：背景色或漸層。

```css
background: var(--soft);
background: linear-gradient(135deg, #237b71, #1f6e84);
```

### Hover state

用途：滑鼠移過時的互動感。

```css
button:hover {
  background: var(--accent-dark);
  transform: translateY(-1px);
}
```

### Focus state

用途：keyboard navigation 和可及性。

```css
input:focus,
textarea:focus {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px rgba(47, 125, 117, 0.14);
}
```

### Disabled state

用途：按鈕不可用時的樣式。

```css
button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}
```

MindBridge 送訊息時會：

```js
els.sendButton.disabled = true;
```

### Text overflow

用途：避免長文字撐爆 layout。

```css
.admin-row-title {
  overflow-wrap: anywhere;
}
```

### Scroll area

用途：讓 messages/admin table 可以滾動。

```css
.messages {
  overflow: auto;
}
```

常搭配：

```css
min-height: 0;
```

在 grid/flex child 裡，`min-height: 0` 常用來允許內部 scroll。

### Responsive media query

用途：小螢幕改 layout。

```css
@media (max-width: 820px) {
  .workspace {
    grid-template-columns: 1fr;
  }
}
```

### 檢查 CSS 是否被載入

用途：確認 static asset 正常。

```bash
curl http://localhost:8080/styles.css
```

### 在 browser debug CSS

用途：找出哪個 selector 影響元素。

```text
Right click -> Inspect -> Elements -> Styles
```

常做：

- toggle class
- 修改 CSS value 試 layout
- 看 computed styles
- 開 mobile viewport

### 搜尋某個 class 定義

用途：快速找到 CSS 位置。

```powershell
Select-String -Path src\main\resources\static\styles.css -Pattern ".admin-dashboard"
```

### 搜尋 CSS variables

用途：看設計 token。

```powershell
Select-String -Path src\main\resources\static\styles.css -Pattern "--accent|--risk|--muted"
```

## 官方參考

- MDN CSS basics: <https://developer.mozilla.org/en-US/docs/Learn/CSS>
- MDN CSS Grid: <https://developer.mozilla.org/en-US/docs/Web/CSS/CSS_grid_layout>
- MDN Flexbox: <https://developer.mozilla.org/en-US/docs/Web/CSS/CSS_flexible_box_layout>
