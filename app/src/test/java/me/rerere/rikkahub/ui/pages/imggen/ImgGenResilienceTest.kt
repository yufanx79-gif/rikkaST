package me.rerere.rikkahub.ui.pages.imggen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

class ImgGenResilienceTest {

    @Test
    fun normalizeImageGenSize_mapsAspectRatiosAndOrientations() {
        assertEquals("1024x1024", normalizeImageGenSize("square"))
        assertEquals("1024x1024", normalizeImageGenSize("1:1"))
        assertEquals("1024x1536", normalizeImageGenSize("portrait"))
        assertEquals("1024x1536", normalizeImageGenSize("2:3"))
        assertEquals("1024x1536", normalizeImageGenSize("9:16"))
        assertEquals("1536x1024", normalizeImageGenSize("landscape"))
        assertEquals("1536x1024", normalizeImageGenSize("16:9"))
        assertEquals("auto", normalizeImageGenSize("auto"))
        assertEquals("auto", normalizeImageGenSize(""))
    }

    @Test
    fun classifyImageGenerationError_handlesAllFailureClassesAndRedactsKeys() {
        val mMissingModel = classifyImageGenerationError(IllegalStateException("No model selected"))
        assertTrue(mMissingModel.contains("尚未选择生图模型"))

        val mMissingProvider = classifyImageGenerationError(IllegalStateException("Provider not found"))
        assertTrue(mMissingProvider.contains("提供商不存在"))

        val m401 = classifyImageGenerationError(RuntimeException("HTTP 401 Unauthorized for key sk-secret123456789"))
        assertTrue(m401.contains("401"))
        assertFalse(m401.contains("sk-secret123456789"))

        val m403 = classifyImageGenerationError(RuntimeException("HTTP 403 Forbidden: organization verification required"))
        assertTrue(m403.contains("403"))

        val m429 = classifyImageGenerationError(RuntimeException("HTTP 429 Too Many Requests: rate limit exceeded"))
        assertTrue(m429.contains("429"))

        val mTimeout = classifyImageGenerationError(SocketTimeoutException("Read timed out"))
        assertTrue(mTimeout.contains("超时"))

        val mMalformed = classifyImageGenerationError(IllegalArgumentException("Malformed image payload: empty image data"))
        assertTrue(mMalformed.contains("格式无效"))
    }

    @Test
    fun extractValidImageBase64_supportsDataUriAndRawBase64AndRejectsInvalid() {
        val png1x1 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        assertEquals(png1x1, extractValidImageBase64(png1x1))
        assertEquals(png1x1, extractValidImageBase64("data:image/png;base64,$png1x1"))

        var failedEmpty = false
        try {
            extractValidImageBase64("")
        } catch (_: IllegalArgumentException) {
            failedEmpty = true
        }
        assertTrue(failedEmpty)
    }
}
