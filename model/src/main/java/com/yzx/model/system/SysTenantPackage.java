package com.yzx.model.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 租户套餐表
 * 参考 RuoYi-Cloud-Plus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_tenant_package")
public class SysTenantPackage extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 套餐ID */
    @TableId(type = IdType.AUTO)
    private Long packageId;

    /** 套餐名称 */
    private String packageName;

    /** 关联菜单ID（逗号分隔） */
    private String menuIds;

    /** 备注 */
    private String remark;

    /** 菜单树选择项是否关联显示 */
    private Boolean menuCheckStrictly;

    /** 状态（0正常 1停用） */
    private String status;

    /** 删除标志（0代表存在 1代表删除） */
    @TableLogic
    private String delFlag;
}
