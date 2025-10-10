package com.yzx.model.distribution;

import lombok.Data;

import java.util.Date;

/**
 * @className: DistributionRelation
 * @author: yzx
 * @date: 2025/10/10 16:53
 * @Version: 1.0
 * @description:
 */
@Data
public class DistributionRelation {
    private Long id;
    private Long userId;
    private Long parentId;
    private Long grandparentId;
    private Integer level;
    private Date createTime;
}