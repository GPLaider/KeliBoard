// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileUtilsTest {
    @Test fun onlyElfFilesAreAcceptedAsNativeLibraries() {
        val elf = File.createTempFile("gesture", ".so").apply {
            writeBytes(byteArrayOf(0x7f, 0x45, 0x4c, 0x46))
            deleteOnExit()
        }
        val html = File.createTempFile("gesture", ".so").apply {
            writeText("<!doctype html>")
            deleteOnExit()
        }

        assertTrue(FileUtils.isElfFile(elf))
        assertFalse(FileUtils.isElfFile(html))
        assertFalse(FileUtils.isElfFile(File(elf.parentFile, "missing.so")))
    }
}
