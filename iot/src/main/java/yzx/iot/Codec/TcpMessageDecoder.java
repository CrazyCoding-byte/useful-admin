package yzx.iot.Codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToMessageDecoder;
import yzx.iot.deviceneum.CmdType;
import yzx.iot.protocol.TcpMessage;
import yzx.iot.utils.Crc16Util;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * @className: TcpMessageDecoder
 * @author: yzx
 * @date: 2026/8/30 15:50
 * @Version: 1.0
 * @description: byteBuf->业务对象
 */
public class TcpMessageDecoder extends ByteToMessageDecoder {

    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List<Object> out) throws Exception {

        short magic = byteBuf.readShort();
        if (magic != TcpMessage.MAGIC) return;
        byte version = byteBuf.readByte();
        byte cmdCode = byteBuf.readByte();
        CmdType cmdType = CmdType.getByCode(cmdCode);
        if (cmdType == null) return;

        int seqId = byteBuf.readInt();
        short deviceIdLen = byteBuf.readShort();
        int payloadLen = byteBuf.readInt();

        // 2. 读取变长字段
        byte[] deviceIdBytes = new byte[deviceIdLen];
        byteBuf.readBytes(deviceIdBytes);
        String deviceId = new String(deviceIdBytes, StandardCharsets.UTF_8);

        byte[] payload = new byte[payloadLen];
        byteBuf.readBytes(payload);
        //crc校验
        short receivedCrc = byteBuf.readShort();
        ByteBuf calcBuf = byteBuf.copy(byteBuf.readerIndex() - (deviceIdLen + payloadLen + 14), deviceIdLen + payloadLen + 14);
        byte[] calcBytes = new byte[calcBuf.readableBytes()];
        calcBuf.readBytes(calcBytes);
        calcBuf.release();
        short calcCrc = Crc16Util.calculateCrc16(calcBytes);
        if (receivedCrc != calcCrc) {
            // CRC校验失败，丢弃报文
            return;
        }
// 4. 封装业务对象
        TcpMessage message = new TcpMessage();
        message.setVersion(version);
        message.setCmdType(cmdType);
        message.setSeqId(seqId);
        message.setDeviceId(deviceId);
        message.setPayload(payload);

        out.add(message);
    }
}
