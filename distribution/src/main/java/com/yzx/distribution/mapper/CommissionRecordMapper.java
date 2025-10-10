package com.yzx.distribution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzx.model.distribution.CommissionRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * @className: CommissionRecordMapper
 * @author: yzx
 * @date: 2025/10/10 17:03
 * @Version: 1.0
 * @description:
 */
@Mapper
public interface CommissionRecordMapper extends BaseMapper<CommissionRecord> {
    /**
     * 查询用户总佣金
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM commission_record WHERE user_id = #{userId} AND status IN (1,2)")
    BigDecimal selectTotalCommissionByUserId(@Param("userId") String userId);

    /**
     * 查询用户待结算佣金
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM commission_record WHERE user_id = #{userId} AND status = 1")
    BigDecimal selectPendingCommissionByUserId(@Param("userId") String userId);

    /**
     * 查询用户已结算佣金
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM commission_record WHERE user_id = #{userId} AND status = 2")
    BigDecimal selectSettledCommissionByUserId(@Param("userId") String userId);
}
