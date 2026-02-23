package com.yzx.usefulagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzx.model.agent.ChatAuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatAuditLogMapper extends BaseMapper<ChatAuditLog> {
}