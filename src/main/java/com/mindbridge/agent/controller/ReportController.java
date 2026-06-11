package com.mindbridge.agent.controller;

import com.mindbridge.agent.dto.ConversationResponse;
import com.mindbridge.agent.dto.AlertRecordResponse;
import com.mindbridge.agent.dto.ExcelRecordResponse;
import com.mindbridge.agent.dto.ReportResponse;
import com.mindbridge.agent.security.CurrentUser;
import com.mindbridge.agent.service.ReportService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
/**
 * 報告、Excel 記錄、郵件記錄和完整會話查詢接口。
 *
 * <p>管理員後臺的數據列表和詳情彈窗主要由這些接口驅動。</p>
 */
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/reports/me")
    public List<ReportResponse> myReports(@AuthenticationPrincipal CurrentUser currentUser) {
        return reportService.myReports(currentUser.getId()).stream()
                .map(ReportResponse::from)
                .toList();
    }

    @GetMapping("/admin/reports")
    public List<ReportResponse> latestReports() {
        // 管理員統計大屏使用這個接口作爲對話報告主數據源。
        return reportService.latestReports().stream()
                .map(ReportResponse::from)
                .toList();
    }

    @GetMapping("/admin/excel-records")
    public List<ExcelRecordResponse> excelRecords() {
        return reportService.excelRecords();
    }

    @GetMapping("/admin/alerts")
    public List<AlertRecordResponse> alertRecords() {
        return reportService.alertRecords();
    }

    @GetMapping("/admin/conversations/{sessionId}")
    public ConversationResponse conversation(@PathVariable String sessionId) {
        // 點開任一後臺記錄時讀取完整會話，便於輔導員回看上下文。
        return reportService.conversation(sessionId);
    }
}
