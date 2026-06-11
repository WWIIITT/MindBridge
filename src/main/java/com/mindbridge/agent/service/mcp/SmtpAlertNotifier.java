package com.mindbridge.agent.service.mcp;

import com.mindbridge.agent.config.MindBridgeProperties;
import com.mindbridge.agent.domain.AlertRecord;
import com.mindbridge.agent.domain.PsychologicalReport;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * SMTP 郵件預警實現。
 *
 * <p>高風險報告觸發後，把摘要信息發送給配置的輔導員或心理中心郵箱。</p>
 */
public class SmtpAlertNotifier implements AlertNotifier {

    private final JavaMailSender mailSender;
    private final MindBridgeProperties properties;

    public SmtpAlertNotifier(JavaMailSender mailSender, MindBridgeProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void notify(AlertRecord alertRecord, PsychologicalReport report) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getMcp().getEmail().getFrom());
        message.setTo(alertRecord.getRecipient());
        message.setSubject("【高危心理預警】學生用戶 %s 存在高風險信號".formatted(report.getUser().getUsername()));
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

                """.formatted(
                report.getId(),
                report.getUser().getUsername(),
                report.getUser().getDisplayName(),
                report.getContent(),
                report.getEmotion(),
                report.getEmotionScore(),
                report.getRiskLevel(),
                report.getSummary()));
        mailSender.send(message);
    }
}
