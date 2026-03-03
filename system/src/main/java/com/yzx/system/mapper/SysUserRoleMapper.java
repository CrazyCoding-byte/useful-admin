package com.yzx.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzx.model.system.SysUserRole;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 用户与角色关联表 数据层
 * 
 * @author ruoyi
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole>
{
    /**
     * 批量删除用户与角色关联
     * 
     * @param userIds 需要删除的用户ID
     * @return 结果
     */
    public int deleteUserRoleByUserIds(Long[] userIds);
    
    /**
     * 批量删除用户与角色关联
     * 
     * @param roleIds 需要删除的角色ID
     * @return 结果
     */
    public int deleteUserRoleByRoleIds(Long[] roleIds);
    
    /**
     * 批量新增用户角色信息
     * 
     * @param userRoleList 用户角色列表
     * @return 结果
     */
    public int batchUserRole(List<SysUserRole> userRoleList);
}
