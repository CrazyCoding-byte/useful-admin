package com.yzx.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yzx.model.product.PmsAttr;

import java.util.List;

/**
 * @className: AttrService
 * @author: yzx
 * @date: 2025/9/18 16:00
 * @Version: 1.0
 * @description:
 */
public interface PmsAttrService extends IService<PmsAttr> {
    List<Long> selectByIds(List<Long> attrIds);
}
