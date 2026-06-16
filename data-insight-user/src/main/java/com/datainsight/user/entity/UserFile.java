package com.datainsight.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_user_file")
public class UserFile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String originalName;
    private Long fileSize;
    private String filePath;
    /** PENDING / PARSING / COMPLETED / FAILED */
    private String status;
    private Integer rowCount;
    private Integer columnCount;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
