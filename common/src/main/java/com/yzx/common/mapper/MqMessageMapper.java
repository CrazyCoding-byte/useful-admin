package com.yzx.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import  com.yzx.common.mqlocalmessage.MqMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MqMessageMapper extends BaseMapper<MqMessage> {

    /**
     * 查询失败的消息（重试用）
     */
    List<MqMessage> selectFailMessage(@Param("maxRetry") int maxRetry);
}