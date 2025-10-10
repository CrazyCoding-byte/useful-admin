package com.yzx.distribution.controller;

import com.yzx.distribution.service.DistributionService;
import com.yzx.model.AjaxResult;
import com.yzx.model.distribution.BuildRelationRequest;
import com.yzx.model.distribution.CalculateCommissionRequest;
import com.yzx.model.distribution.CommissionRecord;
import com.yzx.model.distribution.UserTeamInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @className: DistributionController
 * @author: yzx
 * @date: 2025/10/10 17:03
 * @Version: 1.0
 * @description: 分销系统控制器
 */
@RestController
@RequestMapping("/api/distribution")
@Validated
public class DistributionController {

    @Autowired
    private DistributionService distributionService;

    /**
     * 建立分销关系
     */
    @PostMapping("/build-relation")
    public AjaxResult buildRelation(@RequestBody @Validated BuildRelationRequest request) {
        try {
            boolean result = distributionService.buildDistributionRelation(
                    request.getUserId(),
                    request.getInviteQrCode()
            );
            return AjaxResult.success(result);
        } catch (Exception e) {
            return AjaxResult.error("建立分销关系失败: " + e.getMessage());
        }
    }

    /**
     * 计算订单佣金
     */
    @PostMapping("/calculate-commission")
    public AjaxResult calculateCommission(@RequestBody @Validated CalculateCommissionRequest request) {
        try {
            distributionService.calculateOrderCommission(
                    request.getOrderId(),
                    request.getUserId(),
                    request.getOrderAmount()
            );
            return AjaxResult.success("佣金计算完成");
        } catch (Exception e) {
            return AjaxResult.error("计算佣金失败: " + e.getMessage());
        }
    }

    /**
     * 获取团队信息
     */
    @GetMapping("/team-info/{userId}")
    public AjaxResult getTeamInfo(@PathVariable Long userId) {
        try {
            UserTeamInfo teamInfo = distributionService.getUserTeamInfo(userId);
            return AjaxResult.success(teamInfo);
        } catch (Exception e) {
            return AjaxResult.error("获取团队信息失败: " + e.getMessage());
        }
    }

    /**
     * 获取佣金记录
     */
    @GetMapping("/commission-records/{userId}")
    public AjaxResult getCommissionRecords(
            @PathVariable Long userId,
            @RequestParam(required = false) Integer status) {
        try {
            List<CommissionRecord> records = distributionService.getUserCommissionRecords(userId, status);
            return AjaxResult.success(records);
        } catch (Exception e) {
            return AjaxResult.error("获取佣金记录失败: " + e.getMessage());
        }
    }

    /**
     * 结算佣金
     */
    @PostMapping("/settle-commission")
    public AjaxResult settleCommission(@RequestParam Long commissionRecordId) {
        try {
            distributionService.settleCommission(commissionRecordId);
            return AjaxResult.success("佣金结算成功");
        } catch (Exception e) {
            return AjaxResult.error("结算佣金失败: " + e.getMessage());
        }
    }

    /**
     * 批量结算佣金
     */
    @PostMapping("/batch-settle-commission")
    public AjaxResult batchSettleCommission(@RequestBody List<Long> commissionRecordIds) {
        try {
            boolean result = distributionService.batchSettleCommission(commissionRecordIds);
            return AjaxResult.success(result);
        } catch (Exception e) {
            return AjaxResult.error("批量结算佣金失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户的佣金统计
     */
    @GetMapping("/commission-stats/{userId}")
    public AjaxResult getCommissionStats(@PathVariable Long userId) {
        try {
            UserTeamInfo teamInfo = distributionService.getUserTeamInfo(userId);

            // 提取佣金统计信息
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalCommission", teamInfo.getTotalCommission());
            stats.put("pendingCommission", teamInfo.getPendingCommission());
            stats.put("settledCommission", teamInfo.getSettledCommission());
            stats.put("level1Count", teamInfo.getLevel1Count());
            stats.put("level2Count", teamInfo.getLevel2Count());

            return AjaxResult.success(stats);
        } catch (Exception e) {
            return AjaxResult.error("获取佣金统计失败: " + e.getMessage());
        }
    }
}