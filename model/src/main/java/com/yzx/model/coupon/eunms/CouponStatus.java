package com.yzx.model.coupon.eunms;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum CouponStatus {
    NOT_USED(1, "未使用"),
    USED(2, "已使用"),

    LOCKED(0, "已锁定");

    @EnumValue
    private Integer code;
    private String comment;

    CouponStatus(Integer code, String comment) {
        this.code = code;
        this.comment = comment;
    }
}