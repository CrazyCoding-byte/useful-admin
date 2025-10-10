package com.yzx.model.wms.vo;

import lombok.Data;

/**
 * @className: SkuHasStockVo
 * @author: yzx
 * @date: 2025/9/19 14:36
 * @Version: 1.0
 * @description:
 */
@Data
public class SkuHasStockVo {
    private Long skuId;
    private Boolean hasStock;
}
