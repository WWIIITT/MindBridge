package com.mindbridge.agent.service.knowledge;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

@Service
/**
 * 管理員文件上傳知識庫服務。
 *
 * <p>負責文件大小校驗、類型識別和文本抽取，抽取後的文本交給 KnowledgeService 處理。</p>
 */
public class KnowledgeFileService {

    private static final int MAX_FILE_BYTES = 10 * 1024 * 1024;

    private final KnowledgeService knowledgeService;

    public KnowledgeFileService(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    public int ingest(String filename, byte[] bytes) {
        // 文件上傳入口只負責校驗和抽取文本，真正切塊、向量化、落庫交給 KnowledgeService。
        if (bytes.length == 0) {
            throw new IllegalArgumentException("文件內容爲空");
        }
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("文件不能超過 10MB");
        }
        String source = sanitizeSource(filename);
        String text = extractText(source, bytes);
        if (text.isBlank()) {
            throw new IllegalArgumentException("沒有從文件中解析出可用文本");
        }
        return knowledgeService.ingest(source, text);
    }

    private String extractText(String filename, byte[] bytes) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return extractPdf(bytes);
        }
        // Markdown 和 txt 都按 UTF-8 文本處理，適合管理員維護輕量知識庫。
        if (lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".txt")) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        throw new IllegalArgumentException("僅支持 PDF、Markdown 和 txt 文件");
    }

    private String extractPdf(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document);
        } catch (Exception exception) {
            throw new IllegalArgumentException("PDF 文本解析失敗：" + exception.getMessage());
        }
    }

    private String sanitizeSource(String filename) {
        String source = filename == null || filename.isBlank() ? "uploaded-knowledge" : filename.trim();
        // source 會進入數據庫和後臺列表，去掉路徑分隔符避免顯示本地路徑。
        source = source.replaceAll("[\\\\/]+", "-");
        return source.length() > 180 ? source.substring(source.length() - 180) : source;
    }
}
