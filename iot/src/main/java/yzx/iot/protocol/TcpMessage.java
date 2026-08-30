package yzx.iot.protocol;

import lombok.Data;
import yzx.iot.deviceneum.CmdType;

/**
 * @className: TcpMessage
 * @author: yzx
 * @date: 2026/8/30 14:45
 * @Version: 1.0
 * @description:
 */
@Data
public class TcpMessage {
    /** 魔数 */
    public static final short MAGIC = (short) 0xBEEF;
    /** 协议版本 */
    public static final byte VERSION = 0x01;
    /** 固定帧头长度 */
    public static final int HEADER_FIX_LEN = 14;
    /** 校验位长度 */
    public static final int CRC_LEN = 2;

    private byte version = VERSION;
    private CmdType cmdType;
    private int seqId;
    private String deviceId;
    private byte[] payload;

    public TcpMessage() {
    }

    public TcpMessage(CmdType cmdType, int seqId, String deviceId, byte[] payload) {
        this.cmdType = cmdType;
        this.seqId = seqId;
        this.deviceId = deviceId;
        this.payload = payload;
    }
}
