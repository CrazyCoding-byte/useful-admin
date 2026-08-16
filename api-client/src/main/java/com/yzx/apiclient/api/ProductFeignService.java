package com.yzx.apiclient.api;

import com.yzx.model.AjaxResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * @className: ProductFeignService
 * @author: yzx
 * @date: 2025/8/29 12:49
 * @Version: 1.0
 * @description:
 */
@FeignClient("product-service")
public interface ProductFeignService {
    /**
     * 根据skuId查询sku信息
     * @param skuId
     * @return
     */
    @RequestMapping("/product/skuinfo/info/{skuId}")
    AjaxResult getInfo(@PathVariable("skuId") Long skuId);

    /**
     * 根据skuId查询pms_sku_sale_attr_value表中的信息
     * @param skuId
     * @return
     */
    @GetMapping(value = "/product/skusaleattrvalue/stringList/{skuId}")
    List<String> getSkuSaleAttrValues(@PathVariable("skuId") Long skuId);

    /**
     * 根据skuId查询当前商品的最新价格
     * @param skuId
     * @return
     */
    @GetMapping(value = "/product/skuinfo/{skuId}/price")
    BigDecimal getPrice(@PathVariable("skuId") Long skuId);

    /**
     * 根据skuId查询spu的信息
     * @param skuId
     * @return
     */
    @GetMapping(value = "/product/spuinfo/skuId/{skuId}")
    public AjaxResult getSpuInfoBySkuId(@PathVariable("skuId") Long skuId);

    /**
     *根据catIds 获取子分类
     * @param catIds
     * @return 返回父 它下面子分类的map
     */
    @PostMapping(value = "/product/category/descendantIds")
    AjaxResult getCategoryDescendantIds(@RequestBody List<Long> catIds);
}
