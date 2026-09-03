package yzx.iot.exchange;

import io.netty.channel.Channel;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.function.Consumer;

/**
 * @className: NettyExchange
 * @author: yzx
 * @date: 2026/9/3 9:33
 * @Version: 1.0
 * @description:
 */
public class NettyDeviceExchange implements DeviceExchange {
    public static void main(String[] args) throws IOException {

//        SocketChannel socketChannel = SocketChannel.open();
//        socketChannel.configureBlocking(false);
//        socketChannel.bind(new InetSocketAddress(8888));
//        Selector selector = Selector.open();
//
//        FileInputStream fileInputStream = new FileInputStream("./src/main/resources/yzx.iot.exchange.txt");
//        FileChannel fileChannel = fileInputStream.getChannel();
//        //分配一个缓冲内存,可以堆内/堆外
//        ByteBuffer byteBuffer = ByteBuffer.allocate(1024); //堆内 java类 byte
//        //ByteBuffer buffer=ByteBuffer.allocateDirect(10); //堆外 直接操作电脑资源
//        int readLen;
//        //capacity=10 初始化position=0 limit=0
//        while ((readLen = fileChannel.read(byteBuffer)) != -1) {  channel是用来操作io的也实际的io 比如磁盘和网卡 bytebuffer get put是内存操作
//            System.out.println("本次读到的字节数");
//            byteBuffer.flip();//读完buffer,必须flip 切换为读模式
//            byte[] arr = new byte[byteBuffer.remaining()];
//            byteBuffer.get(arr);
//            System.out.println(new String(arr));
//            byteBuffer.clear();
//        }
//        fileChannel.close();
//        fileInputStream.close();
        ByteBuffer buf = ByteBuffer.allocate(10);  //capacity:总容量 position:当前操作位置指针 limit:有效数据边界
//        ByteBuffer.allocate(10); 堆内创建byteBuffer
//        ByteBuffer.allocateDirect(100); 堆外创建buffer
//        buf.flip(); 写->读  写:从磁盘或者网卡读取数据到 bytebuffer 读:从bytebuffer读取数据
//        buf.clear(); 重置 只重置位置不重置数据 数据是写覆盖
        buf.compact();
        /**
         *      buf.compact();
         * TCP 非阻塞场景专用：缓冲区还有未读完的数据，不想丢弃，想继续往后面写新数据。
         * 逻辑：把`[position, limit)`未读完的数据拷贝到缓冲区头部；position 移动到未读数据末尾；limit=capacity。
         */
        buf.put((byte) 'a');
        buf.put((byte) 'b');
        buf.put((byte) 'c');
        buf.put((byte) 'd');
        buf.put((byte) 'e');
        buf.clear();
        System.out.println(buf.position()); //位置 5
        System.out.println(buf.limit()); //limit 10
        System.out.println(buf.capacity()); //capacity 10
        byte b = buf.get();
        System.out.println(String.valueOf(b));
    }

    private final Channel channel;
    private String deviceId;
    private Consumer<Object> inboundListener;

    public NettyDeviceExchange(Channel channel) {
        this.channel = channel;
    }

    /**
     * 登录成功后设置设备ID
     * @param deviceId
     */
    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    @Override
    public String deviceId() {
        return deviceId;
    }

    @Override
    public String protocol() {
        return "private-tcp";
    }

    /**
     *  给Netty Handler调用,把消息投递到上行监听器 仅在NettyIO线程执行
     * @param msg
     */
    public void fireInbound(Object msg) {
        if (inboundListener != null) {
            inboundListener.accept(msg);
        }
    }

    @Override
    public void onInbound(Consumer<Object> listener) {
        this.inboundListener = listener;
    }

    @Override
    public void sendOutbound(Object msg) {
        //netty线程安全规则,写操作必须在EventLoop线程执行
        if (channel.eventLoop().inEventLoop()) {
            channel.writeAndFlush(msg);
        } else {
            channel.eventLoop().execute(() -> channel.writeAndFlush(msg));
        }
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public void close() {

    }
}
