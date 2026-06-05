/**
 * 命令构建模块。
 *
 * 本模块负责为每种协议命令构建完整的帧数据。
 * 每个方法根据特定命令的要求，将参数序列化为负载数据（payload），
 * 然后调用 [FrameParser.encode] 进行帧编码，返回可直接发送的字节数组。
 *
 * 所有多字节字段均采用大端序（Big-Endian）编码。
 */
package com.example.datacollector.protocol

import com.example.datacollector.domain.model.DeviceConfig
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 命令构建工具对象。
 *
 * 提供构建各种协议命令帧的静态方法，每种方法对应一种协议命令。
 * 返回的字节数组可直接通过蓝牙串口发送给设备端。
 */
object CommandBuilder {

    /**
     * 构建 Ping 命令帧。
     *
     * Ping 命令用于检测设备是否在线，并返回设备基本标识信息。
     * 此命令无负载数据。
     *
     * @param seq 帧序号
     * @return 编码后的 Ping 命令帧字节数组
     */
    fun ping(seq: Int): ByteArray {
        return FrameParser.encode(seq, ProtocolConstants.CMD_PING)
    }

    /**
     * 构建获取设备信息命令帧。
     *
     * 获取设备详细信息，包括固件版本、记录数、剩余空间等。
     * 此命令无负载数据。
     *
     * @param seq 帧序号
     * @return 编码后的获取设备信息命令帧字节数组
     */
    fun getInfo(seq: Int): ByteArray {
        return FrameParser.encode(seq, ProtocolConstants.CMD_GET_INFO)
    }

    /**
     * 构建设置设备时间命令帧。
     *
     * 将 Unix 时间戳（秒）发送给设备，用于同步设备端的实时时钟（RTC）。
     * 负载数据为 4 字节的大端序 Unix 时间戳。
     *
     * @param seq 帧序号
     * @param unixTimestamp Unix 时间戳（秒），将被截断为 32 位整数
     * @return 编码后的设置时间命令帧字节数组
     */
    fun setTime(seq: Int, unixTimestamp: Long): ByteArray {
        /* 将 Unix 时间戳转换为 4 字节大端序字节数组作为负载 */
        val payload = ByteBuffer.allocate(4).apply {
            order(ByteOrder.BIG_ENDIAN)
            putInt(unixTimestamp.toInt())
        }.array()
        return FrameParser.encode(seq, ProtocolConstants.CMD_SET_TIME, payload)
    }

    /**
     * 构建获取设备配置命令帧。
     *
     * 请求设备返回当前的配置参数（采样间隔、Modbus 参数等）。
     * 此命令无负载数据。
     *
     * @param seq 帧序号
     * @return 编码后的获取配置命令帧字节数组
     */
    fun getConfig(seq: Int): ByteArray {
        return FrameParser.encode(seq, ProtocolConstants.CMD_GET_CONFIG)
    }

    /**
     * 构建设备配置设置命令帧。
     *
     * 将配置参数序列化为负载数据发送给设备。
     * 负载数据布局（共 14 字节）：
     * - [0-3] 采样间隔（4 字节，大端序）
     * - [4]   传感器地址（1 字节）
     * - [5-6] 传感器起始寄存器（2 字节，大端序）
     * - [7-8] 传感器寄存器数量（2 字节，大端序）
     * - [9]   数据类型（1 字节）
     * - [10-13] Modbus 波特率（4 字节，大端序）
     * - [14]  Modbus 校验方式（1 字节）
     *
     * @param seq 帧序号
     * @param config 设备配置对象，包含所有需要设置的参数
     * @return 编码后的设置配置命令帧字节数组
     */
    fun setConfig(seq: Int, config: DeviceConfig): ByteArray {
        /* 使用 ByteArrayOutputStream 逐步构建负载数据 */
        val payload = ByteArrayOutputStream()
        payload.write(intToBytesBigEndian(config.samplingIntervalSec.toInt()))  // 采样间隔（4 字节）
        payload.write(config.sensorAddr)                                        // 传感器地址（1 字节）
        payload.write((config.sensorStartReg shr 8) and 0xFF)                  // 起始寄存器高字节
        payload.write(config.sensorStartReg and 0xFF)                           // 起始寄存器低字节
        payload.write((config.sensorRegCount shr 8) and 0xFF)                  // 寄存器数量高字节
        payload.write(config.sensorRegCount and 0xFF)                           // 寄存器数量低字节
        payload.write(config.sensorDataType)                                    // 数据类型（1 字节）
        payload.write(intToBytesBigEndian(config.modbusBaudrate))               // Modbus 波特率（4 字节）
        payload.write(config.modbusParity)                                      // Modbus 校验方式（1 字节）
        return FrameParser.encode(seq, ProtocolConstants.CMD_SET_CONFIG, payload.toByteArray())
    }

    /**
     * 构建获取数据记录命令帧。
     *
     * 按索引范围从设备端请求传感器数据记录。
     * 负载数据为 6 字节：起始索引（4 字节）+ 记录数量（2 字节），均为大端序。
     *
     * @param seq 帧序号
     * @param startIndex 起始记录索引（从 0 开始）
     * @param count 请求的记录数量
     * @return 编码后的获取数据命令帧字节数组
     */
    fun getData(seq: Int, startIndex: Int, count: Int): ByteArray {
        /* 构建 6 字节负载：起始索引(4字节) + 记录数量(2字节) */
        val payload = ByteBuffer.allocate(6).apply {
            order(ByteOrder.BIG_ENDIAN)
            putInt(startIndex)            // 起始索引（4 字节大端序）
            putShort(count.toShort())     // 记录数量（2 字节大端序）
        }.array()
        return FrameParser.encode(seq, ProtocolConstants.CMD_GET_DATA, payload)
    }

    /**
     * 构建数据接收确认应答帧。
     *
     * 在接收到数据分片后，客户端发送此应答以告知设备端接收状态。
     * 负载数据为 3 字节：分片索引（2 字节）+ 应答状态（1 字节）。
     *
     * @param seq 帧序号
     * @param chunkIndex 已接收的数据分片索引
     * @param status 应答状态码，默认为 [ProtocolConstants.ACK_OK]（成功）
     * @return 编码后的数据确认应答帧字节数组
     */
    fun dataAck(seq: Int, chunkIndex: Int, status: Byte = ProtocolConstants.ACK_OK): ByteArray {
        /* 构建 3 字节负载：分片索引(2字节) + 应答状态(1字节) */
        val payload = ByteBuffer.allocate(3).apply {
            order(ByteOrder.BIG_ENDIAN)
            putShort(chunkIndex.toShort())  // 分片索引（2 字节大端序）
            put(status)                     // 应答状态（1 字节）
        }.array()
        return FrameParser.encode(seq, ProtocolConstants.CMD_DATA_ACK, payload)
    }

    /**
     * 构建擦除数据命令帧。
     *
     * 用于清除设备端存储的所有传感器数据记录。
     * 为防止误操作，负载必须携带确认码 [ProtocolConstants.ERASE_CONFIRM_CODE] (0xDEADBEEF)。
     *
     * @param seq 帧序号
     * @return 编码后的擦除数据命令帧字节数组
     */
    fun eraseData(seq: Int): ByteArray {
        /* 构建 4 字节负载：擦除确认码 (0xDEADBEEF) */
        val payload = ByteBuffer.allocate(4).apply {
            order(ByteOrder.BIG_ENDIAN)
            putInt(ProtocolConstants.ERASE_CONFIRM_CODE)
        }.array()
        return FrameParser.encode(seq, ProtocolConstants.CMD_ERASE_DATA, payload)
    }

    /**
     * 构建获取设备状态命令帧。
     *
     * 请求设备返回当前运行状态（正常/错误/忙碌等）和错误信息。
     * 此命令无负载数据。
     *
     * @param seq 帧序号
     * @return 编码后的获取状态命令帧字节数组
     */
    fun getStatus(seq: Int): ByteArray {
        return FrameParser.encode(seq, ProtocolConstants.CMD_GET_STATUS)
    }

    /**
     * 构建设备 ID 设置命令帧。
     *
     * 设置设备的唯一标识 ID 和可读名称。
     * 负载数据布局（共 20 字节）：
     * - [0-3]   设备 ID（4 字节，大端序）
     * - [4-19]  设备名称（16 字节，UTF-8 编码，不足部分以 0x00 填充）
     *
     * @param seq 帧序号
     * @param deviceId 设备 ID（32 位整数）
     * @param name 设备名称字符串（UTF-8 编码，最多 16 字节，超出部分将被截断）
     * @return 编码后的设置设备 ID 命令帧字节数组
     */
    fun setDeviceId(seq: Int, deviceId: Long, name: String): ByteArray {
        val payload = ByteArrayOutputStream()
        payload.write(intToBytesBigEndian(deviceId.toInt()))               // 设备 ID（4 字节）
        /* 将设备名称转换为 UTF-8 字节数组，截断或填充至固定 16 字节 */
        val nameBytes = name.toByteArray(Charsets.UTF_8).copyOf(16)
        payload.write(nameBytes)                                           // 设备名称（16 字节）
        return FrameParser.encode(seq, ProtocolConstants.CMD_SET_DEVICE_ID, payload.toByteArray())
    }

    /**
     * 构建设备重启命令帧。
     *
     * 用于远程重启设备端。
     * 为防止误操作，负载必须携带确认码 [ProtocolConstants.REBOOT_CONFIRM_CODE] (0xCAFEBABE)。
     *
     * @param seq 帧序号
     * @return 编码后的重启命令帧字节数组
     */
    fun reboot(seq: Int): ByteArray {
        /* 构建 4 字节负载：重启确认码 (0xCAFEBABE) */
        val payload = ByteBuffer.allocate(4).apply {
            order(ByteOrder.BIG_ENDIAN)
            putInt(ProtocolConstants.REBOOT_CONFIRM_CODE)
        }.array()
        return FrameParser.encode(seq, ProtocolConstants.CMD_REBOOT, payload)
    }

    /**
     * 将整数转换为 4 字节大端序字节数组。
     *
     * 工具方法，用于将整数值序列化为网络字节序（大端序）格式。
     *
     * @param value 需要转换的整数值
     * @return 4 字节大端序字节数组
     */
    private fun intToBytesBigEndian(value: Int): ByteArray {
        return ByteBuffer.allocate(4).apply {
            order(ByteOrder.BIG_ENDIAN)
            putInt(value)
        }.array()
    }
}
