package com.datainsight.parser.mq;

import com.datainsight.parser.service.CsvParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 消费者 —— 监听文件上传消息，自动触发解析。
 * 开发环境可通过 rocketmq.enabled=false 关闭。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
// TODO: 接入 RocketMQ 后取消注释
// @RocketMQMessageListener(topic = "file-parse", consumerGroup = "parser-group")
public class FileParseConsumer { // implements RocketMQListener<String> {

    private final CsvParserService csvParserService;

    // @Override
    public void onMessage(String message) {
        try {
            Long fileId = Long.valueOf(message);
            log.info("收到文件解析消息: fileId={}", fileId);
            csvParserService.parse(fileId);
        } catch (Exception e) {
            log.error("消费消息失败: {}", message, e);
        }
    }
}
