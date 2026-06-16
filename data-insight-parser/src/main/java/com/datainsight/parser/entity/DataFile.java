package com.datainsight.parser.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("data_file")
public class DataFile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String originalName;
    private Long fileSize;
    private String filePath;
    private String status;  // PENDING / PARSING / COMPLETED / FAILED
    private String errorMsg;
    private Integer rowCount;
    private Integer columnCount;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
