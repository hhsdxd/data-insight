package com.datainsight.analyzer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datainsight.analyzer.entity.AiMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AiMessageMapper extends BaseMapper<AiMessage> {

    @Select("SELECT * FROM ai_message WHERE conversation_id = #{conversationId} ORDER BY create_time ASC")
    List<AiMessage> selectByConversationId(@Param("conversationId") Long conversationId);
}
