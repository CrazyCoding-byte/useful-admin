package com.yzx.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzx.model.system.SysConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 配置表 数据层
 * 
 * @author ruoyi
 */
@Mapper
public interface SysConfigMapper extends BaseMapper<SysConfig>
{
    // 所有方法都通过MyBatis-Plus的BaseMapper实现
}
