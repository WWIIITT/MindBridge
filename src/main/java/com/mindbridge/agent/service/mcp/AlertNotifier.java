package com.mindbridge.agent.service.mcp;

import com.mindbridge.agent.domain.AlertRecord;
import com.mindbridge.agent.domain.PsychologicalReport;

/**
 * 高風險預警通知接口。
 *
 * <p>具體實現可以是日誌、SMTP 郵件或 HTTP MCP 服務。</p>
 */
public interface AlertNotifier {

    void notify(AlertRecord alertRecord, PsychologicalReport report);
}
