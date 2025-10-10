package com.yzx.model.distribution;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * @className: CommissionConfig
 * @author: yzx
 * @date: 2025/10/10 16:53
 * @Version: 1.0
 * @description:
 */
@Data
public class CommissionConfig {
    private Long id;
    private Integer level;
    private BigDecimal rate;
    private Integer status;
    private Date createTime;
    private Date updateTime;
}
