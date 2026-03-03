package com.yzx.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzx.model.system.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表 数据层
 * 
 * @author ruoyi
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser>
{
    // 所有方法都通过MyBatis-Plus的BaseMapper实现
}
