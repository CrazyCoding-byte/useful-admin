package yzx.iot.processor;

import lombok.extern.slf4j.Slf4j;
import yzx.iot.deviceneum.CmdType;
import yzx.iot.exchange.DeviceExchange;
import yzx.iot.exchange.DeviceMessage;
import yzx.iot.protocol.TcpMessage;
import yzx.iot.schedule.ExecutorPartitionScheduler;
import yzx.iot.session.DeviceSession;
import yzx.iot.session.SessionManager;

import java.util.concurrent.*;

import org.springframework.stereotype.Component;

/**
 * @className: DeviceBusinessProcessor
 * @author: yzx
 * @date: 2026/9/25 19:46
 * @Version: 1.0
 * @description:
 *
 * 这个业务消息处理会有一个问题设备的消息处理不是按照顺序的
 * #定目标：我们到底要这个类承诺什么？
 * 从痛点反推出三条 不可协商的承诺 ，这就是设计验收标准：
 *
 * 1. 同一台设备的消息：严格按到达顺序处理，一次只处理一条，天然无并发
 * 2. 不同设备的消息：互不阻塞，尽量并行
 * 3. 线程数量与设备总数解耦 ：一万台设备不能开一万个线程，但也不能因为设备多就变慢
 * 再补一条工程约束：
 *
 * 4. 实现必须简单到你能在脑子里证明它正确。锁、CAS、信号量能不用就不用—— 能靠数据结构保证的，不要靠人肉同步 。
 *
 * 解决办法 ： 让同一台设备的所有任务永远只能进入一个只有单线程的队列。
 */
@Slf4j
@Component
public class DeviceBusinessProcessor {
    private final ExecutorPartitionScheduler schedule;

    public DeviceBusinessProcessor(ExecutorPartitionScheduler schedule) {
        this.schedule = schedule;
    }

    public void handle(DeviceExchange exchange, DeviceMessage message) {
        //fireInbound 在IO线程,这里立刻丢业务池,绝不堵io
        log.info("handle device message: {}", message);
        log.info("handle device exchange: {}", exchange);
        schedule.schedule(message.getDeviceId(), () -> {
            try {
                processor(exchange, message);
            } catch (Exception e) {
                log.warn("业务处理异常 deviceId={}", message.getDeviceId(), e);
            }
        });
    }


    private void processor(DeviceExchange exchange, DeviceMessage message) {
        switch (message.getCmdType()) {
            case "DATA_REPORT" -> handlerDataReport(exchange, message);
            case "CMD_PUSH_RESP" -> handlerCmdPushResp(message);
            default -> log.warn("未知指令 cmdType={} deviceId={}", message.getCmdType(), message.getDeviceId());
        }
    }

    private void handlerDataReport(DeviceExchange exchange, DeviceMessage message) {
        //todo 真实业务:入库,规则引擎
        byte success = 0x00;
        TcpMessage response = new TcpMessage(CmdType.DATA_REPORT_RESP, message.getSeqId(), message.getDeviceId(), new byte[]{success});
        exchange.sendOutbound(response);
    }

    private void handlerCmdPushResp(DeviceMessage message) {
        DeviceSession deviceSession = SessionManager.INSTANCE.get(message.getDeviceId());
        if (deviceSession == null) {
            log.warn("响应到达但会话不存在,seqId={}", message.getSeqId());
            return;
        }
        //统一消息要唤醒future,需要转回协议消息,配对只用到seqId
        //所以这里直接用 seqId 完成配对即可(future)里的Tcpmessage
        CompletableFuture<TcpMessage> pending = deviceSession.removePendingRequest(message.getSeqId());
        if (pending == null) {
            log.warn("未找到待响应请求,seqId={},deviceId={}", message.getSeqId(), message.getDeviceId());
            return;
        }
        pending.complete(new TcpMessage(CmdType.CMD_PUSH_RESP, message.getSeqId(), message.getDeviceId(), message.getPayload()));
    }
}
