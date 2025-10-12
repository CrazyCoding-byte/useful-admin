package com.yzx.model.coupon;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yzx.model.order.enums.CouponRangeType;
import com.yzx.model.system.BaseEntity;
import lombok.Data;

/**
 * <p>
 * CouponRange
 * </p>
 *
 * @author qy
 */
@Data
@TableName("coupon_range")
public class CouponRange extends BaseEntity {
	
	private static final long serialVersionUID = 1L;
	@TableId(type = IdType.AUTO)
	private Long id;
	@TableField("coupon_id")
	private Long couponId;

	@TableField("range_type")
	private CouponRangeType rangeType;

	@TableField("range_id")
	private Long rangeId;

}

