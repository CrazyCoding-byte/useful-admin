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

import java.util.*;
import java.util.stream.Collectors;

/**
 * @className: ProductCateGoryServiceImpl
 * @author: yzx
 * @date: 2025/9/18 11:47
 * @Version: 1.0
 * @description:
 */
@Service
public class ProductCateGoryServiceImpl extends ServiceImpl<PmsCategoryMapper, PmsCategory>
        implements ProductCateGoryService {
    private CategoryVo categoryVo;

    public static void main(String[] args) {
        HashMap<String, List<String>> map = new HashMap<>();
        map.computeIfAbsent("高三一班", k -> new ArrayList<>()).add("张三");
        map.computeIfAbsent("高三一班", k -> new ArrayList<>()).add("李四");
        System.out.println(map);
    }

    public Map<Long, List<Long>> findCategoryDescendantIds(List<Long> catIds) {
        Map<Long, List<Long>> result = new HashMap<>();
        if (CollectionUtils.isEmpty(catIds))
            return result;
        List<PmsCategory> categories = this.list();
        // 父->子
        Map<Long, List<Long>> childrenMap = new HashMap<>();
        for (PmsCategory cat : categories) {
            if (cat.getParentCid() != null && cat.getParentCid() > 0) {
                childrenMap.computeIfAbsent(cat.getParentCid(), k -> new ArrayList<>()).add(cat.getCatId());
            }
        }
        // 从每个锚点 BFS 向下展开（含自己）
        for (Long catId : catIds) {
            if (catId == null)
                continue;
            List<Long> descendants = new ArrayList<>();
            Deque<Long> queue = new ArrayDeque<>();
            queue.offer(catId);
            while (!queue.isEmpty()) {
                Long cur = queue.poll();
                descendants.add(cur);
                List<Long> children = childrenMap.get(cur);
                if (children != null)
                    queue.addAll(children);
            }
            result.put(catId, descendants);
        }
        return result;
    }

    public Map<Long, List<Long>> getChild(List<Long> catIds) {
        if (CollectionUtils.isEmpty(catIds))
            return new HashMap<>();
        Map<Long, List<Long>> result = new HashMap<>();
        List<PmsCategory> categories = this.list();
        Map<Long, List<Long>> childrenMap = new HashMap<>();
        for (PmsCategory cat : categories) {
            if (cat.getParentCid() != null && cat.getParentCid() > 0) {
                childrenMap.putIfAbsent(cat.getParentCid(), new ArrayList<>()).add(cat.getCatId());
            }
        }
        for (Long catId : catIds) {
            if (catId == null)
                continue;
            List<Long> descendants = new ArrayList<>();
            Deque<Long> queue = new ArrayDeque<>();
            queue.offer(catId);
            while (!queue.isEmpty()) {
                Long cur = queue.poll();
                descendants.add(cur);
                List<Long> children = childrenMap.get(cur);
                if (children != null)
                    queue.addAll(children);
            }
            result.put(catId, descendants);
        }
        return result;
    }

    @Override
    public void updateChildrenLevel(Long catId, int newLevel) {
        LambdaQueryWrapper<PmsCategory> pmsCategoryLambdaQueryWrapper = new LambdaQueryWrapper<>();
        pmsCategoryLambdaQueryWrapper.eq(PmsCategory::getParentCid, catId);
        List<PmsCategory> pmsCategories = this.baseMapper.selectList(pmsCategoryLambdaQueryWrapper);
        if (CollectionUtils.isEmpty(pmsCategories))
            return;
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
                .filter(category -> parentId.equals(category.getParentCid())).collect(Collectors.toList());
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

    @Override
    public List<CategoryVo> findChildren(Long parentCid) {
        if (parentCid == null) {
            parentCid = 0l;
        }
        List<PmsCategory> pmsCategories = this.lambdaQuery().eq(PmsCategory::getParentCid, parentCid)
                .eq(PmsCategory::getShowStatus, 1)
                .orderByAsc(PmsCategory::getSort)
                .list();
        List<CategoryVo> result = pmsCategories.stream().map(item -> {
            CategoryVo categoryVo = new CategoryVo();
            categoryVo.setCatId(item.getCatId());
            categoryVo.setName(item.getName());
            categoryVo.setParentCid(item.getParentCid());
            categoryVo.setCatLevel(item.getCatLevel());
            categoryVo.setShowStatus(item.getShowStatus());
            categoryVo.setIcon(item.getIcon());
            categoryVo.setProductUnit(item.getProductUnit());
            boolean hasChildren = this.lambdaQuery().eq(PmsCategory::getParentCid, item.getCatId())
                    .eq(PmsCategory::getShowStatus, 1)
                    .count() > 0;
            if (hasChildren) {
                categoryVo.setChildren(new ArrayList());
            } else {
                categoryVo.setChildren(null);
            }
            return categoryVo;
        }).collect(Collectors.toList());
        return result;
    }
}
