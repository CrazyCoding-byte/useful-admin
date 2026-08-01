package com.yzx.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yzx.model.Result;
import com.yzx.model.product.PmsCategory;

import java.util.Map;

/**
 * @className: ProductCateGoryService
 * @author: yzx
 * @date: 2025/9/18 11:47
 * @Version: 1.0
 * @description:
 */
public interface ProductCateGoryService extends IService<PmsCategory> {
    Result getCateGory(Map<String, Object> map);
}
