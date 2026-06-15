package com.yzx.model.system;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * 租户基类
 * 所有需要租户隔离的实体都继承此类
 * 
 * @author ruoyi
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TenantEntity extends BaseEntity implements Serializable {
    
    private static final long serialVersionUID = 1L;

    /**
     * 租户编号
     */
    @TableField("tenant_id")
    private String tenantId;
}
