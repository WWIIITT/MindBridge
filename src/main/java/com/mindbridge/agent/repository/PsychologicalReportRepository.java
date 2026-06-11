package com.mindbridge.agent.repository;

import com.mindbridge.agent.domain.PsychologicalReport;
import com.mindbridge.agent.domain.ToolStatus;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 心理報告的數據訪問接口。
 */
public interface PsychologicalReportRepository extends JpaRepository<PsychologicalReport, Long> {

    /** 當前用戶自己的報告查詢，主要保留給接口擴展。 */
    @EntityGraph(attributePaths = {"user", "session"})
    List<PsychologicalReport> findTop50ByUser_IdOrderByCreatedAtDesc(Long userId);

    /** 管理員後臺報告列表。 */
    @EntityGraph(attributePaths = {"user", "session"})
    List<PsychologicalReport> findTop100ByOrderByCreatedAtDesc();

    /** Excel 寫入記錄頁面只需要寫入成功的報告。 */
    @EntityGraph(attributePaths = {"user", "session"})
    List<PsychologicalReport> findTop100ByExcelStatusOrderByCreatedAtDesc(ToolStatus excelStatus);
}
