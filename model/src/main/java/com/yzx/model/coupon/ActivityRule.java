package com.yzx.model.coupon;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yzx.model.coupon.eunms.ActivityType;
import com.yzx.model.system.BaseEntity;
import lombok.Data;

import java.math.BigDecimal;

/**
 * <p>
 * ActivityRule
 * </p>
 *
 * @author qy
 */
@Data
@TableName("activity_rule")
public class ActivityRule extends BaseEntity {

    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("activity_id")
    private Long activityId;

    @TableField("activity_type")
    private ActivityType activityType;

    /**
     * 满减金额
     */
    @TableField("condition_amount")
    private BigDecimal conditionAmount;

    /**
     * 满减件数
     */
    @TableField("condition_num")
    private Long conditionNum;

    /**
     * 优惠金额
     */
    @TableField("benefit_amount")
    private BigDecimal benefitAmount;

    /**
     * 优惠折扣
     */
    @TableField("benefit_discount")
    private BigDecimal benefitDiscount;

    @TableField(exist = false)
    private Long skuId;

    @TableField(exist = false)
    private BigDecimal reduceAmount;

    @TableField(exist = false)
    private Integer selectType;

    @TableField(exist = false)
    private String ruleDesc;

}

