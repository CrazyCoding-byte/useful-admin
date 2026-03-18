package com.yzx.product.contoller;

import com.yzx.model.AjaxResult;
import com.yzx.product.service.SpuInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotNull;

/**
 * @className: ProductController
 * @author: yzx
 * @date: 2025/9/18 14:49
 * @Version: 1.0
 * @description:
 */
@RequestMapping("/product")
@RestController
@Slf4j
public class ProductController {

    @Autowired
    private SpuInfoService spuInfoService;

    /**
     * 上架商品
     * @param spuId  上架/修改商品
     * @return
     */
    @PostMapping("/upPd/{spuId}")
    public AjaxResult upPd(@PathVariable("spuId") @NotNull(message = "spuId不能为空") String spuId) {
        // 1. 基础日志（便于排查问题）
        log.info("开始执行SPU上架操作，spuId:{}", spuId);
        // 2. 调用服务层执行上架逻辑
        boolean success = spuInfoService.upSpu(spuId);

        // 3. 统一返回结果
        if (success) {
            return AjaxResult.success("SPU上架成功");
        } else {
            return AjaxResult.error("SPU上架失败，请检查SPU是否存在或已上架");
        }
    }

    @GetMapping("Test")
    public AjaxResult ajaxResult(){
        return AjaxResult.success();
    }
}
