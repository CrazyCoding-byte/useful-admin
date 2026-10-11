package yzx.iot.exchange;


import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * @className: DeviceMessage
 * @author: yzx
 * @date: 2026/9/2 8:34
 * @Version: 1.0
 * @description: 统一设备协议
 * TcpMessage是设备传上来的协议 他可能有 http-mqttMessage-modbus-自定义协议 等等协议
 * DeviceMessage是平台的统一协议 经过conver进行一个转换把不同的tcpMessage转换成deviceMessage
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceMessage {
    //指令类型
   private String cmdType;
   //请求序列
   private int seqId;
   //设备唯一标识
   private String deviceId;
   //业务负载(原始字节)
   private byte[] payload;    
}
