package com.yzx.model.coupon;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yzx.model.coupon.eunms.CouponStatus;
import com.yzx.model.system.BaseEntity;
import lombok.Data;

import java.util.Date;

/**
 * <p>
 * CouponUse
 * </p>
 *
 * @author qy
 */
@Data
@TableName("coupon_use")
public class CouponUse extends BaseEntity {
	
	private static final long serialVersionUID = 1L;
	@TableId(type = IdType.AUTO)
	private Long id;
	@TableField("coupon_id")
	private Long couponId;

	@TableField("user_id")
	private Long userId;

	@TableField("order_id")
	private Long orderId;

	@TableField("coupon_status")
	private CouponStatus couponStatus;

	@TableField("get_time")
	private Date getTime;

	@TableField("using_time")
	private Date usingTime;

	@TableField("used_time")
	private Date usedTime;

	@TableField("expire_time")
	private Date expireTime;

}

