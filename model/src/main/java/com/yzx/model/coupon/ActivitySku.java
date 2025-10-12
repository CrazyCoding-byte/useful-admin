package com.yzx.model.coupon;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yzx.model.system.BaseEntity;
import lombok.Data;

/**
 * <p>
 * ActivitySku
 * </p>
 *
 * @author qy
 */
@Data
@TableName("activity_sku")
public class ActivitySku extends BaseEntity {
	
	private static final long serialVersionUID = 1L;
	@TableId(type = IdType.AUTO)
	private Long id;
	@TableField("activity_id")
	private Long activityId;

	@TableField("sku_id")
	private Long skuId;

//	@TableField(exist = false)
//	private SkuInfo skuInfo;

}

