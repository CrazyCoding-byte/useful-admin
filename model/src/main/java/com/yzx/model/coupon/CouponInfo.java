package com.yzx.model.coupon;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import com.yzx.model.order.enums.CouponRangeType;
import com.yzx.model.order.enums.CouponType;
import com.yzx.model.system.BaseEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@TableName("coupon_info")
public class CouponInfo extends BaseEntity {

    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("coupon_type")
    private CouponType couponType;

    @TableField("coupon_name")
    private String couponName;

    @TableField("amount")
    private BigDecimal amount;

    @TableField("condition_amount")
    private BigDecimal conditionAmount;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @TableField("start_time")
    private Date startTime;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @TableField("end_time")
    private Date endTime;

    @TableField("range_type")
    private CouponRangeType rangeType;

    @TableField("range_desc")
    private String rangeDesc;

    @TableField("publish_count")
    private Integer publishCount;

    @TableField("per_limit")
    private Integer perLimit;

    @TableField("use_count")
    private Integer useCount;

    @TableField("receive_count")
    private Integer receiveCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("expire_time")
    private Date expireTime;

    @TableField("publish_status")
    private Boolean publishStatus;

    @TableField(exist = false)
    private String couponTypeString;
    @TableField(exist = false)
    private String rangeTypeString;

    @TableField(exist = false)
    private Integer couponStatus;

    @TableField(exist = false)
    private Integer isSelect = 0;

    @TableField(exist = false)
    private Integer isOptimal = 0;

    @TableField(exist = false)
    private List<Long> skuIdList;
}