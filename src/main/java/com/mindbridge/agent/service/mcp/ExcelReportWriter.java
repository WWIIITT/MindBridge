package com.mindbridge.agent.service.mcp;

import com.mindbridge.agent.domain.PsychologicalReport;

/**
 * 心理報告寫入 Excel 的工具接口。
 *
 * <p>本地文件寫入和遠程 MCP 寫入都實現這個接口。</p>
 */
public interface ExcelReportWriter {

    void write(PsychologicalReport report);
}
