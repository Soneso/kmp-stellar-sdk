//
//  AndroidAdapterBytecodeTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.smartaccount.AndroidStorageAdapter
import com.soneso.stellar.sdk.smartaccount.AndroidWebAuthnProvider
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.util.jar.JarFile
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Guards the Android adapters against bytecode calls to Kotlin default-argument constructors
 * and `$default` methods of androidx classes, whose JVM signatures change when a library adds a
 * parameter.
 */
class AndroidAdapterBytecodeTest {

    @Test
    fun testNoAndroidxDefaultArgumentCalls() {
        val violations = mutableListOf<String>()
        val anchors = listOf(AndroidWebAuthnProvider::class.java, AndroidStorageAdapter::class.java)
        for (anchor in anchors) {
            for ((classFile, bytes) in classFiles(anchor)) {
                methodReferences(classFile, bytes)
                    .filter { it.isAndroidxDefaultArgumentCall() }
                    .mapTo(violations) { "$classFile: ${it.owner}.${it.name}${it.descriptor}" }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Calls to androidx default-argument constructors or \$default methods:\n" +
                violations.joinToString("\n")
        )
    }

    private data class MethodReference(
        val owner: String,
        val name: String,
        val descriptor: String
    ) {
        fun isAndroidxDefaultArgumentCall(): Boolean =
            owner.startsWith("androidx/") &&
                ((name == "<init>" && descriptor.contains(DEFAULT_CONSTRUCTOR_MARKER)) ||
                    name.endsWith("\$default"))
    }

    /**
     * Returns the file name and bytes of the class file of [anchor] and of every `Name$*.class`
     * file in its package, read from the directory or JAR that holds [anchor].
     */
    private fun classFiles(anchor: Class<*>): List<Pair<String, ByteArray>> {
        val location = anchor.protectionDomain?.codeSource?.location
            ?: fail("${anchor.name} has no code source location")
        val packagePath = anchor.name.substringBeforeLast('.').replace('.', '/')
        val topLevelFile = anchor.name.substringAfterLast('.') + ".class"
        val nestedPrefix = anchor.name.substringAfterLast('.') + "$"
        fun matches(fileName: String) = fileName == topLevelFile ||
            (fileName.startsWith(nestedPrefix) && fileName.endsWith(".class"))

        val root = File(location.toURI())
        val files = if (root.isDirectory) {
            File(root, packagePath).listFiles().orEmpty()
                .filter { it.isFile && matches(it.name) }
                .map { it.name to it.readBytes() }
        } else {
            JarFile(root).use { jar ->
                jar.entries().toList()
                    .filter { entry ->
                        !entry.isDirectory &&
                            entry.name.substringBeforeLast('/') == packagePath &&
                            matches(entry.name.substringAfterLast('/'))
                    }
                    .map { entry ->
                        val fileName = entry.name.substringAfterLast('/')
                        fileName to jar.getInputStream(entry).use { it.readBytes() }
                    }
            }
        }
        if (files.none { it.first == topLevelFile }) {
            fail("$topLevelFile not found in $packagePath of $location")
        }
        return files.sortedBy { it.first }
    }

    /**
     * Reads the constant pool of [bytes] and returns its `CONSTANT_Methodref` and
     * `CONSTANT_InterfaceMethodref` entries resolved to owner, name and descriptor.
     */
    private fun methodReferences(classFile: String, bytes: ByteArray): List<MethodReference> {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        fun skip(count: Int) = input.readFully(ByteArray(count))

        val magic = input.readInt().toUInt()
        check(magic == 0xCAFEBABEu) {
            "$classFile starts with 0x${magic.toString(16)}, not 0xcafebabe"
        }
        skip(4) // minor_version, major_version
        val count = input.readUnsignedShort()
        val tags = IntArray(count)
        val utf8 = arrayOfNulls<String>(count)
        val firstIndex = IntArray(count)
        val secondIndex = IntArray(count)
        var index = 1
        while (index < count) {
            val tag = input.readUnsignedByte()
            tags[index] = tag
            when (tag) {
                TAG_UTF8 -> utf8[index] = input.readUTF()
                // Integer, Float
                3, 4 -> skip(4)
                // Long, Double: each takes two constant pool slots
                5, 6 -> {
                    skip(8)
                    index++
                }
                // Class, String, MethodType, Module, Package
                TAG_CLASS, 8, 16, 19, 20 -> firstIndex[index] = input.readUnsignedShort()
                // Fieldref, Methodref, InterfaceMethodref, NameAndType, Dynamic, InvokeDynamic
                9, TAG_METHODREF, TAG_INTERFACE_METHODREF, TAG_NAME_AND_TYPE, 17, 18 -> {
                    firstIndex[index] = input.readUnsignedShort()
                    secondIndex[index] = input.readUnsignedShort()
                }
                // MethodHandle: reference_kind, then reference_index
                15 -> {
                    skip(1)
                    firstIndex[index] = input.readUnsignedShort()
                }
                else -> error("$classFile has unknown constant pool tag $tag at index $index")
            }
            index++
        }

        fun entry(at: Int, tag: Int): Int {
            check(at in 1 until count && tags[at] == tag) {
                "$classFile: constant pool index $at has tag ${tags.getOrNull(at)}, expected $tag"
            }
            return at
        }
        fun utf8At(at: Int): String = utf8[entry(at, TAG_UTF8)]!!

        return (1 until count)
            .filter { tags[it] == TAG_METHODREF || tags[it] == TAG_INTERFACE_METHODREF }
            .map { reference ->
                val nameAndType = entry(secondIndex[reference], TAG_NAME_AND_TYPE)
                MethodReference(
                    owner = utf8At(firstIndex[entry(firstIndex[reference], TAG_CLASS)]),
                    name = utf8At(firstIndex[nameAndType]),
                    descriptor = utf8At(secondIndex[nameAndType])
                )
            }
    }

    private companion object {
        const val DEFAULT_CONSTRUCTOR_MARKER = "Lkotlin/jvm/internal/DefaultConstructorMarker;"
        const val TAG_UTF8 = 1
        const val TAG_CLASS = 7
        const val TAG_METHODREF = 10
        const val TAG_INTERFACE_METHODREF = 11
        const val TAG_NAME_AND_TYPE = 12
    }
}
