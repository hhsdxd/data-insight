package com.datainsight.analyzer.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ai_conversation")
public class AiConversation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long fileId;
    private String sessionId;
    private String title;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
