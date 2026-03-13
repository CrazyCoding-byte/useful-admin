package com.yzx.common.enums;

import lombok.Getter;

/**
 * 消息状态枚举
 */
@Getter
public enum MessageStatusEnum {
    /**
     * 待确认/待执行
     */
    PENDING(0, "待确认"),
    /**
     * 已完成/已确认
     */
    SUCCESS(1, "已完成"),
    /**
     * 执行失败/确认失败
     */
    FAIL(2, "执行失败");

    private final Integer code;
    private final String desc;

    MessageStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}