/**
 * @file    ble_protocol.h
 * @brief   BLE通信协议库 - 头文件
 * @author  数据采集系统
 * @version V1.0.0
 * @date    2026-06-04
 * @note    适用于STM32L451 + HC-08/BT02蓝牙透传模块
 *
 * 协议说明:
 *   帧格式: SOF(0xAA) + SEQ(1B) + CMD(1B) + LEN(2B) + PAYLOAD(0~237B) + CRC16(2B) + EOF(0x55)
 *   字节序: LEN字段使用大端序, CRC16使用小端序
 *   校验算法: CRC16/Modbus (多项式0xA001, 初始值0xFFFF)
 */

#ifndef __BLE_PROTOCOL_H
#define __BLE_PROTOCOL_H

#ifdef __cplusplus
extern "C" {
#endif

#include <stdint.h>
#include <stdbool.h>
#include <string.h>

/*============================================================================
 *                           常量定义
 *============================================================================*/

/** @defgroup Protocol_Constants 协议常量
 * @{
 */
#define FRAME_SOF               0xAA    /**< 帧起始标志 */
#define FRAME_EOF               0x55    /**< 帧结束标志 */
#define FRAME_HEADER_SIZE       5       /**< 帧头大小: SOF(1) + SEQ(1) + CMD(1) + LEN(2) */
#define FRAME_FOOTER_SIZE       3       /**< 帧尾大小: CRC(2) + EOF(1) */
#define FRAME_MIN_SIZE          7       /**< 最小帧大小 (无载荷) */
#define FRAME_MAX_PAYLOAD       237     /**< 最大载荷大小 (MTU=247时) */
#define FRAME_MAX_SIZE          244     /**< 最大帧大小 (237+7) */

#define SEQ_MAX                 0xFF    /**< 序列号最大值 */
/** @} */

/** @defgroup Command_Codes 命令码定义
 * @{
 */
#define CMD_PING                0x01    /**< 设备探测 - 测试连接 */
#define CMD_GET_INFO            0x02    /**< 获取设备完整信息 */
#define CMD_SET_TIME            0x03    /**< 时间同步 */
#define CMD_GET_CONFIG          0x04    /**< 读取配置参数 */
#define CMD_SET_CONFIG          0x05    /**< 写入配置参数 */
#define CMD_GET_DATA            0x06    /**< 请求数据下载 */
#define CMD_DATA_ACK            0x07    /**< 数据确认 */
#define CMD_ERASE_DATA          0x08    /**< 擦除数据 */
#define CMD_GET_STATUS          0x09    /**< 获取设备状态 */
#define CMD_SET_DEVICE_ID       0x0A    /**< 设置设备ID */
#define CMD_REBOOT              0x0B    /**< 重启设备 */
#define CMD_DATA_FRAG           0xFE    /**< 数据分片 (设备主动发送) */
#define CMD_ERROR               0xFF    /**< 错误通知 */
/** @} */

/** @defgroup Status_Codes 状态码定义
 * @{
 */
#define STATUS_OK               0       /**< 操作成功 */
#define STATUS_FAIL             1       /**< 操作失败 */
#define STATUS_BUSY             2       /**< 设备忙碌 */
/** @} */

/** @defgroup Device_State 设备状态
 * @{
 */
#define STATE_IDLE              0       /**< 空闲状态 */
#define STATE_COLLECTING        1       /**< 采集中 */
#define STATE_BLE_TRANSFER      2       /**< BLE传输中 */
#define STATE_SLEEP             3       /**< 睡眠状态 */
/** @} */

/** @defgroup Error_Codes 错误码定义
 * @{
 */
#define ERR_UNSUPPORTED_CMD     0x01    /**< 不支持的命令 */
#define ERR_PARAM               0x02    /**< 参数错误 */
#define ERR_BUSY                0x03    /**< 忙碌中 */
#define ERR_STORAGE             0x04    /**< 存储错误 */
#define ERR_CRC                 0x05    /**< CRC校验失败 */
#define ERR_TIMEOUT             0x06    /**< 超时 */
/** @} */

/** @defgroup Data_Types 数据类型定义
 * @{
 */
#define DATATYPE_UINT16         0x00    /**< 无符号16位整数 */
#define DATATYPE_INT16          0x01    /**< 有符号16位整数 */
#define DATATYPE_UINT32         0x02    /**< 无符号32位整数 */
#define DATATYPE_FLOAT32        0x03    /**< 32位浮点数 (IEEE754) */
#define DATATYPE_RAW            0x04    /**< 原始字节 */
/** @} */

/** @defgroup Confirm_Codes 确认码 (防误操作)
 * @{
 */
#define ERASE_CONFIRM_CODE      0xDEADBEEF  /**< 数据擦除确认码 */
#define REBOOT_CONFIRM_CODE     0xCAFEBABE  /**< 设备重启确认码 */
/** @} */

/** @defgroup ACK_Codes 数据确认码
 * @{
 */
#define ACK_OK                  0x00    /**< 接收成功 */
#define ACK_ERR_CRC             0x01    /**< CRC校验错误, 请重发 */
#define ACK_CANCEL              0x02    /**< 取消传输 */
/** @} */

/** @defgroup Record_Size 数据记录大小
 * @{
 */
#define RECORD_SIZE             32      /**< 单条记录大小 (字节) */
#define RECORDS_PER_CHUNK       7       /**< 每片最大记录数 */
/** @} */

/** @defgroup Timeouts 超时定义
 * @{
 */
#define TIMEOUT_MS              5000    /**< 命令响应超时 (毫秒) */
#define RETRY_COUNT             3       /**< 最大重试次数 */
#define FRAME_INTERVAL_MS       20      /**< 帧间最小间隔 (毫秒) */
/** @} */


/*============================================================================
 *                           数据结构定义
 *============================================================================*/

/**
 * @brief 帧结构体 - 解析后的帧数据
 */
typedef struct {
    uint8_t  seq;           /**< 序列号 (0x00-0xFF) */
    uint8_t  cmd;           /**< 命令码 */
    uint16_t len;           /**< 载荷长度 */
    uint8_t  payload[FRAME_MAX_PAYLOAD]; /**< 载荷数据 */
} Frame_t;

/**
 * @brief 设备信息结构体 - 对应CMD_GET_INFO响应
 */
typedef struct {
    uint32_t device_id;     /**< 设备唯一标识符 */
    uint16_t fw_version;    /**< 固件版本号 (如: 0x0102 = V1.02) */
    uint32_t record_count;  /**< 已存储记录总数 */
    uint32_t free_space;    /**< 剩余存储空间 */
    uint32_t uptime;        /**< 设备运行时间 (秒) */
    uint8_t  battery;       /**< 电池电量 (0-100, 0xFF=无电池) */
} DeviceInfo_t;

/**
 * @brief 配置结构体 - 对应CMD_GET_CONFIG / CMD_SET_CONFIG
 */
typedef struct {
    uint32_t sampling_interval; /**< 采样间隔 (秒), 0=连续采集 */
    uint8_t  sensor_addr;       /**< 传感器Modbus从站地址 (1-247) */
    uint16_t sensor_start_reg;  /**< 起始寄存器地址 (0x0000-0xFFFF) */
    uint16_t sensor_reg_count;  /**< 读取寄存器数量 (1-125) */
    uint8_t  sensor_data_type;  /**< 数据类型 (DATATYPE_xxx) */
    uint32_t modbus_baudrate;   /**< Modbus波特率 */
    uint8_t  modbus_parity;     /**< 校验位 (0=无, 1=奇, 2=偶) */
} DeviceConfig_t;

/**
 * @brief 设备状态结构体 - 对应CMD_GET_STATUS响应
 */
typedef struct {
    uint8_t  state;         /**< 设备状态 (STATE_xxx) */
    uint8_t  error_code;    /**< 错误码 (0=正常) */
    uint32_t next_read_in;  /**< 距下次采集的秒数 (0xFFFF=连续模式) */
} DeviceStatus_t;

/**
 * @brief 传感器记录结构体 - 32字节
 */
typedef struct {
    uint32_t timestamp;         /**< Unix时间戳 */
    uint8_t  sensor_addr;       /**< 传感器地址 */
    uint8_t  status;            /**< 状态码 (0=OK, 1=超时, 2=CRC错误, 3=Modbus异常) */
    uint16_t reg_count;         /**< 寄存器数量 */
    uint16_t reg_values[8];     /**< 寄存器值 (最多8个) */
    uint32_t sequence_num;      /**< 单调递增序号 */
    uint16_t crc16;             /**< CRC16校验 */
    uint16_t reserved;          /**< 保留 */
} SensorRecord_t;

/**
 * @brief 数据分片回调函数类型
 * @param chunk_index 分片序号
 * @param data 数据指针
 * @param data_len 数据长度
 */
typedef void (*DataFragmentCallback)(uint16_t chunk_index, const uint8_t *data, uint16_t data_len);

/**
 * @brief 错误回调函数类型
 * @param err_code 错误码
 * @param orig_cmd 引起错误的原始命令码
 */
typedef void (*ErrorCallback)(uint8_t err_code, uint8_t orig_cmd);


/*============================================================================
 *                           函数声明
 *============================================================================*/

/** @defgroup CRC_Functions CRC校验函数
 * @{
 */
/**
 * @brief  计算CRC16/Modbus校验值
 * @param  data: 数据指针
 * @param  len: 数据长度
 * @return CRC16校验值 (小端序)
 */
uint16_t CRC16_Modbus(const uint8_t *data, uint16_t len);
/** @} */

/** @defgroup Frame_Building 帧构建函数
 * @{
 */
/**
 * @brief  构建发送帧
 * @param  seq: 序列号
 * @param  cmd: 命令码
 * @param  payload: 载荷数据指针 (可为NULL)
 * @param  payload_len: 载荷长度
 * @param  frame_buf: 输出帧缓冲区
 * @return 帧总长度
 */
uint16_t Frame_Build(uint8_t seq, uint8_t cmd, const uint8_t *payload,
                     uint16_t payload_len, uint8_t *frame_buf);

/**
 * @brief  构建空载荷帧 (用于PING, GET_INFO等无参命令)
 * @param  seq: 序列号
 * @param  cmd: 命令码
 * @param  frame_buf: 输出帧缓冲区
 * @return 帧总长度
 */
uint16_t Frame_BuildEmpty(uint8_t seq, uint8_t cmd, uint8_t *frame_buf);
/** @} */

/** @defgroup Frame_Parsing 帧解析函数
 * @{
 */
/**
 * @brief  解析接收帧
 * @param  data: 接收数据指针
 * @param  data_len: 数据长度
 * @param  frame: 输出帧结构体
 * @return true=解析成功, false=解析失败 (CRC错误或格式错误)
 */
bool Frame_Parse(const uint8_t *data, uint16_t data_len, Frame_t *frame);
/** @} */

/** @defgroup Command_Builders 命令构建函数
 * @{
 */
/** @brief 构建PING命令帧 */
uint16_t Cmd_BuildPing(uint8_t seq, uint8_t *frame_buf);
/** @brief 构建GET_INFO命令帧 */
uint16_t Cmd_BuildGetInfo(uint8_t seq, uint8_t *frame_buf);
/** @brief 构建SET_TIME命令帧 */
uint16_t Cmd_BuildSetTime(uint8_t seq, uint32_t timestamp, uint8_t *frame_buf);
/** @brief 构建GET_CONFIG命令帧 */
uint16_t Cmd_BuildGetConfig(uint8_t seq, uint8_t *frame_buf);
/** @brief 构建SET_CONFIG命令帧 */
uint16_t Cmd_BuildSetConfig(uint8_t seq, const DeviceConfig_t *config, uint8_t *frame_buf);
/** @brief 构建GET_DATA命令帧 */
uint16_t Cmd_BuildGetData(uint8_t seq, uint32_t start_index, uint16_t count, uint8_t *frame_buf);
/** @brief 构建DATA_ACK命令帧 */
uint16_t Cmd_BuildDataAck(uint8_t seq, uint16_t chunk_index, uint8_t status, uint8_t *frame_buf);
/** @brief 构建ERASE_DATA命令帧 */
uint16_t Cmd_BuildEraseData(uint8_t seq, uint8_t *frame_buf);
/** @brief 构建GET_STATUS命令帧 */
uint16_t Cmd_BuildGetStatus(uint8_t seq, uint8_t *frame_buf);
/** @brief 构建SET_DEVICE_ID命令帧 */
uint16_t Cmd_BuildSetDeviceId(uint8_t seq, uint32_t device_id, const char *name, uint8_t *frame_buf);
/** @brief 构建REBOOT命令帧 */
uint16_t Cmd_BuildReboot(uint8_t seq, uint8_t *frame_buf);
/** @brief 构建DATA_FRAG帧 (设备主动发送) */
uint16_t Cmd_BuildDataFrag(uint8_t seq, uint16_t chunk_index, const uint8_t *data, uint16_t data_len, uint8_t *frame_buf);
/** @brief 构建ERROR帧 (设备主动发送) */
uint16_t Cmd_BuildError(uint8_t seq, uint8_t err_code, uint8_t orig_cmd, uint8_t *frame_buf);
/** @} */

/** @defgroup Response_Parsers 响应解析函数
 * @{
 */
/**
 * @brief  解析PING响应
 * @param  payload: 载荷数据
 * @param  len: 载荷长度
 * @param  info: 输出设备信息
 * @return true=解析成功
 */
bool Resp_ParsePing(const uint8_t *payload, uint16_t len, DeviceInfo_t *info);

/**
 * @brief  解析GET_INFO响应
 * @param  payload: 载荷数据
 * @param  len: 载荷长度
 * @param  info: 输出设备信息
 * @return true=解析成功
 */
bool Resp_ParseGetInfo(const uint8_t *payload, uint16_t len, DeviceInfo_t *info);

/**
 * @brief  解析GET_CONFIG响应
 * @param  payload: 载荷数据
 * @param  len: 载荷长度
 * @param  config: 输出配置信息
 * @return true=解析成功
 */
bool Resp_ParseGetConfig(const uint8_t *payload, uint16_t len, DeviceConfig_t *config);

/**
 * @brief  解析GET_STATUS响应
 * @param  payload: 载荷数据
 * @param  len: 载荷长度
 * @param  status: 输出状态信息
 * @return true=解析成功
 */
bool Resp_ParseGetStatus(const uint8_t *payload, uint16_t len, DeviceStatus_t *status);

/**
 * @brief  解析DATA_FRAG分片 (32字节记录)
 * @param  payload: 载荷数据
 * @param  len: 载荷长度
 * @param  chunk_index: 输出分片序号
 * @param  records: 输出记录数组
 * @param  max_records: 最大记录数
 * @return 实际解析的记录数
 */
uint16_t Resp_ParseDataFrag(const uint8_t *payload, uint16_t len,
                            uint16_t *chunk_index, SensorRecord_t *records, uint16_t max_records);
/** @} */

/** @defgroup Utility_Functions 工具函数
 * @{
 */
/**
 * @brief  从大端序字节数组读取32位无符号整数
 * @param  data: 字节数组指针
 * @return 32位无符号整数
 */
uint32_t Bytes_ToUint32BE(const uint8_t *data);

/**
 * @brief  从大端序字节数组读取16位无符号整数
 * @param  data: 字节数组指针
 * @return 16位无符号整数
 */
uint16_t Bytes_ToUint16BE(const uint8_t *data);

/**
 * @brief  将32位无符号整数写入大端序字节数组
 * @param  value: 32位无符号整数
 * @param  data: 输出字节数组指针 (至少4字节)
 */
void Uint32_ToBytesBE(uint32_t value, uint8_t *data);

/**
 * @brief  将16位无符号整数写入大端序字节数组
 * @param  value: 16位无符号整数
 * @param  data: 输出字节数组指针 (至少2字节)
 */
void Uint16_ToBytesBE(uint16_t value, uint8_t *data);

/**
 * @brief  将传感器记录结构体转换为32字节原始数据
 * @param  record: 记录结构体
 * @param  buf: 输出缓冲区 (至少32字节)
 */
void Record_ToBytes(const SensorRecord_t *record, uint8_t *buf);

/**
 * @brief  将32字节原始数据转换为传感器记录结构体
 * @param  buf: 输入缓冲区 (32字节)
 * @param  record: 输出记录结构体
 * @return true=转换成功
 */
bool Record_FromBytes(const uint8_t *buf, SensorRecord_t *record);
/** @} */


#ifdef __cplusplus
}
#endif

#endif /* __BLE_PROTOCOL_H */
