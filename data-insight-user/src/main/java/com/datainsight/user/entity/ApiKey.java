package com.datainsight.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_api_key")
public class ApiKey {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String accessKey;
    private String secretKey;
    /** ACTIVE / REVOKED */
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
