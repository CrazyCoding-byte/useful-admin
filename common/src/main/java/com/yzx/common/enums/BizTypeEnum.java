package com.yzx.common.enums;

import lombok.Getter;

/**
 * 通用业务类型枚举（所有项目统一维护）
 */
@Getter
public enum BizTypeEnum {
    /**
     * 商品上架
     */
    PRODUCT_UP("PRODUCT_UP", "商品上架"),
    /**
     * 订单延迟任务
     */
    ORDER_DELAY("ORDER_DELAY", "订单延迟任务"),
    /**
     * 库存锁定
     */
    STOCK_LOCK("STOCK_LOCK", "库存锁定"),
    /**
     * 通用本地任务
     */
    LOCAL_TASK("LOCAL_TASK", "本地通用任务");

    private final String code;
    private final String desc;

    BizTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}