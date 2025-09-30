import org.apache.commons.codec.binary.Base32

fun main() {
    val publicKey = byteArrayOf(
        0x3f.toByte(), 0x0c.toByte(), 0x34.toByte(), 0xbf.toByte(),
        0x93.toByte(), 0xad.toByte(), 0x0d.toByte(), 0x99.toByte(),
        0x71.toByte(), 0xd0.toByte(), 0x4c.toByte(), 0xcc.toByte(),
        0x90.toByte(), 0xf7.toByte(), 0x05.toByte(), 0x51.toByte(),
        0x1c.toByte(), 0x83.toByte(), 0x8a.toByte(), 0xad.toByte(),
        0x97.toByte(), 0x34.toByte(), 0xa4.toByte(), 0xa2.toByte(),
        0xfb.toByte(), 0x0d.toByte(), 0x7a.toByte(), 0x03.toByte(),
        0xfc.toByte(), 0x7f.toByte(), 0xe8.toByte(), 0x9a.toByte()
    )
    
    val versionByte = (6 shl 3).toByte()
    val payload = byteArrayOf(versionByte) + publicKey
    println("Payload hex: " + payload.joinToString("") { "%02x".format(it) })
    
    val codec = Base32()
    val encoded = codec.encode(payload)
    println("Encoded: " + String(encoded))
}
