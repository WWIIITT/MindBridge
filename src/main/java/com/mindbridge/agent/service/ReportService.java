package com.mindbridge.agent.service;

import com.mindbridge.agent.domain.ChatMessage;
import com.mindbridge.agent.domain.ChatSession;
import com.mindbridge.agent.domain.PsychologicalReport;
import com.mindbridge.agent.domain.ToolStatus;
import com.mindbridge.agent.domain.UserAccount;
import com.mindbridge.agent.dto.AlertRecordResponse;
import com.mindbridge.agent.dto.ConversationResponse;
import com.mindbridge.agent.dto.ExcelRecordResponse;
import com.mindbridge.agent.repository.AlertRecordRepository;
import com.mindbridge.agent.repository.ChatMessageRepository;
import com.mindbridge.agent.repository.ChatSessionRepository;
import com.mindbridge.agent.repository.PsychologicalReportRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
/**
 * 管理員後臺數據查詢服務。
 *
 * <p>封裝報告列表、Excel 寫入記錄、預警記錄和完整會話讀取邏輯。</p>
 */
public class ReportService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final PsychologicalReportRepository psychologicalReportRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AlertRecordRepository alertRecordRepository;

    public ReportService(
            PsychologicalReportRepository psychologicalReportRepository,
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            AlertRecordRepository alertRecordRepository
    ) {
        this.psychologicalReportRepository = psychologicalReportRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.alertRecordRepository = alertRecordRepository;
    }

    @Transactional(readOnly = true)
    public List<PsychologicalReport> myReports(Long userId) {
        return psychologicalReportRepository.findTop50ByUser_IdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<PsychologicalReport> latestReports() {
        // 管理員後臺只展示學生對話產生的報告，避免管理員測試消息混入統計大屏。
        return psychologicalReportRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .filter(ReportService::isStudentReport)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExcelRecordResponse> excelRecords() {
        return psychologicalReportRepository
                .findTop100ByExcelStatusOrderByCreatedAtDesc(ToolStatus.SUCCESS)
                .stream()
                .filter(ReportService::isStudentReport)
                .map(ExcelRecordResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AlertRecordResponse> alertRecords() {
        return alertRecordRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .filter(alertRecord -> isStudentReport(alertRecord.getReport()))
                .map(AlertRecordResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationResponse conversation(String sessionId) {
        ChatSession session = chatSessionRepository.findByPublicId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        // 管理員只能查看學生會話；非學生會話統一按不存在處理，減少後臺數據誤展示。
        if (!isStudentUser(session.getUser())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        }
        List<ChatMessage> messages = chatMessageRepository.findBySession_PublicIdOrderByCreatedAtAsc(sessionId);
        return ConversationResponse.from(session, messages);
    }

    private static boolean isStudentReport(PsychologicalReport report) {
        return report != null && isStudentUser(report.getUser());
    }

    private static boolean isStudentUser(UserAccount user) {
        return user != null && !user.getRoles().contains(ROLE_ADMIN);
    }
}
