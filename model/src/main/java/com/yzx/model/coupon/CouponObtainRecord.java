package com.yzx.model.coupon;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * @className: CouponObtainRecord
 * @author: yzx
 * @date: 2026/7/23 16:10
 * @Version: 1.0
 * @description:
/**
 * 优惠券领取流水表。
 *
 * 【为什么要多一张表？coupon_use 不是已经记录了谁领了券吗？】
 * 1. 幂等：领券接口可能被重复调用（网络超时、前端重试、MQ 重投），
 *    唯一索引 (user_id, coupon_id, obtain_time) 保证一条流水只插入一次。
 * 2. 审计独立性：coupon_use 表可能会被业务代码修改/删除（退款退券等），
 *    但领取流水是不能改的，就像银行的流水不能删一样。
 * 3. 时序保证：先写流水（唯一索引拦截），再写 coupon_use。
 *    如果先写 coupon_use 再写流水，coupon_use 插入成功了但流水插入失败，
 *    就不知道这张券到底是谁领的。
 */

@Data
@TableName("coupon_obtain_record")
public class CouponObtainRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("use_id")
    private Long useId;
    @TableField("coupon_id")
    private Long couponId;
    @TableField("obtain_id")
    private Date obtainTime;
    /** MANUAL-手动领取  ACTIVITY-活动发放  SYSTEM-系统补偿 */
    @TableField("source")
    private String source;
}
