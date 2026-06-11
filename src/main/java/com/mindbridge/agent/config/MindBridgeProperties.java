package com.mindbridge.agent.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mindbridge")
/**
 * mindbridge.* 配置映射。
 *
 * <p>所有業務配置集中在這裏，便於通過 application.yml 或環境變量切換模型、
 * RAG、知識庫切塊、Excel 寫入和郵件預警行爲。</p>
 */
public class MindBridgeProperties {

    private final Ai ai = new Ai();
    private final Chat chat = new Chat();
    private final Embedding embedding = new Embedding();
    private final Knowledge knowledge = new Knowledge();
    private final RagEval ragEval = new RagEval();
    private final Mcp mcp = new Mcp();

    public Ai getAi() {
        return ai;
    }

    public Chat getChat() {
        return chat;
    }

    public Embedding getEmbedding() {
        return embedding;
    }

    public Knowledge getKnowledge() {
        return knowledge;
    }

    public RagEval getRagEval() {
        return ragEval;
    }

    public Mcp getMcp() {
        return mcp;
    }

    public static class Ai {
        /** 模型提供方：ollama、openai 或 mock。 */
        private String provider = "ollama";
        /** 生成溫度，值越高回答越發散。 */
        private double temperature = 0.35;
        /** 學生端單次回覆的最大生成 token 數，避免本地模型無邊界擴寫。 */
        private int maxTokens = 512;
        private final Ollama ollama = new Ollama();
        private final OpenAi openai = new OpenAi();

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public Ollama getOllama() {
            return ollama;
        }

        public OpenAi getOpenai() {
            return openai;
        }
    }

    public static class Ollama {
        /** 本地模型服務地址。 */
        private String baseUrl = "http://localhost:11434";
        /** MindBridge 項目模型名稱。 */
        private String model = "mindbridge-qwen2.5-7b-ft:latest";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    public static class OpenAi {
        /** OpenAI 兼容接口地址。 */
        private String baseUrl = "https://api.openai.com";
        /** OpenAI API Key，未配置時不能啓用 openai provider。 */
        private String apiKey = "";
        /** OpenAI 聊天模型名稱。 */
        private String model = "gpt-4o-mini";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    public static class Chat {
        /** 保留給模型的歷史輪次數，服務層會換算成用戶/助手消息條數。 */
        private int historyLimit = 10;
        /** Redis 短期記憶 TTL，過期後可從 MySQL 長期記憶恢復最近上下文。 */
        private long shortMemoryTtlHours = 24;

        public int getHistoryLimit() {
            return historyLimit;
        }

        public void setHistoryLimit(int historyLimit) {
            this.historyLimit = historyLimit;
        }

        public long getShortMemoryTtlHours() {
            return shortMemoryTtlHours;
        }

        public void setShortMemoryTtlHours(long shortMemoryTtlHours) {
            this.shortMemoryTtlHours = shortMemoryTtlHours;
        }
    }

    public static class Embedding {
        /** Embedding 服務地址。 */
        private String baseUrl = "https://api.openai.com";
        /** Embedding API Key，留空時自動走本地檢索兜底。 */
        private String apiKey = "";
        /** 文檔要求的默認 embedding 模型。 */
        private String model = "text-embedding-3-small";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    public static class Knowledge {
        /** 每次 RAG 檢索返回的候選片段數量。 */
        private int topK = 4;
        /** 是否啓用外部 Chroma 向量庫。 */
        private boolean useChroma;
        private String chromaBaseUrl = "http://localhost:8000";
        private String chromaCollection = "mindbridge_knowledge";
        private int chunkSize = 512;
        private int chunkOverlap = 64;

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public boolean isUseChroma() {
            return useChroma;
        }

        public void setUseChroma(boolean useChroma) {
            this.useChroma = useChroma;
        }

        public String getChromaBaseUrl() {
            return chromaBaseUrl;
        }

        public void setChromaBaseUrl(String chromaBaseUrl) {
            this.chromaBaseUrl = chromaBaseUrl;
        }

        public String getChromaCollection() {
            return chromaCollection;
        }

        public void setChromaCollection(String chromaCollection) {
            this.chromaCollection = chromaCollection;
        }

        public int getChunkSize() {
            return chunkSize;
        }

        public void setChunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
        }

        public int getChunkOverlap() {
            return chunkOverlap;
        }

        public void setChunkOverlap(int chunkOverlap) {
            this.chunkOverlap = chunkOverlap;
        }
    }

    public static class RagEval {
        /** 是否在啓動後運行 RAG 檢索評測。 */
        private boolean enabled;
        /** 評測集 JSON 路徑，支持 classpath: 或文件系統路徑。 */
        private String dataset = "classpath:rag-eval/mindbridge-rag-eval.json";
        /** 評測 TopK。 */
        private int topK = 4;
        /** 是否在評測完成後退出應用，便於命令行/CI 單獨跑評測。 */
        private boolean exitAfterRun;
        /** JSON 報告輸出路徑，留空則只打印控制檯摘要。 */
        private String outputPath = "target/rag-eval-report.json";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getDataset() {
            return dataset;
        }

        public void setDataset(String dataset) {
            this.dataset = dataset;
        }

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public boolean isExitAfterRun() {
            return exitAfterRun;
        }

        public void setExitAfterRun(boolean exitAfterRun) {
            this.exitAfterRun = exitAfterRun;
        }

        public String getOutputPath() {
            return outputPath;
        }

        public void setOutputPath(String outputPath) {
            this.outputPath = outputPath;
        }
    }

    public static class Mcp {
        private final Excel excel = new Excel();
        private final Email email = new Email();

        public Excel getExcel() {
            return excel;
        }

        public Email getEmail() {
            return email;
        }
    }

    public static class Excel {
        /** Excel 寫入模式：local、http 或 mcp。 */
        private String mode = "local";
        private String url = "http://localhost:8081";
        private String localPath = "./data/mindbridge-reports.xlsx";

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getLocalPath() {
            return localPath;
        }

        public void setLocalPath(String localPath) {
            this.localPath = localPath;
        }
    }

    public static class Email {
        /** 郵件預警模式：log、smtp、http 或 mcp。 */
        private String mode = "log";
        private String url = "http://localhost:8082";
        private String from = "mindbridge@example.com";
        private List<String> recipients = new ArrayList<>(List.of("counselor@example.com"));
        private int maxRetries = 2;
        /** MCP Server 收到 send_risk_alert 工具調用後實際投遞方式：log 或 smtp。 */
        private String mcpServerDeliveryMode = "log";

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public List<String> getRecipients() {
            return recipients;
        }

        public void setRecipients(List<String> recipients) {
            this.recipients = recipients;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public String getMcpServerDeliveryMode() {
            return mcpServerDeliveryMode;
        }

        public void setMcpServerDeliveryMode(String mcpServerDeliveryMode) {
            this.mcpServerDeliveryMode = mcpServerDeliveryMode;
        }
    }
}
