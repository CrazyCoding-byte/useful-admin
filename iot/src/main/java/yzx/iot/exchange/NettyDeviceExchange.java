package yzx.iot.exchange;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.DefaultFileRegion;

import java.io.File;
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
        //capacity 创建缓冲区的时候就固定死，永远不会变！allocate (10) capacity=10，一辈子都是 10，put/read/flip/clear/compact 全都不会修改 capacity
        //limit、position 是可变指针；capacity 是缓冲区物理内存总大小。 limit 想要等于有效数据长度，**必须手动 flip ()**。
        //capacity 永远不变；limit 是逻辑边界，由 flip/clear/compact/limit () 控制。
//        ByteBuffer.allocate(10); 堆内创建byteBuffer
//        ByteBuffer.allocateDirect(100); 堆外创建buffer
//        buf.flip(); 写->读  写:从磁盘或者网卡读取数据到 bytebuffer 读:从bytebuffer读取数据
//        buf.clear(); 重置 只重置位置不重置数据 数据是写覆盖
//        buf.compact();
        /**
         *      buf.compact();
         * TCP 非阻塞场景专用：缓冲区还有未读完的数据，不想丢弃，想继续往后面写新数据。
         * 逻辑：把`[position, limit)`未读完的数据拷贝到缓冲区头部；position 移动到未读数据末尾；limit=capacity。
         * 举例子：buffer 容量 10，position=3，limit=7；还有 4 字节没读完。
         * compact 之后：把下标 3‑6 字节复制到 0‑3；position=4；limit=10
         *  clear：全部丢弃，从头写；适合文件读取（读完就不要旧数据）
         *  compact：保留未读完半包，把残留挪到头部；适合 TCP 网络（会粘包半包）
         */
//        buf.rewind();  //position=0 mark=-1 limit不变,回到开头,重新重读一遍已有数据
//        buf.remaining(); `remaining()` = limit‑position，本身不知道是读还是写，全靠 flip 切换 limit。
        buf.put((byte) 'a');
        buf.put((byte) 'b');
        buf.put((byte) 'c');
        buf.put((byte) 'd');
        buf.put((byte) 'e');
        buf.flip(); //put、channel.read、get，不会修改 limit！只移动 position。
        System.out.println(buf.position()); //位置 5
        System.out.println(buf.limit()); //limit 10
        System.out.println(buf.capacity()); //capacity 10
        byte b = buf.get(0);
        System.out.println(String.valueOf(b));

        //netty ByteBuf 对jdk nio bytebuffer进行了封装 不需要用flip 转换进行读取
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeByte('a');
        buffer.writeByte('b');
        buffer.writeByte('c');
        System.out.println(buffer.readableBytes());
        byte b1 = buffer.readByte(); //读a，readerIndex变成1  读取会移动索引
        byte b2 = buffer.readByte(); //读b，readerIndex变成2
        buffer.getByte(2);//绝对读 不移动指针
        buffer.discardReadBytes(); //等价于jdk compact() 把`[0,readerIndex)`已经读完的废弃区域丢掉；**把未读`[readerIndex,writerIndex)`整块拷贝到缓冲区头部**。
        buffer.clear();  //不释放内存，不清零内存字节，仅仅重置两个指针。旧数据残留在内存。
        buffer.markReaderIndex();
        //ByteBuf->ByteBuffer
        ByteBuffer byteBuffer = buffer.nioBuffer(buffer.readerIndex(), buffer.readableBytes());
        //ByteBuffer->ByteBuf
        ByteBuffer byteBuffer1 = ByteBuffer.allocateDirect(1024);
        //包装视图 不拷贝
        ByteBuf byteBuf = Unpooled.wrappedBuffer(byteBuffer1);
        File file = new File("xx");
        DefaultFileRegion defaultFileRegion = new DefaultFileRegion(new File("test.txt"), 0, file.length());
        /**
         * # ByteBuf 视图（view）到底是什么
         *
         * >
         * > **视图 = 不拷贝新内存，只是新建一个对象，复用原始 ByteBuf 的同一块内存。**
         * > 没有分配新的堆 / 堆外内存，只是搞一套自己独立的 `readerIndex / writerIndex`，指向原来那块字节。
         * `slice()`、`duplicate()`、`nioBuffer()`、`wrappedBuffer()`
         * ## 举个生活化例子
         *
         * 原始缓冲区：内存块 `[a b c d e f]`
         *
         * - 原始 buf：完整窗口看全部 6 个字节
         * - slice (1,3)：生成**视图对象**，窗口只看 `[b c d]`
         */
        testView();
    }

    public static void testView() {
        ByteBuf origin = Unpooled.buffer();
        origin.writeBytes(new byte[]{'a', 'b', 'c', 'd', 'e'});
        origin.release(); //原内存收回
        origin.readByte(); //报错 内存没了
    }

    public static void testView2() {
        ByteBuf origin = Unpooled.buffer();
        origin.writeBytes(new byte[]{'a', 'b', 'c', 'd', 'e'});
        ByteBuf byteBuf = origin.readRetainedSlice(3);
        origin.release();
        byteBuf.readByte();//安全 refCnt还大于0
        byteBuf.release();//业务自己释放
    }

    private final Channel channel;
    private String deviceId;
    private Consumer<Object> inboundListener; //存者谁要消息

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
     *  `fireInbound` （往通道里灌消息）
     *  快递员按照快递地址投递
     * @param msg
     */
    public void fireInbound(Object msg) {
        if (inboundListener != null) {
            inboundListener.accept(msg); //投递消息
        }
    }

    /**
     * 类似填写快递地址
     * @param listener
     */
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
        return channel.isActive();
    }

    @Override
    public void close() {
        channel.close();
    }
}
