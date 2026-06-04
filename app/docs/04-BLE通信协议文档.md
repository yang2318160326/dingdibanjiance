# BLE通信协议文档

> 版本: V2.0  
> 日期: 2026-06-04  
> 传输层: BLE 4.2 (UART透传)  
> 更新说明: GET_CONFIG/SET_CONFIG简化为单传感器参数

---

## 1. 协议概述

### 1.1 物理层

本协议运行在BLE UART透传服务之上。支持两种BLE模块：

| 模块类型 | 服务UUID | TX特征 | RX特征 |
|----------|----------|--------|--------|
| HC-08/BT02 (外部) | FFE0 | FFE1 (Notify) | FFE1 (Write) |
| Nordic NUS | 6E400001-... | 6E400003-... (Notify) | 6E400002-... (Write) |

### 1.2 通信模式

- **主从关系:** 安卓APP为主机(发起请求), STM32为从机(响应请求)
- **通信方式:** 请求-应答模式 (APP发命令, STM32回响应)
- **特殊:** 数据下载时, STM32主动发送DATA_FRAG分片

### 1.3 基本参数

| 参数 | 值 | 说明 |
|------|------|------|
| 默认MTU | 247字节 | 可协商, 最小23字节 |
| 帧最大载荷 | MTU-3-7 = 237字节 | BLE开销3字节 + 帧开销7字节 |
| 超时时间 | 5000ms | 单帧等待超时 |
| 最大重试 | 3次 | 命令发送失败重试 |
| 序列号范围 | 0x00 - 0xFF | 循环使用 |

---

## 2. 帧格式

### 2.1 帧结构

```
+------+--------+--------+---------+----------+----------+-----+
| 字段  | SOF    | SEQ    | CMD     | LEN      | PAYLOAD  | CRC | EOF |
| 长度  | 1B     | 1B     | 1B      | 2B       | 0~237B   | 2B  | 1B  |
+------+--------+--------+---------+----------+----------+-----+
| 值    | 0xAA   | 0x00-  | 命令码  | 大端     | 可变     | Modbus|0x55|
|      |        | 0xFF   |         |          |          | CRC16|    |
+------+--------+--------+---------+----------+----------+-----+

总开销: 7字节
最小帧长: 7字节 (无载荷)
最大帧长: 244字节 (237字节载荷)
```

### 2.2 字段说明

| 字段 | 偏移 | 长度 | 说明 |
|------|------|------|------|
| SOF | 0 | 1 | 帧起始标志, 固定0xAA |
| SEQ | 1 | 1 | 序列号, 0x00-0xFF循环递增 |
| CMD | 2 | 1 | 命令/响应码 |
| LEN | 3-4 | 2 | PAYLOAD长度, 大端序 (Big-Endian) |
| PAYLOAD | 5 | 0~N | 命令/响应数据 |
| CRC | 5+N, 6+N | 2 | CRC16/Modbus, 小端序 (Little-Endian) |
| EOF | 7+N | 1 | 帧结束标志, 固定0x55 |

### 2.3 CRC16计算

```c
/**
 * CRC16/Modbus 多项式: 0xA001 (反转0x8005)
 * 计算范围: SEQ + CMD + LEN + PAYLOAD (不包含SOF和EOF)
 * 初始值: 0xFFFF
 * 输出: 小端序 (低字节在前)
 */
uint16_t CRC16_Modbus(const uint8_t *data, uint16_t len)
{
    uint16_t crc = 0xFFFF;
    for (uint16_t i = 0; i < len; i++) {
        crc ^= data[i];
        for (int j = 0; j < 8; j++) {
            if (crc & 0x0001)
                crc = (crc >> 1) ^ 0xA001;
            else
                crc >>= 1;
        }
    }
    return crc;
}
```

### 2.4 帧解析流程

```
接收字节流
    │
    ▼
┌───────────────┐
│ 等待SOF (0xAA)│
└───────┬───────┘
        │ 收到0xAA
        ▼
┌───────────────┐
│ 读取SEQ (1B)  │
└───────┬───────┘
        ▼
┌───────────────┐
│ 读取CMD (1B)  │
└───────┬───────┘
        ▼
┌───────────────┐
│ 读取LEN (2B)  │
│ (大端序)      │
└───────┬───────┘
        ▼
┌───────────────┐
│ 读取PAYLOAD   │
│ (LEN字节)     │
└───────┬───────┘
        ▼
┌───────────────┐
│ 读取CRC (2B)  │
│ (小端序)      │
└───────┬───────┘
        ▼
┌───────────────┐
│ 读取EOF (0x55)│
└───────┬───────┘
        ▼
┌───────────────┐    失败
│ 验证CRC       │───────▶ 丢弃, 重新等待SOF
└───────┬───────┘
        │ 成功
        ▼
   帧解析成功
   交给命令处理
```

---

## 3. 命令集定义

### 3.1 命令码总览

| CMD | 名称 | 方向 | 请求载荷 | 响应载荷 |
|-----|------|------|----------|----------|
| 0x01 | PING | APP→设备 | (空) | DeviceID(4B)+FWver(2B)+Status(1B) |
| 0x02 | GET_INFO | APP→设备 | (空) | 设备信息 (见详情) |
| 0x03 | SET_TIME | APP→设备 | UnixTimestamp(4B) | Status(1B) |
| 0x04 | GET_CONFIG | APP→设备 | (空) | 配置数据 (见详情) |
| 0x05 | SET_CONFIG | APP→设备 | 配置数据 (见详情) | Status(1B) |
| 0x06 | GET_DATA | APP→设备 | StartIndex(4B)+Count(2B) | (触发DATA_FRAG流) |
| 0x07 | DATA_ACK | APP→设备 | ChunkIndex(2B)+Status(1B) | (无, 触发下一包) |
| 0x08 | ERASE_DATA | APP→设备 | ConfirmCode(4B) | Status(1B) |
| 0x09 | GET_STATUS | APP→设备 | (空) | 状态信息 (见详情) |
| 0x0A | SET_DEVICE_ID | APP→设备 | DeviceID(4B)+Name(16B) | Status(1B) |
| 0x0B | REBOOT | APP→设备 | ConfirmCode(4B) | Status(1B) |
| 0xFE | DATA_FRAG | 设备→APP | ChunkIdx(2B)+Data | (由DATA_ACK确认) |
| 0xFF | ERROR | 设备→APP | ErrCode(1B)+OrigCMD(1B) | (无) |

### 3.2 命令详情

#### 0x01 -- PING (设备探测)

**用途:** 测试BLE连接是否正常, 获取基本设备标识

**请求:**
```
载荷: 无 (LEN=0)
```

**响应:**
```
+-----------+----------+----------+
| DeviceID  | FW_ver   | Status   |
| 4B (大端) | 2B (大端)| 1B       |
+-----------+----------+----------+

DeviceID:  设备唯一标识符
FW_ver:    固件版本号, 如V1.02 = 0x0102
Status:    设备状态 (0=正常, 1=采集忙, 2=存储满)
```

**示例:**
```
请求: AA 01 01 00 00 [CRC] 55
响应: AA 01 01 00 07 [A1B2C3D4] [0102] [00] [CRC] 55
```

---

#### 0x02 -- GET_INFO (获取设备信息)

**用途:** 获取设备完整状态信息

**请求:**
```
载荷: 无
```

**响应:**
```
+-----------+----------+------------+-----------+----------+----------+
| DeviceID  | FW_ver   | RecordCount| FreeSpace | Uptime   | Battery  |
| 4B        | 2B       | 4B         | 4B        | 4B       | 1B       |
+-----------+----------+------------+-----------+----------+----------+

RecordCount: 已存储记录总数 (条)
FreeSpace:   剩余存储空间 (条)
Uptime:      设备运行时间 (秒)
Battery:     电池电量百分比 (0-100), 0xFF=无电池
```

---

#### 0x03 -- SET_TIME (时间同步)

**用途:** 同步设备RTC时间

**请求:**
```
+------------------+
| UnixTimestamp    |
| 4B (大端序)      |
+------------------+

UnixTimestamp: 自1970-01-01 00:00:00 UTC以来的秒数
```

**响应:**
```
+--------+
| Status |
| 1B     |
+--------+

Status: 0=成功, 1=失败
```

**示例:**
```
Unix时间戳 1717500000 = 2024-06-04 10:00:00 UTC
请求载荷: 66 5C B2 80 (大端序)
```

---

#### 0x04 -- GET_CONFIG (读取配置)

**用途:** 读取设备当前配置参数 (单传感器版本)

**请求:**
```
载荷: 无
```

**响应:**
```
+------------+--------+-----------+-----------+----------+------------+---------+
| Interval   | Addr   | StartReg  | RegCount  | DataType | Baudrate   | Parity  |
| 4B (大端)  | 1B     | 2B (大端) | 2B (大端) | 1B       | 4B (大端)  | 1B      |
+------------+--------+-----------+-----------+----------+------------+---------+

Interval:   采样间隔 (秒), 0=连续采集
Addr:       传感器Modbus从站地址 (1-247)
StartReg:   起始寄存器地址 (0x0000-0xFFFF)
RegCount:   读取寄存器数量 (1-125)
DataType:   数据类型 (见数据类型定义)
Baudrate:   Modbus波特率 (如9600)
Parity:     校验位 (0=无, 1=奇, 2=偶)
```

**数据类型:**
| 值 | 类型 | 说明 |
|----|------|------|
| 0x00 | UINT16 | 无符号16位 |
| 0x01 | INT16 | 有符号16位 |
| 0x02 | UINT32 | 无符号32位 (2个寄存器) |
| 0x03 | FLOAT32 | 浮点32位 (2个寄存器, IEEE754) |
| 0x04 | RAW | 原始字节 |

---

#### 0x05 -- SET_CONFIG (写入配置)

**用途:** 设置设备配置参数 (单传感器版本)

**请求:**
```
载荷格式与GET_CONFIG响应相同:
+------------+--------+-----------+-----------+----------+------------+---------+
| Interval   | Addr   | StartReg  | RegCount  | DataType | Baudrate   | Parity  |
| 4B (大端)  | 1B     | 2B (大端) | 2B (大端) | 1B       | 4B (大端)  | 1B      |
+------------+--------+-----------+-----------+----------+------------+---------+
```

**响应:**
```
+--------+
| Status |
| 1B     |
+--------+

Status: 0=成功, 1=参数非法, 2=存储失败
```

**校验规则:**
- Interval: 0 ~ 86400 (24小时)
- Addr: 1 ~ 247
- RegCount: 1 ~ 125
- DataType: 0x00 ~ 0x04
- Baudrate: 1200 / 2400 / 4800 / 9600 / 19200 / 38400 / 115200
- Parity: 0 / 1 / 2

---

#### 0x06 -- GET_DATA (请求数据)

**用途:** 请求从设备下载存储的数据记录

**请求:**
```
+------------+-------+
| StartIndex | Count |
| 4B (大端)  | 2B    |
+------------+-------+

StartIndex: 起始记录索引 (从0开始)
Count:      请求记录数量
```

**响应流程:**

设备收到GET_DATA后, 不直接回复响应帧, 而是:
1. 计算总记录数
2. 按每次最多10条记录分片
3. 逐片发送DATA_FRAG
4. 等待APP对每片回复DATA_ACK
5. 收到ACK后发送下一片
6. 全部发完后传输结束

**数据分片大小计算:**
```
每片最大字节数 = MTU_payload - 2(ChunkIndex) = 237 - 2 = 235字节
每片记录数 = floor(235 / 32) = 7条记录 (32字节/条)
实际每片字节数 = 7 * 32 = 224字节
```

---

#### 0x07 -- DATA_ACK (数据确认)

**用途:** APP确认收到DATA_FRAG分片

**请求:**
```
+------------+--------+
| ChunkIndex | Status |
| 2B (大端)  | 1B     |
+------------+--------+

ChunkIndex: 确认的分片序号
Status:     0=成功, 接收下一包
            1=校验错误, 请重发
            2=取消传输
```

**响应:** 无 (设备收到ACK后直接发下一包)

---

#### 0x08 -- ERASE_DATA (擦除数据)

**用途:** 清空设备所有存储的数据记录

**请求:**
```
+----------------+
| ConfirmCode    |
| 4B (大端)      |
+----------------+

ConfirmCode: 必须为 0xDEADBEEF (防误操作)
```

**响应:**
```
+--------+
| Status |
| 1B     |
+--------+

Status: 0=成功, 1=确认码错误
```

---

#### 0x09 -- GET_STATUS (获取状态)

**用途:** 获取设备当前运行状态

**请求:**
```
载荷: 无
```

**响应:**
```
+-------+-----------+-------------+
| State | ErrorCode | NextReadIn  |
| 1B    | 1B        | 4B (大端)   |
+-------+-----------+-------------+

State:       0=空闲, 1=采集中, 2=BLE传输中, 3=睡眠
ErrorCode:   0=正常, 1=传感器错误, 2=存储错误, 3=RTC错误
NextReadIn:  距下次采集的秒数 (0xFFFF=连续模式或未知)
```

---

#### 0x0A -- SET_DEVICE_ID (设置设备标识)

**用途:** 修改设备ID和名称

**请求:**
```
+------------+-------------------+
| DeviceID   | DeviceName        |
| 4B (大端)  | 16B (UTF-8, \0结尾) |
+------------+-------------------+

DeviceID:   新的设备ID
DeviceName: 新的设备名称 (最多15字符+结束符)
```

**响应:**
```
+--------+
| Status |
| 1B     |
+--------+

Status: 0=成功, 1=名称过长, 2=存储失败
```

---

#### 0x0B -- REBOOT (重启设备)

**用途:** 远程重启设备

**请求:**
```
+----------------+
| ConfirmCode    |
| 4B (大端)      |
+----------------+

ConfirmCode: 必须为 0xCAFEBABE (防误操作)
```

**响应:**
```
+--------+
| Status |
| 1B     |
+--------+

Status: 0=即将重启 (设备发送后200ms内重启)
```

---

#### 0xFE -- DATA_FRAG (数据分片)

**用途:** 设备向APP发送数据分片 (设备主动发送)

**载荷:**
```
+------------+---------------------+
| ChunkIndex | RecordData          |
| 2B (大端)  | N * 32B (记录数据)  |
+------------+---------------------+

ChunkIndex: 分片序号 (从0开始递增)
RecordData: 一条或多条32字节记录拼接
```

**流程:**
1. 设备发送DATA_FRAG [ChunkIdx=0]
2. APP收到后回复DATA_ACK [ChunkIdx=0, OK]
3. 设备发送DATA_FRAG [ChunkIdx=1]
4. 重复直到所有记录发完
5. 如果APP回复DATA_ACK [Status=1], 设备重发该包
6. 如果APP回复DATA_ACK [Status=2], 设备取消传输

---

#### 0xFF -- ERROR (错误通知)

**用途:** 设备向APP报告错误

**载荷:**
```
+----------+---------+
| ErrCode  | OrigCMD |
| 1B       | 1B      |
+----------+---------+

ErrCode:  错误码
OrigCMD:  引起错误的原始命令码
```

**错误码定义:**
| 值 | 含义 |
|----|------|
| 0x01 | 不支持的命令 |
| 0x02 | 参数错误 |
| 0x03 | 忙碌中 (正在采集中) |
| 0x04 | 存储错误 |
| 0x05 | CRC校验失败 |
| 0x06 | 超时 |

---

## 4. 传输流程详解

### 4.1 完整数据下载流程

```
    APP                                    STM32
     │                                       │
     │  1. 发送GET_DATA请求                   │
     │── [CMD=0x06, Start=0, Count=100] ────▶│
     │                                       │
     │                              2. 设备准备数据
     │                              读取Flash记录
     │                                       │
     │  3. 设备发送第0片                      │
     │◀── [CMD=0xFE, ChunkIdx=0, 7条记录] ───│
     │                                       │
     │  4. APP校验并存储                      │
     │  5. APP回复ACK                         │
     │── [CMD=0x07, ChunkIdx=0, OK] ────────▶│
     │                                       │
     │  6. 设备发送第1片                      │
     │◀── [CMD=0xFE, ChunkIdx=1, 7条记录] ───│
     │                                       │
     │  7. APP回复ACK                         │
     │── [CMD=0x07, ChunkIdx=1, OK] ────────▶│
     │                                       │
     │         ... (重复直到所有记录发完) ...  │
     │                                       │
     │  N. 设备发送最后一片                   │
     │◀── [CMD=0xFE, ChunkIdx=N, 3条记录] ───│
     │                                       │
     │  N+1. APP回复ACK                       │
     │── [CMD=0x07, ChunkIdx=N, OK] ────────▶│
     │                                       │
     │  (传输完成, APP将数据存入Room)          │
     │                                       │
```

### 4.2 传输错误处理

#### CRC校验失败

```
    APP                                    STM32
     │                                       │
     │◀── [CMD=0xFE, ChunkIdx=2, 数据] ──────│
     │   (CRC校验失败)                        │
     │                                       │
     │── [CMD=0x07, ChunkIdx=2, ERR_CRC] ───▶│
     │                                       │
     │◀── [CMD=0xFE, ChunkIdx=2, 数据(重发)] ─│
     │   (CRC校验成功)                        │
     │                                       │
     │── [CMD=0x07, ChunkIdx=2, OK] ────────▶│
```

#### 超时处理

```
    APP                                    STM32
     │                                       │
     │── [CMD=0x06, Start=0, Count=100] ────▶│
     │                                       │
     │  (等待5秒无响应)                       │
     │                                       │
     │── [CMD=0x06, Start=0, Count=100] ────▶│  (重试1)
     │                                       │
     │  (等待5秒无响应)                       │
     │                                       │
     │── [CMD=0x06, Start=0, Count=100] ────▶│  (重试2)
     │                                       │
     │  (等待5秒无响应)                       │
     │                                       │
     │  报错: 设备无响应                      │
```

#### BLE断连恢复

```
    APP                                    STM32
     │                                       │
     │◀── [CMD=0xFE, ChunkIdx=5, 数据] ──────│
     │── [CMD=0x07, ChunkIdx=5, OK] ────────▶│
     │                                       │
     │◀── [CMD=0xFE, ChunkIdx=6, 数据] ──────│
     │                                       │
     │  ╳ BLE连接断开 ╳                       │
     │                                       │
     │  (自动重连...)                         │
     │                                       │
     │  (重连成功后, 从ChunkIdx=6继续)        │
     │── [CMD=0x06, Start=42+1, Count=58] ──▶│
     │  (StartIndex = 已下载数量)             │
     │                                       │
```

### 4.3 配置写入流程

```
    APP                                    STM32
     │                                       │
     │  1. 读取当前配置                       │
     │── [CMD=0x04] ────────────────────────▶│
     │◀── [Interval=60, Addr=1, Start=0, ...]│
     │                                       │
     │  2. 用户修改配置                       │
     │  (Interval改为120, 修改传感器地址等)   │
     │                                       │
     │  3. 写入新配置                         │
     │── [CMD=0x05, Interval=120, Addr=2,..]▶│
     │                                       │
     │                              4. 设备校验参数
     │                              5. 保存到Flash
     │                                       │
     │◀── [Status=0 (成功)] ────────────────│
     │                                       │
```

### 4.4 时间同步流程

```
    APP                                    STM32
     │                                       │
     │  1. 获取当前手机时间                   │
     │  UnixTimestamp = 1717500000            │
     │                                       │
     │  2. 发送时间同步命令                   │
     │── [CMD=0x03, Timestamp=1717500000] ──▶│
     │                                       │
     │                              3. 设备设置RTC
     │                                       │
     │◀── [Status=0 (成功)] ────────────────│
     │                                       │
```

---

## 5. 帧编码示例

### 5.1 C语言编码示例 (STM32端)

```c
/**
 * 编码并发送一帧
 */
int BleTransport_SendFrame(uint8_t cmd, const uint8_t *payload, uint16_t len)
{
    uint8_t frame[256];
    uint16_t idx = 0;
    static uint8_t seq = 0;

    // SOF
    frame[idx++] = 0xAA;

    // SEQ
    frame[idx++] = seq++;

    // CMD
    frame[idx++] = cmd;

    // LEN (大端序)
    frame[idx++] = (len >> 8) & 0xFF;
    frame[idx++] = len & 0xFF;

    // PAYLOAD
    if (len > 0 && payload != NULL) {
        memcpy(&frame[idx], payload, len);
        idx += len;
    }

    // CRC16 (计算范围: SEQ+CMD+LEN+PAYLOAD)
    uint16_t crc = Modbus_CRC16(&frame[1], idx - 1);
    frame[idx++] = crc & 0xFF;         // CRC低字节
    frame[idx++] = (crc >> 8) & 0xFF;  // CRC高字节

    // EOF
    frame[idx++] = 0x55;

    // 通过BLE UART发送
    return BSP_BLE_Transmit(frame, idx);
}
```

### 5.2 Kotlin编码示例 (APP端)

```kotlin
object BleProtocol {

    fun encodeFrame(seq: Int, cmd: Byte, payload: ByteArray = byteArrayOf()): ByteArray {
        val frame = ByteArrayOutputStream()

        // SOF
        frame.write(0xAA)

        // SEQ
        frame.write(seq and 0xFF)

        // CMD
        frame.write(cmd.toInt())

        // LEN (大端序)
        frame.write((payload.size shr 8) and 0xFF)
        frame.write(payload.size and 0xFF)

        // PAYLOAD
        frame.write(payload)

        // CRC16 (计算范围: SEQ+CMD+LEN+PAYLOAD)
        val crcData = frame.toByteArray().copyOfRange(1, frame.size())
        val crc = crc16(crcData)
        frame.write(crc and 0xFF)           // CRC低字节
        frame.write((crc shr 8) and 0xFF)   // CRC高字节

        // EOF
        frame.write(0x55)

        return frame.toByteArray()
    }

    fun decodeFrame(data: ByteArray): Triple<Int, Byte, ByteArray>? {
        if (data.size < 7) return null
        if (data[0] != 0xAA.toByte()) return null
        if (data[data.size - 1] != 0x55.toByte()) return null

        val seq = data[1].toInt() and 0xFF
        val cmd = data[2]
        val len = ((data[3].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)

        if (data.size != 7 + len) return null

        val payload = data.copyOfRange(5, 5 + len)

        // 验证CRC
        val crcData = data.copyOfRange(1, 5 + len)
        val expectedCrc = crc16(crcData)
        val actualCrc = (data[5 + len].toInt() and 0xFF) or
                        ((data[6 + len].toInt() and 0xFF) shl 8)

        if (expectedCrc != actualCrc) return null

        return Triple(seq, cmd, payload)
    }

    fun crc16(data: ByteArray): Int {
        var crc = 0xFFFF
        for (byte in data) {
            crc = crc xor (byte.toInt() and 0xFF)
            for (j in 0 until 8) {
                crc = if (crc and 1 != 0)
                    (crc shr 1) xor 0xA001
                else
                    crc shr 1
            }
        }
        return crc and 0xFFFF
    }
}
```

---

## 6. 时序约束

| 参数 | 值 | 说明 |
|------|------|------|
| 帧间最小间隔 | 20ms | 连续两帧之间的最小时间 |
| 命令超时 | 5000ms | 等待响应的最大时间 |
| 重试次数 | 3 | 超时后重试次数 |
| 数据片超时 | 5000ms | DATA_FRAG等待ACK的时间 |
| ACK处理时间 | <100ms | 设备收到ACK到发出下一包的时间 |
| BLE连接超时 | 30s | 连接建立的最大等待时间 |
| MTU协商超时 | 5s | MTU协商的最大等待时间 |
