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
             OutputStream out = socket.getOutputStream();
        ) {
            String deviceId = "dev001";
            int seqId = 1001;
            byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
            byte[] frame = buildLoginFrame(deviceId, seqId, payload);
            out.write(frame);
            out.flush();
            //读取服务端返回
            byte[] header = new byte[1024];
            int readLen = in.read(header);
            if (readLen > 0) {
                byte[] reak=Arrays.copyOf(header,readLen);
                System.out.println("收到响应,长度=" + readLen);
                System.out.println(Arrays.toString(reak));
            }
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] buildLoginFrame(String deviceId, int seqId, byte[] payload) {
        byte[] deviceIdBytes = deviceId.getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        // 1. magic
        bos.write(toBytes((short) TcpMessage.MAGIC), 0, 2);

        // 2. version
        bos.write(TcpMessage.VERSION);

        // 3. cmdType
        bos.write(CmdType.LOGIN_REQ.getCode());

        // 4. seqId
        bos.write(toBytes(seqId), 0, 4);

        // 5. deviceIdLen
        bos.write(toBytes((short) deviceIdBytes.length), 0, 2);

        // 6. payloadLen
        bos.write(toBytes(payload.length), 0, 4);

        // 7. deviceId
        bos.write(deviceIdBytes, 0, deviceIdBytes.length);

        // 8. payload
        bos.write(payload, 0, payload.length);

        // 9. crc16（对前面的内容做 CRC）
        byte[] bodyWithoutCrc = bos.toByteArray();
        short crc = Crc16Util.calculateCrc16(bodyWithoutCrc);
        bos.write(toBytes(crc), 0, 2);

        return bos.toByteArray();
    }

    private static byte[] toBytes(short value) {
        return ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(value).array();
    }

    private static byte[] toBytes(int value) {
        return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array();
    }
}
