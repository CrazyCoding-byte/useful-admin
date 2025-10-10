package com.yzx.model.distribution;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * @className: UserTeamInfo
 * @author: yzx
 * @date: 2025/10/10 17:16
 * @Version: 1.0
 * @description:
 */
@Data
public class UserTeamInfo {
    private Integer level1Count = 0;
    private Integer level2Count = 0;
    private List<Long> level1UserIds = new ArrayList<>();
    private List<Long> level2UserIds = new ArrayList<>();
    private BigDecimal totalCommission = BigDecimal.ZERO;
    private BigDecimal pendingCommission = BigDecimal.ZERO;
    private BigDecimal settledCommission = BigDecimal.ZERO;
}
