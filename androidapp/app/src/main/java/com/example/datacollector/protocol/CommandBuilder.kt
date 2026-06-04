package com.example.datacollector.protocol

import com.example.datacollector.domain.model.DeviceConfig
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object CommandBuilder {

    fun ping(seq: Int): ByteArray {
        return FrameParser.encode(seq, ProtocolConstants.CMD_PING)
    }

    fun getInfo(seq: Int): ByteArray {
        return FrameParser.encode(seq, ProtocolConstants.CMD_GET_INFO)
    }

    fun setTime(seq: Int, unixTimestamp: Long): ByteArray {
        val payload = ByteBuffer.allocate(4).apply {
            order(ByteOrder.BIG_ENDIAN)
            putInt(unixTimestamp.toInt())
        }.array()
        return FrameParser.encode(seq, ProtocolConstants.CMD_SET_TIME, payload)
    }

    fun getConfig(seq: Int): ByteArray {
        return FrameParser.encode(seq, ProtocolConstants.CMD_GET_CONFIG)
    }

    fun setConfig(seq: Int, config: DeviceConfig): ByteArray {
        val payload = ByteArrayOutputStream()
        payload.write(intToBytesBigEndian(config.samplingIntervalSec.toInt()))
        payload.write(config.sensorAddr)
        payload.write((config.sensorStartReg shr 8) and 0xFF)
        payload.write(config.sensorStartReg and 0xFF)
        payload.write((config.sensorRegCount shr 8) and 0xFF)
        payload.write(config.sensorRegCount and 0xFF)
        payload.write(config.sensorDataType)
        payload.write(intToBytesBigEndian(config.modbusBaudrate))
        payload.write(config.modbusParity)
        return FrameParser.encode(seq, ProtocolConstants.CMD_SET_CONFIG, payload.toByteArray())
    }

    fun getData(seq: Int, startIndex: Int, count: Int): ByteArray {
        val payload = ByteBuffer.allocate(6).apply {
            order(ByteOrder.BIG_ENDIAN)
            putInt(startIndex)
            putShort(count.toShort())
        }.array()
        return FrameParser.encode(seq, ProtocolConstants.CMD_GET_DATA, payload)
    }

    fun dataAck(seq: Int, chunkIndex: Int, status: Byte = ProtocolConstants.ACK_OK): ByteArray {
        val payload = ByteBuffer.allocate(3).apply {
            order(ByteOrder.BIG_ENDIAN)
            putShort(chunkIndex.toShort())
            put(status)
        }.array()
        return FrameParser.encode(seq, ProtocolConstants.CMD_DATA_ACK, payload)
    }

    fun eraseData(seq: Int): ByteArray {
        val payload = ByteBuffer.allocate(4).apply {
            order(ByteOrder.BIG_ENDIAN)
            putInt(ProtocolConstants.ERASE_CONFIRM_CODE)
        }.array()
        return FrameParser.encode(seq, ProtocolConstants.CMD_ERASE_DATA, payload)
    }

    fun getStatus(seq: Int): ByteArray {
        return FrameParser.encode(seq, ProtocolConstants.CMD_GET_STATUS)
    }

    fun setDeviceId(seq: Int, deviceId: Long, name: String): ByteArray {
        val payload = ByteArrayOutputStream()
        payload.write(intToBytesBigEndian(deviceId.toInt()))
        val nameBytes = name.toByteArray(Charsets.UTF_8).copyOf(16)
        payload.write(nameBytes)
        return FrameParser.encode(seq, ProtocolConstants.CMD_SET_DEVICE_ID, payload.toByteArray())
    }

    fun reboot(seq: Int): ByteArray {
        val payload = ByteBuffer.allocate(4).apply {
            order(ByteOrder.BIG_ENDIAN)
            putInt(ProtocolConstants.REBOOT_CONFIRM_CODE)
        }.array()
        return FrameParser.encode(seq, ProtocolConstants.CMD_REBOOT, payload)
    }

    private fun intToBytesBigEndian(value: Int): ByteArray {
        return ByteBuffer.allocate(4).apply {
            order(ByteOrder.BIG_ENDIAN)
            putInt(value)
        }.array()
    }
}
