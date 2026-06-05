/**
 * @file    ble_protocol.c
 * @brief   BLE通信协议库 - 实现文件
 * @author  数据采集系统
 * @version V1.0.0
 * @date    2026-06-04
 * @note    适用于STM32L451 + HC-08/BT02蓝牙透传模块
 *
 * 使用说明:
 *   1. 调用 Frame_BuildEmpty() 或 Cmd_BuildXxx() 构建命令帧
 *   2. 通过UART发送给蓝牙透传模块
 *   3. 接收数据后调用 Frame_Parse() 解析帧
 *   4. 根据命令码调用对应的 Resp_ParseXxx() 解析响应
 */

#include "ble_protocol.h"


/*============================================================================
 *                           CRC16校验函数
 *============================================================================*/

/**
 * @brief  计算CRC16/Modbus校验值
 * @note   多项式: 0xA001 (反转0x8005)
 *         初始值: 0xFFFF
 *         计算范围: 从第一个字节开始到CRC之前的所有字节
 * @param  data: 数据指针
 * @param  len: 数据长度
 * @return CRC16校验值 (小端序: 低字节在前)
 */
uint16_t CRC16_Modbus(const uint8_t *data, uint16_t len)
{
    uint16_t crc = 0xFFFF;  /* 初始化CRC寄存器 */

    /* 逐字节处理 */
    for (uint16_t i = 0; i < len; i++) {
        crc ^= data[i];     /* 将当前字节与CRC低字节异或 */

        /* 处理8位 */
        for (int j = 0; j < 8; j++) {
            if (crc & 0x0001) {
                /* 最低位为1, 右移并异或多项式 */
                crc = (crc >> 1) ^ 0xA001;
            } else {
                /* 最低位为0, 仅右移 */
                crc >>= 1;
            }
        }
    }

    return crc;
}


/*============================================================================
 *                           帧构建函数
 *============================================================================*/

/**
 * @brief  构建发送帧
 * @note   帧格式: SOF + SEQ + CMD + LEN(大端) + PAYLOAD + CRC16(小端) + EOF
 * @param  seq: 序列号 (0x00-0xFF)
 * @param  cmd: 命令码
 * @param  payload: 载荷数据指针 (可为NULL)
 * @param  payload_len: 载荷长度
 * @param  frame_buf: 输出帧缓冲区 (至少 FRAME_MAX_SIZE 字节)
 * @return 帧总长度
 */
uint16_t Frame_Build(uint8_t seq, uint8_t cmd, const uint8_t *payload,
                     uint16_t payload_len, uint8_t *frame_buf)
{
    uint16_t idx = 0;

    /* 1. 帧起始标志 */
    frame_buf[idx++] = FRAME_SOF;

    /* 2. 序列号 */
    frame_buf[idx++] = seq;

    /* 3. 命令码 */
    frame_buf[idx++] = cmd;

    /* 4. 载荷长度 (大端序: 高字节在前) */
    frame_buf[idx++] = (payload_len >> 8) & 0xFF;
    frame_buf[idx++] = payload_len & 0xFF;

    /* 5. 载荷数据 */
    if (payload_len > 0 && payload != NULL) {
        memcpy(&frame_buf[idx], payload, payload_len);
        idx += payload_len;
    }

    /* 6. CRC16校验 (计算范围: 从SEQ到PAYLOAD结束) */
    uint16_t crc = CRC16_Modbus(&frame_buf[1], idx - 1);
    frame_buf[idx++] = crc & 0xFF;         /* CRC低字节 */
    frame_buf[idx++] = (crc >> 8) & 0xFF;  /* CRC高字节 */

    /* 7. 帧结束标志 */
    frame_buf[idx++] = FRAME_EOF;

    return idx;  /* 返回帧总长度 */
}

/**
 * @brief  构建空载荷帧 (用于PING, GET_INFO等无参命令)
 */
uint16_t Frame_BuildEmpty(uint8_t seq, uint8_t cmd, uint8_t *frame_buf)
{
    return Frame_Build(seq, cmd, NULL, 0, frame_buf);
}


/*============================================================================
 *                           帧解析函数
 *============================================================================*/

/**
 * @brief  解析接收帧
 * @note   解析流程: 检查SOF → 检查长度 → 提取字段 → 验证CRC → 检查EOF
 * @param  data: 接收数据指针
 * @param  data_len: 数据长度
 * @param  frame: 输出帧结构体
 * @return true=解析成功, false=解析失败
 */
bool Frame_Parse(const uint8_t *data, uint16_t data_len, Frame_t *frame)
{
    /* 1. 检查最小长度 */
    if (data_len < FRAME_MIN_SIZE) {
        return false;
    }

    /* 2. 检查帧起始标志 */
    if (data[0] != FRAME_SOF) {
        return false;
    }

    /* 3. 检查帧结束标志 */
    if (data[data_len - 1] != FRAME_EOF) {
        return false;
    }

    /* 4. 提取序列号 */
    frame->seq = data[1];

    /* 5. 提取命令码 */
    frame->cmd = data[2];

    /* 6. 提取载荷长度 (大端序) */
    frame->len = ((uint16_t)data[3] << 8) | data[4];

    /* 7. 检查帧长度是否匹配 */
    if (data_len != FRAME_MIN_SIZE + frame->len) {
        return false;
    }

    /* 8. 提取载荷数据 */
    if (frame->len > 0) {
        memcpy(frame->payload, &data[5], frame->len);
    }

    /* 9. 验证CRC16 (计算范围: 从SEQ到PAYLOAD结束) */
    uint16_t expected_crc = CRC16_Modbus(&data[1], frame->len + 4);
    uint16_t actual_crc = (uint16_t)data[5 + frame->len] |
                          ((uint16_t)data[6 + frame->len] << 8);

    if (expected_crc != actual_crc) {
        return false;  /* CRC校验失败 */
    }

    return true;  /* 解析成功 */
}


/*============================================================================
 *                           命令构建函数
 *============================================================================*/

/**
 * @brief  构建PING命令帧 (0x01)
 * @note   无载荷, 用于测试BLE连接是否正常
 */
uint16_t Cmd_BuildPing(uint8_t seq, uint8_t *frame_buf)
{
    return Frame_BuildEmpty(seq, CMD_PING, frame_buf);
}

/**
 * @brief  构建GET_INFO命令帧 (0x02)
 * @note   无载荷, 获取设备完整信息
 */
uint16_t Cmd_BuildGetInfo(uint8_t seq, uint8_t *frame_buf)
{
    return Frame_BuildEmpty(seq, CMD_GET_INFO, frame_buf);
}

/**
 * @brief  构建SET_TIME命令帧 (0x03)
 * @note   载荷: UnixTimestamp (4字节, 大端序)
 * @param  timestamp: Unix时间戳 (自1970-01-01 00:00:00 UTC以来的秒数)
 */
uint16_t Cmd_BuildSetTime(uint8_t seq, uint32_t timestamp, uint8_t *frame_buf)
{
    uint8_t payload[4];
    Uint32_ToBytesBE(timestamp, payload);
    return Frame_Build(seq, CMD_SET_TIME, payload, 4, frame_buf);
}

/**
 * @brief  构建GET_CONFIG命令帧 (0x04)
 * @note   无载荷, 读取设备配置参数
 */
uint16_t Cmd_BuildGetConfig(uint8_t seq, uint8_t *frame_buf)
{
    return Frame_BuildEmpty(seq, CMD_GET_CONFIG, frame_buf);
}

/**
 * @brief  构建SET_CONFIG命令帧 (0x05)
 * @note   载荷: 15字节配置数据
 *         Interval(4B) + Addr(1B) + StartReg(2B) + RegCount(2B) + DataType(1B) + Baudrate(4B) + Parity(1B)
 * @param  config: 配置结构体指针
 */
uint16_t Cmd_BuildSetConfig(uint8_t seq, const DeviceConfig_t *config, uint8_t *frame_buf)
{
    uint8_t payload[15];
    uint16_t idx = 0;

    /* 采样间隔 (4字节, 大端序) */
    Uint32_ToBytesBE(config->sampling_interval, &payload[idx]);
    idx += 4;

    /* 传感器地址 (1字节) */
    payload[idx++] = config->sensor_addr;

    /* 起始寄存器 (2字节, 大端序) */
    Uint16_ToBytesBE(config->sensor_start_reg, &payload[idx]);
    idx += 2;

    /* 寄存器数量 (2字节, 大端序) */
    Uint16_ToBytesBE(config->sensor_reg_count, &payload[idx]);
    idx += 2;

    /* 数据类型 (1字节) */
    payload[idx++] = config->sensor_data_type;

    /* 波特率 (4字节, 大端序) */
    Uint32_ToBytesBE(config->modbus_baudrate, &payload[idx]);
    idx += 4;

    /* 校验位 (1字节) */
    payload[idx++] = config->modbus_parity;

    return Frame_Build(seq, CMD_SET_CONFIG, payload, idx, frame_buf);
}

/**
 * @brief  构建GET_DATA命令帧 (0x06)
 * @note   载荷: StartIndex(4B) + Count(2B)
 * @param  start_index: 起始记录索引 (从0开始)
 * @param  count: 请求记录数量
 */
uint16_t Cmd_BuildGetData(uint8_t seq, uint32_t start_index, uint16_t count, uint8_t *frame_buf)
{
    uint8_t payload[6];
    Uint32_ToBytesBE(start_index, &payload[0]);
    Uint16_ToBytesBE(count, &payload[4]);
    return Frame_Build(seq, CMD_GET_DATA, payload, 6, frame_buf);
}

/**
 * @brief  构建DATA_ACK命令帧 (0x07)
 * @note   载荷: ChunkIndex(2B) + Status(1B)
 * @param  chunk_index: 确认的分片序号
 * @param  status: 0=成功, 1=CRC错误, 2=取消
 */
uint16_t Cmd_BuildDataAck(uint8_t seq, uint16_t chunk_index, uint8_t status, uint8_t *frame_buf)
{
    uint8_t payload[3];
    Uint16_ToBytesBE(chunk_index, &payload[0]);
    payload[2] = status;
    return Frame_Build(seq, CMD_DATA_ACK, payload, 3, frame_buf);
}

/**
 * @brief  构建ERASE_DATA命令帧 (0x08)
 * @note   载荷: ConfirmCode (0xDEADBEEF, 防误操作)
 */
uint16_t Cmd_BuildEraseData(uint8_t seq, uint8_t *frame_buf)
{
    uint8_t payload[4];
    Uint32_ToBytesBE(ERASE_CONFIRM_CODE, payload);
    return Frame_Build(seq, CMD_ERASE_DATA, payload, 4, frame_buf);
}

/**
 * @brief  构建GET_STATUS命令帧 (0x09)
 * @note   无载荷, 获取设备运行状态
 */
uint16_t Cmd_BuildGetStatus(uint8_t seq, uint8_t *frame_buf)
{
    return Frame_BuildEmpty(seq, CMD_GET_STATUS, frame_buf);
}

/**
 * @brief  构建SET_DEVICE_ID命令帧 (0x0A)
 * @note   载荷: DeviceID(4B) + DeviceName(16B, UTF-8, \0结尾)
 * @param  device_id: 新的设备ID
 * @param  name: 设备名称字符串 (最多15字符)
 */
uint16_t Cmd_BuildSetDeviceId(uint8_t seq, uint32_t device_id, const char *name, uint8_t *frame_buf)
{
    uint8_t payload[20];
    uint16_t idx = 0;

    /* 设备ID (4字节, 大端序) */
    Uint32_ToBytesBE(device_id, &payload[idx]);
    idx += 4;

    /* 设备名称 (16字节, UTF-8, 零填充) */
    memset(&payload[idx], 0, 16);
    if (name != NULL) {
        uint16_t name_len = strlen(name);
        if (name_len > 15) name_len = 15;  /* 最多15字符 */
        memcpy(&payload[idx], name, name_len);
    }
    idx += 16;

    return Frame_Build(seq, CMD_SET_DEVICE_ID, payload, idx, frame_buf);
}

/**
 * @brief  构建REBOOT命令帧 (0x0B)
 * @note   载荷: ConfirmCode (0xCAFEBABE, 防误操作)
 */
uint16_t Cmd_BuildReboot(uint8_t seq, uint8_t *frame_buf)
{
    uint8_t payload[4];
    Uint32_ToBytesBE(REBOOT_CONFIRM_CODE, payload);
    return Frame_Build(seq, CMD_REBOOT, payload, 4, frame_buf);
}

/**
 * @brief  构建DATA_FRAG帧 (0xFE) - 设备主动发送
 * @note   载荷: ChunkIndex(2B) + RecordData(N*32B)
 * @param  chunk_index: 分片序号
 * @param  data: 记录数据指针
 * @param  data_len: 数据长度 (字节)
 */
uint16_t Cmd_BuildDataFrag(uint8_t seq, uint16_t chunk_index,
                           const uint8_t *data, uint16_t data_len, uint8_t *frame_buf)
{
    uint8_t payload[2 + FRAME_MAX_PAYLOAD];
    Uint16_ToBytesBE(chunk_index, &payload[0]);
    if (data_len > 0 && data != NULL) {
        memcpy(&payload[2], data, data_len);
    }
    return Frame_Build(seq, CMD_DATA_FRAG, payload, 2 + data_len, frame_buf);
}

/**
 * @brief  构建ERROR帧 (0xFF) - 设备主动发送
 * @note   载荷: ErrCode(1B) + OrigCMD(1B)
 * @param  err_code: 错误码
 * @param  orig_cmd: 引起错误的原始命令码
 */
uint16_t Cmd_BuildError(uint8_t seq, uint8_t err_code, uint8_t orig_cmd, uint8_t *frame_buf)
{
    uint8_t payload[2];
    payload[0] = err_code;
    payload[1] = orig_cmd;
    return Frame_Build(seq, CMD_ERROR, payload, 2, frame_buf);
}


/*============================================================================
 *                           响应解析函数
 *============================================================================*/

/**
 * @brief  解析PING响应
 * @note   响应格式: DeviceID(4B) + FW_ver(2B) + Status(1B)
 */
bool Resp_ParsePing(const uint8_t *payload, uint16_t len, DeviceInfo_t *info)
{
    if (len < 7) return false;  /* 最小7字节 */

    info->device_id = Bytes_ToUint32BE(&payload[0]);   /* 设备ID */
    info->fw_version = ((uint16_t)payload[4] << 8) | payload[5];  /* 固件版本 */
    info->battery = 0xFF;  /* PING响应不含电量, 设为无效值 */

    return true;
}

/**
 * @brief  解析GET_INFO响应
 * @note   响应格式: DeviceID(4B) + FW_ver(2B) + RecordCount(4B) + FreeSpace(4B) + Uptime(4B) + Battery(1B)
 */
bool Resp_ParseGetInfo(const uint8_t *payload, uint16_t len, DeviceInfo_t *info)
{
    if (len < 19) return false;  /* 最小19字节 */

    info->device_id = Bytes_ToUint32BE(&payload[0]);     /* 设备ID */
    info->fw_version = ((uint16_t)payload[4] << 8) | payload[5];  /* 固件版本 */
    info->record_count = Bytes_ToUint32BE(&payload[6]);  /* 已记录数 */
    info->free_space = Bytes_ToUint32BE(&payload[10]);   /* 剩余空间 */
    info->uptime = Bytes_ToUint32BE(&payload[14]);       /* 运行时间 */
    info->battery = payload[18];                          /* 电池电量 */

    return true;
}

/**
 * @brief  解析GET_CONFIG响应
 * @note   响应格式: Interval(4B) + Addr(1B) + StartReg(2B) + RegCount(2B) + DataType(1B) + Baudrate(4B) + Parity(1B)
 */
bool Resp_ParseGetConfig(const uint8_t *payload, uint16_t len, DeviceConfig_t *config)
{
    if (len < 15) return false;  /* 最小15字节 */

    uint16_t idx = 0;

    config->sampling_interval = Bytes_ToUint32BE(&payload[idx]);  /* 采样间隔 */
    idx += 4;

    config->sensor_addr = payload[idx++];                         /* 传感器地址 */

    config->sensor_start_reg = Bytes_ToUint16BE(&payload[idx]);   /* 起始寄存器 */
    idx += 2;

    config->sensor_reg_count = Bytes_ToUint16BE(&payload[idx]);   /* 寄存器数量 */
    idx += 2;

    config->sensor_data_type = payload[idx++];                    /* 数据类型 */

    config->modbus_baudrate = Bytes_ToUint32BE(&payload[idx]);    /* 波特率 */
    idx += 4;

    config->modbus_parity = payload[idx++];                       /* 校验位 */

    return true;
}

/**
 * @brief  解析GET_STATUS响应
 * @note   响应格式: State(1B) + ErrorCode(1B) + NextReadIn(4B)
 */
bool Resp_ParseGetStatus(const uint8_t *payload, uint16_t len, DeviceStatus_t *status)
{
    if (len < 6) return false;  /* 最小6字节 */

    status->state = payload[0];                  /* 设备状态 */
    status->error_code = payload[1];             /* 错误码 */
    status->next_read_in = Bytes_ToUint32BE(&payload[2]);  /* 下次采集时间 */

    return true;
}

/**
 * @brief  解析DATA_FRAG分片
 * @note   载荷格式: ChunkIndex(2B) + N * RecordData(32B)
 * @param  payload: 载荷数据
 * @param  len: 载荷长度
 * @param  chunk_index: 输出分片序号
 * @param  records: 输出记录数组
 * @param  max_records: 最大记录数
 * @return 实际解析的记录数
 */
uint16_t Resp_ParseDataFrag(const uint8_t *payload, uint16_t len,
                            uint16_t *chunk_index, SensorRecord_t *records, uint16_t max_records)
{
    if (len < 2) return 0;

    /* 提取分片序号 */
    *chunk_index = Bytes_ToUint16BE(&payload[0]);

    /* 解析记录数据 */
    uint16_t record_count = 0;
    uint16_t offset = 2;

    while (offset + RECORD_SIZE <= len && record_count < max_records) {
        if (Record_FromBytes(&payload[offset], &records[record_count])) {
            record_count++;
        }
        offset += RECORD_SIZE;
    }

    return record_count;
}


/*============================================================================
 *                           工具函数
 *============================================================================*/

/**
 * @brief  从大端序字节数组读取32位无符号整数
 */
uint32_t Bytes_ToUint32BE(const uint8_t *data)
{
    return ((uint32_t)data[0] << 24) |
           ((uint32_t)data[1] << 16) |
           ((uint32_t)data[2] << 8)  |
           ((uint32_t)data[3]);
}

/**
 * @brief  从大端序字节数组读取16位无符号整数
 */
uint16_t Bytes_ToUint16BE(const uint8_t *data)
{
    return ((uint16_t)data[0] << 8) | data[1];
}

/**
 * @brief  将32位无符号整数写入大端序字节数组
 */
void Uint32_ToBytesBE(uint32_t value, uint8_t *data)
{
    data[0] = (value >> 24) & 0xFF;
    data[1] = (value >> 16) & 0xFF;
    data[2] = (value >> 8) & 0xFF;
    data[3] = value & 0xFF;
}

/**
 * @brief  将16位无符号整数写入大端序字节数组
 */
void Uint16_ToBytesBE(uint16_t value, uint8_t *data)
{
    data[0] = (value >> 8) & 0xFF;
    data[1] = value & 0xFF;
}

/**
 * @brief  将传感器记录结构体转换为32字节原始数据
 * @note   布局: timestamp(4B) + addr(1B) + status(1B) + reg_count(2B) + reg_values(16B) + seq_num(4B) + crc16(2B) + reserved(2B)
 */
void Record_ToBytes(const SensorRecord_t *record, uint8_t *buf)
{
    uint16_t idx = 0;

    /* 时间戳 (4字节, 大端序) */
    Uint32_ToBytesBE(record->timestamp, &buf[idx]);
    idx += 4;

    /* 传感器地址 (1字节) */
    buf[idx++] = record->sensor_addr;

    /* 状态码 (1字节) */
    buf[idx++] = record->status;

    /* 寄存器数量 (2字节, 大端序) */
    Uint16_ToBytesBE(record->reg_count, &buf[idx]);
    idx += 2;

    /* 寄存器值 (8个, 每个2字节, 大端序) */
    for (int i = 0; i < 8; i++) {
        Uint16_ToBytesBE(record->reg_values[i], &buf[idx]);
        idx += 2;
    }

    /* 序列号 (4字节, 大端序) */
    Uint32_ToBytesBE(record->sequence_num, &buf[idx]);
    idx += 4;

    /* CRC16 (2字节, 小端序) - 计算范围: 前28字节 */
    uint16_t crc = CRC16_Modbus(buf, 28);
    buf[idx++] = crc & 0xFF;         /* CRC低字节 */
    buf[idx++] = (crc >> 8) & 0xFF;  /* CRC高字节 */

    /* 保留 (2字节) */
    buf[idx++] = 0;
    buf[idx++] = 0;
}

/**
 * @brief  将32字节原始数据转换为传感器记录结构体
 */
bool Record_FromBytes(const uint8_t *buf, SensorRecord_t *record)
{
    uint16_t idx = 0;

    /* 时间戳 */
    record->timestamp = Bytes_ToUint32BE(&buf[idx]);
    idx += 4;

    /* 传感器地址 */
    record->sensor_addr = buf[idx++];

    /* 状态码 */
    record->status = buf[idx++];

    /* 寄存器数量 */
    record->reg_count = Bytes_ToUint16BE(&buf[idx]);
    idx += 2;

    /* 寄存器值 */
    for (int i = 0; i < 8; i++) {
        record->reg_values[i] = Bytes_ToUint16BE(&buf[idx]);
        idx += 2;
    }

    /* 序列号 */
    record->sequence_num = Bytes_ToUint32BE(&buf[idx]);
    idx += 4;

    /* CRC16 */
    record->crc16 = buf[idx] | ((uint16_t)buf[idx + 1] << 8);
    idx += 2;

    /* 保留 */
    record->reserved = Bytes_ToUint16BE(&buf[idx]);

    /* 验证CRC */
    uint16_t expected_crc = CRC16_Modbus(buf, 28);
    if (record->crc16 != expected_crc) {
        return false;  /* CRC校验失败 */
    }

    return true;
}
