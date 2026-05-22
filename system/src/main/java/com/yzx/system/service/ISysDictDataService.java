package com.yzx.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yzx.model.system.SysDictData;
import com.yzx.model.system.SysDictType;

import java.util.List;

/**
 * @className: ISysDictDataService
 * @author: yzx
 * @date: 2026/5/21 16:01
 * @Version: 1.0
 * @description:
 */
public interface ISysDictDataService extends IService<SysDictData> {
    public List<SysDictData> selectDictDataByType(String dictType);

    public void insertDictData(SysDictData dictData);

    public void updateDictType(SysDictType dictType);
}
