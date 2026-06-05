/**
 * @file    main_example.c
 * @brief   BLE协议库使用示例 - STM32L451主程序框架
 * @author  数据采集系统
 * @version V1.0.0
 * @date    2026-06-04
 *
 * 本文件展示了如何在STM32L451中使用ble_protocol.h库
 * 实际使用时, 请根据你的硬件平台修改UART初始化和发送/接收函数
 */

#include "ble_protocol.h"
#include "stm32l4xx_hal.h"  /* HAL库头文件, 根据实际芯片修改 */
#include <stdio.h>


/*============================================================================
 *                           全局变量
 *============================================================================*/

/** 序列号计数器, 每次发送帧时自动递增 */
static uint8_t g_seq_counter = 0;

/** 接收缓冲区 */
static uint8_t g_rx_buffer[FRAME_MAX_SIZE * 2];

/** 接收数据长度 */
static uint16_t g_rx_length = 0;

/** 设备配置 */
static DeviceConfig_t g_device_config = {
    .sampling_interval = 60,     /* 默认60秒采集一次 */
    .sensor_addr = 1,            /* 默认从站地址1 */
    .sensor_start_reg = 0,       /* 默认起始寄存器0 */
    .sensor_reg_count = 4,       /* 默认读4个寄存器 */
    .sensor_data_type = DATATYPE_UINT16,  /* 默认无符号16位 */
    .modbus_baudrate = 9600,     /* 默认波特率9600 */
    .modbus_parity = 0           /* 默认无校验 */
};

/** 设备状态 */
static DeviceStatus_t g_device_status = {
    .state = STATE_IDLE,
    .error_code = 0,
    .next_read_in = 0
};

/** 设备信息 */
static DeviceInfo_t g_device_info = {
    .device_id = 0x00000001,     /* 设备ID */
    .fw_version = 0x0100,        /* 固件版本 V1.00 */
    .record_count = 0,           /* 已记录数 */
    .free_space = 1024,          /* 剩余空间 */
    .uptime = 0,                 /* 运行时间 */
    .battery = 0xFF              /* 无电池 */
};


/*============================================================================
 *                           UART发送函数 (需要根据实际硬件修改)
 *============================================================================*/

/**
 * @brief  通过UART发送数据给蓝牙透传模块
 * @note   此函数需要根据你的STM32硬件平台实现
 *         STM32L451通过UART发送数据给HC-08/BT02蓝牙模块
 * @param  data: 数据指针
 * @param  len: 数据长度
 */
void BLE_UART_Send(const uint8_t *data, uint16_t len)
{
    /* 示例: 使用STM32 HAL库发送数据 */
    /* HAL_UART_Transmit(&huart1, (uint8_t *)data, len, 1000); */

    /* 或者使用DMA发送 (推荐, 不阻塞) */
    /* HAL_UART_Transmit_DMA(&huart1, (uint8_t *)data, len); */

    /* 临时实现: 逐字节发送 */
    for (uint16_t i = 0; i < len; i++) {
        /* 等待UART发送缓冲区空 */
        /* while (!(USART1->ISR & USART_ISR_TXE)); */
        /* USART1->TDR = data[i]; */
        (void)data[i];  /* 避免编译警告 */
    }
}


/*============================================================================
 *                           命令处理函数
 *============================================================================*/

/**
 * @brief  获取下一个序列号
 */
static uint8_t GetNextSeq(void)
{
    return g_seq_counter++;
}

/**
 * @brief  发送PING响应 (设备收到PING命令后调用)
 * @note   响应格式: DeviceID(4B) + FW_ver(2B) + Status(1B)
 */
void Handle_Ping(void)
{
    uint8_t payload[7];
    uint16_t idx = 0;

    /* 设备ID (4字节, 大端序) */
    Uint32_ToBytesBE(g_device_info.device_id, &payload[idx]);
    idx += 4;

    /* 固件版本 (2字节, 大端序) */
    payload[idx++] = (g_device_info.fw_version >> 8) & 0xFF;
    payload[idx++] = g_device_info.fw_version & 0xFF;

    /* 设备状态 */
    payload[idx++] = g_device_status.state;

    /* 构建响应帧并发送 */
    uint8_t frame[FRAME_MAX_SIZE];
    uint16_t frame_len = Frame_Build(GetNextSeq(), CMD_PING, payload, idx, frame);
    BLE_UART_Send(frame, frame_len);
}

/**
 * @brief  发送GET_INFO响应
 * @note   响应格式: 19字节设备完整信息
 */
void Handle_GetInfo(void)
{
    uint8_t payload[19];
    uint16_t idx = 0;

    /* 设备ID */
    Uint32_ToBytesBE(g_device_info.device_id, &payload[idx]);
    idx += 4;

    /* 固件版本 */
    payload[idx++] = (g_device_info.fw_version >> 8) & 0xFF;
    payload[idx++] = g_device_info.fw_version & 0xFF;

    /* 已记录数 */
    Uint32_ToBytesBE(g_device_info.record_count, &payload[idx]);
    idx += 4;

    /* 剩余空间 */
    Uint32_ToBytesBE(g_device_info.free_space, &payload[idx]);
    idx += 4;

    /* 运行时间 */
    Uint32_ToBytesBE(g_device_info.uptime, &payload[idx]);
    idx += 4;

    /* 电池电量 */
    payload[idx++] = g_device_info.battery;

    /* 构建响应帧并发送 */
    uint8_t frame[FRAME_MAX_SIZE];
    uint16_t frame_len = Frame_Build(GetNextSeq(), CMD_GET_INFO, payload, idx, frame);
    BLE_UART_Send(frame, frame_len);
}

/**
 * @brief  处理SET_TIME命令 (时间同步)
 * @param  payload: 载荷数据 (UnixTimestamp, 4字节)
 */
void Handle_SetTime(const uint8_t *payload)
{
    /* 解析时间戳 */
    uint32_t timestamp = Bytes_ToUint32BE(payload);

    /* TODO: 设置RTC时间 */
    /* HAL_RTC_SetTime(&hrtc, &time, RTC_FORMAT_BIN); */

    /* 发送成功响应 */
    uint8_t frame[FRAME_MAX_SIZE];
    uint8_t status = STATUS_OK;
    uint16_t frame_len = Frame_Build(GetNextSeq(), CMD_SET_TIME, &status, 1, frame);
    BLE_UART_Send(frame, frame_len);
}

/**
 * @brief  发送GET_CONFIG响应
 */
void Handle_GetConfig(void)
{
    uint8_t payload[15];
    uint16_t idx = 0;

    /* 采样间隔 (4字节, 大端序) */
    Uint32_ToBytesBE(g_device_config.sampling_interval, &payload[idx]);
    idx += 4;

    /* 传感器地址 */
    payload[idx++] = g_device_config.sensor_addr;

    /* 起始寄存器 (2字节, 大端序) */
    Uint16_ToBytesBE(g_device_config.sensor_start_reg, &payload[idx]);
    idx += 2;

    /* 寄存器数量 (2字节, 大端序) */
    Uint16_ToBytesBE(g_device_config.sensor_reg_count, &payload[idx]);
    idx += 2;

    /* 数据类型 */
    payload[idx++] = g_device_config.sensor_data_type;

    /* 波特率 (4字节, 大端序) */
    Uint32_ToBytesBE(g_device_config.modbus_baudrate, &payload[idx]);
    idx += 4;

    /* 校验位 */
    payload[idx++] = g_device_config.modbus_parity;

    /* 构建响应帧并发送 */
    uint8_t frame[FRAME_MAX_SIZE];
    uint16_t frame_len = Frame_Build(GetNextSeq(), CMD_GET_CONFIG, payload, idx, frame);
    BLE_UART_Send(frame, frame_len);
}

/**
 * @brief  处理SET_CONFIG命令 (写入配置)
 * @param  payload: 载荷数据 (15字节配置)
 */
void Handle_SetConfig(const uint8_t *payload)
{
    uint16_t idx = 0;

    /* 解析配置参数 */
    g_device_config.sampling_interval = Bytes_ToUint32BE(&payload[idx]);
    idx += 4;

    g_device_config.sensor_addr = payload[idx++];

    g_device_config.sensor_start_reg = Bytes_ToUint16BE(&payload[idx]);
    idx += 2;

    g_device_config.sensor_reg_count = Bytes_ToUint16BE(&payload[idx]);
    idx += 2;

    g_device_config.sensor_data_type = payload[idx++];

    g_device_config.modbus_baudrate = Bytes_ToUint32BE(&payload[idx]);
    idx += 4;

    g_device_config.modbus_parity = payload[idx++];

    /* TODO: 保存配置到Flash */
    /* Flash_SaveConfig(&g_device_config); */

    /* TODO: 更新UART波特率 (如果修改了波特率) */
    /* UpdateModbusBaudrate(g_device_config.modbus_baudrate); */

    /* 发送成功响应 */
    uint8_t frame[FRAME_MAX_SIZE];
    uint8_t status = STATUS_OK;
    uint16_t frame_len = Frame_Build(GetNextSeq(), CMD_SET_CONFIG, &status, 1, frame);
    BLE_UART_Send(frame, frame_len);
}

/**
 * @brief  处理GET_STATUS命令 (获取状态)
 */
void Handle_GetStatus(void)
{
    uint8_t payload[6];
    uint16_t idx = 0;

    /* 设备状态 */
    payload[idx++] = g_device_status.state;

    /* 错误码 */
    payload[idx++] = g_device_status.error_code;

    /* 下次采集时间 */
    Uint32_ToBytesBE(g_device_status.next_read_in, &payload[idx]);
    idx += 4;

    /* 构建响应帧并发送 */
    uint8_t frame[FRAME_MAX_SIZE];
    uint16_t frame_len = Frame_Build(GetNextSeq(), CMD_GET_STATUS, payload, idx, frame);
    BLE_UART_Send(frame, frame_len);
}

/**
 * @brief  处理SET_DEVICE_ID命令 (设置分机号)
 * @param  payload: 载荷数据 (DeviceID(4B) + Name(16B))
 */
void Handle_SetDeviceId(const uint8_t *payload)
{
    /* 解析设备ID */
    uint32_t device_id = Bytes_ToUint32BE(payload);

    /* 更新设备ID */
    g_device_info.device_id = device_id;

    /* TODO: 保存到Flash */
    /* Flash_SaveDeviceId(device_id); */

    /* 发送成功响应 */
    uint8_t frame[FRAME_MAX_SIZE];
    uint8_t status = STATUS_OK;
    uint16_t frame_len = Frame_Build(GetNextSeq(), CMD_SET_DEVICE_ID, &status, 1, frame);
    BLE_UART_Send(frame, frame_len);
}

/**
 * @brief  处理ERASE_DATA命令 (擦除数据)
 * @param  payload: 载荷数据 (ConfirmCode, 4字节)
 */
void Handle_EraseData(const uint8_t *payload)
{
    uint32_t confirm_code = Bytes_ToUint32BE(payload);

    /* 检查确认码 */
    if (confirm_code != ERASE_CONFIRM_CODE) {
        /* 确认码错误 */
        uint8_t frame[FRAME_MAX_SIZE];
        uint8_t status = STATUS_FAIL;
        uint16_t frame_len = Frame_Build(GetNextSeq(), CMD_ERASE_DATA, &status, 1, frame);
        BLE_UART_Send(frame, frame_len);
        return;
    }

    /* TODO: 擦除Flash数据 */
    /* Flash_EraseAllRecords(); */

    /* 更新记录数 */
    g_device_info.record_count = 0;
    g_device_info.free_space = 1024;

    /* 发送成功响应 */
    uint8_t frame[FRAME_MAX_SIZE];
    uint8_t status = STATUS_OK;
    uint16_t frame_len = Frame_Build(GetNextSeq(), CMD_ERASE_DATA, &status, 1, frame);
    BLE_UART_Send(frame, frame_len);
}

/**
 * @brief  处理REBOOT命令 (重启设备)
 * @param  payload: 载荷数据 (ConfirmCode, 4字节)
 */
void Handle_Reboot(const uint8_t *payload)
{
    uint32_t confirm_code = Bytes_ToUint32BE(payload);

    /* 检查确认码 */
    if (confirm_code != REBOOT_CONFIRM_CODE) {
        return;  /* 确认码错误, 不响应 */
    }

    /* 发送成功响应 */
    uint8_t frame[FRAME_MAX_SIZE];
    uint8_t status = STATUS_OK;
    uint16_t frame_len = Frame_Build(GetNextSeq(), CMD_REBOOT, &status, 1, frame);
    BLE_UART_Send(frame, frame_len);

    /* 延时后重启 */
    /* HAL_Delay(200); */
    /* NVIC_SystemReset(); */
}

/**
 * @brief  发送数据分片 (在GET_DATA命令处理中调用)
 * @param  start_index: 起始记录索引
 * @param  count: 记录数量
 */
void Handle_GetData(uint32_t start_index, uint16_t count)
{
    uint16_t chunk_index = 0;
    uint16_t sent = 0;

    while (sent < count) {
        /* 准备数据包 */
        uint8_t chunk_data[RECORDS_PER_CHUNK * RECORD_SIZE];
        uint16_t chunk_len = 0;

        /* 计算本片记录数 */
        uint16_t records_in_chunk = count - sent;
        if (records_in_chunk > RECORDS_PER_CHUNK) {
            records_in_chunk = RECORDS_PER_CHUNK;
        }

        /* 读取记录并转换为字节数据 */
        for (uint16_t i = 0; i < records_in_chunk; i++) {
            SensorRecord_t record;

            /* TODO: 从Flash读取记录 */
            /* Flash_ReadRecord(start_index + sent + i, &record); */

            /* 模拟数据 (实际应从Flash读取) */
            record.timestamp = 1780684800 + (start_index + sent + i) * 60;
            record.sensor_addr = g_device_config.sensor_addr;
            record.status = 0;
            record.reg_count = g_device_config.sensor_reg_count;
            for (int j = 0; j < 8; j++) {
                record.reg_values[j] = 1000 + j * 100 + i;
            }
            record.sequence_num = start_index + sent + i;

            /* 转换为32字节 */
            Record_ToBytes(&record, &chunk_data[chunk_len]);
            chunk_len += RECORD_SIZE;
        }

        /* 构建DATA_FRAG帧并发送 */
        uint8_t frame[FRAME_MAX_SIZE];
        uint16_t frame_len = Cmd_BuildDataFrag(GetNextSeq(), chunk_index, chunk_data, chunk_len, frame);
        BLE_UART_Send(frame, frame_len);

        /* 等待ACK (实际项目中应使用中断或DMA接收) */
        /* TODO: 等待DATA_ACK响应 */

        chunk_index++;
        sent += records_in_chunk;
    }
}


/*============================================================================
 *                           接收处理主函数
 *============================================================================*/

/**
 * @brief  处理接收到的帧
 * @note   在UART接收中断或轮询中调用此函数
 * @param  data: 接收数据
 * @param  len: 数据长度
 */
void BLE_ProcessReceivedData(const uint8_t *data, uint16_t len)
{
    Frame_t frame;

    /* 解析帧 */
    if (!Frame_Parse(data, len, &frame)) {
        /* 帧解析失败 (CRC错误或格式错误) */
        return;
    }

    /* 根据命令码分发处理 */
    switch (frame.cmd) {
        case CMD_PING:
            /* 设备探测 */
            Handle_Ping();
            break;

        case CMD_GET_INFO:
            /* 获取设备信息 */
            Handle_GetInfo();
            break;

        case CMD_SET_TIME:
            /* 时间同步 */
            Handle_SetTime(frame.payload);
            break;

        case CMD_GET_CONFIG:
            /* 读取配置 */
            Handle_GetConfig();
            break;

        case CMD_SET_CONFIG:
            /* 写入配置 */
            Handle_SetConfig(frame.payload);
            break;

        case CMD_GET_DATA:
        {
            /* 请求数据下载 */
            uint32_t start_index = Bytes_ToUint32BE(&frame.payload[0]);
            uint16_t count = Bytes_ToUint16BE(&frame.payload[4]);
            Handle_GetData(start_index, count);
            break;
        }

        case CMD_ERASE_DATA:
            /* 擦除数据 */
            Handle_EraseData(frame.payload);
            break;

        case CMD_GET_STATUS:
            /* 获取状态 */
            Handle_GetStatus();
            break;

        case CMD_SET_DEVICE_ID:
            /* 设置设备ID */
            Handle_SetDeviceId(frame.payload);
            break;

        case CMD_REBOOT:
            /* 重启设备 */
            Handle_Reboot(frame.payload);
            break;

        default:
            /* 不支持的命令, 发送错误响应 */
        {
            uint8_t frame_buf[FRAME_MAX_SIZE];
            uint16_t frame_len = Cmd_BuildError(GetNextSeq(), ERR_UNSUPPORTED_CMD, frame.cmd, frame_buf);
            BLE_UART_Send(frame_buf, frame_len);
            break;
        }
    }
}


/*============================================================================
 *                           主函数示例
 *============================================================================*/

/**
 * @brief  主函数 - 程序入口
 */
int main(void)
{
    /* 1. 初始化硬件 */
    HAL_Init();
    SystemClock_Config();

    /* 2. 初始化UART (用于蓝牙透传) */
    /* MX_USART1_UART_Init(); */

    /* 3. 初始化RTC */
    /* MX_RTC_Init(); */

    /* 4. 初始化Flash存储 */
    /* Flash_Init(); */

    /* 5. 从Flash加载配置 */
    /* Flash_LoadConfig(&g_device_config); */

    printf("系统启动, 固件版本: V%d.%02d\n",
           g_device_info.fw_version >> 8,
           g_device_info.fw_version & 0xFF);

    /* 主循环 */
    while (1) {
        /* 1. 检查UART接收数据 */
        /* if (UART_DataAvailable()) { */
        /*     uint16_t len = UART_Read(g_rx_buffer, sizeof(g_rx_buffer)); */
        /*     BLE_ProcessReceivedData(g_rx_buffer, len); */
        /* } */

        /* 2. 检查采集定时器 */
        if (g_device_config.sampling_interval > 0) {
            g_device_status.next_read_in--;

            if (g_device_status.next_read_in == 0) {
                /* 采集时间到 */
                g_device_status.state = STATE_COLLECTING;

                /* TODO: 读取Modbus传感器数据 */
                /* Sensor_ReadData(&record); */

                /* TODO: 保存到Flash */
                /* Flash_SaveRecord(&record); */

                /* 更新状态 */
                g_device_status.state = STATE_IDLE;
                g_device_status.next_read_in = g_device_config.sampling_interval;
                g_device_info.record_count++;
            }
        }

        /* 3. 低功耗管理 */
        /* if (无任务) { */
        /*     HAL_PWR_EnterSLEEPMode(PWR_MAINREGULATOR_ON, PWR_SLEEPENTRY_WFI); */
        /* } */
    }
}
