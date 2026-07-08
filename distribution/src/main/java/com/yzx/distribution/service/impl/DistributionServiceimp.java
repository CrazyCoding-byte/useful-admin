package com.yzx.distribution.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzx.apiclient.api.SystemApi;
import com.yzx.distribution.mapper.DistributionRelationMapper;
import com.yzx.distribution.service.CommissionConfigService;
import com.yzx.distribution.service.CommissionRecordService;
import com.yzx.distribution.service.DistributionService;
import com.yzx.model.AjaxResult;
import com.yzx.model.StringUtils;
import com.yzx.model.distribution.CommissionConfig;
import com.yzx.model.distribution.CommissionRecord;
import com.yzx.model.distribution.DistributionRelation;
import com.yzx.model.distribution.UserTeamInfo;
import com.yzx.model.system.SysUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @className: DistributionServiceimp
 * @author: yzx
 * @date: 2025/10/10 16:55
 * @Version: 1.0
 * @description:
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DistributionServiceimp extends ServiceImpl<DistributionRelationMapper, DistributionRelation> implements DistributionService {

    public final CommissionConfigService commissionConfigService;
    public final CommissionRecordService commissionRecordService;
    public final SystemApi api;

    /**
     * 建立分销关系
     */
    @Override
    @Transactional
    public boolean buildDistributionRelation(Long userId, String inviteQrCode) {
        log.info("开始为用户 {} 建立分销关系", userId);

        // 1. 检查是否已存在分销关系
        DistributionRelation existingRelation = this.getOne(
                new QueryWrapper<DistributionRelation>().eq("user_id", userId)
        );
        if (existingRelation != null) {
            log.info("用户 {} 已存在分销关系", userId);
            return true;
        }

        // 2. 如果没有邀请码，不建立关系
        if (StringUtils.isBlank(inviteQrCode)) {
            log.info("用户 {} 无邀请码，不建立分销关系", userId);
            return true;
        }

        // 3. 根据邀请码查找邀请人
        AjaxResult inviterResult = api.getUserInfoByQrCode(inviteQrCode);
        if (inviterResult.get("data") == null) {
            log.info("邀请码 {} 无效，用户 {} 无上级", inviteQrCode, userId);
            return true;
        }

        SysUser inviter = (SysUser) inviterResult.get("data");
        Long inviterUserId = inviter.getUserId();

        // 4. 防止自己邀请自己
        if (userId.equals(inviterUserId)) {
            log.warn("用户 {} 不能邀请自己", userId);
            return true;
        }

        // 5. 建立分销关系
        DistributionRelation relation = new DistributionRelation();
        relation.setUserId(userId);
        relation.setParentId(inviterUserId);
        relation.setLevel(1);
        relation.setCreateTime(new Date());

        // 6. 查找邀请人的上级作为间接上级
        DistributionRelation inviterRelation = this.getOne(
                new QueryWrapper<DistributionRelation>().eq("user_id", inviterUserId)
        );
        if (inviterRelation != null && inviterRelation.getParentId() != null) {
            relation.setGrandparentId(inviterRelation.getParentId());
            log.info("找到间接上级: {}", relation.getGrandparentId());
        }

        // 7. 保存分销关系
        boolean saveResult = this.save(relation);
        if (saveResult) {
            log.info("为用户 {} 建立分销关系成功，直接上级: {}, 间接上级: {}",
                    userId, inviterUserId, relation.getGrandparentId());
        } else {
            log.error("为用户 {} 保存分销关系失败", userId);
        }

        return saveResult;
    }

    /**
     * 计算订单佣金（最终优化版）
     */
    @Override
    @Transactional
    public void calculateOrderCommission(String orderId, Long userId, BigDecimal orderAmount) {
        log.info("开始计算订单佣金，订单ID: {}, 用户ID: {}, 订单金额: {}", orderId, userId, orderAmount);

        // 1. 参数验证
        if (StringUtils.isBlank(orderId) || userId == null || orderAmount == null) {
            log.error("佣金计算参数错误");
            return;
        }

        if (orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("订单金额为0或负数，不计算佣金");
            return;
        }

        // 2. 查询分销关系
        DistributionRelation relation = this.getOne(
                new QueryWrapper<DistributionRelation>().eq("user_id", userId)
        );

        if (relation == null) {
            log.info("用户 {} 无分销关系，不计算佣金", userId);
            return;
        }

        boolean hasCommission = false;

        // 3. 计算直接上级佣金
        if (relation.getParentId() != null) {
            calculateAndSaveCommission(orderId, relation.getParentId(), userId, 1, orderAmount);
            hasCommission = true;
            log.debug("计算直接上级佣金，上级ID: {}", relation.getParentId());
        }

        // 4. 计算间接上级佣金
        if (relation.getGrandparentId() != null) {
            calculateAndSaveCommission(orderId, relation.getGrandparentId(), userId, 2, orderAmount);
            hasCommission = true;
            log.debug("计算间接上级佣金，上级ID: {}", relation.getGrandparentId());
        }

        if (hasCommission) {
            log.info("订单 {} 佣金计算完成，用户: {}, 金额: {}", orderId, userId, orderAmount);
        } else {
            log.info("订单 {} 无有效上级，未计算佣金", orderId);
        }
    }


    /**
     * 计算并保存佣金记录
     * commissionUserId 获取资金的用户id 金钱流入
     * formUserId 来源用户id  金钱流出
     */
    private void calculateAndSaveCommission(String orderId, Long commissionUserId,
                                            Long fromUserId, Integer level, BigDecimal orderAmount) {
        // 1. 参数验证
        if (StringUtils.isBlank(orderId) || commissionUserId == null ||
                fromUserId == null || level == null || orderAmount == null) {
            log.error("佣金记录参数错误，orderId: {}, commissionUserId: {}, fromUserId: {}, level: {}, orderAmount: {}",
                    orderId, commissionUserId, fromUserId, level, orderAmount);
            return;
        }

        // 2. 获取佣金配置
        CommissionConfig config = commissionConfigService.getCommissionConfigByLevel(level);
        if (config == null) {
            log.warn("层级 {} 的佣金配置不存在", level);
            return;
        }

        if (!config.getStatus().equals(1)) {
            log.warn("层级 {} 的佣金配置已禁用", level);
            return;
        }

        // 3. 计算佣金金额
        BigDecimal commissionAmount = orderAmount.multiply(config.getRate());
        if (commissionAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("佣金金额为0，不保存记录，用户: {}, 层级: {}", commissionUserId, level);
            return;
        }

        // 4. 检查是否已存在相同订单的佣金记录（防止重复计算） 一个订单只能计算一次佣金
        Long exists = commissionRecordService.count(new QueryWrapper<CommissionRecord>()
                .eq("order_id", orderId)
                .eq("user_id", commissionUserId)
                .eq("level", level));
        if (exists > 0) {
            log.warn("订单 {} 的 {} 级佣金记录已存在，用户: {}", orderId, level, commissionUserId);
            return;
        }

        // 5. 创建佣金记录
        CommissionRecord record = new CommissionRecord();
        record.setOrderId(orderId);
        record.setUserId(commissionUserId);
        record.setFromUserId(fromUserId);
        record.setLevel(level);
        record.setAmount(commissionAmount);
        record.setRate(config.getRate());
        record.setOrderAmount(orderAmount);
        record.setStatus(1); // 待结算
        record.setCreateTime(new Date());

        // 6. 保存佣金记录
        boolean saveResult = commissionRecordService.save(record);
        if (saveResult) {
            log.info("为用户 {} 生成 {} 级佣金 {} 元，来源用户: {}, 订单: {}",
                    commissionUserId, level, commissionAmount, fromUserId, orderId);
        } else {
            log.error("保存佣金记录失败，用户: {}, 层级: {}, 金额: {}",
                    commissionUserId, level, commissionAmount);
        }
    }

    /**
     * 获取用户团队信息
     */
    @Override
    public UserTeamInfo getUserTeamInfo(Long userId) {
        UserTeamInfo teamInfo = new UserTeamInfo();

        // 获取直接下级（一级团队）
        List<DistributionRelation> level1Team = this.list(
                new QueryWrapper<DistributionRelation>().eq("parent_id", userId)
        );
        teamInfo.setLevel1Count(level1Team.size());
        teamInfo.setLevel1UserIds(level1Team.stream()
                .map(DistributionRelation::getUserId)
                .collect(Collectors.toList()));

        // 获取间接下级（二级团队）
        if (!level1Team.isEmpty()) {
            List<Long> level1UserIds = level1Team.stream()
                    .map(DistributionRelation::getUserId)
                    .collect(Collectors.toList());

            List<DistributionRelation> level2Team = this.list(
                    new QueryWrapper<DistributionRelation>().in("parent_id", level1UserIds)
            );
            teamInfo.setLevel2Count(level2Team.size());
            teamInfo.setLevel2UserIds(level2Team.stream()
                    .map(DistributionRelation::getUserId)
                    .collect(Collectors.toList()));
        }

        // 计算佣金统计
        calculateCommissionStats(userId, teamInfo);

        return teamInfo;
    }

    /**
     * 计算佣金统计信息
     */
    private void calculateCommissionStats(Long userId, UserTeamInfo teamInfo) {
        // 总佣金
        BigDecimal totalCommission = commissionRecordService.getTotalCommissionByUserId(userId.toString());
        teamInfo.setTotalCommission(totalCommission != null ? totalCommission : BigDecimal.ZERO);

        // 待结算佣金
        BigDecimal pendingCommission = commissionRecordService.getPendingCommissionByUserId(userId.toString());
        teamInfo.setPendingCommission(pendingCommission != null ? pendingCommission : BigDecimal.ZERO);

        // 已结算佣金
        BigDecimal settledCommission = commissionRecordService.getSettledCommissionByUserId(userId.toString());
        teamInfo.setSettledCommission(settledCommission != null ? settledCommission : BigDecimal.ZERO);
    }

    /**
     * 获取用户的佣金记录
     */
    @Override
    public List<CommissionRecord> getUserCommissionRecords(Long userId, Integer status) {
        QueryWrapper<CommissionRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);

        if (status != null) {
            queryWrapper.eq("status", status);
        }
        queryWrapper.orderByDesc("create_time");
        return commissionRecordService.list(queryWrapper);
    }

    /**
     * 结算佣金
     */
    @Override
    @Transactional
    public boolean settleCommission(Long commissionRecordId) {
        CommissionRecord record = commissionRecordService.getById(commissionRecordId);
        if (record == null) {
            throw new RuntimeException("佣金记录不存在");
        }

        if (record.getStatus().equals(2)) {
            log.info("佣金记录 {} 已结算", commissionRecordId);
            return true;
        }

        record.setStatus(2); // 已结算
        record.setSettleTime(new Date());

        boolean updateResult = commissionRecordService.updateById(record);
        if (updateResult) {
            log.info("佣金记录 {} 结算完成，金额: {}", commissionRecordId, record.getAmount());
        }

        return updateResult;
    }

    /**
     * 批量结算佣金
     */
    @Override
    @Transactional
    public boolean batchSettleCommission(List<Long> commissionRecordIds) {
        if (commissionRecordIds == null || commissionRecordIds.isEmpty()) {
            return true;
        }

        List<CommissionRecord> records = commissionRecordService.listByIds(commissionRecordIds);
        if (records.isEmpty()) {
            return true;
        }

        Date settleTime = new Date();
        List<CommissionRecord> toUpdate = records.stream()
                .filter(record -> !record.getStatus().equals(2))
                .peek(record -> {
                    record.setStatus(2);
                    record.setSettleTime(settleTime);
                })
                .collect(Collectors.toList());

        if (toUpdate.isEmpty()) {
            return true;
        }

        boolean updateResult = commissionRecordService.updateBatchById(toUpdate);
        log.info("批量结算佣金完成，记录数: {}, 成功: {}", toUpdate.size(), updateResult);

        return updateResult;
    }

    /**
     * 重新计算订单佣金
     */
    @Override
    @Transactional
    public void recalculateOrderCommission(String orderId, Long userId, BigDecimal newOrderAmount) {
        log.info("重新计算订单佣金，订单ID: {}, 用户ID: {}, 新金额: {}", orderId, userId, newOrderAmount);

        // 1. 删除原有的佣金记录
        boolean deleteResult = commissionRecordService.remove(
                new QueryWrapper<CommissionRecord>().eq("order_id", orderId)
        );

        if (deleteResult) {
            log.info("已删除订单 {} 的原有佣金记录", orderId);
        } else {
            log.warn("删除订单 {} 的原有佣金记录失败", orderId);
        }

        // 2. 重新计算佣金
        calculateOrderCommission(orderId, userId, newOrderAmount);

        log.info("订单 {} 佣金重新计算完成", orderId);
    }
}