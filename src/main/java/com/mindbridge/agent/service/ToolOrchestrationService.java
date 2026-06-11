package com.mindbridge.agent.service;

import com.mindbridge.agent.config.MindBridgeProperties;
import com.mindbridge.agent.domain.AlertRecord;
import com.mindbridge.agent.domain.PsychologicalReport;
import com.mindbridge.agent.domain.RiskLevel;
import com.mindbridge.agent.domain.ToolStatus;
import com.mindbridge.agent.repository.AlertRecordRepository;
import com.mindbridge.agent.repository.PsychologicalReportRepository;
import com.mindbridge.agent.service.mcp.AlertNotifier;
import com.mindbridge.agent.service.mcp.ExcelReportWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
/**
 * 後臺工具編排服務。
 *
 * <p>心理報告生成後，按“寫 Excel -> 高風險發預警”的順序執行工具鏈並持久化狀態。</p>
 */
public class ToolOrchestrationService {

    private final ExcelReportWriter excelReportWriter;
    private final AlertNotifier alertNotifier;
    private final PsychologicalReportRepository reportRepository;
    private final AlertRecordRepository alertRecordRepository;
    private final MindBridgeProperties properties;
    private final TaskExecutor mcpTaskExecutor;
    private final TransactionTemplate transactionTemplate;

    public ToolOrchestrationService(
            ExcelReportWriter excelReportWriter,
            AlertNotifier alertNotifier,
            PsychologicalReportRepository reportRepository,
            AlertRecordRepository alertRecordRepository,
            MindBridgeProperties properties,
            @Qualifier("mcpTaskExecutor")
            TaskExecutor mcpTaskExecutor,
            TransactionTemplate transactionTemplate
    ) {
        this.excelReportWriter = excelReportWriter;
        this.alertNotifier = alertNotifier;
        this.reportRepository = reportRepository;
        this.alertRecordRepository = alertRecordRepository;
        this.properties = properties;
        this.mcpTaskExecutor = mcpTaskExecutor;
        this.transactionTemplate = transactionTemplate;
    }

    public void handleAsync(Long reportId) {
        mcpTaskExecutor.execute(() -> {
            try {
                transactionTemplate.executeWithoutResult(status -> handleInTransaction(reportId));
            } catch (Exception ignored) {
                // 工具執行失敗會寫入報告狀態，這裏吞掉異常，避免後臺任務影響聊天主流程。
            }
        });
    }

    @Transactional
    public void handle(Long reportId) {
        handleInTransaction(reportId);
    }

    private void handleInTransaction(Long reportId) {
        PsychologicalReport managedReport = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));
        writeExcel(managedReport);
        // 只有 Excel 寫入成功且風險等級爲 HIGH，才進入預警通知，和文檔中的工具鏈順序保持一致。
        if (managedReport.getRiskLevel() == RiskLevel.HIGH && managedReport.getExcelStatus() == ToolStatus.SUCCESS) {
            sendAlerts(managedReport);
        }
        reportRepository.save(managedReport);
    }

    private void writeExcel(PsychologicalReport report) {
        try {
            excelReportWriter.write(report);
            report.setExcelStatus(ToolStatus.SUCCESS);
        } catch (Exception exception) {
            report.setExcelStatus(ToolStatus.FAILED);
            report.setToolError(shorten(exception.getMessage()));
        }
    }

    private void sendAlerts(PsychologicalReport report) {
        boolean allSuccess = true;
        for (String recipient : properties.getMcp().getEmail().getRecipients()) {
            AlertRecord alertRecord = new AlertRecord();
            alertRecord.setReport(report);
            alertRecord.setRecipient(recipient);
            alertRecordRepository.save(alertRecord);

            boolean sent = false;
            int maxAttempts = Math.max(1, properties.getMcp().getEmail().getMaxRetries() + 1);
            // 每個收件人獨立重試和落庫，管理員後臺可以看到每封通知的最終狀態。
            for (int attempt = 0; attempt < maxAttempts && !sent; attempt++) {
                try {
                    alertRecord.incrementAttempts();
                    alertNotifier.notify(alertRecord, report);
                    alertRecord.setStatus(ToolStatus.SUCCESS);
                    sent = true;
                } catch (Exception exception) {
                    alertRecord.setStatus(ToolStatus.FAILED);
                    alertRecord.setErrorMessage(shorten(exception.getMessage()));
                }
            }
            alertRecordRepository.save(alertRecord);
            allSuccess = allSuccess && sent;
        }
        report.setEmailStatus(allSuccess ? ToolStatus.SUCCESS : ToolStatus.FAILED);
    }

    private String shorten(String message) {
        if (message == null) {
            return "";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
