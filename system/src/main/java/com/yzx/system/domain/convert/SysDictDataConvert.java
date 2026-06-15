package com.yzx.system.domain.convert;

import com.yzx.model.system.SysDictData;
import com.yzx.system.domain.bo.SysDictDataBo;
import com.yzx.system.domain.vo.SysDictDataVo;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 字典数据转换器
 *
 * @author yzx
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SysDictDataConvert {

    SysDictDataConvert INSTANCE = Mappers.getMapper(SysDictDataConvert.class);

    /**
     * Bo转Entity
     */
    SysDictData boToEntity(SysDictDataBo bo);

    /**
     * Entity转Bo
     */
    SysDictDataBo entityToBo(SysDictData entity);

    /**
     * Entity转Vo
     */
    SysDictDataVo entityToVo(SysDictData entity);

    /**
     * Vo转Entity
     */
    SysDictData voToEntity(SysDictDataVo vo);

    /**
     * Bo转Vo
     */
    SysDictDataVo boToVo(SysDictDataBo bo);

    /**
     * Vo转Bo
     */
    SysDictDataBo voToBo(SysDictDataVo vo);

    /**
     * Entity列表转Vo列表
     */
    List<SysDictDataVo> entityListToVoList(List<SysDictData> entityList);

    /**
     * Entity列表转Bo列表
     */
    List<SysDictDataBo> entityListToBoList(List<SysDictData> entityList);

    /**
     * Bo列表转Entity列表
     */
    List<SysDictData> boListToEntityList(List<SysDictDataBo> boList);

}
