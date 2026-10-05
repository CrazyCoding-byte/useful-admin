package yzx.iot.test;

import yzx.iot.deviceneum.CmdType;
import yzx.iot.protocol.TcpMessage;
import yzx.iot.utils.Crc16Util;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * @className: ClientTest
 * @author: yzx
 * @date: 2026/9/13 17:08
 * @Version: 1.0
 * @description:
 */
public class ClientTest {
    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = 8080;
        try (Socket socket = new Socket(host, port);
             InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            String deviceId = "dev001";

            // 1. 登录（27字节）
            out.write(buildFrame(CmdType.LOGIN_REQ, deviceId, 1001, "hello".getBytes(StandardCharsets.UTF_8)));
            out.flush();

            // 2. 数据上报（24字节）—— debug 命中 processor 的就是这条
            out.write(buildFrame(CmdType.DATA_REPORT, deviceId, 2001, new byte[]{0x01, 0x02}));
            out.flush();

            // 3. 循环读响应：登录响应 + 上报响应 都要读完再关，否则又是 reset
            byte[] buf = new byte[1024];
            int frameNo = 0;
            int len;
            // 简单演示：读够两帧后退出；read 返回 -1 表示服务端关连接
            while (frameNo < 2 && (len = in.read(buf)) != -1) {
                System.out.println("收到响应" + (++frameNo) + ", 长度=" + len
                        + " " + Arrays.toString(Arrays.copyOf(buf, len)));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] buildFrame(CmdType cmdType, String deviceId, int seqId, byte[] payload) {
        byte[] deviceIdBytes = deviceId.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(toBytes((short) TcpMessage.MAGIC), 0, 2);
        bos.write(TcpMessage.VERSION);
        bos.write(cmdType.getCode());
        bos.write(toBytes(seqId), 0, 4);
        bos.write(toBytes((short) deviceIdBytes.length), 0, 2);
        bos.write(toBytes(payload.length), 0, 4);
        bos.write(deviceIdBytes, 0, deviceIdBytes.length);
        bos.write(payload, 0, payload.length);
        byte[] body = bos.toByteArray();
        short crc = Crc16Util.calculateCrc16(body);
        bos.write(toBytes(crc), 0, 2);
        return bos.toByteArray();
    }

    private static byte[] toBytes(short value) {
        return ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(value).array();
    }

    private static byte[] toBytes(int value) {
        return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array();
    }

    /** 读一次响应并打印（服务端响应可能分多次到，这里简单按一次read读） */
    private static void readOneFrame(InputStream in, String tag) throws IOException {
        byte[] buf = new byte[1024];
        int len = in.read(buf);
        if (len > 0) {
            System.out.println(tag + ", 长度=" + len + " " + Arrays.toString(Arrays.copyOf(buf, len)));
        }
    }


}
