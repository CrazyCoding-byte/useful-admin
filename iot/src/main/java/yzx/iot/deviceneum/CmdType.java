package yzx.iot.deviceneum;

import lombok.Getter;

@Getter
public enum CmdType {
    LOGIN_REQ((byte) 0x01, "设备登录请求"),
    LOGIN_RESP((byte) 0x02, "设备登录响应"),
    HEARTBEAT_REQ((byte) 0x03, "心跳请求"),
    HEARTBEAT_RESP((byte) 0x04, "心跳响应"),
    DATA_REPORT((byte) 0x05, "数据上报"),
    DATA_REPORT_RESP((byte) 0x06, "数据上报响应"),
    CMD_PUSH((byte) 0x07, "指令下发"),
    CMD_PUSH_RESP((byte) 0x08, "指令下发响应");

    private final byte code;
    private final String desc;

    CmdType(byte code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static CmdType getByCode(byte code) {
        for (CmdType type : values()) {
            if (type.code == code) return type;
        }
        return null;
    }
}
