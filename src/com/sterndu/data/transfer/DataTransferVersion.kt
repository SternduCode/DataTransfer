package com.sterndu.data.transfer

object DataTransferVersion {
    const val MAJOR_VERSION = 1
    const val MINOR_VERSION = 0

    val packedVersion get() = ByteArray(2).apply {
        this[0] = MAJOR_VERSION.toByte()
        this[1] = MINOR_VERSION.toByte()
    }

}
