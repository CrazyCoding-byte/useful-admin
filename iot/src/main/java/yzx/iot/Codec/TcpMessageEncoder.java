package yzx.iot.Codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import yzx.iot.protocol.TcpMessage;
import yzx.iot.utils.Crc16Util;

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
        byte[] deviceIdBytes = tcpMessage.getDeviceId().getBytes();
        byte[] payload = tcpMessage.getPayload() == null ? new byte[0] : tcpMessage.getPayload();
        // 1. 先写入帧头和数据，最后计算CRC
        int startIdx = byteBuf.writerIndex();
        byteBuf.writeShort(TcpMessage.MAGIC);
        byteBuf.writeByte(tcpMessage.getVersion());
        byteBuf.writeByte(tcpMessage.getCmdType().getCode());
        byteBuf.writeInt(tcpMessage.getSeqId());
        byteBuf.writeShort(deviceIdBytes.length);
        byteBuf.writeInt(payload.length);
        byteBuf.writeBytes(deviceIdBytes);
        byteBuf.writeBytes(payload);

        // 2. 计算CRC并写入
        int endIdx = byteBuf.writerIndex();
        byte[] frameBytes = new byte[endIdx - startIdx];
        byteBuf.getBytes(startIdx, frameBytes);
        short crc = Crc16Util.calculateCrc16(frameBytes);
        byteBuf.writeShort(crc);
    }
}
