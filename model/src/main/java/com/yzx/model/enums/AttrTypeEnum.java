package com.yzx.model.enums;

import lombok.Getter;

/**
 * 属性类型枚举
 * 对应 pms_attr.attr_type
 */
@Getter
public enum AttrTypeEnum {
    SALE_ATTR(0, "销售属性"),
    BASE_ATTR(1, "基本属性");

    private final Integer code;
    private final String msg;

    AttrTypeEnum(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}