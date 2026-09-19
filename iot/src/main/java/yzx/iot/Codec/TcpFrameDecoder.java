package yzx.iot.Codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import yzx.iot.protocol.TcpMessage;

import java.util.List;

/**
 * @className: TcpFrameDecoder
 * @author: yzx
 * @date: 2026/8/30 15:50
 * @Version: 1.0
 * @description:
 *
 * 先查看，不移动：
readerIndex()
readableBytes()
getShort()
getInt()

确认完整后，再真正消费：
readBytes(frameLen)
 */
public class TcpFrameDecoder extends ByteToMessageDecoder {
    private static final int MAX_FRAME_LEN = 1024 * 1024;

    //数据会一段一段填充到 in，decode() 会被 Netty 多次调用，直到数据不足或没有更多数据可解析

    /**
     * - `ctx`：通道上下文，可以拿到 channel、触发写 / 关闭等操作
     - `in`：**累积的接收字节缓冲区**。Netty 会把收到的数据不断填充进这个 ByteBuf；`readerIndex` 是当前读取指针。
     - `out`：输出列表。**往 out.add () 放入解析完成的消息对象，消息就会传递给下一个 ChannelHandler**。
     */
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        System.out.println("有信息进来了" + in);
        //获取当前Bytebuf中下一段未处理数据的起始位置
        int readerIndex = in.readerIndex();

        //如果读取的所有字节数小于帧头数就直接返回
        if (in.readableBytes() < TcpMessage.HEADER_FIX_LEN) {
            return;
        }

        short magic = in.getShort(readerIndex);
        if (magic != TcpMessage.MAGIC) {
            ctx.close();
            return;
        }

        int deviceIdLen = in.getUnsignedShort(readerIndex + 8);
        int payloadLen = in.getInt(readerIndex + 10);
        if (payloadLen < 0) {
            ctx.close();
            return;
        }

        int frameLen = TcpMessage.HEADER_FIX_LEN + deviceIdLen + payloadLen + TcpMessage.CRC_LEN;
        if (frameLen > MAX_FRAME_LEN) {
            ctx.close();
            return;
        }
        if (in.readableBytes() < frameLen) {
            return;
        }
        ByteBuf frame = in.readBytes(frameLen);
        out.add(frame);
    }
}
