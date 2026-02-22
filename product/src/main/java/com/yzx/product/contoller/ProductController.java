package com.yzx.product.contoller;

import com.yzx.model.Result;
import com.yzx.product.service.SpuInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @className: ProductController
 * @author: yzx
 * @date: 2025/9/18 14:49
 * @Version: 1.0
 * @description:
 */
@RequestMapping("/product")
@RestController
public class ProductController {

    @Autowired
    private SpuInfoService spuInfoService;

    /**
     * 上架商品
     * @param spuId
     * @return
     */
    @PostMapping("/upPd/{spuId}")
    public Result upPd(String spuId) {
        return spuInfoService.up(spuId);
    }

}
