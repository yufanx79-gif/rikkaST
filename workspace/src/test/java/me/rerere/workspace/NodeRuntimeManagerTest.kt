package me.rerere.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NodeRuntimeManagerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `version check accepts Node 18 and 20_11_0 and above and rejects old versions`() {
        assertTrue(NodeRuntimeManager.isNodeVersionSupported("v18.20.4"))
        assertTrue(NodeRuntimeManager.isNodeVersionSupported("v20.11.0"))
        assertTrue(NodeRuntimeManager.isNodeVersionSupported("v20.18.1"))
        assertTrue(NodeRuntimeManager.isNodeVersionSupported("v22.4.0"))
        assertFalse(NodeRuntimeManager.isNodeVersionSupported("v16.20.0"))
        assertFalse(NodeRuntimeManager.isNodeVersionSupported("v14.0.0"))
    }

    @Test
    fun `redactSensitiveLog masks Bearer tokens and API keys`() {
        val raw = "Authorization: Bearer secretToken123456 key: \"pst-abcdef1234567890\" RIKKA_NODE_TOKEN=mySecret999"
        val redacted = NodeRuntimeManager.redactSensitiveLog(raw)
        assertFalse(redacted.contains("secretToken123456"))
        assertFalse(redacted.contains("abcdef1234567890"))
        assertFalse(redacted.contains("mySecret999"))
        assertTrue(redacted.contains("[REDACTED]"))
    }

    @Test
    fun `resolveArchiveSpec returns official Node v20_18_1 checksums`() {
        val arm64Spec = NodeRuntimeManager.resolveArchiveSpec("arm64")
        assertEquals("v20.18.1", arm64Spec.version)
        assertEquals("node-v20.18.1-linux-arm64.tar.xz", arm64Spec.fileName)
        assertEquals(
            "44d1ffc5905c005ace4515ca6f8c090c4c7cfce3a9a67df0dba35c727590b8f6",
            arm64Spec.sha256
        )
        assertEquals(NodeRuntimeManager.SHA256_LINUX_ARM64, arm64Spec.sha256)

        val x64Spec = NodeRuntimeManager.resolveArchiveSpec("x64")
        assertEquals("node-v20.18.1-linux-x64.tar.xz", x64Spec.fileName)
        assertEquals(
            "c6fa75c841cbffac851678a472f2a5bd612fff8308ef39236190e1f8dbb0e567",
            x64Spec.sha256
        )
        assertEquals(NodeRuntimeManager.SHA256_LINUX_X64, x64Spec.sha256)
    }

    @Test
    fun `computeSha256 matches expected digest`() {
        val f = File(tmp.root, "sample.txt")
        f.writeText("hello-rikka-node", Charsets.UTF_8)
        val sha = NodeRuntimeManager.computeSha256(f)
        assertEquals(64, sha.length)
        assertEquals(sha, NodeRuntimeManager.computeSha256(f))
    }
}
