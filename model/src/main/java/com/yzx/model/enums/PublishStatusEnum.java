package com.yzx.model.enums;

import lombok.Getter;

/**
 * 商品上架状态枚举
 * 对应 pms_spu_info.publish_status
 */
@Getter
public enum PublishStatusEnum {
    OFF_SALE(0, "下架"),
    ON_SALE(1, "上架");

    private final Integer code;
    private final String msg;

    PublishStatusEnum(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}

