package me.rerere.rikkahub.data.datastore

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.migration.migrateProvidersQuotaQuery
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderResilienceAndMigrationTest {

    @Test
    fun decodeProvidersResilient_isolatesMalformedRecords_andPreservesValidProviders() {
        val rawJson = """
            [
              {
                "type": "openai",
                "id": "11111111-1111-1111-1111-111111111111",
                "enabled": true,
                "name": "Custom Relay",
                "apiKey": "sk-secret-openai-key-123456",
                "baseUrl": "https://relay.example.com/v1",
                "balanceOption": {
                  "enabled": true,
                  "apiPath": "/v1/dashboard/billing/usage",
                  "resultPath": "total_usage"
                },
                "models": [
                  {
                    "id": "22222222-2222-2222-2222-222222222222",
                    "modelId": "gpt-4o",
                    "displayName": "GPT-4o",
                    "type": "CHAT",
                    "providerOverwrite": {
                      "type": "openai",
                      "id": "33333333-3333-3333-3333-333333333333",
                      "name": "Override Relay",
                      "apiKey": "sk-secret-override-key-9999",
                      "baseUrl": "https://override.example.com/v1"
                    }
                  },
                  {
                    "id": "not-a-valid-uuid",
                    "modelId": "gpt-image-1",
                    "displayName": "GPT Image 1",
                    "type": "IMAGE",
                    "abilities": ["REASONING", "UNKNOWN_FUTURE_ABILITY"],
                    "providerOverwrite": {
                      "type": "corrupted_unknown_provider_type"
                    }
                  }
                ]
              },
              {
                "type": "completely_unknown_provider_type",
                "id": "bad-id",
                "apiKey": "sk-should-never-appear-in-logs"
              },
              "not-even-an-object",
              {
                "type": "claude",
                "id": "44444444-4444-4444-4444-444444444444",
                "enabled": true,
                "name": "Anthropic Direct",
                "apiKey": "sk-ant-secret-key-7890",
                "baseUrl": "https://api.anthropic.com/v1",
                "promptCacheTtl": "invalid_ttl_value",
                "models": [
                  {
                    "modelId": "claude-3-7-sonnet",
                    "displayName": "Claude 3.7 Sonnet"
                  }
                ]
              },
              {
                "name": "Legacy Inferred OpenAI",
                "apiKey": "sk-legacy-no-type-field",
                "baseUrl": "https://legacy.example.com/v1",
                "models": []
              }
            ]
        """.trimIndent()

        val logs = mutableListOf<String>()
        val corruptBackups = mutableListOf<String>()
        val providers = decodeProvidersResilient(
            rawJson = rawJson,
            json = JsonInstant,
            onCorruptBackup = { backup ->
                if (corruptBackups.isEmpty()) {
                    corruptBackups += backup
                }
            },
            onDiagnostic = { logs += it },
        )

        // 3 valid providers preserved ("Custom Relay", "Anthropic Direct", "Legacy Inferred OpenAI")
        assertEquals(3, providers.size)
        assertEquals(1, corruptBackups.size)
        assertEquals(rawJson, corruptBackups.first())

        val relay = providers[0] as ProviderSetting.OpenAI
        assertEquals("Custom Relay", relay.name)
        assertEquals("https://relay.example.com/v1", relay.baseUrl)
        assertTrue(relay.quotaQuery.enabled)
        assertEquals("{{baseUrl}}/v1/dashboard/billing/usage", relay.quotaQuery.url)
        assertEquals(2, relay.models.size)
        assertNotNull(relay.models[0].providerOverwrite)
        assertEquals(ModelType.IMAGE, relay.models[1].type)
        // Broken providerOverwrite on model[1] was isolated to null without dropping model[1]
        assertNull(relay.models[1].providerOverwrite)

        val claude = providers[1] as ProviderSetting.Claude
        assertEquals("Anthropic Direct", claude.name)
        assertEquals(1, claude.models.size)
        assertEquals("claude-3-7-sonnet", claude.models[0].modelId)

        val legacy = providers[2] as ProviderSetting.OpenAI
        assertEquals("Legacy Inferred OpenAI", legacy.name)
        assertEquals("https://legacy.example.com/v1", legacy.baseUrl)

        // Verify diagnostic logs and sanitizeProviderLogSummary NEVER contain any secret API keys
        assertTrue(logs.isNotEmpty())
        val combinedLogs = logs.joinToString("\n")
        assertFalse(combinedLogs.contains("sk-secret"))
        assertFalse(combinedLogs.contains("sk-should-never"))
        assertFalse(combinedLogs.contains("sk-ant-secret"))
        assertFalse(combinedLogs.contains("sk-legacy"))

        val directSummary = sanitizeProviderLogSummary(
            rawObj = JsonObject(
                mapOf(
                    "type" to JsonPrimitive("openai"),
                    "id" to JsonPrimitive("p-1"),
                    "name" to JsonPrimitive("SecretProvider"),
                    "apiKey" to JsonPrimitive("sk-ultra-secret-value"),
                    "accessToken" to JsonPrimitive("eyJ-jwt-secret"),
                )
            ),
            index = 2,
            reason = "unit_test",
        )
        assertFalse(directSummary.contains("sk-ultra-secret-value"))
        assertFalse(directSummary.contains("eyJ-jwt-secret"))
        assertTrue(directSummary.contains("apiKey=<REDACTED>"))
        assertTrue(directSummary.contains("accessToken=<REDACTED>"))
    }

    @Test
    fun migrateProvidersQuotaQuery_convertsLegacyBalanceOptionAndNestedOverride() {
        val legacyJson = """
            [
              {
                "type": "openai",
                "id": "11111111-1111-1111-1111-111111111111",
                "name": "MyOpenAI",
                "balanceOption": {
                  "enabled": true,
                  "apiPath": "/credits",
                  "resultPath": "data.total_usage"
                }
              }
            ]
        """.trimIndent()
        val migrated = migrateProvidersQuotaQuery(legacyJson)
        val arr = JsonInstant.parseToJsonElement(migrated) as JsonArray
        val obj = arr[0] as JsonObject
        assertFalse("balanceOption" in obj)
        val quotaQuery = obj["quotaQuery"] as? JsonObject
        assertNotNull(quotaQuery)
        assertEquals(true, (quotaQuery!!["enabled"] as? JsonPrimitive)?.booleanOrNull)
        assertEquals("{{baseUrl}}/credits", (quotaQuery["url"] as? JsonPrimitive)?.contentOrNull)
    }
}
