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
