package com.yzx.product.service.serviceimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzx.model.AjaxResult;
import com.yzx.model.Result;
import com.yzx.model.product.PmsCategory;
import com.yzx.model.product.vo.CategoryVo;
import com.yzx.product.mapper.PmsCategoryMapper;
import com.yzx.product.service.ProductCateGoryService;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @className: ProductCateGoryServiceImpl
 * @author: yzx
 * @date: 2025/9/18 11:47
 * @Version: 1.0
 * @description:
 */
@Service
public class ProductCateGoryServiceImpl extends ServiceImpl<PmsCategoryMapper, PmsCategory> implements ProductCateGoryService {
    @Override
    public void updateChildrenLevel(Long catId, int newLevel) {
        LambdaQueryWrapper<PmsCategory> pmsCategoryLambdaQueryWrapper = new LambdaQueryWrapper<>();
        pmsCategoryLambdaQueryWrapper.eq(PmsCategory::getParentCid, catId);
        List<PmsCategory> pmsCategories = this.baseMapper.selectList(pmsCategoryLambdaQueryWrapper);
        if (CollectionUtils.isEmpty(pmsCategories)) return;
        for (PmsCategory child : pmsCategories) {
            child.setCatLevel(newLevel + 1);
            this.baseMapper.updateById(child);
            updateChildrenLevel(child.getCatId(), child.getCatLevel());
        }
    }

    @Override
    public AjaxResult getParentTree(Long catId) {
        List<PmsCategory> pmsCategories = baseMapper.selectList(null);
        List<CategoryVo> categoryVos = buildCategoryTree(0L, pmsCategories);
        // 如果 excludeCatId 有值，递归过滤掉该节点及其所有子孙
        if (catId != null && catId > 0L) {
            categoryVos = filterNodeAndDescendants(categoryVos, catId);
        }
        return AjaxResult.success(categoryVos);
    }


    private List<CategoryVo> filterNodeAndDescendants(List<CategoryVo> categoryVos, Long catId) {
        return categoryVos.stream().filter(node -> !node.getCatId().equals(catId)).map(node -> {
            CategoryVo vo = new CategoryVo();
            BeanUtils.copyProperties(node, vo);
            if (node.getParentCid() != null) {
                vo.setChildren(filterNodeAndDescendants(node.getChildren(), catId));
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @Cacheable(value = "category", key = "#root.methodName")
    @Override
    public AjaxResult getCateGory(Map<String, Object> params) {
        LambdaQueryWrapper<PmsCategory> pmsCategoryLambdaQueryWrapper = new LambdaQueryWrapper<>();
        if (params.containsKey("categoryName")) {
            pmsCategoryLambdaQueryWrapper.like(PmsCategory::getName, params.get("categoryName"));
        }
        List<PmsCategory> categoryEntities = this.baseMapper.selectList(pmsCategoryLambdaQueryWrapper);
        return AjaxResult.success(buildCategoryTree(0L, categoryEntities));
    }


    private List<CategoryVo> buildCategoryTree(Long parentId, List<PmsCategory> entities) {
        List<PmsCategory> childCategories = entities.stream()
                .filter(category -> parentId.equals(category.getParentCid()))
                .collect(Collectors.toList());
        List<CategoryVo> categoryVos = new ArrayList<>();
        for (PmsCategory entity : childCategories) {
            CategoryVo vo = new CategoryVo();
            vo.setCatId(entity.getCatId());
            vo.setIcon(entity.getIcon());
            vo.setName(entity.getName());
            vo.setParentCid(entity.getParentCid());
            vo.setProductUnit(entity.getProductUnit());
            vo.setShowStatus(entity.getShowStatus());
            vo.setIcon(entity.getIcon());
            vo.setChildren(buildCategoryTree(entity.getCatId(), entities));
            categoryVos.add(vo);
        }
        return categoryVos;
    }
}
