package com.yzx.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzx.model.StringUtils;
import com.yzx.model.system.PageQuery;
import com.yzx.model.system.SysDictData;
import com.yzx.model.system.SysDictType;
import com.yzx.model.system.TableDataInfo;
import com.yzx.system.domain.bo.SysDictTypeBo;
import com.yzx.system.domain.convert.SysDictTypeConvert;
import com.yzx.system.domain.vo.SysDictDataVo;
import com.yzx.system.domain.vo.SysDictTypeVo;
import com.yzx.system.mapper.SysDictDataMapper;
import com.yzx.system.mapper.SysDictTypeMapper;
import com.yzx.system.service.ISysDictTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 字典 业务层处理
 *
 * @author yzx
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SysDictTypeServiceImpl implements ISysDictTypeService {

    private final SysDictTypeMapper baseMapper;
    private final SysDictDataMapper dictDataMapper;
    private final SysDictTypeConvert dictTypeConvert;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 分页查询字典类型列表
     *
     * @param dictType  查询条件
     * @param pageQuery 分页参数
     * @return 字典类型分页列表
     */
    @Override
    public TableDataInfo<SysDictTypeVo> selectPageDictTypeList(SysDictTypeBo dictType, PageQuery pageQuery) {
        LambdaQueryWrapper<SysDictType> lqw = buildQueryWrapper(dictType);
        Page<SysDictType> page = baseMapper.selectPage(pageQuery.build(), lqw);
        
        TableDataInfo<SysDictTypeVo> tableDataInfo = new TableDataInfo<>();
        tableDataInfo.setTotal(page.getTotal());
        tableDataInfo.setRows(dictTypeConvert.entityListToVoList(page.getRecords()));
        return tableDataInfo;
    }

    /**
     * 根据条件分页查询字典类型
     *
     * @param dictType 字典类型信息
     * @return 字典类型集合信息
     */
    @Override
    public List<SysDictTypeVo> selectDictTypeList(SysDictTypeBo dictType) {
        LambdaQueryWrapper<SysDictType> lqw = buildQueryWrapper(dictType);
        List<SysDictType> list = baseMapper.selectList(lqw);
        return dictTypeConvert.entityListToVoList(list);
    }

    private LambdaQueryWrapper<SysDictType> buildQueryWrapper(SysDictTypeBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<SysDictType> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getDictName()), SysDictType::getDictName, bo.getDictName());
        lqw.like(StringUtils.isNotBlank(bo.getDictType()), SysDictType::getDictType, bo.getDictType());
        if (params != null) {
            lqw.between(params.get("beginTime") != null && params.get("endTime") != null,
                SysDictType::getCreateTime, params.get("beginTime"), params.get("endTime"));
        }
        lqw.orderByAsc(SysDictType::getDictId);
        return lqw;
    }

    /**
     * 根据所有字典类型
     *
     * @return 字典类型集合信息
     */
    @Override
    public List<SysDictTypeVo> selectDictTypeAll() {
        List<SysDictType> list = baseMapper.selectList(null);
        return dictTypeConvert.entityListToVoList(list);
    }

    /**
     * 根据字典类型查询字典数据
     *
     * @param dictType 字典类型
     * @return 字典数据集合信息
     */
    @Override
    public List<SysDictDataVo> selectDictDataByType(String dictType) {
        // 1.先从Redis查询
        String cacheKey = "sys_dict:" + dictType;
        @SuppressWarnings("unchecked")
        List<SysDictDataVo> cache = (List<SysDictDataVo>) redisTemplate.opsForValue().get(cacheKey);
        if (cache != null) {
            log.debug("从缓存获取字典数据:{}", dictType);
            return cache;
        }

        // 2.缓存未命中,查询数据库
        LambdaQueryWrapper<SysDictData> lqw = new LambdaQueryWrapper<>();
        lqw.eq(SysDictData::getDictType, dictType);
        lqw.orderByAsc(SysDictData::getDictSort);
        List<SysDictData> list = dictDataMapper.selectList(lqw);
        
        // 3.存入redis(过期1小时)
        if (list != null && !list.isEmpty()) {
            List<SysDictDataVo> voList = new ArrayList<>();
            for (SysDictData data : list) {
                SysDictDataVo vo = new SysDictDataVo();
                vo.setDictCode(data.getDictCode());
                vo.setDictSort(data.getDictSort());
                vo.setDictLabel(data.getDictLabel());
                vo.setDictValue(data.getDictValue());
                vo.setDictType(data.getDictType());
                vo.setCssClass(data.getCssClass());
                vo.setListClass(data.getListClass());
                vo.setIsDefault(data.getIsDefault());
                vo.setStatus(data.getStatus());
                vo.setRemark(data.getRemark());
                vo.setCreateTime(data.getCreateTime());
                vo.setUpdateTime(data.getUpdateTime());
                voList.add(vo);
            }
            redisTemplate.opsForValue().set(cacheKey, voList, 1, TimeUnit.HOURS);
            log.debug("字典数据已缓存:{}", dictType);
            return voList;
        }
        return new ArrayList<>();
    }

    /**
     * 根据字典类型ID查询信息
     *
     * @param dictId 字典类型ID
     * @return 字典类型
     */
    @Override
    public SysDictTypeVo selectDictTypeById(Long dictId) {
        SysDictType dictType = baseMapper.selectById(dictId);
        return dictTypeConvert.entityToVo(dictType);
    }

    /**
     * 根据字典类型查询信息
     *
     * @param dictType 字典类型
     * @return 字典类型
     */
    @Override
    public SysDictTypeVo selectDictTypeByType(String dictType) {
        LambdaQueryWrapper<SysDictType> lqw = Wrappers.lambdaQuery();
        lqw.eq(SysDictType::getDictType, dictType);
        SysDictType type = baseMapper.selectOne(lqw);
        return dictTypeConvert.entityToVo(type);
    }

    /**
     * 批量删除字典类型信息
     *
     * @param dictIds 需要删除的字典ID
     */
    @Override
    public void deleteDictTypeByIds(List<Long> dictIds) {
        List<SysDictType> list = baseMapper.selectBatchIds(dictIds);
        baseMapper.deleteBatchIds(dictIds);
        // 清除缓存
        for (SysDictType type : list) {
            redisTemplate.delete("sys_dict:" + type.getDictType());
        }
    }

    /**
     * 重置字典缓存数据
     */
    @Override
    public void resetDictCache() {
        // 清除所有字典缓存
        // 这里可以遍历所有字典类型并清除缓存
        log.info("重置字典缓存");
    }

    /**
     * 新增保存字典类型信息
     *
     * @param bo 字典类型信息
     * @return 结果
     */
    @Override
    public List<SysDictDataVo> insertDictType(SysDictTypeBo bo) {
        SysDictType dict = dictTypeConvert.boToEntity(bo);
        baseMapper.insert(dict);
        // 新增 type 下无 data 数据 返回空防止缓存穿透
        return new ArrayList<>();
    }

    /**
     * 修改保存字典类型信息
     *
     * @param bo 字典类型信息
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<SysDictDataVo> updateDictType(SysDictTypeBo bo) {
        SysDictType dict = dictTypeConvert.boToEntity(bo);
        SysDictType oldDict = baseMapper.selectById(dict.getDictId());
        
        // 同步修改字典数据中的字典类型
        if (oldDict != null && !oldDict.getDictType().equals(dict.getDictType())) {
            LambdaUpdateWrapper<SysDictData> updateWrapper = Wrappers.lambdaUpdate();
            updateWrapper.set(SysDictData::getDictType, dict.getDictType());
            updateWrapper.eq(SysDictData::getDictType, oldDict.getDictType());
            dictDataMapper.update(null, updateWrapper);
            // 清除旧缓存
            redisTemplate.delete("sys_dict:" + oldDict.getDictType());
        }
        
        baseMapper.updateById(dict);
        return selectDictDataByType(dict.getDictType());
    }

    /**
     * 校验字典类型称是否唯一
     *
     * @param dictType 字典类型
     * @return 结果
     */
    @Override
    public boolean checkDictTypeUnique(SysDictTypeBo dictType) {
        LambdaQueryWrapper<SysDictType> lqw = Wrappers.lambdaQuery();
        lqw.eq(SysDictType::getDictType, dictType.getDictType());
        if (dictType.getDictId() != null) {
            lqw.ne(SysDictType::getDictId, dictType.getDictId());
        }
        Long count = baseMapper.selectCount(lqw);
        return count == 0;
    }
}
