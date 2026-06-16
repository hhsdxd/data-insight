package com.datainsight.analyzer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.regex.Pattern;

/**
 * NL2SQL 核心服务：自然语言 → SQL → 结果解读
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Nl2SqlService {

    @Value("${langchain4j.openai.api-key}")
    private String apiKey;

    @Value("${langchain4j.openai.model-name:qwen-plus}")
    private String model;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper mapper = new ObjectMapper();
    private WebClient webClient;

    private static final String API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private static final Pattern DANGEROUS_SQL = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|TRUNCATE|ALTER|CREATE|REPLACE|GRANT|REVOKE)\\b",
            Pattern.CASE_INSENSITIVE);

    @jakarta.annotation.PostConstruct
    public void init() {
        this.webClient = WebClient.builder()
                .baseUrl(API_URL)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * NL2SQL 同步调用
     */
    public Map<String, Object> analyze(Long fileId, String question) {
        // 1. 获取列元数据
        List<Map<String, Object>> columns = getColumnMeta(fileId);

        // 2. 构建 system prompt
        String systemPrompt = buildSystemPrompt(fileId, columns);

        // 3. 调用 LLM 生成 SQL
        String sql = callLlmSync(systemPrompt, question);
        log.info("NL2SQL 生成: question={} → sql={}", question, sql);

        // 4. 安全检查
        if (DANGEROUS_SQL.matcher(sql).find()) {
            throw new IllegalArgumentException("SQL包含危险操作，仅允许SELECT查询。SQL: " + sql);
        }

        // 5. 执行 SQL
        List<Map<String, Object>> sqlResult;
        try {
            sqlResult = jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            throw new IllegalArgumentException("SQL执行失败: " + e.getMessage() + "。SQL: " + sql);
        }

        // 6. 解读结果
        String explanation = explainResult(question, sql, sqlResult);

        return Map.of(
                "question", question,
                "sql", sql,
                "data", sqlResult,
                "explanation", explanation,
                "totalRows", sqlResult.size()
        );
    }

    /**
     * NL2SQL SSE 流式对话
     */
    public Flux<String> analyzeStream(Long fileId, Long conversationId, String question) {
        // 1. 获取列元数据
        List<Map<String, Object>> columns = getColumnMeta(fileId);

        // 2. 构建 system prompt
        String systemPrompt = buildSystemPrompt(fileId, columns);

        // 3. 构建消息
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", question)
        );

        // 4. 调用 LLM 流式
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", 0.3);
        body.put("max_tokens", 2000);
        body.put("stream", true);

        StringBuilder fullResponse = new StringBuilder();

        return webClient.post()
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(line -> line.startsWith("data: "))
                .map(line -> {
                    try {
                        String data = line.substring(6);
                        if ("[DONE]".equals(data.trim())) return "";

                        JsonNode node = mapper.readTree(data);
                        JsonNode choices = node.get("choices");
                        if (choices != null && choices.size() > 0) {
                            JsonNode delta = choices.get(0).get("delta");
                            if (delta != null) {
                                JsonNode content = delta.get("content");
                                if (content != null && !content.asText().isEmpty()) {
                                    String chunk = content.asText();
                                    fullResponse.append(chunk);
                                    Map<String, String> sse = new LinkedHashMap<>();
                                    sse.put("content", chunk);
                                    return mapper.writeValueAsString(sse);
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                    return "";
                })
                .filter(s -> !s.isEmpty())
                .doOnComplete(() -> {
                    // 尝试从完整回复中提取SQL并执行
                    String full = fullResponse.toString();
                    String sql = extractSql(full);
                    if (sql != null && !DANGEROUS_SQL.matcher(sql).find()) {
                        try {
                            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
                            log.info("SQL执行成功: {} 行结果", result.size());
                        } catch (Exception e) {
                            log.error("SQL执行失败: {}", e.getMessage());
                        }
                    }
                });
    }

    /** 获取列元数据（查询 parser 数据库） */
    private List<Map<String, Object>> getColumnMeta(Long fileId) {
        String sql = """
                SELECT column_name, column_type, nullable_ratio, sample_values, ordinal_position
                FROM data_insight_parser.column_meta
                WHERE file_id = ?
                ORDER BY ordinal_position
                """;
        return jdbcTemplate.queryForList(sql, fileId);
    }

    /** 获取总行数 */
    private Long getRowCount(Long fileId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM data_insight_parser.data_record WHERE file_id = ?",
                Long.class, fileId);
    }

    /** 构建 NL2SQL System Prompt */
    private String buildSystemPrompt(Long fileId, List<Map<String, Object>> columns) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个数据分析助手。用户上传了一个数据文件，其结构如下：\n\n");
        sb.append("| 序号 | 列名 | 类型 | 示例值 |\n");
        sb.append("|------|------|------|--------|\n");
        for (Map<String, Object> col : columns) {
            int idx = ((Number) col.get("ordinal_position")).intValue();
            sb.append(String.format("| col_%d | %s | %s | %s |\n",
                    idx,
                    col.get("column_name"),
                    col.get("column_type"),
                    col.get("sample_values")));
        }

        sb.append("\n数据在 data_insight_parser.data_record 表。");
        sb.append("row_data 是 JSON 列，键名使用 col_N (N是列序号，如 col_0, col_1...)。");
        sb.append("查询时使用 JSON_EXTRACT(row_data, '$.col_N') 获取值。\n");
        sb.append("所有查询必须加 WHERE file_id = ").append(fileId).append(" 过滤。\n\n");
        sb.append("规则：\n");
        sb.append("1. 只允许 SELECT 查询\n");
        sb.append("2. JSON键名必须是 col_0, col_1, col_2... 格式\n");
        sb.append("3. 数值比较用 CAST(JSON_EXTRACT(row_data, '$.col_N') AS DECIMAL)\n");
        sb.append("4. 用中文解读，简洁专业\n\n");
        sb.append("先输出SQL（```sql代码块），然后解读。");
        return sb.toString();
    }

    /** 同步调用 LLM */
    private String callLlmSync(String systemPrompt, String question) {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", question)
        );

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", 0.3);
        body.put("max_tokens", 2000);

        try {
            String response = webClient.post()
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode node = mapper.readTree(response);
            String content = node.get("choices").get(0).get("message").get("content").asText();

            // 提取 SQL
            String sql = extractSql(content);
            if (sql != null) return sql;

            return content;
        } catch (Exception e) {
            log.error("LLM调用失败", e);
            throw new RuntimeException("AI服务调用失败: " + e.getMessage());
        }
    }

    /** 从 LLM 回复中提取 SQL */
    private String extractSql(String content) {
        int start = content.indexOf("```sql");
        if (start == -1) return null;
        start += 6;
        int end = content.indexOf("```", start);
        if (end == -1) return null;
        return content.substring(start, end).trim();
    }

    /** 解读查询结果 */
    private String explainResult(String question, String sql, List<Map<String, Object>> result) {
        if (result.isEmpty()) {
            return "查询结果为空，没有符合条件的数据。";
        }
        if (result.size() == 1) {
            return "查询到 1 条结果。" + mapper.valueToTree(result.get(0)).toString();
        }
        return String.format("共查询到 %d 条结果。", result.size());
    }
}
