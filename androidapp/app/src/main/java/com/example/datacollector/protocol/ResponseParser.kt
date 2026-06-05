/**
 * 响应解析模块。
 *
 * 本模块负责解析设备端返回的各种命令响应数据。
 * 每种命令对应一个解析方法，将负载字节数组反序列化为相应的数据类对象。
 *
 * 所有多字节字段均按大端序（Big-Endian）解析。
 * 解析过程中会进行基本的长度校验，数据不完整时返回 null。
 */
package com.example.datacollector.protocol

import com.example.datacollector.domain.model.DeviceConfig
import com.example.datacollector.domain.model.SensorRecord

/**
 * 响应解析工具对象。
 *
 * 提供将设备端返回的原始负载数据解析为结构化数据类的能力。
 * 每个解析方法在数据长度不足或格式错误时会安全地返回 null，不会抛出异常。
 */
object ResponseParser {

    /**
     * Ping 响应数据结构。
     *
     * 设备端收到 Ping 命令后返回的基本设备信息。
     *
     * @property deviceId 设备唯一标识 ID
     * @property firmwareVersion 固件版本字符串，格式为 "V主版本号.次版本号"
     * @property status 设备当前状态码
     */
    data class PingResponse(
        val deviceId: Long,
        val firmwareVersion: String,
        val status: Int
    )

    /**
     * 设备信息响应数据结构。
     *
     * 设备端收到获取信息命令后返回的详细设备信息。
     *
     * @property deviceId 设备唯一标识 ID
     * @property firmwareVersion 固件版本字符串，格式为 "V主版本号.次版本号"
     * @property recordCount 设备端存储的传感器数据记录总数
     * @property freeSpace 设备端剩余可用存储空间（字节）
     * @property uptime 设备自上次启动以来的运行时间（秒）
     * @property batteryLevel 电池电量百分比（0~100）
     */
    data class InfoResponse(
        val deviceId: Long,
        val firmwareVersion: String,
        val recordCount: Long,
        val freeSpace: Long,
        val uptime: Long,
        val batteryLevel: Int
    )

    /**
     * 设备状态响应数据结构。
     *
     * 设备端收到获取状态命令后返回的运行状态信息。
     *
     * @property state 设备当前运行状态码（参见 [ProtocolConstants.STATUS_OK] 等常量）
     * @property errorCode 错误码（0 表示无错误，非零值参见 [ProtocolConstants.ERR_*] 常量）
     * @property nextReadIn 距离下次自动采集数据的剩余时间（秒）
     */
    data class StatusResponse(
        val state: Int,
        val errorCode: Int,
        val nextReadIn: Long
    )

    /**
     * 解析 Ping 响应负载数据。
     *
     * 负载数据布局（最少 7 字节）：
     * - [0-3]   设备 ID（4 字节，大端序）
     * - [4]     固件主版本号（1 字节）
     * - [5]     固件次版本号（1 字节）
     * - [6]     设备状态码（1 字节）
     *
     * @param payload Ping 响应的负载字节数组
     * @return 解析成功返回 [PingResponse] 对象；数据不完整时返回 null
     */
    fun parsePing(payload: ByteArray): PingResponse? {
        /* 负载长度至少为 7 字节（设备ID 4 + 主版本 1 + 次版本 1 + 状态 1） */
        if (payload.size < 7) return null
        val deviceId = bytesToLong(payload, 0, 4)                    // 提取设备 ID（4 字节）
        val fwMajor = payload[4].toInt() and 0xFF                    // 固件主版本号
        val fwMinor = payload[5].toInt() and 0xFF                    // 固件次版本号
        val status = payload[6].toInt() and 0xFF                     // 设备状态码
        return PingResponse(deviceId, "V$fwMajor.$fwMinor", status)
    }

    /**
     * 解析设备信息响应负载数据。
     *
     * 负载数据布局（最少 19 字节）：
     * - [0-3]   设备 ID（4 字节，大端序）
     * - [4]     固件主版本号（1 字节）
     * - [5]     固件次版本号（1 字节）
     * - [6-9]   数据记录总数（4 字节，大端序）
     * - [10-13] 剩余存储空间（4 字节，大端序）
     * - [14-17] 设备运行时间（4 字节，大端序）
     * - [18]    电池电量百分比（1 字节）
     *
     * @param payload 设备信息响应的负载字节数组
     * @return 解析成功返回 [InfoResponse] 对象；数据不完整时返回 null
     */
    fun parseInfo(payload: ByteArray): InfoResponse? {
        /* 负载长度至少为 19 字节 */
        if (payload.size < 19) return null
        val deviceId = bytesToLong(payload, 0, 4)             // 设备 ID（4 字节）
        val fwMajor = payload[4].toInt() and 0xFF             // 固件主版本号
        val fwMinor = payload[5].toInt() and 0xFF             // 固件次版本号
        val recordCount = bytesToLong(payload, 6, 4)          // 数据记录总数（4 字节）
        val freeSpace = bytesToLong(payload, 10, 4)           // 剩余存储空间（4 字节）
        val uptime = bytesToLong(payload, 14, 4)              // 设备运行时间（4 字节）
        val battery = payload[18].toInt() and 0xFF            // 电池电量百分比
        return InfoResponse(deviceId, "V$fwMajor.$fwMinor", recordCount, freeSpace, uptime, battery)
    }

    /**
     * 解析设备配置响应负载数据。
     *
     * 负载数据布局（最少 15 字节）：
     * - [0-3]   采样间隔（4 字节，大端序）
     * - [4]     传感器地址（1 字节）
     * - [5-6]   传感器起始寄存器（2 字节，大端序）
     * - [7-8]   传感器寄存器数量（2 字节，大端序）
     * - [9]     数据类型（1 字节）
     * - [10-13] Modbus 波特率（4 字节，大端序）
     * - [14]    Modbus 校验方式（1 字节）
     *
     * @param payload 配置响应的负载字节数组
     * @return 解析成功返回 [DeviceConfig] 对象；数据不完整时返回 null
     */
    fun parseConfig(payload: ByteArray): DeviceConfig? {
        /* 负载长度至少为 15 字节 */
        if (payload.size < 15) return null
        val interval = bytesToLong(payload, 0, 4)              // 采样间隔（4 字节）
        val addr = payload[4].toInt() and 0xFF                 // 传感器地址（1 字节）

        /* 解析起始寄存器地址（2 字节大端序：高字节左移 8 位后与低字节合并） */
        val startReg = ((payload[5].toInt() and 0xFF) shl 8) or (payload[6].toInt() and 0xFF)

        /* 解析寄存器数量（2 字节大端序） */
        val regCount = ((payload[7].toInt() and 0xFF) shl 8) or (payload[8].toInt() and 0xFF)

        val dataType = payload[9].toInt() and 0xFF             // 数据类型（1 字节）
        val baudrate = bytesToLong(payload, 10, 4).toInt()     // Modbus 波特率（4 字节）
        val parity = payload[14].toInt() and 0xFF              // Modbus 校验方式（1 字节）
        return DeviceConfig(interval, addr, startReg, regCount, dataType, baudrate, parity)
    }

    /**
     * 解析设备状态响应负载数据。
     *
     * 负载数据布局（最少 6 字节）：
     * - [0]     设备运行状态码（1 字节）
     * - [1]     错误码（1 字节）
     * - [2-5]   距离下次采集的剩余时间（4 字节，大端序）
     *
     * @param payload 状态响应的负载字节数组
     * @return 解析成功返回 [StatusResponse] 对象；数据不完整时返回 null
     */
    fun parseStatus(payload: ByteArray): StatusResponse? {
        /* 负载长度至少为 6 字节 */
        if (payload.size < 6) return null
        val state = payload[0].toInt() and 0xFF                  // 设备运行状态码
        val errorCode = payload[1].toInt() and 0xFF              // 错误码
        val nextReadIn = bytesToLong(payload, 2, 4)              // 距离下次采集的剩余时间（4 字节）
        return StatusResponse(state, errorCode, nextReadIn)
    }

    /**
     * 解析数据分片响应负载数据。
     *
     * 设备端在传输大量传感器数据时，会将数据分成多个分片发送。
     * 每个分片包含一个分片索引和若干条传感器数据记录。
     *
     * 负载数据布局：
     * - [0-1]   分片索引（2 字节，大端序）
     * - [2...]  传感器数据记录序列（每条 [ProtocolConstants.RECORD_SIZE] 字节）
     *
     * @param payload 数据分片响应的负载字节数组
     * @return 解析成功返回包含分片索引和传感器记录列表的 [Pair]；数据不完整时返回 null
     */
    fun parseDataFrag(payload: ByteArray): Pair<Int, List<SensorRecord>>? {
        /* 负载长度至少为 2 字节（分片索引） */
        if (payload.size < 2) return null

        /* 提取分片索引（2 字节大端序） */
        val chunkIndex = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)

        val records = mutableListOf<SensorRecord>()
        var offset = 2  /* 从第 2 字节开始解析记录 */

        /* 按固定记录大小逐条解析传感器数据记录 */
        while (offset + ProtocolConstants.RECORD_SIZE <= payload.size) {
            val record = parseRecord(payload, offset)
            if (record != null) records.add(record)
            offset += ProtocolConstants.RECORD_SIZE  /* 移动偏移量到下一条记录 */
        }
        return Pair(chunkIndex, records)
    }

    /**
     * 解析单条传感器数据记录。
     *
     * 每条记录固定占用 [ProtocolConstants.RECORD_SIZE]（32）字节，布局如下：
     * - [offset+0..offset+3]   时间戳（4 字节，大端序，Unix 时间戳秒）
     * - [offset+4]             传感器地址（1 字节）
     * - [offset+5]             采集状态码（1 字节）
     * - [offset+6..offset+7]   寄存器数量（2 字节，大端序）
     * - [offset+8..offset+23]  寄存器值数组（最多 8 个 uint16 值，每个 2 字节，大端序）
     * - [offset+24..offset+27] 序列号（4 字节，大端序）
     * - [offset+28..offset+31] 保留字段（4 字节，未使用）
     *
     * @param data 包含记录数据的字节数组
     * @param offset 记录在字节数组中的起始偏移量
     * @return 解析成功返回 [SensorRecord] 对象；数据越界时返回 null
     */
    fun parseRecord(data: ByteArray, offset: Int): SensorRecord? {
        /* 检查从 offset 开始是否有足够的字节容纳一条完整记录 */
        if (offset + ProtocolConstants.RECORD_SIZE > data.size) return null

        /* 逐字段解析记录数据 */
        val timestamp = bytesToLong(data, offset, 4)             // 时间戳（4 字节）
        val sensorAddr = data[offset + 4].toInt() and 0xFF      // 传感器地址（1 字节）
        val status = data[offset + 5].toInt() and 0xFF          // 采集状态码（1 字节）

        /* 解析寄存器数量（2 字节大端序） */
        val regCount = ((data[offset + 6].toInt() and 0xFF) shl 8) or
                (data[offset + 7].toInt() and 0xFF)

        /* 逐个解析寄存器值（每个 2 字节大端序），最多解析 8 个 */
        val regValues = mutableListOf<Int>()
        for (i in 0 until minOf(regCount, 8)) {
            val v = ((data[offset + 8 + i * 2].toInt() and 0xFF) shl 8) or
                    (data[offset + 9 + i * 2].toInt() and 0xFF)
            regValues.add(v)
        }

        /* 解析序列号（4 字节大端序，从 offset+24 开始） */
        val seqNum = bytesToLong(data, offset + 24, 4)

        return SensorRecord(timestamp, sensorAddr, status, regValues, seqNum)
    }

    /**
     * 解析错误响应负载数据。
     *
     * 当设备端处理命令失败时，会返回错误响应帧。
     * 负载数据布局（最少 2 字节）：
     * - [0]   错误码（1 字节，参见 [ProtocolConstants.ERR_*] 常量）
     * - [1]   原始命令码（1 字节，表示出错的是哪个命令）
     *
     * @param payload 错误响应的负载字节数组
     * @return 解析成功返回包含错误码和原始命令码的 [Pair]；数据不完整时返回 null
     */
    fun parseError(payload: ByteArray): Pair<Int, Byte>? {
        /* 负载长度至少为 2 字节（错误码 + 原始命令码） */
        if (payload.size < 2) return null
        val errCode = payload[0].toInt() and 0xFF    // 错误码
        val origCmd = payload[1]                      // 引发错误的原始命令码
        return Pair(errCode, origCmd)
    }

    /**
     * 将字节数组中的指定区域转换为长整数值。
     *
     * 工具方法，按大端序从字节数组中提取指定偏移量和长度的整数值。
     * 通过逐字节左移并合并的方式实现，支持 1~8 字节的任意长度。
     *
     * @param data 源字节数组
     * @param offset 起始偏移量
     * @param length 字节长度（1~8）
     * @return 解析得到的长整数值
     */
    private fun bytesToLong(data: ByteArray, offset: Int, length: Int): Long {
        var result = 0L
        /* 逐字节左移 8 位并与当前字节合并，实现大端序字节到长整数的转换 */
        for (i in 0 until length) {
            result = (result shl 8) or (data[offset + i].toLong() and 0xFF)
        }
        return result
    }
}
