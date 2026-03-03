package com.yzx.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzx.model.system.SysRole;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 角色表 数据层
 * 
 * @author ruoyi
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole>
{
    /**
     * 根据用户ID查询角色权限
     * 
     * @param userId 用户ID
     * @return 角色权限列表
     */
    public List<String> selectRolePermissionByUserId(Long userId);
    
    /**
     * 根据用户ID查询角色
     * 
     * @param userId 用户ID
     * @return 角色列表
     */
    public List<SysRole> selectRolesByUserId(Long userId);
    
    /**
     * 根据用户名查询角色
     * 
     * @param userName 用户名
     * @return 角色列表
     */
    public List<SysRole> selectRolesByUserName(String userName);
}
