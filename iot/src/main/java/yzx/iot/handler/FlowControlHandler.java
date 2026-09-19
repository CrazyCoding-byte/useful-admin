package yzx.iot.handler;

import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import com.google.common.util.concurrent.RateLimiter;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @className: FlowControlHandler
 * @author: yzx
 * @date: 2026/8/30 15:55
 * @Version: 1.0
 * @description: 单设备限流器
 */
public class FlowControlHandler extends ChannelInboundHandlerAdapter {
    private static final int MAX_QPS_PER_DEVICE = 100;
    private final ConcurrentHashMap<String, RateLimiter> rateLimitermap = new ConcurrentHashMap();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
//        CompositeByteBuf byteBufs = ctx.alloc().compositeBuffer(); //多段缓冲区拼接 CompositeByteBuf（零拷贝拼接）
//        byteBufs.addComponent(true,xxbuffer);
//        byteBufs.addComponent(true,xx1buffer);

        String channelId = ctx.channel().id().asLongText();
        RateLimiter rateLimiter = rateLimitermap.computeIfAbsent(channelId, k -> RateLimiter.create(MAX_QPS_PER_DEVICE));
        if (rateLimiter.tryAcquire()) {
            //入站流转
            super.channelRead(ctx, msg);
        } else {
            //触发限流,直接丢弃报文不响应
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        rateLimitermap.remove(ctx.channel().id().asLongText());
        super.channelInactive(ctx);
    }
}
