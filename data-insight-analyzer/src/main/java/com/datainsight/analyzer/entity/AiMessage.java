package com.datainsight.analyzer.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ai_message")
public class AiMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private String role;  // user / assistant / system
    private String content;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
