package com.yzx.product.contoller;

import com.yzx.model.AjaxResult;
import com.yzx.model.product.PmsCategory;
import com.yzx.model.product.vo.PmsGroupVo;
import com.yzx.product.service.ProductCateGoryService;
import com.yzx.product.service.SpuInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @className: PmsCategory
 * @author: yzx
 * @date: 2026/8/1 23:26
 * @Version: 1.0
 * @description:
 */
@RequestMapping("product")
@RestController
@Slf4j
public class PmsCategoryController {
    @Autowired
    private ProductCateGoryService productCateGoryService;
    @Autowired
    private SpuInfoService spuInfoService;

    /**
     *  分类列表接口
     * @param params
     * @return
     */
    @PostMapping("/CategoryList")
    public AjaxResult getAllAttr(@RequestBody(required = false) Map<String, Object> params) {
        AjaxResult result = productCateGoryService.getCateGory(params);
        return result;
    }

    /**
     * 根据分类id查询属性
     * @param id
     * @return
     */
    @GetMapping("/getAttrByCategoryId/{id}")
    public AjaxResult getAttrByCategoryId(@PathVariable Long id) {
        log.info("查询商品属性，id={}", id);
        List<PmsGroupVo> attrs = spuInfoService.getAttrByCategoryId(id);
        return AjaxResult.success(attrs);
    }

    /**
     * 保存修改
     * @param category
     * @return
     */
    @PostMapping("/category/save")
    @CacheEvict(value = "category", allEntries = true)
    public AjaxResult save(@RequestBody PmsCategory category) {
        //判断是否修改+父类是否变了
        if (category.getCatId() != null) {
            PmsCategory old = productCateGoryService.getById(category.getCatId());
            if (old != null && !Objects.equals(old.getParentCid(), category.getCatId())) {
                //父类变了+计算新层级
                int newLevel = calcLevel(category.getParentCid());
                category.setCatLevel(newLevel);
                //同步更新所有子孙层级
                productCateGoryService.updateChildrenLevel(category.getCatId(), newLevel);
            }
        } else {
// 新增时自动算层级
            int newLevel = calcLevel(category.getParentCid());
            category.setCatLevel(newLevel);
        }
        boolean success = productCateGoryService.saveOrUpdate(category);
        return success ? AjaxResult.success("操作成功") : AjaxResult.error("操作失败");
    }

    private int calcLevel(Long parentCid) {
        if (parentCid == null || parentCid == 0) return 1;
        PmsCategory parent = productCateGoryService.getById(parentCid);
        return parent != null ? parent.getCatLevel() + 1 : 0;
    }

    /**
     * 删除分类
     */
    @PostMapping("/category/delete")
    @CacheEvict(value = "category", allEntries = true)
    public AjaxResult deleteCategory(@RequestBody List<Long> ids) {
        productCateGoryService.removeByIds(ids);
        return AjaxResult.success("删除成功");
    }

    @GetMapping("/category/parentTree/{catId}")
    public AjaxResult getParentCategoryTree(@PathVariable(required = false) Long catId) {
        AjaxResult result = productCateGoryService.getParentTree(catId);
        return result;
    }
}
