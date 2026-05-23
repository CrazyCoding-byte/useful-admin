package com.yzx.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzx.model.system.SysDictData;
import com.yzx.model.system.SysDictType;
import com.yzx.system.mapper.SysDictDataMapper;
import com.yzx.system.service.ISysDictDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @className: SysDictDataServiceimpl
 * @author: yzx
 * @date: 2026/5/21 16:01
 * @Version: 1.0
 * @description:
 */
@Service
@Slf4j
public class SysDictDataServiceimpl extends ServiceImpl<SysDictDataMapper, SysDictData> implements ISysDictDataService {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public List<SysDictData> selectDictDataByType(String dictType) {
        //1.先从Redis查询
        String cacheKey = "sys_dict:" + dictType;
        List<SysDictData> cache = (List<SysDictData>) redisTemplate.opsForValue().get(cacheKey);
        if (cache != null) {
            log.debug("从缓存获取字典数据:{}", dictType);
            return cache;
        }

        //2.缓存未命中,查询数据库（修复：调用 Mapper 而不是递归调用自己）
        List<SysDictData> list = baseMapper.selectDictDataByType(dictType);
        
        //3.存入redis(过期1小时)
        if (list != null && !list.isEmpty()) {
            redisTemplate.opsForValue().set(cacheKey, list, 1, TimeUnit.HOURS);
            log.debug("字典数据已缓存:{}", dictType);
        }
        return list;
    }

    @Override
    public void insertDictData(SysDictData dictData) {
        baseMapper.insert(dictData);

        //清除该字典类型的缓存
//        resetDictCache(dictData.getDictType());
    }

    @Override
    public void updateDictType(SysDictType dictType) {

    }


}
