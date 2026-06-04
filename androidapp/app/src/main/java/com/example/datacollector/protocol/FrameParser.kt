package com.example.datacollector.protocol

import java.io.ByteArrayOutputStream

object FrameParser {

    data class Frame(
        val seq: Int,
        val cmd: Byte,
        val payload: ByteArray
    )

    fun encode(seq: Int, cmd: Byte, payload: ByteArray = byteArrayOf()): ByteArray {
        val frame = ByteArrayOutputStream()
        frame.write(ProtocolConstants.SOF.toInt())
        frame.write(seq and 0xFF)
        frame.write(cmd.toInt())
        frame.write((payload.size shr 8) and 0xFF)
        frame.write(payload.size and 0xFF)
        if (payload.isNotEmpty()) {
            frame.write(payload)
        }
        val crcData = frame.toByteArray().copyOfRange(1, frame.size())
        val crc = crc16(crcData)
        frame.write(crc and 0xFF)
        frame.write((crc shr 8) and 0xFF)
        frame.write(ProtocolConstants.EOF.toInt())
        return frame.toByteArray()
    }

    fun parse(data: ByteArray): Frame? {
        if (data.size < ProtocolConstants.MIN_FRAME_SIZE) return null
        if (data[0] != ProtocolConstants.SOF) return null
        if (data[data.size - 1] != ProtocolConstants.EOF) return null

        val seq = data[1].toInt() and 0xFF
        val cmd = data[2]
        val len = ((data[3].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)

        if (data.size != ProtocolConstants.MIN_FRAME_SIZE + len) return null

        val payload = data.copyOfRange(5, 5 + len)

        val crcData = data.copyOfRange(1, 5 + len)
        val expectedCrc = crc16(crcData)
        val actualCrc = (data[5 + len].toInt() and 0xFF) or
                ((data[6 + len].toInt() and 0xFF) shl 8)

        if (expectedCrc != actualCrc) return null

        return Frame(seq, cmd, payload)
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
