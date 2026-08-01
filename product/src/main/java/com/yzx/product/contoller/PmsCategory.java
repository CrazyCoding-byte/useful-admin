package com.yzx.product.contoller;

import com.yzx.model.AjaxResult;
import com.yzx.model.product.vo.PmsGroupVo;
import com.yzx.product.service.ProductCateGoryService;
import com.yzx.product.service.SpuInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
public class PmsCategory {
    @Autowired
    private ProductCateGoryService productCateGoryService;
    @Autowired
    private SpuInfoService spuInfoService;

    /**
     *  分类列表接口
     * @param pageNum
     * @param pageSize
     * @param params
     * @return
     */
    @PostMapping("/CategoryList")
    public AjaxResult getAllAttr( @RequestBody(required = false) Map<String, Object> params) {
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
}
