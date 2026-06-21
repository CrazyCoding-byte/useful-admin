package com.yzx.apiclient.api.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @className: RemoteDictTypeVo
 * @author: yzx
 * @date: 2026/6/21 14:46
 * @Version: 1.0
 * @description:
 */
@Data
public class RemoteDictTypeVo implements Serializable {


    private static final long serialVersionUID = 1L;

    /**
     * 字典主键
     */
    private Long dictId;

    /**
     * 字典名称
     */
    private String dictName;

    /**
     * 字典类型
     */
    private String dictType;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private Date createTime;
}
