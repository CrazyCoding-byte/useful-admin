package com.yzx.wms.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yzx.model.AjaxResult;
import com.yzx.model.exception.NoStockException;
import com.yzx.model.order.WareSkuLockVo;
import com.yzx.model.wms.WareSkuEntity;
import com.yzx.model.wms.vo.SkuHasStockVo;
import com.yzx.wms.service.IWareSkuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

import static com.yzx.model.enums.BizCodeEnum.NO_STOCK_EXCEPTION;

/**
 * @className: WareController
 * @author: yzx
 * @date: 2025/9/2 7:14
 * @Version: 1.0
 * @description:
 */
@RestController
@RequestMapping("ware/waresku")
public class WareController {

    @Autowired
    private IWareSkuService wareSkuService;


    @PostMapping("/lock/order")
    public AjaxResult lockOrder(@RequestBody WareSkuLockVo vo) {
        try {
            boolean lockStock = wareSkuService.orderLockStock(vo);
            return AjaxResult.success(lockStock);
        } catch (NoStockException e) {
            return AjaxResult.error(NO_STOCK_EXCEPTION.getCode(), NO_STOCK_EXCEPTION.getMessage());
        }
    }

    @PostMapping("/hasstock")
    public AjaxResult hasStock(@RequestBody List<Long> skuIds) {
        List<SkuHasStockVo> vos = wareSkuService.getSkusHasStock(skuIds);
        return AjaxResult.success(vos);
    }

    /**
     * 根据SkuId获取库存信息
     */
    @GetMapping("/getStockBySkuId")
    public AjaxResult getStockBySkuId(@RequestParam("skuId") Long skuId) {
        if (Objects.isNull(skuId)) return AjaxResult.error("根据skuId获取库存信息参数错误");
        WareSkuEntity wareSkuEntity = wareSkuService.getOne(new QueryWrapper<WareSkuEntity>().eq("sku_id", skuId));
        return AjaxResult.success(wareSkuEntity);
    }
}
