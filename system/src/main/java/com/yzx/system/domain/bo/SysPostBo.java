package com.yzx.system.domain.bo;

import com.yzx.model.system.SysPost;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 岗位信息业务对象 sys_post
 *
 * @author yzx
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysPostBo extends SysPost {

    private static final long serialVersionUID = 1L;

    /**
     * 岗位ID
     */
    private Long postId;

    /**
     * 岗位编码
     */
    @NotBlank(message = "岗位编码不能为空")
    @Size(min = 0, max = 64, message = "岗位编码长度不能超过64个字符")
    private String postCode;

    /**
     * 岗位名称
     */
    @NotBlank(message = "岗位名称不能为空")
    @Size(min = 0, max = 50, message = "岗位名称长度不能超过50个字符")
    private String postName;

    /**
     * 岗位排序
     */
    @NotNull(message = "显示顺序不能为空")
    private Integer postSort;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

}
