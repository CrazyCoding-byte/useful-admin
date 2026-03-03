package com.yzx.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzx.model.system.SysMenu;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 菜单表 数据层
 * 
 * @author ruoyi
 */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu>
{
    /**
     * 根据用户ID查询权限
     * 
     * @param userId 用户ID
     * @return 权限列表
     */
    public List<String> selectMenuPermsByUserId(Long userId);
    
    /**
     * 根据用户ID查询菜单
     * 
     * @param userId 用户ID
     * @return 菜单列表
     */
    public List<SysMenu> selectMenuTreeByUserId(Long userId);
}
