package com.yzx.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yzx.model.AjaxResult;
import com.yzx.model.product.PmsCategory;

import java.util.List;
import java.util.Map;

/**
 * @className: ProductCateGoryService
 * @author: yzx
 * @date: 2025/9/18 11:47
 * @Version: 1.0
 * @description:
 */
public interface ProductCateGoryService extends IService<PmsCategory> {
    AjaxResult getCateGory(Map<String, Object> map);

    AjaxResult getParentTree(Long catId);

    void updateChildrenLevel(Long catId, int newLevel);

    Map<Long, List<Long>> findCategoryDescendantIds(List<Long> catIds);
}
