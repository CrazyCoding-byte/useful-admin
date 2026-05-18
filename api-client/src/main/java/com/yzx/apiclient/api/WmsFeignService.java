package com.yzx.apiclient.api;

import com.yzx.model.AjaxResult;
import com.yzx.model.order.WareSkuLockVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @className: WmsFeignService
 * @author: yzx
 * @date: 2025/8/30 6:57
 * @Version: 1.0
 * @description:
 */
@FeignClient("wms-server")
public interface WmsFeignService {
    /**
     * 查询sku是否有库存
     * @return
     */
    @PostMapping(value = "/wms/waresku/hasStock")
    AjaxResult getSkuHasStock(@RequestBody List<Long> skuIds);


    /**
     * 查询运费和收货地址信息
     * @param addrId
     * @return
     */
    @GetMapping(value = "/wms/waresku/wareinfo/fare")
    AjaxResult getFare(@RequestParam("addrId") Long addrId);


    /**
     * 锁定库存
     * @param vo
     * @return
     */
    @PostMapping(value = "/wms/waresku/lock/order")
    AjaxResult orderLockStock(@RequestBody WareSkuLockVo vo);


    /**
     * 根据SkuId获取库存信息
     */
    @GetMapping("/wms/waresku/getStockBySkuId")
    public AjaxResult getStockBySkuId(@RequestParam("skuId") Long skuId);

    /**
     * 根据skuId添加库存
     */
    @PostMapping("/wms/waresku/add/{skuId}/{stock}")
    public AjaxResult addStockBySkuId(@PathVariable Integer stock, @PathVariable("skuId") Long skuId, @RequestParam("skuName") String skuName);

    /**
     * 根据skuId修改库存
     */
    @PostMapping("/wms/waresku/update/{skuId}/{stock}")
    public AjaxResult updateStockBySkuId(@PathVariable("skuId") Long skuId, @PathVariable("stock") Integer stock);
}
