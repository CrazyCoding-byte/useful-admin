package com.yzx.system.domain.convert;

import com.yzx.model.system.SysMenu;
import com.yzx.system.domain.bo.SysMenuBo;
import com.yzx.system.domain.vo.SysMenuVo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 菜单权限对象转换器
 *
 * @author yzx
 */
@Mapper(componentModel = "spring")
public interface SysMenuConvert {

    SysMenuConvert INSTANCE = Mappers.getMapper(SysMenuConvert.class);

    /**
     * Bo转实体
     */
    SysMenu boToEntity(SysMenuBo bo);

    /**
     * 实体转Bo
     */
    SysMenuBo entityToBo(SysMenu entity);

    /**
     * 实体转Vo
     */
    SysMenuVo entityToVo(SysMenu entity);

    /**
     * Vo转实体
     */
    SysMenu voToEntity(SysMenuVo vo);

    /**
     * Bo转Vo
     */
    SysMenuVo boToVo(SysMenuBo bo);

    /**
     * Vo转Bo
     */
    SysMenuBo voToBo(SysMenuVo vo);

    /**
     * 实体列表转Vo列表
     */
    List<SysMenuVo> entityListToVoList(List<SysMenu> entityList);

    /**
     * 实体列表转Bo列表
     */
    List<SysMenuBo> entityListToBoList(List<SysMenu> entityList);

    /**
     * Bo列表转实体列表
     */
    List<SysMenu> boListToEntityList(List<SysMenuBo> boList);

}
