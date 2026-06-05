/**
 * 帧编解码模块。
 *
 * 本模块负责通信协议帧的编码（encode）和解析（parse）操作。
 * 协议帧的结构为：[SOF][序号][命令码][长度高字节][长度低字节][负载数据...][CRC16低字节][CRC16高字节][EOF]
 *
 * 同时提供 CRC16 校验算法的实现，用于确保数据传输的完整性。
 */
package com.example.datacollector.protocol

import java.io.ByteArrayOutputStream

/**
 * 帧编解码工具对象。
 *
 * 提供将命令和负载数据编码为协议帧，以及将接收到的原始字节数据解析为帧结构的功能。
 * 所有方法均为线程安全的无状态方法。
 */
object FrameParser {

    /**
     * 协议帧数据结构。
     *
     * 表示一个解析完成的通信帧，包含序号、命令码和负载数据三个核心字段。
     *
     * @property seq 帧序号（0~255），用于匹配请求和响应，以及检测丢帧
     * @property cmd 命令码字节，标识帧的类型（参见 [ProtocolConstants] 中的 CMD_* 常量）
     * @property payload 负载数据字节数组，其内容和格式由命令码决定，部分命令可能为空
     */
    data class Frame(
        val seq: Int,
        val cmd: Byte,
        val payload: ByteArray
    )

    /**
     * 将命令和负载数据编码为完整的协议帧。
     *
     * 编码过程：
     * 1. 写入帧头：SOF + 序号(1字节) + 命令码(1字节) + 负载长度(2字节，大端序)
     * 2. 写入负载数据（如果存在）
     * 3. 计算 CRC16 校验值（对 SOF 之后、CRC 之前的所有字节进行计算）
     * 4. 写入帧尾：CRC16(2字节，小端序) + EOF
     *
     * @param seq 帧序号（0~255），只取低 8 位
     * @param cmd 命令码字节
     * @param payload 负载数据字节数组，默认为空数组
     * @return 编码后的完整帧字节数组
     */
    fun encode(seq: Int, cmd: Byte, payload: ByteArray = byteArrayOf()): ByteArray {
        /* 创建字节输出流用于构建帧数据 */
        val frame = ByteArrayOutputStream()
        frame.write(ProtocolConstants.SOF.toInt())          // 写入帧起始标志 SOF (0xAA)
        frame.write(seq and 0xFF)                            // 写入序号（取低 8 位）
        frame.write(cmd.toInt())                             // 写入命令码
        frame.write((payload.size shr 8) and 0xFF)           // 写入负载长度高字节
        frame.write(payload.size and 0xFF)                   // 写入负载长度低字节
        if (payload.isNotEmpty()) {
            frame.write(payload)                             // 写入负载数据
        }

        /* 计算 CRC16 校验值：对 SOF 之后的所有字节（序号+命令码+长度+负载）进行 CRC 计算 */
        val crcData = frame.toByteArray().copyOfRange(1, frame.size())
        val crc = crc16(crcData)

        /* 写入 CRC16（小端序：低字节在前，高字节在后） */
        frame.write(crc and 0xFF)
        frame.write((crc shr 8) and 0xFF)

        /* 写入帧结束标志 EOF (0x55) */
        frame.write(ProtocolConstants.EOF.toInt())

        return frame.toByteArray()
    }

    /**
     * 将接收到的原始字节数组解析为协议帧结构。
     *
     * 解析过程：
     * 1. 基本校验：检查帧大小、起始标志和结束标志
     * 2. 提取头部字段：序号、命令码、负载长度
     * 3. 提取负载数据
     * 4. CRC16 校验：验证数据完整性
     * 5. 返回解析后的 Frame 对象
     *
     * @param data 接收到的原始字节数组
     * @return 解析成功返回 [Frame] 对象；校验失败或数据格式错误时返回 null
     */
    fun parse(data: ByteArray): Frame? {
        /* 帧大小必须至少等于最小帧大小（头部 + 尾部 = 8 字节） */
        if (data.size < ProtocolConstants.MIN_FRAME_SIZE) return null

        /* 检查帧起始标志是否为 SOF (0xAA) */
        if (data[0] != ProtocolConstants.SOF) return null

        /* 检查帧结束标志是否为 EOF (0x55) */
        if (data[data.size - 1] != ProtocolConstants.EOF) return null

        /* 提取头部字段 */
        val seq = data[1].toInt() and 0xFF                           // 序号
        val cmd = data[2]                                             // 命令码
        val len = ((data[3].toInt() and 0xFF) shl 8) or              // 负载长度（高字节左移8位后与低字节合并）
                (data[4].toInt() and 0xFF)

        /* 验证帧总长度是否与声明的负载长度一致 */
        if (data.size != ProtocolConstants.MIN_FRAME_SIZE + len) return null

        /* 提取负载数据（从第 5 字节开始，长度为 len） */
        val payload = data.copyOfRange(5, 5 + len)

        /* CRC16 校验：对序号+命令码+长度+负载数据进行 CRC 计算，并与帧中的 CRC 比较 */
        val crcData = data.copyOfRange(1, 5 + len)
        val expectedCrc = crc16(crcData)

        /* 从帧中提取实际的 CRC 值（小端序：低字节在前，高字节在后） */
        val actualCrc = (data[5 + len].toInt() and 0xFF) or
                ((data[6 + len].toInt() and 0xFF) shl 8)

        /* CRC 不匹配则丢弃该帧 */
        if (expectedCrc != actualCrc) return null

        return Frame(seq, cmd, payload)
    }

    /**
     * 计算 CRC16/Modbus 校验值。
     *
     * 使用 CRC16/Modbus 算法，多项式为 0xA001（CRC16-Modbus 的反转多项式）。
     * 初始值为 0xFFFF，逐字节进行异或和移位运算。
     *
     * 算法步骤：
     * 1. 初始化 CRC 为 0xFFFF
     * 2. 对每个字节：
     *    a. 将字节与 CRC 低字节异或
     *    b. 循环 8 次：如果 CRC 最低位为 1，则右移一位后与多项式异或；否则仅右移一位
     * 3. 返回 CRC 低 16 位
     *
     * @param data 需要计算 CRC 校验值的字节数组
     * @return CRC16 校验值（0~0xFFFF）
     */
    fun crc16(data: ByteArray): Int {
        /* 初始化 CRC 值为 0xFFFF */
        var crc = 0xFFFF
        for (byte in data) {
            /* 将当前字节与 CRC 低字节异或 */
            crc = crc xor (byte.toInt() and 0xFF)
            /* 对 8 位逐一进行移位和异或操作 */
            for (j in 0 until 8) {
                crc = if (crc and 1 != 0)
                    (crc shr 1) xor 0xA001    // 最低位为 1 时：右移后与多项式 0xA001 异或
                else
                    crc shr 1                  // 最低位为 0 时：仅右移
            }
        }
        /* 返回 CRC 低 16 位结果 */
        return crc and 0xFFFF
    }
}
