package com.example.datacollector.protocol

object ProtocolConstants {
    const val SOF: Byte = 0xAA.toByte()
    const val EOF: Byte = 0x55.toByte()
    const val HEADER_SIZE = 5
    const val FOOTER_SIZE = 3
    const val MIN_FRAME_SIZE = HEADER_SIZE + FOOTER_SIZE
    const val MAX_PAYLOAD_SIZE = 237
    const val TIMEOUT_MS = 5000L
    const val MAX_RETRY = 3
    const val FRAME_INTERVAL_MS = 20L

    const val CMD_PING: Byte = 0x01
    const val CMD_GET_INFO: Byte = 0x02
    const val CMD_SET_TIME: Byte = 0x03
    const val CMD_GET_CONFIG: Byte = 0x04
    const val CMD_SET_CONFIG: Byte = 0x05
    const val CMD_GET_DATA: Byte = 0x06
    const val CMD_DATA_ACK: Byte = 0x07
    const val CMD_ERASE_DATA: Byte = 0x08
    const val CMD_GET_STATUS: Byte = 0x09
    const val CMD_SET_DEVICE_ID: Byte = 0x0A
    const val CMD_REBOOT: Byte = 0x0B
    const val CMD_DATA_FRAG: Byte = 0xFE.toByte()
    const val CMD_ERROR: Byte = 0xFF.toByte()

    const val ERASE_CONFIRM_CODE = 0xDEADBEEF.toInt()
    const val REBOOT_CONFIRM_CODE = 0xCAFEBABE.toInt()

    const val STATUS_OK = 0
    const val STATUS_FAIL = 1
    const val STATUS_BUSY = 2

    const val DATA_TYPE_UINT16: Byte = 0x00
    const val DATA_TYPE_INT16: Byte = 0x01
    const val DATA_TYPE_UINT32: Byte = 0x02
    const val DATA_TYPE_FLOAT32: Byte = 0x03
    const val DATA_TYPE_RAW: Byte = 0x04

    const val RECORD_SIZE = 32

    const val ACK_OK: Byte = 0x00
    const val ACK_ERR_CRC: Byte = 0x01
    const val ACK_CANCEL: Byte = 0x02

    const val ERR_UNSUPPORTED_CMD: Byte = 0x01
    const val ERR_PARAM: Byte = 0x02
    const val ERR_BUSY: Byte = 0x03
    const val ERR_STORAGE: Byte = 0x04
    const val ERR_CRC: Byte = 0x05
    const val ERR_TIMEOUT: Byte = 0x06

    val UUID_SERVICE_HM10 = java.util.UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB")
    val UUID_CHAR_HM10_TX = java.util.UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB")
    val UUID_CHAR_HM10_RX = java.util.UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB")

    val UUID_SERVICE_NUS = java.util.UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    val UUID_CHAR_NUS_TX = java.util.UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    val UUID_CHAR_NUS_RX = java.util.UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
}
