package com.yzx.model.coupon;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @className: CouponStock
 * @author: yzx
 * @date: 2026/7/23 15:11
 * @Version: 1.0
 * @description:
 * /**
 *  * 优惠券库存表。
 *  *
 *  * 为什么拆出来？
 *  *   如果把 remain_count 放 coupon_info 里，10000 人同时领券，
 *  *   全部执行 update coupon_info set remain_count = remain_count - 1 where id = ?，
 *  *   MySQL InnoDB 对同一行的 update 是串行的（行锁），
 *  *   实际 TPS = 1000ms / 每次锁持有时间，可能只有 50~200。
 *  *
 *  * 拆成独立表后：
 *  *   - coupon_info 只读不写，无锁竞争
 *  *   - coupon_stock 也有一行，但实际并发扣减由 Redis 完成
 *  *   - coupon_stock 只在 MQ 消费者里异步更新，作为兜底持久化
 *  *
 *  * version 乐观锁：
 *  *   @Version 注解配合 MyBatis-Plus，update 时自动加 version = version + 1
 *  *   where version = 旧值。如果两个 MQ 消费线程同时更新，有一个会失败重试。
 *  *
 **
 */
@Data
@TableName("coupon_stock")
public class CouponStock {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long couponId;
    private Integer remainCount;  // 剩余库存
    private Integer version;   // 乐观锁
}
