package com.yzx.model.system;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * @className: RegisterUserTo
 * @author: yzx
 * @date: 2025/10/10 15:00
 * @Version: 1.0
 * @description:
 */
@Data
public class RegisterUserTo {
    @Email(message = "邮箱格式错误")
    private String phone;
    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 10, message = "用户名长度必须在2-10之间")
    private String username;
    @Size(min = 6, max = 20, message = "密码长度必须在6-20之间")
    private String password;
    private String code;
}
