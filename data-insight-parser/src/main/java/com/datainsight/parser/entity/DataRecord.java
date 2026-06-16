package com.datainsight.parser.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("data_record")
public class DataRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long fileId;
    private Integer rowIndex;
    private String rowData;  // JSON object
}
