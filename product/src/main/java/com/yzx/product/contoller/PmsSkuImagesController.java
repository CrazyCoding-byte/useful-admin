package com.yzx.product.contoller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzx.model.AjaxResult;
import com.yzx.model.product.PmsSkuImages;
import com.yzx.product.service.PmsSkuImagesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @className: PmsSkuImagesController
 * @author: yzx
 * @date: 2026/4/6 13:00
 * @Version: 1.0
 * @description: SKU图片管理控制器
 */
@RestController
@RequestMapping("/product/sku/images")
@Slf4j
public class PmsSkuImagesController {

    @Autowired
    private PmsSkuImagesService pmsSkuImagesService;

    /**
     * 分页查询SKU图片列表
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @param params 查询参数（可选：skuId）
     * @return 分页数据
     */
    @PostMapping("/list/{pageNum}/{pageSize}")
    public AjaxResult list(
            @PathVariable Integer pageNum,
            @PathVariable Integer pageSize,
            @RequestBody(required = false) Map<String, Object> params) {
        log.info("查询SKU图片列表，pageNum={}, pageSize={}, params={}", pageNum, pageSize, params);
        
        Page<PmsSkuImages> page = new Page<>(pageNum, pageSize);
        
        // 如果有skuId参数，按skuId查询
        if (params != null && params.containsKey("skuId")) {
            Long skuId = Long.valueOf(params.get("skuId").toString());
            page = pmsSkuImagesService.lambdaQuery()
                    .eq(PmsSkuImages::getSkuId, skuId)
                    .orderByAsc(PmsSkuImages::getImgSort)
                    .page(page);
        } else {
            page = pmsSkuImagesService.page(page);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", page.getRecords());
        result.put("total", page.getTotal());

        return AjaxResult.success(result);
    }

    /**
     * 根据SKU ID查询图片列表
     * @param skuId SKU ID
     * @return 图片列表
     */
    @GetMapping("/list/{skuId}")
    public AjaxResult listBySkuId(@PathVariable Long skuId) {
        log.info("查询SKU图片列表，skuId={}", skuId);
        List<PmsSkuImages> images = pmsSkuImagesService.listBySkuId(skuId);
        return AjaxResult.success(images);
    }

    /**
     * 根据ID查询SKU图片详情
     * @param id 图片ID
     * @return 图片信息
     */
    @GetMapping("/{id}")
    public AjaxResult info(@PathVariable Long id) {
        log.info("查询SKU图片详情，id={}", id);
        PmsSkuImages image = pmsSkuImagesService.getById(id);
        if (image == null) {
            return AjaxResult.error("图片不存在");
        }
        return AjaxResult.success(image);
    }

    /**
     * 新增SKU图片
     * @param pmsSkuImages 图片信息
     * @return 操作结果
     */
    @PostMapping
    public AjaxResult save(@RequestBody PmsSkuImages pmsSkuImages) {
        log.info("新增SKU图片，skuId={}, imgUrl={}", 
                pmsSkuImages.getSkuId(), pmsSkuImages.getImgUrl());
        
        boolean success = pmsSkuImagesService.save(pmsSkuImages);
        
        if (success) {
            return AjaxResult.success("新增成功");
        } else {
            return AjaxResult.error("新增失败");
        }
    }

    /**
     * 修改SKU图片
     * @param pmsSkuImages 图片信息
     * @return 操作结果
     */
    @PutMapping
    public AjaxResult update(@RequestBody PmsSkuImages pmsSkuImages) {
        log.info("修改SKU图片，id={}", pmsSkuImages.getId());
        
        if (pmsSkuImages.getId() == null) {
            return AjaxResult.error("图片ID不能为空");
        }
        
        boolean success = pmsSkuImagesService.updateById(pmsSkuImages);
        
        if (success) {
            return AjaxResult.success("修改成功");
        } else {
            return AjaxResult.error("修改失败");
        }
    }

    /**
     * 保存或更新SKU图片列表（批量操作）
     * @param skuId SKU ID
     * @param images 图片列表
     * @return 操作结果
     */
    @PostMapping("/batch/{skuId}")
    public AjaxResult saveOrUpdateBatch(
            @PathVariable Long skuId,
            @RequestBody List<PmsSkuImages> images) {
        log.info("批量保存或更新SKU图片，skuId={}, 图片数量={}", skuId, images != null ? images.size() : 0);
        
        boolean success = pmsSkuImagesService.saveOrUpdateBySkuId(skuId, images);
        
        if (success) {
            return AjaxResult.success("操作成功");
        } else {
            return AjaxResult.error("操作失败");
        }
    }

    /**
     * 删除SKU图片（支持批量）
     * @param ids 图片ID数组
     * @return 操作结果
     */
    @DeleteMapping("/{ids}")
    public AjaxResult delete(@PathVariable List<Long> ids) {
        log.info("删除SKU图片，ids={}", ids);
        
        try {
            boolean success = pmsSkuImagesService.removeByIds(ids);
            if (success) {
                return AjaxResult.success("删除成功");
            } else {
                return AjaxResult.error("删除失败");
            }
        } catch (Exception e) {
            log.error("删除失败", e);
            return AjaxResult.error("删除失败：" + e.getMessage());
        }
    }

    /**
     * 删除指定SKU的所有图片
     * @param skuId SKU ID
     * @return 操作结果
     */
    @DeleteMapping("/bySkuId/{skuId}")
    public AjaxResult deleteBySkuId(@PathVariable Long skuId) {
        log.info("删除SKU所有图片，skuId={}", skuId);
        
        boolean success = pmsSkuImagesService.removeBySkuId(skuId);
        
        if (success) {
            return AjaxResult.success("删除成功");
        } else {
            return AjaxResult.error("删除失败");
        }
    }
}
