package com.yzx.coupon.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yzx.coupon.mapper.CouponInfoMapper;
import com.yzx.coupon.mapper.CouponObtainRecordMapper;
import com.yzx.coupon.mapper.CouponStockMapper;
import com.yzx.coupon.mapper.CouponUseMapper;
import com.yzx.model.coupon.CouponInfo;
import com.yzx.model.coupon.CouponObtainRecord;
import com.yzx.model.coupon.CouponStock;
import com.yzx.model.coupon.CouponUse;
import com.yzx.model.coupon.eunms.CouponStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * @className: CouponObtainService
 * @author: yzx
 * @date: 2026/7/23 16:26
 * @Version: 1.0
 * @description:
 */
@Service
@Slf4j
public class CouponObtainServiceImpl {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private CouponInfoMapper couponInfoMapper;
    @Autowired
    private CouponStockMapper couponStockMapper;
    @Autowired
    private CouponUseMapper couponUseMapper;
    @Autowired
    private CouponObtainRecordMapper obtainRecordMapper;
    private static final String STOCK_KEY = "coupon:stock:";
    private static final String LOCK_KEY = "coupon:obtain:lock:";
    private static final String OBTAIN_QUEUE = "coupon.obtain.queue";

    /**
     * 用户领取优惠卷
     * @param userId
     * @param couponId
     * @return
     */
    public boolean obtainCoupon(Long userId, Long couponId) {
        String lockKey = LOCK_KEY + userId + ":" + couponId;
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 5, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(locked)) {
            log.warn("重复领取拦截:userId={},couponId={}", userId, couponId);
            return false;
        }
        String stockkey = STOCK_KEY + couponId;
        Long remain = stringRedisTemplate.opsForValue().decrement(stockkey);
        //库存耗尽
        if (remain == null || remain < 0) {
            stringRedisTemplate.opsForValue().increment(stockkey);
            log.warn("库存不足:couponId={},remain={}", couponId, remain);
            return false;
        }

        try {
            CouponObtainMessage msg = new CouponObtainMessage(userId, couponId);
            stringRedisTemplate.convertAndSend(OBTAIN_QUEUE, msg);
            log.info("领券成功(已发MQ): userId={}, couponId={}", userId, couponId);
            return true;
        } catch (Exception e) {
            // MQ 发送失败 → 回滚库存，不能白扣
            stringRedisTemplate.opsForValue().increment(stockkey);
            log.error("MQ 发送失败，库存已回滚", e);
            return false;
        }
    }

    @RabbitListener(queues = OBTAIN_QUEUE)
    @Transactional(rollbackFor = Exception.class)
    public void handleObtainMessage(CouponObtainMessage msg) {
        Long userId = msg.getUserId();
        Long couponId = msg.getCouponId();
        try {
            //插入领取流水
            CouponObtainRecord record = new CouponObtainRecord();
            record.setUseId(userId);
            record.setCouponId(couponId);
            record.setObtainTime(new Date());
            record.setSource("MANUAL");
            obtainRecordMapper.insert(record);
            //插入用户卷包
            CouponUse couponUse = new CouponUse();
            couponUse.setUserId(userId);
            couponUse.setCouponId(couponId);
            couponUse.setCouponStatus(CouponStatus.NOT_USED);
            couponUse.setGetTime(new Date());

            //从卷模板取过期时间
            CouponInfo info = couponInfoMapper.selectById(couponId);
            if (info != null && info.getExpireTime() != null) {
                couponUse.setExpireTime(info.getExpireTime());
            }
            couponUseMapper.insert(couponUse);
            CouponStock stock = couponStockMapper.selectOne(new LambdaQueryWrapper<CouponStock>().eq(CouponStock::getCouponId, couponId));
            if (stock != null && stock.getRemainCount() > 0) {
                stock.setRemainCount(stock.getRemainCount() - 1);
                couponStockMapper.updateById(stock);
            }
            log.info("落库完成:userId={},couponId={}", userId, couponId);
        } catch (Exception e) {
            // 唯一键冲突 = MQ 重复投递，直接吞掉（消息 ACK）
            if (e.getMessage() != null && e.getMessage().contains("Duplicate")) {
                log.warn("重复消费，跳过: userId={}, couponId={}", userId, couponId);
                return;
            }
            // 其他异常：抛出去，MQ 重新投递
            throw e;
        }
    }

    /**
     * 运营发布优惠券时调用：把 publish_count 写到 Redis。
     * 一定要在券可被领取之前调用，否则 Redis 里没有 key，decr 返回 null。
     */
    public void warmupStock(Long couponId, Integer publishCount) {
        stringRedisTemplate.opsForValue().set(STOCK_KEY + couponId, String.valueOf(publishCount));
        log.info("库存预热完成:couponId={},publishCount={}", couponId, publishCount);

    }


    public static class CouponObtainMessage {
        private Long userId;
        private Long couponId;

        public CouponObtainMessage() {
        }

        public CouponObtainMessage(Long userId, Long couponId) {
            this.userId = userId;
            this.couponId = couponId;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public Long getCouponId() {
            return couponId;
        }

        public void setCouponId(Long couponId) {
            this.couponId = couponId;
        }
    }
}
