package com.yzx.system.domain.convert;

import com.yzx.model.system.SysDept;
import com.yzx.system.domain.bo.SysDeptBo;
import com.yzx.system.domain.vo.SysDeptVo;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * 部门对象转换器
 *
 * @author yzx
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SysDeptConvert {

    /**
     * SysDeptBo 转 SysDept
     *
     * @param bo 业务对象
     * @return 实体对象
     */
    SysDept boToEntity(SysDeptBo bo);

    /**
     * SysDept 转 SysDeptBo
     *
     * @param entity 实体对象
     * @return 业务对象
     */
    SysDeptBo entityToBo(SysDept entity);

    /**
     * SysDept 转 SysDeptVo
     *
     * @param entity 实体对象
     * @return 视图对象
     */
    SysDeptVo entityToVo(SysDept entity);

    /**
     * SysDeptVo 转 SysDept
     *
     * @param vo 视图对象
     * @return 实体对象
     */
    SysDept voToEntity(SysDeptVo vo);

    /**
     * SysDeptBo 转 SysDeptVo
     *
     * @param bo 业务对象
     * @return 视图对象
     */
    SysDeptVo boToVo(SysDeptBo bo);

    /**
     * 实体列表转视图对象列表
     *
     * @param list 实体列表
     * @return 视图对象列表
     */
    List<SysDeptVo> entityListToVoList(List<SysDept> list);

}
