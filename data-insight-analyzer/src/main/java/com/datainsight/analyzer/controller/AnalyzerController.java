package com.datainsight.analyzer.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.datainsight.analyzer.entity.AiConversation;
import com.datainsight.analyzer.entity.AiMessage;
import com.datainsight.analyzer.mapper.AiConversationMapper;
import com.datainsight.analyzer.mapper.AiMessageMapper;
import com.datainsight.analyzer.service.Nl2SqlService;
import com.datainsight.common.BizException;
import com.datainsight.common.R;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AnalyzerController {

    private final Nl2SqlService nl2SqlService;
    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;

    /** NL2SQL 同步查询 */
    @PostMapping("/query")
    public R<Map<String, Object>> query(@RequestBody Map<String, Object> body) {
        Long fileId = Long.valueOf(body.get("fileId").toString());
        String question = (String) body.get("question");

        Map<String, Object> result = nl2SqlService.analyze(fileId, question);
        return R.ok(result);
    }

    /** SSE 流式对话 */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long fileId = Long.valueOf(body.get("fileId").toString());
        String question = (String) body.get("question");
        Long userId = getUserId(request);
        String sessionId = body.containsKey("sessionId") ? (String) body.get("sessionId") : UUID.randomUUID().toString();

        // 查找或创建会话
        AiConversation conv = findOrCreateConversation(userId, fileId, sessionId);

        // 保存用户消息
        AiMessage userMsg = new AiMessage();
        userMsg.setConversationId(conv.getId());
        userMsg.setRole("user");
        userMsg.setContent(question);
        messageMapper.insert(userMsg);

        // 流式返回
        StringBuilder fullResponse = new StringBuilder();

        return nl2SqlService.analyzeStream(fileId, conv.getId(), question)
                .doOnNext(chunk -> fullResponse.append(chunk))
                .doOnComplete(() -> {
                    AiMessage aiMsg = new AiMessage();
                    aiMsg.setConversationId(conv.getId());
                    aiMsg.setRole("assistant");
                    aiMsg.setContent(fullResponse.toString());
                    messageMapper.insert(aiMsg);
                })
                .doOnError(e -> {
                    log.error("AI对话异常", e);
                });
    }

    /** 自动洞察：让AI分析数据特征 */
    @PostMapping("/insight/{fileId}")
    public R<Map<String, Object>> insight(@PathVariable Long fileId) {
        String question = """
                请对这个数据集进行整体分析，包括：
                1. 数据概况（行数、列数）
                2. 每列的数据分布特征
                3. 发现的异常值或有趣规律
                4. 建议的可视化方式
                请用中文简洁回答。
                """;
        Map<String, Object> result = nl2SqlService.analyze(fileId, question);
        return R.ok(result);
    }

    /** 获取会话历史 */
    @GetMapping("/history/{conversationId}")
    public R<List<AiMessage>> history(@PathVariable Long conversationId) {
        List<AiMessage> messages = messageMapper.selectByConversationId(conversationId);
        return R.ok(messages);
    }

    /** 获取用户的所有会话 */
    @GetMapping("/conversations")
    public R<List<AiConversation>> conversations(HttpServletRequest request) {
        Long userId = getUserId(request);
        List<AiConversation> list = conversationMapper.selectList(
                new LambdaQueryWrapper<AiConversation>()
                        .eq(AiConversation::getUserId, userId)
                        .orderByDesc(AiConversation::getCreateTime));
        return R.ok(list);
    }

    private AiConversation findOrCreateConversation(Long userId, Long fileId, String sessionId) {
        // 先按 sessionId 查找
        List<AiConversation> existing = conversationMapper.selectList(
                new LambdaQueryWrapper<AiConversation>()
                        .eq(AiConversation::getSessionId, sessionId));
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        // 创建新会话
        AiConversation conv = new AiConversation();
        conv.setUserId(userId);
        conv.setFileId(fileId);
        conv.setSessionId(sessionId);
        conv.setTitle("数据查询");
        conversationMapper.insert(conv);
        return conv;
    }

    private Long getUserId(HttpServletRequest request) {
        String userIdHeader = request.getHeader("X-User-Id");
        if (userIdHeader != null) {
            return Long.valueOf(userIdHeader);
        }
        return 1L; // 测试默认
    }
}
