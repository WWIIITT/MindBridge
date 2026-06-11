package com.mindbridge.agent.service.knowledge;

import java.util.ArrayList;
import java.util.List;

/**
 * 知識庫文本切塊器。
 *
 * <p>優先在換行、句號和英文標點附近切分，減少單個片段語義被截斷。</p>
 */
public class KnowledgeChunker {

    public List<String> chunk(String content, int chunkSize, int overlap) {
        String text = content.replace("\r\n", "\n").trim();
        if (text.isBlank()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        int safeSize = Math.max(120, chunkSize);
        int safeOverlap = Math.max(0, Math.min(overlap, safeSize / 2));
        int index = 0;
        while (index < text.length()) {
            int end = Math.min(text.length(), index + safeSize);
            if (end < text.length()) {
                // 儘量在自然邊界切開，找不到合適邊界時才按固定長度切。
                int boundary = Math.max(
                        Math.max(text.lastIndexOf("\n", end), text.lastIndexOf("。", end)),
                        Math.max(text.lastIndexOf(".", end), text.lastIndexOf("?", end)));
                if (boundary > index + safeSize / 2) {
                    end = boundary + 1;
                }
            }
            chunks.add(text.substring(index, end).trim());
            if (end >= text.length()) {
                break;
            }
            index = Math.max(0, end - safeOverlap);
        }
        return chunks;
    }
}
