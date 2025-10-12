package com.yzx.model.coupon;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.yzx.model.coupon.eunms.ActivityType;
import com.yzx.model.system.BaseEntity;
import lombok.Data;

import java.util.Date;

/**
 * <p>
 * ActivityInfo
 * </p>
 *
 * @author qy
 */
@Data
@TableName("activity_info")
public class ActivityInfo extends BaseEntity {
	
	private static final long serialVersionUID = 1L;
	@TableId(type = IdType.AUTO)
	private Long id;
	@TableField("activity_name")
	private String activityName;

	@TableField("activity_type")
	private ActivityType activityType;

	@TableField("activity_desc")
	private String activityDesc;

	@TableField("start_time")
	@JsonFormat(pattern = "yyyy-MM-dd")
	private Date startTime;

	@TableField("end_time")
	@JsonFormat(pattern = "yyyy-MM-dd")
	private Date endTime;

	@TableField("create_time")
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private Date createTime;

	@TableField(exist = false)
	private String activityTypeString;
}

