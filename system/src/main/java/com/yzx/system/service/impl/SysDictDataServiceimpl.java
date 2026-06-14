package com.yzx.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzx.model.StringUtils;
import com.yzx.model.system.PageQuery;
import com.yzx.model.system.SysDictData;
import com.yzx.model.system.TableDataInfo;
import com.yzx.system.domain.bo.SysDictDataBo;
import com.yzx.system.domain.convert.SysDictDataConvert;
import com.yzx.system.domain.vo.SysDictDataVo;
import com.yzx.system.mapper.SysDictDataMapper;
import com.yzx.system.service.ISysDictDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 字典 业务层处理
 *
 * @author yzx
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SysDictDataServiceimpl implements ISysDictDataService {

    private final SysDictDataMapper baseMapper;
    private final SysDictDataConvert dictDataConvert;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 分页查询字典数据列表
     *
     * @param dictData  查询条件
     * @param pageQuery 分页参数
     * @return 字典数据分页列表
     */
    @Override
    public TableDataInfo<SysDictDataVo> selectPageDictDataList(SysDictDataBo dictData, PageQuery pageQuery) {
        LambdaQueryWrapper<SysDictData> lqw = buildQueryWrapper(dictData);
        Page<SysDictData> page = baseMapper.selectPage(pageQuery.build(), lqw);
        
        TableDataInfo<SysDictDataVo> tableDataInfo = new TableDataInfo<>();
        tableDataInfo.setTotal(page.getTotal());
        tableDataInfo.setRows(dictDataConvert.entityListToVoList(page.getRecords()));
        return tableDataInfo;
    }

    /**
     * 根据条件分页查询字典数据
     *
     * @param dictData 字典数据信息
     * @return 字典数据集合信息
     */
    @Override
    public List<SysDictDataVo> selectDictDataList(SysDictDataBo dictData) {
        LambdaQueryWrapper<SysDictData> lqw = buildQueryWrapper(dictData);
        List<SysDictData> list = baseMapper.selectList(lqw);
        return dictDataConvert.entityListToVoList(list);
    }

    private LambdaQueryWrapper<SysDictData> buildQueryWrapper(SysDictDataBo bo) {
        LambdaQueryWrapper<SysDictData> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getDictSort() != null, SysDictData::getDictSort, bo.getDictSort());
        lqw.like(StringUtils.isNotBlank(bo.getDictLabel()), SysDictData::getDictLabel, bo.getDictLabel());
        lqw.eq(StringUtils.isNotBlank(bo.getDictType()), SysDictData::getDictType, bo.getDictType());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), SysDictData::getStatus, bo.getStatus());
        lqw.orderByAsc(SysDictData::getDictSort, SysDictData::getDictCode);
        return lqw;
    }

    /**
     * 根据字典类型和字典键值查询字典数据信息
     *
     * @param dictType  字典类型
     * @param dictValue 字典键值
     * @return 字典标签
     */
    @Override
    public String selectDictLabel(String dictType, String dictValue) {
        LambdaQueryWrapper<SysDictData> lqw = Wrappers.lambdaQuery();
        lqw.select(SysDictData::getDictLabel);
        lqw.eq(SysDictData::getDictType, dictType);
        lqw.eq(SysDictData::getDictValue, dictValue);
        SysDictData dictData = baseMapper.selectOne(lqw);
        return dictData != null ? dictData.getDictLabel() : null;
    }

    /**
     * 根据字典数据ID查询信息
     *
     * @param dictCode 字典数据ID
     * @return 字典数据
     */
    @Override
    public SysDictDataVo selectDictDataById(Long dictCode) {
        SysDictData dictData = baseMapper.selectById(dictCode);
        return dictDataConvert.entityToVo(dictData);
    }

    /**
     * 批量删除字典数据信息
     *
     * @param dictCodes 需要删除的字典数据ID
     */
    @Override
    public void deleteDictDataByIds(List<Long> dictCodes) {
        List<SysDictData> list = baseMapper.selectBatchIds(dictCodes);
        baseMapper.deleteBatchIds(dictCodes);
        // 清除缓存
        for (SysDictData data : list) {
            redisTemplate.delete("sys_dict:" + data.getDictType());
        }
    }

    /**
     * 新增保存字典数据信息
     *
     * @param bo 字典数据信息
     * @return 结果
     */
    @Override
    public List<SysDictDataVo> insertDictData(SysDictDataBo bo) {
        SysDictData data = dictDataConvert.boToEntity(bo);
        baseMapper.insert(data);
        // 清除该字典类型的缓存
        redisTemplate.delete("sys_dict:" + data.getDictType());
        // 返回该字典类型的所有数据
        return selectDictDataListByType(data.getDictType());
    }

    /**
     * 修改保存字典数据信息
     *
     * @param bo 字典数据信息
     * @return 结果
     */
    @Override
    public List<SysDictDataVo> updateDictData(SysDictDataBo bo) {
        SysDictData data = dictDataConvert.boToEntity(bo);
        baseMapper.updateById(data);
        // 清除该字典类型的缓存
        redisTemplate.delete("sys_dict:" + data.getDictType());
        // 返回该字典类型的所有数据
        return selectDictDataListByType(data.getDictType());
    }

    /**
     * 校验字典键值是否唯一
     *
     * @param dict 字典数据
     * @return 结果
     */
    @Override
    public boolean checkDictDataUnique(SysDictDataBo dict) {
        LambdaQueryWrapper<SysDictData> lqw = Wrappers.lambdaQuery();
        lqw.eq(SysDictData::getDictType, dict.getDictType());
        lqw.eq(SysDictData::getDictValue, dict.getDictValue());
        if (dict.getDictCode() != null) {
            lqw.ne(SysDictData::getDictCode, dict.getDictCode());
        }
        Long count = baseMapper.selectCount(lqw);
        return count == 0;
    }

    /**
     * 根据字典类型查询字典数据列表（内部方法）
     *
     * @param dictType 字典类型
     * @return 字典数据集合信息
     */
    private List<SysDictDataVo> selectDictDataListByType(String dictType) {
        LambdaQueryWrapper<SysDictData> lqw = Wrappers.lambdaQuery();
        lqw.eq(SysDictData::getDictType, dictType);
        lqw.orderByAsc(SysDictData::getDictSort, SysDictData::getDictCode);
        List<SysDictData> list = baseMapper.selectList(lqw);
        return dictDataConvert.entityListToVoList(list);
    }
}
