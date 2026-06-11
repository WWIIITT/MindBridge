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

const els = {
  mainPanel: document.querySelector("#mainPanel"),
  serviceState: document.querySelector("#serviceState"),
  modelState: document.querySelector("#modelState"),
  loginForm: document.querySelector("#loginForm"),
  username: document.querySelector("#username"),
  password: document.querySelector("#password"),
  loginState: document.querySelector("#loginState"),
  accountPanel: document.querySelector("#accountPanel"),
  activeAccount: document.querySelector("#activeAccount"),
  activeRole: document.querySelector("#activeRole"),
  switchAccount: document.querySelector("#switchAccount"),
  studentCompanionPanel: document.querySelector("#studentCompanionPanel"),
  adminSidePanel: document.querySelector("#adminSidePanel"),
  sideHighRisk: document.querySelector("#sideHighRisk"),
  sideMailFailed: document.querySelector("#sideMailFailed"),
  sideReports: document.querySelector("#sideReports"),
  chatHead: document.querySelector("#chatHead"),
  profileText: document.querySelector("#profileText"),
  sessionBadge: document.querySelector("#sessionBadge"),
  newSessionButton: document.querySelector("#newSessionButton"),
  messages: document.querySelector("#messages"),
  chatForm: document.querySelector("#chatForm"),
  messageInput: document.querySelector("#messageInput"),
  sendButton: document.querySelector("#sendButton"),
  reportsPanel: document.querySelector(".reports-panel"),
  reportsTitle: document.querySelector("#reportsTitle"),
  reportsCaption: document.querySelector("#reportsCaption"),
  reports: document.querySelector("#reports"),
  refreshReports: document.querySelector("#refreshReports"),
  adminDashboard: document.querySelector("#adminDashboard"),
  adminStats: document.querySelector("#adminStats"),
  adminCharts: document.querySelector("#adminCharts"),
  adminReportRows: document.querySelector("#adminReportRows"),
  excelRows: document.querySelector("#excelRows"),
  emailRows: document.querySelector("#emailRows"),
  adminRefresh: document.querySelector("#adminRefresh"),
  knowledgeUploadForm: document.querySelector("#knowledgeUploadForm"),
  knowledgeFile: document.querySelector("#knowledgeFile"),
  knowledgeUploadState: document.querySelector("#knowledgeUploadState"),
  conversationOverlay: document.querySelector("#conversationOverlay"),
  conversationKicker: document.querySelector("#conversationKicker"),
  conversationTitle: document.querySelector("#conversationTitle"),
  conversationMeta: document.querySelector("#conversationMeta"),
  conversationMessages: document.querySelector("#conversationMessages"),
  closeConversation: document.querySelector("#closeConversation")
};

function authHeader() {
  const token = btoa(`${state.auth.username}:${state.auth.password}`);
  return `Basic ${token}`;
}

function setTone(element, tone) {
  element.classList.remove("ok", "warn", "danger");
  if (tone) {
    element.classList.add(tone);
  }
}

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

function setService(text, ok = true) {
  els.serviceState.textContent = text;
  setTone(els.serviceState, ok ? "ok" : "danger");
}

function setModel(status) {
  state.modelName = status.model || state.modelName;
  const modelLabel = displayModelName(state.modelName);
  const label = status.realModelEnabled
    ? `${status.provider} · ${modelLabel}`
    : "mock 演示 · 未接入大模型";
  els.modelState.textContent = label;
  setTone(els.modelState, status.realModelEnabled ? "ok" : "warn");
}

function setLogin(text, ok = true) {
  els.loginState.textContent = text;
  setTone(els.loginState, ok ? "ok" : "danger");
}

function showLoginForm() {
  state.isAdmin = false;
  document.body.classList.remove("admin-view");
  els.loginForm.hidden = false;
  els.accountPanel.hidden = true;
  els.studentCompanionPanel.hidden = false;
  els.adminSidePanel.hidden = true;
  els.reportsPanel.hidden = true;
  els.reports.innerHTML = "";
  els.adminDashboard.hidden = true;
  els.chatHead.hidden = false;
  els.messages.hidden = false;
  els.chatForm.hidden = false;
  els.mainPanel.classList.remove("admin-mode");
  closeConversation();
}

function isAdminProfile(profile) {
  return profile.roles?.some((role) => role.authority === "ROLE_ADMIN");
}

function accountName(profile) {
  return isAdminProfile(profile) ? profile.displayName : profile.username;
}

function showAccountPanel(profile) {
  const isAdmin = isAdminProfile(profile);
  els.activeAccount.textContent = accountName(profile);
  els.activeRole.textContent = isAdmin ? "管理員賬號" : "學生賬號";
  els.loginForm.hidden = true;
  els.accountPanel.hidden = false;
}

function showEmpty() {
  if (!els.messages.children.length) {
    const empty = document.createElement("section");
    empty.className = "empty";
    empty.innerHTML = `
      <div class="empty-visual">
        <img src="/assets/mindbridge-campus-companion.png" alt="">
      </div>
      <div class="empty-copy">
        <p class="eyebrow">MindBridge Companion</p>
        <h3>把今天的想法放在這裏</h3>
        <p>可以聊學習計劃、概念理解、校園生活，也可以把一團亂的心情慢慢拆開。</p>
      </div>
      <div class="empty-prompts">
        <button type="button" class="prompt-chip" data-prompt="幫我制定一個今晚兩小時的學習計劃">今晚學習計劃</button>
        <button type="button" class="prompt-chip" data-prompt="用容易理解的方式解釋一下 PCA 線性降維">解釋 PCA</button>
        <button type="button" class="prompt-chip" data-prompt="我最近有點焦慮，想先把原因寫清楚">梳理焦慮</button>
      </div>
    `;
    els.messages.append(empty);
  }
}

function showStudentChat(profile) {
  // 學生登錄後進入聊天工作區，後臺記錄和統計面板全部隱藏。
  state.isAdmin = false;
  document.body.classList.remove("admin-view");
  els.mainPanel.classList.remove("admin-mode");
  els.studentCompanionPanel.hidden = false;
  els.adminSidePanel.hidden = true;
  els.adminDashboard.hidden = true;
  els.chatHead.hidden = false;
  els.messages.hidden = false;
  els.chatForm.hidden = false;
  els.profileText.textContent = accountName(profile);
  showEmpty();
}

function showAdminDashboard(profile) {
  // 管理員只看後臺數據，不顯示學生聊天輸入框。
  state.isAdmin = true;
  document.body.classList.add("admin-view");
  els.mainPanel.classList.add("admin-mode");
  els.studentCompanionPanel.hidden = true;
  els.adminSidePanel.hidden = true;
  els.chatHead.hidden = true;
  els.messages.hidden = true;
  els.chatForm.hidden = true;
  els.adminDashboard.hidden = false;
  els.profileText.textContent = `${accountName(profile)} (${profile.username})`;
  els.adminStats.innerHTML = "";
  els.adminCharts.innerHTML = "";
  els.adminReportRows.innerHTML = "";
  els.excelRows.innerHTML = "";
  els.emailRows.innerHTML = "";
}

function clearEmpty() {
  const empty = els.messages.querySelector(".empty");
  if (empty) empty.remove();
}

function usePrompt(prompt) {
  if (!prompt || state.isAdmin) return;
  els.messageInput.value = prompt;
  els.messageInput.focus();
}

function addMessage(role, content = "") {
  clearEmpty();
  const bubble = document.createElement("div");
  bubble.className = `bubble ${role}`;

  const avatar = document.createElement("div");
  avatar.className = "bubble-avatar";
  avatar.textContent = role === "user" ? "你" : "AI";

  const body = document.createElement("div");
  body.className = "bubble-content";
  bubble.append(avatar, body);

  if (role === "assistant") {
    bubble.dataset.raw = content;
    renderAssistantContent(bubble);
  } else {
    body.textContent = content;
  }

  if (role === "assistant") {
    const actions = document.createElement("div");
    actions.className = "message-actions";
    actions.innerHTML = `
      <button type="button" data-copy-message>複製</button>
      <button type="button" data-follow-up="請繼續展開剛纔的回答，並給一個具體例子。">繼續展開</button>
      <button type="button" data-follow-up="請把剛纔的回答整理成更清晰的要點。">整理要點</button>
    `;
    bubble.append(actions);
  }

  els.messages.append(bubble);
  els.messages.scrollTop = els.messages.scrollHeight;
  return bubble;
}

function escapeHtml(value) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function renderAssistantContent(bubble) {
  const body = bubble.querySelector(".bubble-content");
  const raw = bubble.dataset.raw || "";
  body.innerHTML = escapeHtml(raw)
    .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
    .replace(/\n/g, "<br>");
}

function formatDate(value) {
  return value ? new Date(value).toLocaleString() : "";
}

function displayModelName(model) {
  if ((model || "").toLowerCase().includes("mindbridge-qwen2.5-7b-ft")) {
    return "微調後的 Qwen2.5-7B";
  }
  return model || "Qwen2.5-7B";
}

function assistantName() {
  return displayModelName(state.modelName);
}

function setSessionBadge(text, tone) {
  els.sessionBadge.textContent = text;
  els.sessionBadge.classList.toggle("high", tone === "high");
  els.sessionBadge.classList.toggle("medium", tone === "medium");
}

function renderReports(items) {
  els.reports.innerHTML = "";
  if (!items.length) {
    const empty = document.createElement("p");
    empty.className = "state";
    empty.textContent = "暫無報告";
    els.reports.append(empty);
    return;
  }

  items.forEach((item) => {
    const report = document.createElement("article");
    report.className = `report ${item.sessionId ? "is-clickable" : "is-disabled"}`;
    report.tabIndex = item.sessionId ? 0 : -1;
    report.setAttribute("role", item.sessionId ? "button" : "article");
    if (item.sessionId) {
      report.setAttribute("aria-label", `查看 ${item.username} 的完整對話`);
      report.addEventListener("click", () => openConversation(item));
      report.addEventListener("keydown", (event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          openConversation(item);
        }
      });
    }
    const createdAt = formatDate(item.createdAt);

    const top = document.createElement("div");
    top.className = "report-top";

    const title = document.createElement("div");
    title.className = "report-title";
    title.textContent = `${item.emotion} / ${item.intent}`;

    const badge = document.createElement("div");
    badge.className = `badge ${item.riskLevel === "HIGH" ? "high" : item.riskLevel === "MEDIUM" ? "medium" : ""}`;
    badge.textContent = item.riskLevel;

    const meta = document.createElement("div");
    meta.className = "report-meta";
    meta.textContent = createdAt;

    const summary = document.createElement("div");
    summary.className = "report-summary";
    summary.textContent = item.summary || "已記錄";

    const hint = document.createElement("div");
    hint.className = "report-hint";
    hint.textContent = item.sessionId ? "點擊查看完整對話" : "無會話記錄";

    top.append(title, badge);
    report.append(top, meta, summary, hint);
    els.reports.append(report);
  });
}

function statusTone(status) {
  if (status === "SUCCESS") return "ok";
  if (status === "FAILED" || status === "HIGH") return "danger";
  if (status === "PENDING" || status === "MEDIUM") return "warn";
  return "";
}

function statusLabel(status) {
  return status || "SKIPPED";
}

function metricCard(label, value, tone) {
  const card = document.createElement("article");
  card.className = `metric-card ${tone || ""}`;
  const number = document.createElement("strong");
  number.textContent = value;
  const text = document.createElement("span");
  text.textContent = label;
  card.append(number, text);
  return card;
}

function countBy(items, key) {
  return items.reduce((acc, item) => {
    const value = item[key] || "UNKNOWN";
    acc[value] = (acc[value] || 0) + 1;
    return acc;
  }, {});
}

function chartCard(title, rows) {
  const total = rows.reduce((sum, row) => sum + row.value, 0);
  const card = document.createElement("article");
  card.className = "chart-card";
  const heading = document.createElement("h3");
  heading.textContent = title;
  card.append(heading);

  rows.forEach((row) => {
    const percent = total ? Math.round((row.value / total) * 100) : 0;
    const line = document.createElement("div");
    line.className = "chart-row";

    const label = document.createElement("span");
    label.textContent = row.label;

    const track = document.createElement("div");
    track.className = "chart-track";
    const fill = document.createElement("div");
    fill.className = `chart-fill ${row.tone || ""}`;
    fill.style.width = `${percent}%`;
    track.append(fill);

    const value = document.createElement("strong");
    value.textContent = `${row.value}`;

    line.append(label, track, value);
    card.append(line);
  });
  return card;
}

function emptyTable(text) {
  const empty = document.createElement("p");
  empty.className = "empty-detail";
  empty.textContent = text;
  return empty;
}

function adminActionRow(title, status, statusClass, meta, summary, onClick) {
  const row = document.createElement("button");
  row.type = "button";
  row.className = "admin-row report-row-action";
  row.addEventListener("click", onClick);

  const main = document.createElement("div");
  main.className = "admin-row-main";

  const titleNode = document.createElement("strong");
  titleNode.className = "admin-row-title";
  titleNode.textContent = title;

  const badge = document.createElement("span");
  badge.className = `admin-status ${statusClass || ""}`;
  badge.textContent = status;

  const metaNode = document.createElement("div");
  metaNode.className = "admin-row-meta";
  metaNode.textContent = meta;

  const summaryNode = document.createElement("div");
  summaryNode.className = "admin-row-summary";
  summaryNode.textContent = summary ?? "點擊查看詳情";

  main.append(titleNode, badge);
  row.append(main, metaNode, summaryNode);
  return row;
}

function renderAdminStats(reports, excelRecords, alerts) {
  // 頂部指標卡用於快速判斷報告量、風險量和工具鏈運行情況。
  els.adminStats.innerHTML = "";
  const highCount = reports.filter((item) => item.riskLevel === "HIGH").length;
  const successEmails = alerts.filter((item) => item.status === "SUCCESS").length;
  const failedEmails = alerts.filter((item) => item.status === "FAILED").length;
  els.sideHighRisk.textContent = highCount;
  els.sideMailFailed.textContent = failedEmails;
  els.sideReports.textContent = reports.length;
  els.adminStats.append(
    metricCard("風險/諮詢報告", reports.length, ""),
    metricCard("高風險記錄", highCount, "danger"),
    metricCard("Excel 寫入", excelRecords.length, "ok"),
    metricCard("郵件成功", successEmails, "ok")
  );
}

function renderAdminCharts(reports, excelRecords, alerts) {
  // 圖表大屏完全由後臺接口數據計算，不在前端重新做風險判斷。
  els.adminCharts.innerHTML = "";
  const risk = countBy(reports, "riskLevel");
  const emotion = countBy(reports, "emotion");
  const intent = countBy(reports, "intent");
  const mail = countBy(alerts, "status");

  els.adminCharts.append(
    chartCard("風險等級", [
      { label: "LOW", value: risk.LOW || 0, tone: "ok" },
      { label: "MEDIUM", value: risk.MEDIUM || 0, tone: "warn" },
      { label: "HIGH", value: risk.HIGH || 0, tone: "danger" }
    ]),
    chartCard("情緒分佈", [
      { label: "NORMAL", value: emotion.NORMAL || 0, tone: "ok" },
      { label: "ANXIETY", value: emotion.ANXIETY || 0, tone: "warn" },
      { label: "DEPRESSED", value: emotion.DEPRESSED || 0, tone: "warn" },
      { label: "HIGH_RISK", value: emotion.HIGH_RISK || 0, tone: "danger" }
    ]),
    chartCard("意圖類型", [
      { label: "CHAT", value: intent.CHAT || 0, tone: "ok" },
      { label: "CONSULT", value: intent.CONSULT || 0, tone: "warn" },
      { label: "RISK", value: intent.RISK || 0, tone: "danger" }
    ]),
    chartCard("工具狀態", [
      { label: "Excel", value: excelRecords.length, tone: "ok" },
      { label: "郵件成功", value: mail.SUCCESS || 0, tone: "ok" },
      { label: "郵件失敗", value: mail.FAILED || 0, tone: "danger" }
    ])
  );
}

function renderAdminReportRows(reports) {
  els.adminReportRows.innerHTML = "";
  if (!reports.length) {
    els.adminReportRows.append(emptyTable("暫無對話記錄"));
    return;
  }

  reports.slice(0, 20).forEach((item) => {
    const row = adminActionRow(
      item.username,
      item.riskLevel,
      statusTone(item.riskLevel),
      `${item.emotion} / ${item.intent} · ${formatDate(item.createdAt)}`,
      item.summary || "點擊查看完整對話",
      () => openConversation(item)
    );
    els.adminReportRows.append(row);
  });
}

function renderExcelRows(records) {
  els.excelRows.innerHTML = "";
  if (!records.length) {
    els.excelRows.append(emptyTable("暫無 Excel 寫入數據"));
    return;
  }

  records.slice(0, 20).forEach((item) => {
    const row = adminActionRow(
      `#${item.reportId} · ${item.username}`,
      statusLabel(item.excelStatus),
      statusTone(item.excelStatus),
      `${item.emotion} / ${item.riskLevel} · ${formatDate(item.createdAt)}`,
      item.summary || item.content || "點擊查看 Excel 寫入詳情",
      () => openExcelRecord(item)
    );
    els.excelRows.append(row);
  });
}

function renderEmailRows(records) {
  els.emailRows.innerHTML = "";
  if (!records.length) {
    els.emailRows.append(emptyTable("暫無郵件發送記錄"));
    return;
  }

  records.slice(0, 20).forEach((item) => {
    const row = adminActionRow(
      `報告 #${item.reportId} · ${item.username}`,
      statusLabel(item.status),
      statusTone(item.status),
      `${item.recipient} · ${item.attempts} 次 · ${formatDate(item.updatedAt)}`,
      item.errorMessage || item.summary || "點擊查看郵件發送詳情",
      () => openEmailRecord(item)
    );
    els.emailRows.append(row);
  });
}

function renderAdminDashboard(reports, excelRecords, alerts) {
  renderAdminStats(reports, excelRecords, alerts);
  renderAdminCharts(reports, excelRecords, alerts);
  renderAdminReportRows(reports);
  renderExcelRows(excelRecords);
  renderEmailRows(alerts);
}

function startNewSession() {
  state.sessionId = null;
  els.messages.innerHTML = "";
  setSessionBadge("READY");
  showEmpty();
  els.messageInput.focus();
}

function roleLabel(role) {
  if (role === "USER") return "學生";
  if (role === "ASSISTANT") return assistantName();
  return "系統";
}

function detailItem(label, value, className) {
  const item = document.createElement("div");
  item.className = `record-detail-item ${className || ""}`;

  const key = document.createElement("span");
  key.textContent = label;

  const val = document.createElement("strong");
  val.textContent = value ?? "無";

  item.append(key, val);
  return item;
}

function renderRecordDetail({ kicker, title, meta, items, summary, conversationRecord }) {
  els.conversationOverlay.hidden = false;
  els.conversationKicker.textContent = kicker;
  els.conversationTitle.textContent = title;
  els.conversationMeta.textContent = meta;
  els.conversationMessages.innerHTML = "";

  const detail = document.createElement("section");
  detail.className = "record-detail";
  items.forEach((item) => {
    detail.append(detailItem(item.label, item.value, item.className));
  });

  if (summary) {
    const summaryBlock = document.createElement("article");
    summaryBlock.className = "record-detail-summary";
    const label = document.createElement("span");
    label.textContent = "內容摘要";
    const content = document.createElement("p");
    content.textContent = summary;
    summaryBlock.append(label, content);
    detail.append(summaryBlock);
  }

  if (conversationRecord?.sessionId) {
    const action = document.createElement("button");
    action.type = "button";
    action.className = "ghost detail-action";
    action.textContent = "查看完整對話";
    action.addEventListener("click", () => openConversation(conversationRecord));
    detail.append(action);
  }

  els.conversationMessages.append(detail);
}

function openExcelRecord(record) {
  renderRecordDetail({
    kicker: `Excel 寫入 · 報告 #${record.reportId}`,
    title: `${record.username} 的 Excel 寫入數據`,
    meta: "管理員視圖",
    conversationRecord: record,
    summary: record.summary || record.content,
    items: [
      { label: "學生賬號", value: record.username },
      { label: "報告編號", value: `#${record.reportId}` },
      { label: "寫入狀態", value: statusLabel(record.excelStatus), className: statusTone(record.excelStatus) },
      { label: "意圖類型", value: record.intent },
      { label: "情緒識別", value: record.emotion },
      { label: "風險等級", value: record.riskLevel, className: statusTone(record.riskLevel) },
      { label: "置信度", value: record.confidence },
      { label: "原始輸入", value: record.content },
      { label: "寫入時間", value: formatDate(record.createdAt) }
    ]
  });
}

function openEmailRecord(record) {
  renderRecordDetail({
    kicker: `郵件發送 · 報告 #${record.reportId}`,
    title: `${record.username} 的郵件發送記錄`,
    meta: "管理員視圖",
    conversationRecord: record,
    summary: record.errorMessage || record.summary,
    items: [
      { label: "學生賬號", value: record.username },
      { label: "報告編號", value: `#${record.reportId}` },
      { label: "收件人", value: record.recipient },
      { label: "發送狀態", value: statusLabel(record.status), className: statusTone(record.status) },
      { label: "嘗試次數", value: `${record.attempts} 次` },
      { label: "風險等級", value: record.riskLevel, className: statusTone(record.riskLevel) },
      { label: "創建時間", value: formatDate(record.createdAt) },
      { label: "更新時間", value: formatDate(record.updatedAt) }
    ]
  });
}

function renderConversation(conversation) {
  els.conversationKicker.textContent = `${conversation.username} · ${conversation.sessionId}`;
  els.conversationTitle.textContent = conversation.title || "對話記錄";
  els.conversationMeta.textContent = `${conversation.displayName} 與 ${assistantName()} 的完整對話`;
  els.conversationMessages.innerHTML = "";

  if (!conversation.messages.length) {
    const empty = document.createElement("p");
    empty.className = "empty-detail";
    empty.textContent = "這次會話還沒有消息。";
    els.conversationMessages.append(empty);
    return;
  }

  conversation.messages.forEach((message) => {
    const item = document.createElement("article");
    item.className = `conversation-message ${message.role.toLowerCase()}`;

    const top = document.createElement("div");
    top.className = "conversation-message-top";

    const role = document.createElement("strong");
    role.textContent = roleLabel(message.role);

    const time = document.createElement("span");
    time.textContent = formatDate(message.createdAt);

    const content = document.createElement("div");
    content.className = "conversation-message-content";
    content.textContent = message.content;

    top.append(role, time);
    item.append(top, content);
    els.conversationMessages.append(item);
  });
}

function showConversationLoading(report) {
  els.conversationOverlay.hidden = false;
  els.conversationKicker.textContent = `${report.username} · ${report.sessionId}`;
  els.conversationTitle.textContent = "正在讀取完整對話";
  els.conversationMeta.textContent = "管理員視圖";
  els.conversationMessages.innerHTML = "";
  const loading = document.createElement("p");
  loading.className = "empty-detail";
  loading.textContent = "加載中...";
  els.conversationMessages.append(loading);
}

async function openConversation(report) {
  if (!report.sessionId) return;
  showConversationLoading(report);
  try {
    const response = await api(`/api/admin/conversations/${encodeURIComponent(report.sessionId)}`);
    const conversation = await response.json();
    renderConversation(conversation);
  } catch (error) {
    els.conversationTitle.textContent = "讀取失敗";
    els.conversationMeta.textContent = "";
    els.conversationMessages.innerHTML = "";
    const failed = document.createElement("p");
    failed.className = "empty-detail";
    failed.textContent = "無法讀取這次對話，請確認管理員賬號仍然有效。";
    els.conversationMessages.append(failed);
  }
}

function closeConversation() {
  els.conversationOverlay.hidden = true;
}

async function loadProfile() {
  const response = await api("/api/profile");
  const profile = await response.json();
  const isAdmin = profile.roles?.some((role) => role.authority === "ROLE_ADMIN");
  showAccountPanel(profile);
  if (isAdmin) {
    showAdminDashboard(profile);
    els.reportsPanel.hidden = false;
    els.reportsTitle.textContent = "後臺記錄";
    els.reportsCaption.textContent = "管理員視圖";
  } else {
    showStudentChat(profile);
    els.reportsPanel.hidden = true;
    els.reports.innerHTML = "";
  }
  setLogin("登錄成功");
  return profile;
}

async function loadAgentStatus() {
  const response = await api("/api/agent/status");
  const status = await response.json();
  setModel(status);
}

async function loadReports() {
  const response = await api("/api/admin/reports");
  const reports = await response.json();
  renderReports(reports);
  return reports;
}

async function loadExcelRecords() {
  const response = await api("/api/admin/excel-records");
  return response.json();
}

async function loadAlertRecords() {
  const response = await api("/api/admin/alerts");
  return response.json();
}

async function loadAdminData() {
  // 三類後臺數據並行加載，避免刷新大屏時互相阻塞。
  const [reports, excelRecords, alerts] = await Promise.all([
    loadReports(),
    loadExcelRecords(),
    loadAlertRecords()
  ]);
  renderAdminDashboard(reports, excelRecords, alerts);
}

async function uploadKnowledgeFile(event) {
  event.preventDefault();
  const file = els.knowledgeFile.files?.[0];
  if (!file) {
    els.knowledgeUploadState.textContent = "請先選擇文件";
    setTone(els.knowledgeUploadState, "warn");
    return;
  }
  // 使用 multipart/form-data 上傳原文件，由後端統一解析和寫入知識庫。
  const body = new FormData();
  body.append("file", file);
  els.knowledgeUploadState.textContent = "正在上傳並切分入庫...";
  setTone(els.knowledgeUploadState, "warn");
  try {
    const response = await api("/api/admin/knowledge/file", {
      method: "POST",
      body
    });
    const result = await response.json();
    els.knowledgeUploadState.textContent = `${result.source} 已入庫 ${result.chunks} 個片段`;
    setTone(els.knowledgeUploadState, "ok");
    els.knowledgeFile.value = "";
  } catch (error) {
    els.knowledgeUploadState.textContent = "上傳失敗：" + error.message;
    setTone(els.knowledgeUploadState, "danger");
  }
}

async function checkHealth() {
  try {
    const response = await fetch("/actuator/health");
    const body = await response.json();
    setService(body.status === "UP" ? "服務正常" : `服務${body.status}`, body.status === "UP");
  } catch (error) {
    setService("服務不可用", false);
  }
}

async function login(event) {
  event?.preventDefault();
  state.auth.username = els.username.value.trim();
  state.auth.password = els.password.value;
  try {
    const profile = await loadProfile();
    await loadAgentStatus();
    const isAdmin = profile.roles?.some((role) => role.authority === "ROLE_ADMIN");
    if (isAdmin) {
      await loadAdminData();
    }
  } catch (error) {
    showLoginForm();
    setLogin("賬號或密碼不正確", false);
  }
}

function parseSse(buffer, onEvent) {
  // SSE 可能分片到達，未完成的事件塊保留到下一次讀取。
  const blocks = buffer.split("\n\n");
  const rest = blocks.pop() || "";
  blocks.forEach((block) => {
    const dataLine = block.split("\n").find((line) => line.startsWith("data:"));
    if (!dataLine) return;
    onEvent(JSON.parse(dataLine.slice(5)));
  });
  return rest;
}

async function sendMessage(event) {
  event.preventDefault();
  // 管理員界面沒有聊天職責，即使表單事件被觸發也直接忽略。
  if (state.isAdmin) return;
  const message = els.messageInput.value.trim();
  if (!message || state.sending) return;

  state.sending = true;
  els.sendButton.disabled = true;
  setSessionBadge("THINKING", "medium");
  els.messageInput.value = "";
  addMessage("user", message);
  const assistant = addMessage("assistant", "");

  try {
    const response = await api("/api/chat/stream", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ sessionId: state.sessionId, message })
    });

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";

    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      buffer = parseSse(buffer, (eventData) => {
        if (eventData.type === "meta") {
          state.sessionId = eventData.sessionId;
        }
        if (eventData.type === "token") {
          assistant.dataset.raw = `${assistant.dataset.raw || ""}${eventData.content}`;
          renderAssistantContent(assistant);
          els.messages.scrollTop = els.messages.scrollHeight;
        }
        if (eventData.type === "error") {
          assistant.dataset.raw = eventData.content || "模型暫時沒有返回內容，請稍後重試。";
          renderAssistantContent(assistant);
          els.messages.scrollTop = els.messages.scrollHeight;
        }
      });
    }

    if (!assistant.dataset.raw) {
      assistant.dataset.raw = "模型暫時沒有返回內容，請稍後重試。";
      renderAssistantContent(assistant);
    }

    if (!els.reportsPanel.hidden) {
      await loadReports();
    }
  } catch (error) {
    assistant.dataset.raw = "請求失敗，請確認後端已啓動並且賬號正確。";
    renderAssistantContent(assistant);
  } finally {
    state.sending = false;
    els.sendButton.disabled = false;
    setSessionBadge("READY");
    els.messageInput.focus();
  }
}

els.loginForm.addEventListener("submit", login);
els.switchAccount.addEventListener("click", () => {
  showLoginForm();
  setLogin("可切換賬號");
  els.username.focus();
});
els.chatForm.addEventListener("submit", sendMessage);
document.addEventListener("click", (event) => {
  const adminRefresh = event.target.closest("[data-admin-refresh]");
  if (adminRefresh) {
    loadAdminData();
    return;
  }
  const scrollTarget = event.target.closest("[data-scroll-target]");
  if (scrollTarget) {
    const target = document.querySelector(`#${scrollTarget.dataset.scrollTarget}`);
    target?.scrollIntoView({ behavior: "smooth", block: "center" });
    return;
  }
  const copyButton = event.target.closest("[data-copy-message]");
  if (copyButton) {
    const bubble = copyButton.closest(".bubble");
    navigator.clipboard?.writeText(bubble?.dataset.raw || "");
    copyButton.textContent = "已複製";
    setTimeout(() => {
      copyButton.textContent = "複製";
    }, 1200);
    return;
  }
  const followUp = event.target.closest("[data-follow-up]");
  if (followUp) {
    usePrompt(followUp.dataset.followUp);
    return;
  }
  const chip = event.target.closest(".prompt-chip");
  if (!chip) return;
  usePrompt(chip.dataset.prompt || chip.textContent.trim());
});
els.newSessionButton.addEventListener("click", startNewSession);
els.refreshReports.addEventListener("click", () => {
  if (state.isAdmin) {
    loadAdminData();
  }
});
els.adminRefresh.addEventListener("click", loadAdminData);
els.knowledgeUploadForm.addEventListener("submit", uploadKnowledgeFile);
els.closeConversation.addEventListener("click", closeConversation);
els.conversationOverlay.addEventListener("click", (event) => {
  if (event.target === els.conversationOverlay) {
    closeConversation();
  }
});
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && !els.conversationOverlay.hidden) {
    closeConversation();
  }
});

checkHealth();
showEmpty();
login();
