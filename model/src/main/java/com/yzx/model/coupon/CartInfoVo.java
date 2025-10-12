package com.yzx.model.coupon;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.yzx.model.cart.vo.CartItemVo;
import com.yzx.model.cart.vo.CartVo;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * OrderDetailActivity
 * </p>
 *
 * @author qy
 */
@Data
public class CartInfoVo implements Serializable {
	
	private static final long serialVersionUID = 1L;
	@TableId(type = IdType.AUTO)
	private Long id;
	/**
	 * 购物项凑单，同一活动对应的最优活动规则
	 */
	private List<CartItemVo> cartInfoList;

	private ActivityRule activityRule;

}

