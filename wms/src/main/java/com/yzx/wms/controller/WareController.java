package com.yzx.wms.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yzx.model.AjaxResult;
import com.yzx.model.exception.NoStockException;
import com.yzx.model.order.WareSkuLockVo;
import com.yzx.model.wms.WareSkuEntity;
import com.yzx.model.wms.vo.SkuHasStockVo;
import com.yzx.wms.service.IWareSkuService;
import org.aspectj.weaver.loadtime.Aj;
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
@RequestMapping("wms/waresku")
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

    /**
     * 根据skuId修改库存（如果不存在则自动添加）
     */
    @PostMapping("/update/{skuId}/{stock}")
    public AjaxResult updateStockBySkuId(@PathVariable("skuId") Long skuId, @PathVariable("stock") Integer stock) {
        if (Objects.isNull(skuId)) return AjaxResult.error("根据skuId修改库存信息参数错误");
        
        // 1. 先查询是否存在库存记录
        WareSkuEntity existRecord = wareSkuService.getOne(
                new LambdaQueryWrapper<WareSkuEntity>().eq(WareSkuEntity::getSkuId, skuId));
        
        if (existRecord != null) {
            // 2. 存在则更新
            WareSkuEntity updateEntity = new WareSkuEntity();
            updateEntity.setStock(stock);
            boolean result = wareSkuService.update(updateEntity,
                    new LambdaQueryWrapper<WareSkuEntity>().eq(WareSkuEntity::getSkuId, skuId));
            if (result) {
                return AjaxResult.success("修改库存成功");
            } else {
                return AjaxResult.error("修改库存失败");
            }
        } else {
            // 3. 不存在则添加（需要 skuName，这里暂时使用默认值，实际应该通过 Feign 调用 product 服务获取）
            WareSkuEntity newEntity = new WareSkuEntity();
            newEntity.setWareId(1L); // 默认仓库
            newEntity.setSkuId(skuId);
            newEntity.setSkuName("SKU-" + skuId); // 临时使用 SKU ID 作为名称
            newEntity.setStock(stock);
            boolean save = wareSkuService.save(newEntity);
            if (save) {
                return AjaxResult.success("添加库存成功");
            } else {
                return AjaxResult.error("添加库存失败");
            }
        }
    }

    /**
     * 根据skuId添加库存
     */
    @PostMapping("/add/{skuId}/{stock}")
    public AjaxResult addStockBySkuId(@PathVariable Integer stock, @PathVariable("skuId") Long skuId, @RequestParam("skuName") String skuName) {
        if (Objects.isNull(skuId)) return AjaxResult.error("根据skuId添加库存信息参数错误");
        WareSkuEntity wareSkuEntity = new WareSkuEntity();
        //todo 现在使用默认的仓库
        wareSkuEntity.setWareId(1L);
        wareSkuEntity.setSkuName(skuName);
        wareSkuEntity.setStock(stock);
        wareSkuEntity.setSkuId(skuId);
        boolean save = wareSkuService.save(wareSkuEntity);
        if (save) {
            return AjaxResult.success();
        }
        return AjaxResult.error();
    }
}
