package yzx.iot.Codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import yzx.iot.protocol.TcpMessage;

/**
 * @className: TcpFrameDecoder
 * @author: yzx
 * @date: 2026/8/30 15:50
 * @Version: 1.0
 * @description:
 */
public class TcpFrameDecoder extends LengthFieldBasedFrameDecoder {
    private static final int MAX_FRAME_LEN = 1024 * 1024;
    private static final int LENGTH_FIELD_OFFSET = 8;
    private static final int LENGTH_FIELD_LENGTH = 4;
    private static final int LENGTH_ADJUSTMENT = 2 + 2;
    private static final int INITIAL_BYTES_TO_STRIP = 0;

    public TcpFrameDecoder() {
        super(MAX_FRAME_LEN, LENGTH_FIELD_OFFSET, LENGTH_FIELD_LENGTH,
                LENGTH_ADJUSTMENT, INITIAL_BYTES_TO_STRIP);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        //可读字节小于魔术长度直接等待
        if (in.readableBytes() < 2) return null;
        //预读魔术,非法直接关闭连接
        short magic = in.getShort(in.readerIndex());
        if (magic != TcpMessage.MAGIC) {
            ctx.close();
            return null;
        }
        return super.decode(ctx, in);
    }
}
