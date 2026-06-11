package com.mindbridge.agent.domain;

/**
 * Excel 寫入、郵件通知等外部工具的執行狀態。
 */
public enum ToolStatus {
    PENDING,
    SUCCESS,
    FAILED,
    SKIPPED
}
