package com.mindbridge.agent.service.mcp;

import com.mindbridge.agent.domain.AlertRecord;
import com.mindbridge.agent.domain.PsychologicalReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 日誌預警實現。
 *
 * <p>用於本地演示或無 SMTP 環境時驗證高風險鏈路是否被觸發。</p>
 */
public class LogAlertNotifier implements AlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(LogAlertNotifier.class);

    @Override
    public void notify(AlertRecord alertRecord, PsychologicalReport report) {
        log.warn(
                "High risk alert dry-run: recipient={}, reportId={}, user={}, summary={}",
                alertRecord.getRecipient(),
                report.getId(),
                report.getUser().getUsername(),
                report.getSummary());
    }
}
