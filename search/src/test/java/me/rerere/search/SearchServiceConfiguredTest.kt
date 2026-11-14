package me.rerere.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 未配置 API key 时必须能识别出来，而不是带着空 Bearer 头发请求拿 401。
 *
 * 真机现象（2026-11-14）：search_web / scrape_web 都返回
 * `#401 Authentication Fails (auth header format should be Bearer sk-...)`，
 * 模型据此误报「搜索服务配置坏了」，实际只是没填 key。
 */
class SearchServiceConfiguredTest {

    @Test
    fun `provider with empty api key is reported as unconfigured`() {
        assertFalse(SearchServiceOptions.DeepSeekOptions(apiKey = "").isConfigured)
        assertFalse(SearchServiceOptions.TavilyOptions(apiKey = "").isConfigured)
        assertFalse(SearchServiceOptions.ExaOptions(apiKey = "").isConfigured)
    }

    @Test
    fun `provider with api key is reported as configured`() {
        assertTrue(SearchServiceOptions.DeepSeekOptions(apiKey = "sk-test").isConfigured)
        assertTrue(SearchServiceOptions.TavilyOptions(apiKey = "tvly-test").isConfigured)
    }

    @Test
    fun `keyless providers stay configured`() {
        assertTrue(SearchServiceOptions.BingLocalOptions().isConfigured)
        assertTrue(SearchServiceOptions.SearXNGOptions().isConfigured)
    }
}
EOF2
echo done
