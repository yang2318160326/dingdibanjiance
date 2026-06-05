# 安卓APP开发进度与协议文档

> 版本: V1.3.0  
> 日期: 2026-06-04  
> 目标: 为下位机开发提供完整的协议对接文档

---

## 1. 开发进度

### 1.1 版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| V1.0.0 | 2026-06-04 | 初始版本 - BLE扫描连接、帧协议、Room数据库、数据导出 |
| V1.1.0 | 2026-06-04 | UI优化 - 扫描引导界面、双模式设置(日常/调试) |
| V1.2.0 | 2026-06-04 | 扫描优化、设备详情增强、调试密码保护 |
| V1.3.0 | 2026-06-04 | 操作反馈提示、进度条、弹窗确认 |

### 1.2 已实现功能

#### 扫描与连接
- ✅ BLE设备扫描 (支持HM-10/Nordic NUS)
- ✅ 设备列表显示 (名称、MAC、RSSI)
- ✅ 一键连接/断开
- ✅ 已知设备历史记录
- ✅ 扫描暂停/停止
- ✅ 扫描浮层可最小化

#### 设备信息
- ✅ 固件地址 (MAC地址)
- ✅ 设备ID (分机号)
- ✅ 固件版本
- ✅ 存储状态 (已记录/剩余/总容量)
- ✅ 设备运行状态 (空闲/采集中/传输中/睡眠)
- ✅ 电池电量
- ✅ 运行时间

#### 日常设置
- ✅ 分机号设置 (0-99)
- ✅ 采集间隔设置 (小时:分钟)
- ✅ 启动/停止采集控制
- ✅ 时间同步 (手机时间→设备)
- ✅ 清除数据 (带确认)

#### 调试设置 (密码: 2611)
- ✅ 一键读取所有配置
- ✅ 485通信参数配置 (波特率/校验位)
- ✅ 传感器参数配置 (从站地址/寄存器/数据类型)
- ✅ Modbus原始命令发送
- ✅ 设备状态读取
- ✅ 设备重启

#### 数据管理
- ✅ 数据下载 (支持断点续传)
- ✅ 下载进度条显示
- ✅ 数据列表查看
- ✅ 数据图表显示
- ✅ CSV/JSON导出
- ✅ 一键分享

#### 操作反馈
- ✅ 所有操作成功/失败弹窗
- ✅ 错误原因显示
- ✅ 下载进度条
- ✅ 清除数据确认弹窗

### 1.3 待实现功能

- [ ] 传感器位移数值实时显示
- [ ] 设备时间显示 (从GET_STATUS获取)
- [ ] 数据压缩传输
- [ ] OTA固件升级
- [ ] 多设备并行管理
- [ ] 数据报警阈值设置

---

## 2. BLE通信协议

### 2.1 物理层

| 参数 | 值 | 说明 |
|------|------|------|
| 协议 | BLE 4.2 | 低功耗蓝牙 |
| 传输方式 | UART透传 | 通过GATT服务 |
| 默认MTU | 247字节 | 可协商 |
| 超时时间 | 5000ms | 单帧等待 |
| 最大重试 | 3次 | 命令发送失败重试 |

#### 支持的BLE模块

| 模块类型 | 服务UUID | TX特征 | RX特征 |
|----------|----------|--------|--------|
| HC-08/BT02 | 0000FFE0-... | 0000FFE1 (Notify) | 0000FFE1 (Write) |
| Nordic NUS | 6E400001-... | 6E400003 (Notify) | 6E400002 (Write) |

### 2.2 帧格式

```
+------+--------+--------+---------+----------+----------+-----+
| SOF  | SEQ    | CMD    | LEN     | PAYLOAD  | CRC      | EOF |
| 1B   | 1B     | 1B     | 2B      | 0~237B   | 2B       | 1B  |
| 0xAA | 00-FF  | 命令码 | 大端序  | 可变     | Modbus   | 0x55|
+------+--------+--------+---------+----------+----------+-----+

总开销: 7字节
最小帧长: 7字节 (无载荷)
最大帧长: 244字节 (237字节载荷)
```

#### 字段说明

| 字段 | 偏移 | 长度 | 说明 |
|------|------|------|------|
| SOF | 0 | 1 | 帧起始标志, 固定0xAA |
| SEQ | 1 | 1 | 序列号, 0x00-0xFF循环递增 |
| CMD | 2 | 1 | 命令/响应码 |
| LEN | 3-4 | 2 | PAYLOAD长度, 大端序 |
| PAYLOAD | 5 | 0~N | 命令/响应数据 |
| CRC | 5+N, 6+N | 2 | CRC16/Modbus, 小端序 |
| EOF | 7+N | 1 | 帧结束标志, 固定0x55 |

### 2.3 CRC16计算

```c
// CRC16/Modbus 多项式: 0xA001 (反转0x8005)
// 计算范围: SEQ + CMD + LEN + PAYLOAD (不包含SOF和EOF)
// 初始值: 0xFFFF
// 输出: 小端序 (低字节在前)
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

---

## 3. 命令集定义

### 3.1 命令码总览

| CMD | 名称 | 方向 | 请求载荷 | 响应载荷 | 说明 |
|-----|------|------|----------|----------|------|
| 0x01 | PING | APP→设备 | (空) | DeviceID(4B)+FWver(2B)+Status(1B) | 设备探测 |
| 0x02 | GET_INFO | APP→设备 | (空) | 设备信息(19B) | 获取完整信息 |
| 0x03 | SET_TIME | APP→设备 | UnixTimestamp(4B) | Status(1B) | 时间同步 |
| 0x04 | GET_CONFIG | APP→设备 | (空) | 配置数据(15B) | 读取配置 |
| 0x05 | SET_CONFIG | APP→设备 | 配置数据(15B) | Status(1B) | 写入配置 |
| 0x06 | GET_DATA | APP→设备 | StartIndex(4B)+Count(2B) | (触发DATA_FRAG流) | 请求数据 |
| 0x07 | DATA_ACK | APP→设备 | ChunkIndex(2B)+Status(1B) | (无) | 数据确认 |
| 0x08 | ERASE_DATA | APP→设备 | ConfirmCode(4B) | Status(1B) | 擦除数据 |
| 0x09 | GET_STATUS | APP→设备 | (空) | 状态信息(6B) | 获取状态 |
| 0x0A | SET_DEVICE_ID | APP→设备 | DeviceID(4B)+Name(16B) | Status(1B) | 设置设备ID |
| 0x0B | REBOOT | APP→设备 | ConfirmCode(4B) | Status(1B) | 重启设备 |
| 0xFE | DATA_FRAG | 设备→APP | ChunkIdx(2B)+Data | (由DATA_ACK确认) | 数据分片 |
| 0xFF | ERROR | 设备→APP | ErrCode(1B)+OrigCMD(1B) | (无) | 错误通知 |

---

### 3.2 详细命令说明

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

DeviceID:  设备唯一标识符 (如: 0x00000001)
FW_ver:    固件版本号 (如: V1.02 = 0x0102)
Status:    设备状态
           0 = 正常
           1 = 采集忙
           2 = 存储满
```

**APP处理:**
```
收到PING响应后:
1. 显示设备信息
2. 保存到已知设备列表
3. 自动发送GET_INFO获取详细信息
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
| 4B (大端) | 2B (大端)| 4B (大端)  | 4B (大端) | 4B (大端)| 1B       |
+-----------+----------+------------+-----------+----------+----------+

DeviceID:    设备唯一标识符
FW_ver:      固件版本号 (如: V1.02 = 0x0102)
RecordCount: 已存储记录总数 (条)
FreeSpace:   剩余存储空间 (条)
Uptime:      设备运行时间 (秒)
Battery:     电池电量百分比 (0-100), 0xFF=无电池
```

**APP处理:**
```
收到GET_INFO响应后:
1. 更新设备信息显示
2. 计算存储使用率
3. 显示电池电量
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
手机时间: 2026-06-04 18:00:00 (北京时间)
Unix时间戳: 1780684800
请求载荷: 6A 24 18 00 (大端序)
```

**APP处理:**
```
用户点击"同步时间"按钮后:
1. 获取当前手机Unix时间戳
2. 发送SET_TIME命令
3. 显示"时间同步成功！"
```

---

#### 0x04 -- GET_CONFIG (读取配置)

**用途:** 读取设备当前配置参数

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

**数据类型定义:**
| 值 | 类型 | 说明 | 字节数 |
|----|------|------|--------|
| 0x00 | UINT16 | 无符号16位 | 2 |
| 0x01 | INT16 | 有符号16位 | 2 |
| 0x02 | UINT32 | 无符号32位 | 4 |
| 0x03 | FLOAT32 | 浮点32位 (IEEE754) | 4 |
| 0x04 | RAW | 原始字节 | 可变 |

**APP处理:**
```
收到GET_CONFIG响应后:
1. 填充配置表单
2. 显示当前参数值
```

---

#### 0x05 -- SET_CONFIG (写入配置)

**用途:** 设置设备配置参数

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

**APP处理:**
```
用户点击"保存"按钮后:
1. 校验参数合法性
2. 发送SET_CONFIG命令
3. 显示"配置保存成功！"
```

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
```
设备收到GET_DATA后:
1. 不直接回复响应帧
2. 计算总记录数
3. 按每次最多7条记录分片
4. 逐片发送DATA_FRAG
5. 等待APP对每片回复DATA_ACK
6. 收到ACK后发送下一片
7. 全部发完后传输结束
```

**数据分片大小:**
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

**APP处理:**
```
用户点击"清除数据"后:
1. 弹出确认对话框
2. 用户输入CONFIRM确认
3. 发送ERASE_DATA命令
4. 显示"数据清除成功！"
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

State:       设备状态
             0 = 空闲
             1 = 采集中
             2 = BLE传输中
             3 = 睡眠
ErrorCode:   错误码
             0 = 正常
             1 = 传感器错误
             2 = 存储错误
             3 = RTC错误
NextReadIn:  距下次采集的秒数 (0xFFFF=连续模式或未知)
```

**APP处理:**
```
收到GET_STATUS响应后:
1. 显示设备运行状态
2. 显示错误码
3. 显示下次采集倒计时
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

DeviceID:   新的设备ID (0-99 作为分机号)
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

**APP处理:**
```
用户设置分机号后:
1. 发送SET_DEVICE_ID命令
2. 显示"分机号设置成功！当前分机号: X"
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

**APP处理:**
```
用户点击"重启设备"后:
1. 发送REBOOT命令
2. 显示"设备重启指令已发送"
3. 自动断开连接
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
```
1. 设备发送DATA_FRAG [ChunkIdx=0]
2. APP收到后校验CRC
3. 校验通过: 回复DATA_ACK [ChunkIdx=0, OK]
4. 校验失败: 回复DATA_ACK [ChunkIdx=0, ERR_CRC]
5. 设备发送DATA_FRAG [ChunkIdx=1]
6. 重复直到所有记录发完
7. APP回复DATA_ACK [Status=2] 可取消传输
```

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
| 值 | 含义 | APP处理 |
|----|------|---------|
| 0x01 | 不支持的命令 | 提示"设备不支持该命令" |
| 0x02 | 参数错误 | 提示"参数设置错误" |
| 0x03 | 忙碌中 | 提示"设备正在忙，请稍后重试" |
| 0x04 | 存储错误 | 提示"存储错误" |
| 0x05 | CRC校验失败 | 重发命令 |
| 0x06 | 超时 | 提示"设备响应超时" |

---

## 4. 数据记录格式

### 4.1 传感器记录 (32字节)

```
偏移    长度    字段名          类型        说明
──────────────────────────────────────────────────────────────
0x00    4B      timestamp       uint32_t    Unix时间戳, 大端序
0x04    1B      sensor_addr     uint8_t     传感器地址 (来自配置)
0x05    1B      status          uint8_t     状态码 (0=OK, 1=超时, 2=CRC错误, 3=Modbus异常)
0x06    2B      reg_count       uint16_t    寄存器数量, 大端序
0x08    2B      reg_values[0]   uint16_t    寄存器值0
0x0A    2B      reg_values[1]   uint16_t    寄存器值1
0x0C    2B      reg_values[2]   uint16_t    寄存器值2
0x0E    2B      reg_values[3]   uint16_t    寄存器值3
0x10    2B      reg_values[4]   uint16_t    寄存器值4
0x12    2B      reg_values[5]   uint16_t    寄存器值5
0x14    2B      reg_values[6]   uint16_t    寄存器值6
0x16    2B      reg_values[7]   uint16_t    寄存器值7
0x18    4B      sequence_num    uint32_t    单调递增序号
0x1C    2B      crc16           uint16_t    CRC16 (字节0x00~0x1B), 小端序
0x1E    2B      reserved        uint16_t    保留
──────────────────────────────────────────────────────────────
总计    32B
```

### 4.2 状态码定义

| 值 | 含义 | 说明 |
|----|------|------|
| 0 | OK | 正常采集 |
| 1 | TIMEOUT | Modbus通信超时 |
| 2 | CRC_ERR | Modbus CRC校验错误 |
| 3 | MODBUS_ERR | Modbus异常响应 |

---

## 5. 时序约束

| 参数 | 值 | 说明 |
|------|------|------|
| 帧间最小间隔 | 20ms | 连续两帧之间的最小时间 |
| 命令超时 | 5000ms | 等待响应的最大时间 |
| 重试次数 | 3 | 超时后重试次数 |
| 数据片超时 | 5000ms | DATA_FRAG等待ACK的时间 |
| ACK处理时间 | <100ms | 设备收到ACK到发出下一包的时间 |
| BLE连接超时 | 30s | 连接建立的最大等待时间 |
| MTU协商超时 | 5s | MTU协商的最大等待时间 |

---

## 6. 通信流程示例

### 6.1 设备连接流程

```
    APP                                    STM32
     │                                       │
     │  1. BLE扫描发现设备                    │
     │── CONNECT ───────────────────────────▶│
     │                                       │
     │  2. 建立GATT连接                       │
     │◀── CONNECTED ────────────────────────│
     │                                       │
     │  3. 发现服务和特征                     │
     │── DISCOVER_SERVICES ────────────────▶│
     │◀── SERVICES_FOUND ──────────────────│
     │                                       │
     │  4. 启用通知                           │
     │── ENABLE_NOTIFICATION ──────────────▶│
     │                                       │
     │  5. 协商MTU                           │
     │── REQUEST_MTU(247) ─────────────────▶│
     │◀── MTU_CHANGED(247) ────────────────│
     │                                       │
     │  6. 发送PING测试连接                   │
     │── [CMD=0x01] ───────────────────────▶│
     │◀── [DeviceID, FWver, Status] ────────│
     │                                       │
     │  7. 获取设备信息                       │
     │── [CMD=0x02] ───────────────────────▶│
     │◀── [完整设备信息] ────────────────────│
     │                                       │
     │  连接完成, 进入设备信息页面             │
```

### 6.2 数据下载流程

```
    APP                                    STM32
     │                                       │
     │  1. 发送GET_DATA请求                   │
     │── [CMD=0x06, Start=0, Count=100] ───▶│
     │                                       │
     │                              2. 设备准备数据
     │                              读取Flash记录
     │                                       │
     │  3. 设备发送第0片                      │
     │◀── [CMD=0xFE, ChunkIdx=0, 7条记录] ──│
     │                                       │
     │  4. APP校验并存储                      │
     │  5. APP回复ACK                         │
     │── [CMD=0x07, ChunkIdx=0, OK] ───────▶│
     │                                       │
     │  6. 设备发送第1片                      │
     │◀── [CMD=0xFE, ChunkIdx=1, 7条记录] ──│
     │                                       │
     │  7. APP回复ACK                         │
     │── [CMD=0x07, ChunkIdx=1, OK] ───────▶│
     │                                       │
     │         ... (重复直到所有记录发完) ...  │
     │                                       │
     │  N. 设备发送最后一片                   │
     │◀── [CMD=0xFE, ChunkIdx=N, 3条记录] ──│
     │                                       │
     │  N+1. APP回复ACK                       │
     │── [CMD=0x07, ChunkIdx=N, OK] ───────▶│
     │                                       │
     │  (传输完成, APP将数据存入Room)          │
```

### 6.3 配置写入流程

```
    APP                                    STM32
     │                                       │
     │  1. 读取当前配置                       │
     │── [CMD=0x04] ───────────────────────▶│
     │◀── [Interval, Addr, Start, ...] ──────│
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

### 6.4 时间同步流程

```
    APP                                    STM32
     │                                       │
     │  1. 获取当前手机时间                   │
     │  UnixTimestamp = 1780684800            │
     │                                       │
     │  2. 发送时间同步命令                   │
     │── [CMD=0x03, Timestamp=1780684800] ──▶│
     │                                       │
     │                              3. 设备设置RTC
     │                                       │
     │◀── [Status=0 (成功)] ────────────────│
     │                                       │
```

---

## 7. 错误处理

### 7.1 CRC校验失败

```
    APP                                    STM32
     │                                       │
     │◀── [CMD=0xFE, ChunkIdx=2, 数据] ─────│
     │   (CRC校验失败)                        │
     │                                       │
     │── [CMD=0x07, ChunkIdx=2, ERR_CRC] ──▶│
     │                                       │
     │◀── [CMD=0xFE, ChunkIdx=2, 数据(重发)] │
     │   (CRC校验成功)                        │
     │                                       │
     │── [CMD=0x07, ChunkIdx=2, OK] ───────▶│
```

### 7.2 超时处理

```
    APP                                    STM32
     │                                       │
     │── [CMD=0x06, Start=0, Count=100] ───▶│
     │                                       │
     │  (等待5秒无响应)                       │
     │                                       │
     │── [CMD=0x06, Start=0, Count=100] ───▶│  (重试1)
     │                                       │
     │  (等待5秒无响应)                       │
     │                                       │
     │── [CMD=0x06, Start=0, Count=100] ───▶│  (重试2)
     │                                       │
     │  (等待5秒无响应)                       │
     │                                       │
     │  报错: 设备无响应                      │
```

### 7.3 BLE断连恢复

```
    APP                                    STM32
     │                                       │
     │◀── [CMD=0xFE, ChunkIdx=5, 数据] ─────│
     │── [CMD=0x07, ChunkIdx=5, OK] ───────▶│
     │                                       │
     │◀── [CMD=0xFE, ChunkIdx=6, 数据] ─────│
     │                                       │
     │  ╳ BLE连接断开 ╳                       │
     │                                       │
     │  (自动重连...)                         │
     │                                       │
     │  (重连成功后, 从已下载数量继续)         │
     │── [CMD=0x06, Start=已下载数, Count=剩余]▶│
```

---

## 8. 下位机开发要点

### 8.1 初始化

```c
// 1. 初始化BLE模块
BLE_Init();

// 2. 初始化RTC
RTC_Init();

// 3. 初始化Flash存储
Storage_Init();

// 4. 读取配置
Config_Load();

// 5. 启动采集任务
Task_Start采集();
```

### 8.2 主循环

```c
while (1) {
    // 1. 处理BLE接收数据
    BLE_Process();
    
    // 2. 处理采集任务
    if (采样时间到) {
        Sensor_Read();
        Storage_Save();
    }
    
    // 3. 低功耗管理
    if (无任务) {
        Enter_Sleep();
    }
}
```

### 8.3 BLE接收处理

```c
void BLE_OnDataReceived(uint8_t *data, uint16_t len) {
    // 1. 帧解析
    Frame_t frame;
    if (!Frame_Parse(data, len, &frame)) {
        return; // 解析失败
    }
    
    // 2. CRC校验
    if (!CRC_Check(frame)) {
        return; // CRC错误
    }
    
    // 3. 命令分发
    switch (frame.cmd) {
        case CMD_PING:
            Handle_PING();
            break;
        case CMD_GET_INFO:
            Handle_GET_INFO();
            break;
        case CMD_SET_TIME:
            Handle_SET_TIME(frame.payload);
            break;
        case CMD_GET_CONFIG:
            Handle_GET_CONFIG();
            break;
        case CMD_SET_CONFIG:
            Handle_SET_CONFIG(frame.payload);
            break;
        case CMD_GET_DATA:
            Handle_GET_DATA(frame.payload);
            break;
        case CMD_DATA_ACK:
            Handle_DATA_ACK(frame.payload);
            break;
        case CMD_ERASE_DATA:
            Handle_ERASE_DATA(frame.payload);
            break;
        case CMD_GET_STATUS:
            Handle_GET_STATUS();
            break;
        case CMD_SET_DEVICE_ID:
            Handle_SET_DEVICE_ID(frame.payload);
            break;
        case CMD_REBOOT:
            Handle_REBOOT();
            break;
    }
}
```

### 8.4 数据发送

```c
void BLE_SendFrame(uint8_t cmd, uint8_t *payload, uint16_t len) {
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
    
    // CRC16
    uint16_t crc = CRC16_Modbus(&frame[1], idx - 1);
    frame[idx++] = crc & 0xFF;
    frame[idx++] = (crc >> 8) & 0xFF;
    
    // EOF
    frame[idx++] = 0x55;
    
    // 发送
    BLE_Transmit(frame, idx);
}
```

### 8.5 数据分片发送

```c
void Handle_GET_DATA(uint8_t *payload) {
    uint32_t startIndex = BytesToUint32(payload);
    uint16_t count = BytesToUint16(payload + 4);
    
    uint16_t chunkIdx = 0;
    uint16_t sent = 0;
    
    while (sent < count) {
        // 准备数据包
        uint8_t chunk[256];
        uint16_t chunkLen = 0;
        
        // ChunkIndex (大端序)
        chunk[chunkLen++] = (chunkIdx >> 8) & 0xFF;
        chunk[chunkLen++] = chunkIdx & 0xFF;
        
        // 记录数据 (最多7条)
        uint16_t recordsInChunk = min(7, count - sent);
        for (int i = 0; i < recordsInChunk; i++) {
            Record_t record;
            Storage_Read(startIndex + sent + i, &record);
            memcpy(&chunk[chunkLen], &record, 32);
            chunkLen += 32;
        }
        
        // 发送DATA_FRAG
        BLE_SendFrame(CMD_DATA_FRAG, chunk, chunkLen);
        
        // 等待ACK (超时5秒)
        if (Wait_ACK(5000) != ACK_OK) {
            // 重发或取消
            break;
        }
        
        chunkIdx++;
        sent += recordsInChunk;
    }
}
```

---

## 9. APP使用指南

### 9.1 首次使用

1. 安装APP到Android手机
2. 打开APP，授予蓝牙权限
3. 点击"开始扫描"
4. 选择设备，点击"连接"
5. 连接成功后进入设备信息页面

### 9.2 日常使用

1. **设置分机号**: 日常设置 → 分机号设置 → 输入0-99 → 点击设置
2. **设置采集间隔**: 日常设置 → 采集间隔 → 输入小时:分钟 → 点击设置
3. **启动采集**: 日常设置 → 采集控制 → 点击"启动采集"
4. **同步时间**: 日常设置 → 时间同步 → 点击"同步时间"
5. **下载数据**: 设备信息 → 点击"下载数据"
6. **导出数据**: 数据管理 → 点击"导出"

### 9.3 调试使用

1. 进入调试设置 (需要密码: 2611)
2. 点击"读取所有状态"获取完整信息
3. 修改485通信参数
4. 修改传感器参数
5. 发送Modbus原始命令测试

---

## 10. 常见问题

### Q1: 连接失败
- 检查设备是否在范围内
- 确认设备BLE功能正常
- 尝试重新扫描

### Q2: 数据下载失败
- 检查BLE连接是否稳定
- 确认设备有数据可下载
- 尝试重新连接

### Q3: 配置保存失败
- 检查参数是否在合法范围
- 确认设备未在采集状态
- 尝试重新读取配置

### Q4: 时间同步失败
- 检查BLE连接状态
- 确认设备RTC功能正常
- 尝试多次同步

---

## 11. 联系方式

- 项目地址: https://github.com/yang2318160326/dingdibanjiance
- 技术文档: docs/ 目录
- 问题反馈: GitHub Issues
