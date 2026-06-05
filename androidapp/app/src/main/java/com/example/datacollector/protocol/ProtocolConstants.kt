/**
 * 协议常量定义包。
 *
 * 本包定义了设备通信协议中使用的所有常量，包括帧结构标识、命令码、
 * 状态码、数据类型标识、错误码以及蓝牙服务/特征的 UUID 等。
 * 所有协议层的编码、解析和命令构建均依赖此处定义的常量。
 */
package com.example.datacollector.protocol

/**
 * 通信协议常量集合。
 *
 * 以单例 object 形式组织，包含设备端与 Android 客户端之间的
 * 自定义串口/蓝牙通信协议所使用的全部常量。协议采用 [SOF]-[序号]-[命令码]-[长度]-[负载]-[CRC16]-[EOF] 的帧结构。
 */
object ProtocolConstants {

    // ==================== 帧结构常量 ====================

    /** 帧起始标志字节（Start Of Frame），固定为 0xAA，用于标识一个新帧的开始 */
    const val SOF: Byte = 0xAA.toByte()

    /** 帧结束标志字节（End Of Frame），固定为 0x55，用于标识帧的结束 */
    const val EOF: Byte = 0x55.toByte()

    /** 帧头部固定占用的字节数：SOF(1) + 序号(1) + 命令码(1) + 长度(2) = 5 字节 */
    const val HEADER_SIZE = 5

    /** 帧尾固定占用的字节数：CRC16(2) + EOF(1) = 3 字节 */
    const val FOOTER_SIZE = 3

    /** 最小帧大小 = 头部 + 尾部 = 8 字节（即无负载时的帧大小） */
    const val MIN_FRAME_SIZE = HEADER_SIZE + FOOTER_SIZE

    /** 负载数据的最大长度限制，单位为字节（237 字节） */
    const val MAX_PAYLOAD_SIZE = 237

    // ==================== 通信超时与重试 ====================

    /** 通信超时时间，单位为毫秒（5 秒），超过此时间未收到响应则认为通信失败 */
    const val TIMEOUT_MS = 5000L

    /** 最大重试次数，当通信失败时最多重试 3 次 */
    const val MAX_RETRY = 3

    /** 帧间最小间隔时间，单位为毫秒（20 毫秒），用于避免发送过快导致设备端缓冲区溢出 */
    const val FRAME_INTERVAL_MS = 20L

    // ==================== 命令码定义 ====================

    /** Ping 命令，用于检测设备是否在线并获取基本设备信息 */
    const val CMD_PING: Byte = 0x01

    /** 获取设备信息命令，返回设备固件版本、记录数、剩余空间等详细信息 */
    const val CMD_GET_INFO: Byte = 0x02

    /** 设置设备时间命令，用于同步设备端的实时时钟（RTC） */
    const val CMD_SET_TIME: Byte = 0x03

    /** 获取设备配置命令，返回采样间隔、Modbus 参数等配置信息 */
    const val CMD_GET_CONFIG: Byte = 0x04

    /** 设置设备配置命令，用于修改采样间隔、Modbus 参数等设备配置 */
    const val CMD_SET_CONFIG: Byte = 0x05

    /** 获取数据记录命令，按索引范围请求设备端存储的传感器数据 */
    const val CMD_GET_DATA: Byte = 0x06

    /** 数据确认应答命令（ACK），用于确认已成功接收某个数据分片 */
    const val CMD_DATA_ACK: Byte = 0x07

    /** 擦除数据命令，用于清除设备端存储的所有数据记录（需要携带确认码） */
    const val CMD_ERASE_DATA: Byte = 0x08

    /** 获取设备状态命令，返回设备当前运行状态和错误信息 */
    const val CMD_GET_STATUS: Byte = 0x09

    /** 设置设备 ID 和名称命令 */
    const val CMD_SET_DEVICE_ID: Byte = 0x0A

    /** 重启设备命令（需要携带确认码以防止误操作） */
    const val CMD_REBOOT: Byte = 0x0B

    /** 数据分片传输命令，设备端通过此命令将大量数据分片发送给客户端 */
    const val CMD_DATA_FRAG: Byte = 0xFE.toByte()

    /** 错误响应命令，设备端在处理命令失败时返回此命令码 */
    const val CMD_ERROR: Byte = 0xFF.toByte()

    // ==================== 确认码 ====================

    /**
     * 擦除数据操作的确认码（0xDEADBEEF）。
     * 发送擦除命令时必须携带此确认码，以防止误操作导致数据丢失。
     */
    const val ERASE_CONFIRM_CODE = 0xDEADBEEF.toInt()

    /**
     * 重启设备操作的确认码（0xCAFEBABE）。
     * 发送重启命令时必须携带此确认码，以防止误操作导致设备意外重启。
     */
    const val REBOOT_CONFIRM_CODE = 0xCAFEBABE.toInt()

    // ==================== 设备状态码 ====================

    /** 设备运行正常状态码 */
    const val STATUS_OK = 0

    /** 设备运行失败状态码 */
    const val STATUS_FAIL = 1

    /** 设备忙碌状态码，表示设备当前正在执行其他操作，无法响应新请求 */
    const val STATUS_BUSY = 2

    // ==================== 数据类型标识 ====================

    /** 无符号 16 位整数（uint16）数据类型标识 */
    const val DATA_TYPE_UINT16: Byte = 0x00

    /** 有符号 16 位整数（int16）数据类型标识 */
    const val DATA_TYPE_INT16: Byte = 0x01

    /** 无符号 32 位整数（uint32）数据类型标识 */
    const val DATA_TYPE_UINT32: Byte = 0x02

    /** 单精度浮点数（float32）数据类型标识 */
    const val DATA_TYPE_FLOAT32: Byte = 0x03

    /** 原始二进制数据（raw bytes）数据类型标识 */
    const val DATA_TYPE_RAW: Byte = 0x04

    // ==================== 数据记录 ====================

    /** 每条传感器数据记录的固定大小，单位为字节（32 字节） */
    const val RECORD_SIZE = 32

    // ==================== 数据传输应答码 ====================

    /** 数据接收成功应答码 */
    const val ACK_OK: Byte = 0x00

    /** CRC 校验失败应答码，表示接收方检测到数据完整性错误 */
    const val ACK_ERR_CRC: Byte = 0x01

    /** 传输取消应答码，表示接收方主动取消数据传输 */
    const val ACK_CANCEL: Byte = 0x02

    // ==================== 错误码 ====================

    /** 不支持的命令错误码，表示设备端无法识别接收到的命令 */
    const val ERR_UNSUPPORTED_CMD: Byte = 0x01

    /** 参数错误码，表示命令中的参数不合法 */
    const val ERR_PARAM: Byte = 0x02

    /** 设备忙碌错误码，表示设备正在处理其他命令，当前命令被拒绝 */
    const val ERR_BUSY: Byte = 0x03

    /** 存储错误码，表示设备端存储介质读写失败 */
    const val ERR_STORAGE: Byte = 0x04

    /** CRC 校验错误码，表示接收到的帧数据校验失败 */
    const val ERR_CRC: Byte = 0x05

    /** 超时错误码，表示设备端处理命令超时 */
    const val ERR_TIMEOUT: Byte = 0x06

    // ==================== 蓝牙服务和特征 UUID ====================

    /**
     * HM-10 蓝牙模块服务 UUID。
     * HM-10 是一种常见的低功耗蓝牙（BLE）透传模块，其服务 UUID 为 0xFFE0。
     */
    val UUID_SERVICE_HM10 = java.util.UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB")

    /**
     * HM-10 蓝牙模块的 TX（发送）特征 UUID。
     * 注意：HM-10 模块的 TX 和 RX 使用相同的特征 UUID（0xFFE1），
     * 通过 BLE 的 Notify/Write 属性区分方向。
     */
    val UUID_CHAR_HM10_TX = java.util.UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB")

    /**
     * HM-10 蓝牙模块的 RX（接收）特征 UUID。
     * 与 TX 共享同一个特征 UUID（0xFFE1）。
     */
    val UUID_CHAR_HM10_RX = java.util.UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB")

    /**
     * Nordic UART Service（NUS）服务 UUID。
     * NUS 是 Nordic Semiconductor 定义的蓝牙串口服务，常用于 BLE 透传通信。
     */
    val UUID_SERVICE_NUS = java.util.UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")

    /**
     * NUS 服务的 TX（发送）特征 UUID。
     * 设备通过此特征向客户端发送数据（Notify 方式）。
     */
    val UUID_CHAR_NUS_TX = java.util.UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")

    /**
     * NUS 服务的 RX（接收）特征 UUID。
     * 客户端通过此特征向设备发送数据（Write 方式）。
     */
    val UUID_CHAR_NUS_RX = java.util.UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
}
