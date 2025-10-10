package com.yzx.model.distribution;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * @className: BuildRelationRequest
 * @author: yzx
 * @date: 2025/10/10 17:09
 * @Version: 1.0
 * @description:
 */
@Data
public class BuildRelationRequest {
    @NotNull(message = "用户ID不能为空")
    private Long userId;         // 改为Long类型

    @NotBlank(message = "邀请码不能为空")
    private String inviteQrCode;
}