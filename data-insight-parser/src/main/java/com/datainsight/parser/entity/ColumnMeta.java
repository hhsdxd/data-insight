package com.datainsight.parser.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("column_meta")
public class ColumnMeta {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long fileId;
    private String columnName;
    private String columnType;  // INT / DECIMAL / DATE / VARCHAR
    private java.math.BigDecimal nullableRatio;
    private String sampleValues;  // JSON array
    private Integer ordinalPosition;
}
