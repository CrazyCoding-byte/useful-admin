package com.yzx.model.distribution;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * @className: CommissionRecord
 * @author: yzx
 * @date: 2025/10/10 16:53
 * @Version: 1.0
 * @description:
 */
@Data
public class CommissionRecord {
    private Long id;
    private String orderId;      // 改为String类型
    private Long userId;         // 保持Long类型
    private Long fromUserId;     // 保持Long类型
    private Integer level;
    private BigDecimal amount;
    private BigDecimal rate;
    private BigDecimal orderAmount;
    private Integer status;
    private Date createTime;
    private Date settleTime;
}