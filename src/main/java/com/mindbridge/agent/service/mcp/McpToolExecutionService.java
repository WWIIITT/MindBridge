package com.mindbridge.agent.service.mcp;

import com.mindbridge.agent.config.MindBridgeProperties;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class McpToolExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(McpToolExecutionService.class);

    private final Path excelPath;
    private final Object excelLock = new Object();
    private final JavaMailSender mailSender;
    private final MindBridgeProperties properties;

    public McpToolExecutionService(JavaMailSender mailSender, MindBridgeProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.excelPath = Path.of(properties.getMcp().getExcel().getLocalPath());
    }

    public String writeExcelReport(
            Long reportId,
            Long userId,
            String username,
            String sessionId,
            String intent,
            String emotion,
            double emotionScore,
            String riskLevel,
            double confidence,
            String summary,
            String content,
            String createdAt
    ) {
        synchronized (excelLock) {
            try {
                if (excelPath.getParent() != null) {
                    Files.createDirectories(excelPath.getParent());
                }
                Workbook workbook = openWorkbook();
                Sheet sheet = workbook.getNumberOfSheets() == 0
                        ? workbook.createSheet("reports")
                        : workbook.getSheetAt(0);
                if (sheet.getPhysicalNumberOfRows() == 0) {
                    writeHeader(sheet.createRow(0));
                }
                Row row = sheet.createRow(sheet.getLastRowNum() + 1);
                cell(row, 0).setCellValue(nullSafe(reportId));
                cell(row, 1).setCellValue(nullSafe(userId));
                cell(row, 2).setCellValue(nullSafe(username));
                cell(row, 3).setCellValue(nullSafe(sessionId));
                cell(row, 4).setCellValue(nullSafe(intent));
                cell(row, 5).setCellValue(nullSafe(emotion));
                cell(row, 6).setCellValue(emotionScore);
                cell(row, 7).setCellValue(nullSafe(riskLevel));
                cell(row, 8).setCellValue(confidence);
                cell(row, 9).setCellValue(nullSafe(summary));
                cell(row, 10).setCellValue(nullSafe(content));
                cell(row, 11).setCellValue(nullSafe(createdAt));
                for (int i = 0; i < 12; i++) {
                    sheet.autoSizeColumn(i);
                }
                try (OutputStream outputStream = Files.newOutputStream(excelPath)) {
                    workbook.write(outputStream);
                }
                workbook.close();
                return "Excel report written: " + reportId;
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to write report through MCP tool", exception);
            }
        }
    }

    public String sendRiskAlert(
            String recipient,
            Long reportId,
            Long userId,
            String username,
            String displayName,
            String riskLevel,
            String emotion,
            double emotionScore,
            String summary,
            String content
    ) {
        if ("log".equalsIgnoreCase(properties.getMcp().getEmail().getMcpServerDeliveryMode())) {
            logger.warn(
                    "MCP risk alert recipient={} reportId={} userId={} username={} riskLevel={} summary={}",
                    recipient, reportId, userId, username, riskLevel, summary);
            return "Risk alert logged: " + reportId;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getMcp().getEmail().getFrom());
        message.setTo(recipient);
        message.setSubject("【高危心理預警】學生用戶 %s 存在高風險信號".formatted(username));
        message.setText("""
                系統在對話中監測到 1 名學生出現高風險心理狀態，請及時關注並幹預。

                【預警信息如下】
                報告ID：%s
                用戶ID：%s
                學生：%s
                對話內容：%s
                情緒判定：%s
                綜合情緒得分：%.2f
                風險等級：%s
                判斷摘要：%s
                發送時間：%s

                """.formatted(
                reportId,
                userId,
                nullSafe(displayName),
                nullSafe(content),
                nullSafe(emotion),
                emotionScore,
                nullSafe(riskLevel),
                nullSafe(summary),
                Instant.now()));
        mailSender.send(message);
        return "Risk alert sent: " + reportId;
    }

    private Workbook openWorkbook() throws Exception {
        if (!Files.exists(excelPath)) {
            return new XSSFWorkbook();
        }
        try (InputStream inputStream = Files.newInputStream(excelPath)) {
            return WorkbookFactory.create(inputStream);
        }
    }

    private void writeHeader(Row row) {
        String[] headers = {
                "報告ID", "用戶ID", "賬號", "會話ID", "意圖", "情緒標籤", "情緒總分",
                "風險等級", "置信度", "判斷摘要", "對話內容", "對話時間"
        };
        for (int i = 0; i < headers.length; i++) {
            cell(row, i).setCellValue(headers[i]);
        }
    }

    private Cell cell(Row row, int index) {
        return row.createCell(index);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private long nullSafe(Long value) {
        return value == null ? 0L : value;
    }
}
