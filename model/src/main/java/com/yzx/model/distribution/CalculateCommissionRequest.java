package com.yzx.model.distribution;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * @className: CalculateCommissionRequest
 * @author: yzx
 * @date: 2025/10/10 17:09
 * @Version: 1.0
 * @description:
 */
@Data
public class CalculateCommissionRequest {
    @NotNull(message = "订单ID不能为空")
    private String orderId;      // 改为String类型

    @NotNull(message = "用户ID不能为空")
    private Long userId;         // 保持Long类型

    @NotNull(message = "订单金额不能为空")
    @DecimalMin(value = "0.01", message = "订单金额必须大于0")
    private BigDecimal orderAmount;
}