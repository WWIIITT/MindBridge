package com.mindbridge.agent.repository;

import com.mindbridge.agent.domain.AlertRecord;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 預警發送記錄的數據訪問接口。
 */
public interface AlertRecordRepository extends JpaRepository<AlertRecord, Long> {

    List<AlertRecord> findByReport_Id(Long reportId);

    /**
     * 管理員後臺列表需要同時展示報告、學生賬號和會話 id，使用 EntityGraph 避免懶加載反覆查詢。
     */
    @EntityGraph(attributePaths = {"report", "report.user", "report.session"})
    List<AlertRecord> findTop100ByOrderByCreatedAtDesc();
}
