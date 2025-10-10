package com.yzx.model.system;

import lombok.Data;

import java.util.Date;

/**
 * @className: UserRegisteredMessage
 * @author: yzx
 * @date: 2025/10/10 19:48
 * @Version: 1.0
 * @description:
 */
@Data
public class UserRegisteredMessage {
    private String userId;
    private String userName;
    private String phoneNumber;
    private String qrCode;
    private String inviteQrCode;
    private Date registerTime;
    private InviterInfo inviterInfo;
}
