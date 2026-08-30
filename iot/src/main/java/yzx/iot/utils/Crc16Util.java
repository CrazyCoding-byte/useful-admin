package yzx.iot.utils;

/**
 * @className: Crc16Util
 * @author: yzx
 * @date: 2026/8/30 15:49
 * @Version: 1.0
 * @description:
 */
public class Crc16Util {
    private static final int POLY = 0xA001;

    public static short calculateCrc16(byte[] data) {
        int crc = 0xFFFF;
        for (byte b : data) {
            crc ^= b & 0xFF;
            for (int i = 0; i < 8; i++) {
                if ((crc & 0x0001) != 0) {
                    crc = (crc >> 1) ^ POLY;
                } else {
                    crc >>= 1;
                }
            }
        }
        return (short) crc;
    }
}
