package yzx.iot.Codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import yzx.iot.protocol.TcpMessage;
import yzx.iot.utils.Crc16Util;

import java.nio.charset.StandardCharsets;

/**
 * @className: TcpMessageEncoder
 * @author: yzx
 * @date: 2026/8/30 15:50
 * @Version: 1.0
 * @description: 业务对象->ByteBuf
 */
public class TcpMessageEncoder extends MessageToByteEncoder<TcpMessage> {

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, TcpMessage tcpMessage, ByteBuf byteBuf) throws Exception {
        byte[] deviceIdBytes = tcpMessage.getDeviceId().getBytes(StandardCharsets.UTF_8);
        byte[] payload = tcpMessage.getPayload() == null ? new byte[0] : tcpMessage.getPayload();
        // 1. 先写入帧头和数据，最后计算CRC
        int startIdx = byteBuf.writerIndex();
        byteBuf.writeShort(TcpMessage.MAGIC); //2
        byteBuf.writeByte(tcpMessage.getVersion());//1
        byteBuf.writeByte(tcpMessage.getCmdType().getCode());//1
        byteBuf.writeInt(tcpMessage.getSeqId());//4
        byteBuf.writeShort(deviceIdBytes.length);//2
        byteBuf.writeInt(payload.length);//4
        byteBuf.writeBytes(deviceIdBytes);
        byteBuf.writeBytes(payload);

        // 2. 计算CRC并写入
        int endIdx = byteBuf.writerIndex();
        byte[] frameBytes = new byte[endIdx - startIdx];
        //从 byteBuf 的 startIdx 位置开始，复制一段字节到 Java 数组 frameBytes 中。
        byteBuf.getBytes(startIdx, frameBytes);
        short crc = Crc16Util.calculateCrc16(frameBytes);
        byteBuf.writeShort(crc);
    }
}
