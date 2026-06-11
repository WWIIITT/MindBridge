package com.mindbridge.agent.service.mcp;

import com.mindbridge.agent.config.MindBridgeProperties;
import com.mindbridge.agent.domain.PsychologicalReport;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * 本地 Excel 文件寫入實現。
 *
 * <p>適合演示環境使用，會把報告追加到 data 目錄下的工作簿中。</p>
 */
public class LocalExcelReportWriter implements ExcelReportWriter {

    private final Path path;
    private final Object lock = new Object();

    public LocalExcelReportWriter(MindBridgeProperties properties) {
        this.path = Path.of(properties.getMcp().getExcel().getLocalPath());
    }

    @Override
    public void write(PsychologicalReport report) {
        synchronized (lock) {
            try {
                // 寫文件需要串行化，防止多個高風險報告同時寫入造成工作簿損壞。
                if (path.getParent() != null) {
                    Files.createDirectories(path.getParent());
                }
                Workbook workbook = openWorkbook();
                Sheet sheet = workbook.getNumberOfSheets() == 0
                        ? workbook.createSheet("reports")
                        : workbook.getSheetAt(0);
                if (sheet.getPhysicalNumberOfRows() == 0) {
                    writeHeader(sheet.createRow(0));
                }
                Row row = sheet.createRow(sheet.getLastRowNum() + 1);
                writeReport(row, report);
                for (int i = 0; i < 12; i++) {
                    sheet.autoSizeColumn(i);
                }
                try (OutputStream outputStream = Files.newOutputStream(path)) {
                    workbook.write(outputStream);
                }
                workbook.close();
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to write local Excel report", exception);
            }
        }
    }

    private Workbook openWorkbook() throws Exception {
        if (!Files.exists(path)) {
            return new XSSFWorkbook();
        }
        // 已存在時追加到原工作簿，保留歷史寫入記錄。
        try (InputStream inputStream = Files.newInputStream(path)) {
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

    private void writeReport(Row row, PsychologicalReport report) {
        cell(row, 0).setCellValue(nullSafe(report.getId()));
        cell(row, 1).setCellValue(nullSafe(report.getUser().getId()));
        cell(row, 2).setCellValue(report.getUser().getUsername());
        cell(row, 3).setCellValue(report.getSession() == null ? "" : report.getSession().getPublicId());
        cell(row, 4).setCellValue(report.getIntent().name());
        cell(row, 5).setCellValue(report.getEmotion().name());
        cell(row, 6).setCellValue(report.getEmotionScore());
        cell(row, 7).setCellValue(report.getRiskLevel().name());
        cell(row, 8).setCellValue(report.getConfidence());
        cell(row, 9).setCellValue(report.getSummary());
        cell(row, 10).setCellValue(report.getContent());
        cell(row, 11).setCellValue(report.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDateTime().toString());
    }

    private Cell cell(Row row, int index) {
        return row.createCell(index);
    }

    private long nullSafe(Long value) {
        return value == null ? 0L : value;
    }
}
