package com.datainsight.parser.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datainsight.parser.entity.DataFile;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DataFileMapper extends BaseMapper<DataFile> {
}
