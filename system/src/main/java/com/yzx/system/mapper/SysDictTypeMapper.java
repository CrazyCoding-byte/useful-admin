package com.yzx.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzx.model.system.SysDictType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 字典类型 数据层
 * 
 * @author yzx
 */
@Mapper
public interface SysDictTypeMapper extends BaseMapper<SysDictType> {
    
    /**
     * 查询字典类型列表
     * 
     * @param dictType 字典类型信息
     * @return 字典类型集合
     */
    List<SysDictType> selectDictTypeList(SysDictType dictType);
    
    /**
     * 根据所有字典类型
     * 
     * @return 字典类型集合
     */
    List<SysDictType> selectDictTypeAll();
    
    /**
     * 根据字典类型ID查询信息
     * 
     * @param dictId 字典类型ID
     * @return 字典类型
     */
    SysDictType selectDictTypeById(Long dictId);
    
    /**
     * 根据字典类型查询信息
     * 
     * @param dictType 字典类型
     * @return 字典类型
     */
    SysDictType selectDictTypeByType(String dictType);
    
    /**
     * 批量删除字典类型
     * 
     * @param dictIds 需要删除的数据ID
     * @return 结果
     */
    int deleteDictTypeByIds(Long[] dictIds);
    
    /**
     * 校验字典类型称是否唯一
     * 
     * @param dictType 字典类型
     * @return 结果
     */
    SysDictType checkDictTypeUnique(String dictType);
}
