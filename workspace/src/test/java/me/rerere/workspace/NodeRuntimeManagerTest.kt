package me.rerere.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class NodeRuntimeManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun ` official Node v20_18_1 checksums are valid 64-char sha256 hex`() {
        val hex64 = Regex("^[0-9a-f]{64}$")
        assertTrue(NodeRuntimeManager.NODE_ARM64_SHA256.matches(hex64))
        assertTrue(NodeRuntimeManager.NODE_X64_SHA256.matches(hex64))
    }

    @Test
    fun `satisfiesNodeEngine handles minimum, caret, and compound ranges`() {
        assertTrue(NodeRuntimeManager.satisfiesNodeEngine("v20.18.1", ">=18"))
        assertTrue(NodeRuntimeManager.satisfiesNodeEngine("v20.18.1", ">=18.0.0 <23.0.0"))
        assertTrue(NodeRuntimeManager.satisfiesNodeEngine("v20.18.1", "^20.10.0"))
        assertTrue(NodeRuntimeManager.satisfiesNodeEngine("v20.18.1", ">=16 || >=18 || >=20"))
        assertFalse(NodeRuntimeManager.satisfiesNodeEngine("v20.18.1", ">=22.0.0"))
        assertFalse(NodeRuntimeManager.satisfiesNodeEngine("v20.18.1", "<18.0.0"))
    }

    @Test
    fun `extractTarXz rejects path traversal entries`() {
        val dest = tmp.newFolder("dest")
        val target = File(dest, "../escaped").canonicalFile
        assertFalse(target.toPath().startsWith(dest.canonicalFile.toPath()))
    }

    @Test
    fun `verifySha256 matches known digest`() {
        val file = tmp.newFile("sample.bin")
        file.writeText("rikkahub-node-runtime-test")
        val expected = "9e70a325c42b62163686f3f217ea239f1e99b5670709ff4842f8bd3fd94731f4"
        assertTrue(NodeRuntimeManager.verifySha256(file, expected))
        assertFalse(NodeRuntimeManager.verifySha256(file, "0".repeat(64)))
    }

    @Test
    fun `resolveSharedNodeDir returns shared_tavern_node binary dir only when local node is absent and never picks arbitrary assistant workspace`() {
        val baseDir = tmp.newFolder("workspaces")
        val assistantLinux = File(baseDir, "assistant_1/linux").apply { mkdirs() }

        // Even if another assistant workspace has a local node binary, resolveSharedNodeDir must NOT pick it
        File(baseDir, "assistant_2/linux/usr/local/node/bin/node").apply {
            parentFile.mkdirs()
            writeText("#!/bin/sh\necho v20.18.1\n")
        }
        assertNull(NodeRuntimeManager.resolveSharedNodeDir(assistantLinux))

        // Once shared_tavern_node is installed, resolveSharedNodeDir resolves it
        val sharedNodeBin = File(baseDir, "shared_tavern_node/linux/usr/local/node/bin/node").apply {
            parentFile.mkdirs()
            writeText("#!/bin/sh\necho v20.18.1\n")
        }
        val resolved = NodeRuntimeManager.resolveSharedNodeDir(assistantLinux)
        assertEquals(sharedNodeBin.parentFile.parentFile.canonicalFile, resolved?.canonicalFile)

        // When assistant has its own local node, resolveSharedNodeDir returns null
        File(assistantLinux, "usr/local/node/bin/node").apply {
            parentFile.mkdirs()
            writeText("#!/bin/sh\n")
        }
        assertNull(NodeRuntimeManager.resolveSharedNodeDir(assistantLinux))
    }

    @Test
    fun `RootfsPatcher provisions rikka-node profile and usr-local-bin symlinks`() {
        val linuxDir = tmp.newFolder("rootfs")
        File(linuxDir, "etc").mkdirs()
        RootfsPatcher().patch(linuxDir)
        val profile = File(linuxDir, "etc/profile.d/rikka-node.sh")
        assertTrue(profile.isFile)
        assertTrue(profile.readText().contains("/usr/local/node/bin"))
        assertTrue(profile.readText().contains("NPM_CONFIG_CACHE"))
        assertTrue(profile.readText().contains("NODE_COMPILE_CACHE"))
        assertTrue(File(linuxDir, "usr/local/node").isDirectory)
        listOf("node", "npm", "npx").forEach { bin ->
            val linkPath = File(linuxDir, "usr/local/bin/$bin").toPath()
            assertTrue(Files.isSymbolicLink(linkPath))
            assertEquals("../node/bin/$bin", Files.readSymbolicLink(linkPath).toString())
        }
    }
}
