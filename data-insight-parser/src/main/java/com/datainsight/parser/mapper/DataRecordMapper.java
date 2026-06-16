package com.datainsight.parser.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datainsight.parser.entity.DataRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DataRecordMapper extends BaseMapper<DataRecord> {

    @Select("SELECT * FROM data_record WHERE file_id = #{fileId} ORDER BY row_index LIMIT #{limit}")
    List<DataRecord> selectPreview(@Param("fileId") Long fileId, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM data_record WHERE file_id = #{fileId}")
    Long countByFileId(@Param("fileId") Long fileId);
}
