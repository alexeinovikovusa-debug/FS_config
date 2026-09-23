package ru.fsconfig.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CnuCodecTest {
    @Test
    fun roundTripPreservesHeaderOrderRowsAndUnknownKeys() {
        val source = "[9]=header\r\nDeviceType=9\r\nVendorKey=kept\r\nLineCount=2\r\nLengthConfiguration=33\r\nLine1=1 2 3\r\nLine2=0\r\n"
        val parsed = CnuCodec.parse(source)
        assertEquals(listOf("[9]", "DeviceType", "VendorKey", "LineCount", "LengthConfiguration"), parsed.document.header.map { it.key })
        assertEquals(source, CnuCodec.encode(parsed.document))
        assertTrue(parsed.warnings.any { it.contains("LineCount") })
        assertTrue(parsed.warnings.any { it.contains("LengthConfiguration") })
    }

    @Test
    fun rejectsValuesOutsideByteRange() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            CnuCodec.parse("[9]=header\nLine1=256\n")
        }
    }
}
