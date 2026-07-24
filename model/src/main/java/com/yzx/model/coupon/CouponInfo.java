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
/**
 * 前端展示时怎么获取这两个数？
 *
 * - 管理端 list 接口里，对每条 CouponInfo 做一次查询：
 *   - receiveCount = publishCount - coupon_stock.remainCount
 *   - useCount = select count(*) from coupon_use where coupon_id=? and coupon_status=2
 * - 两边的数据不一致（最终一致性），但运营看的大盘数据不需要精确到毫秒级
 */
public class CouponInfo extends BaseEntity {

    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;
    //满减卷/现金卷
    @TableField("coupon_type")
    private CouponType couponType;

    //618满100减20
    @TableField("coupon_name")
    private String couponName;

    //优惠金额
    @TableField("amount")
    private BigDecimal amount;

    //使用门槛
    @TableField("condition_amount")
    private BigDecimal conditionAmount;

    //开始
    @JsonFormat(pattern = "yyyy-MM-dd")
    @TableField("start_time")
    private Date startTime;

    //结束
    @JsonFormat(pattern = "yyyy-MM-dd")
    @TableField("end_time")
    private Date endTime;

    //适用范围
    @TableField("range_type")
    private CouponRangeType rangeType;

    @TableField("range_desc")
    private String rangeDesc;
    //发行总数
    @TableField("publish_count")
    private Integer publishCount;
    /**
     * 每人限领张数,0表示不限
     */
    @TableField("per_limit")
    private Integer perLimit;

    /**
     * 已领取数
     */
    @TableField("use_count")
    private Integer useCount;
    /**
     * 已使用数
     */
    @TableField("receive_count")
    private Integer receiveCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("expire_time")
    private Date expireTime;
    //是否发布
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