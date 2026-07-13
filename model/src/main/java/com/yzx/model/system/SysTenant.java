package com.yzx.model.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * 租户对象 sys_tenant
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_tenant")
@JsonIgnoreProperties(ignoreUnknown = true)
public class SysTenant extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String tenantId;

    private String contactUserName;

    private String contactPhone;

    private String companyName;

    private String licenseNumber;

    private String address;

    private String domain;

    private String intro;

    private String remark;

    private Long packageId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date expireTime;

    private Long accountCount;

    private String status;

    @TableLogic
    private String delFlag;
}
