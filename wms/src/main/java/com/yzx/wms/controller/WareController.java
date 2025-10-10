package com.yzx.wms.controller;

import com.yzx.model.AjaxResult;
import com.yzx.model.exception.NoStockException;
import com.yzx.model.order.WareSkuLockVo;
import com.yzx.model.wms.vo.SkuHasStockVo;
import com.yzx.wms.service.IWareSkuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
            return AjaxResult.error(NO_STOCK_EXCEPTION.getCode(),NO_STOCK_EXCEPTION.getMessage());
        }
    }

    @PostMapping("/hasstock")
    public AjaxResult hasStock(@RequestBody List<Long> skuIds) {
        List<SkuHasStockVo> vos =  wareSkuService.getSkusHasStock(skuIds);
        return AjaxResult.success(vos);
    }
}
