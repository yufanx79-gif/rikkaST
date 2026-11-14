package me.rerere.rikkahub.data.db.migrations

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 锁死 [ExpectedSchema] 与 Room 生成的 schema JSON 一致。
 *
 * 为什么必须有这个测试：`ExpectedSchema` 是**手写副本**，用于备份恢复前的结构预检。
 * 一旦有人加了列却忘了同步，预检会把自家备份里的新列判成「不认识的列」，
 * 于是恢复自己刚导出的备份都会失败 —— 这是最难排查的一类回归。
 */
class ExpectedSchemaTest {

    /** 找到仓库里最新的 schema JSON 版本号。 */
    private fun latestSchemaFile(): File {
        val dir = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/schemas/me.rerere.rikkahub.data.db.AppDatabase") }
            .firstOrNull { it.isDirectory }
            ?: error("找不到 schema 目录（测试需在仓库内运行）")
        return dir.listFiles { f -> f.extension == "json" }
            ?.maxByOrNull { it.nameWithoutExtension.toIntOrNull() ?: -1 }
            ?: error("schema 目录为空")
    }

    @Test
    fun `ExpectedSchema 与最新 Room schema 完全一致`() {
        val file = latestSchemaFile()
        val root = Json.parseToJsonElement(file.readText()).jsonObject
        val entities = root["database"]!!.jsonObject["entities"]!!.jsonArray

        val fromRoom = entities.associate { entity ->
            val obj = entity.jsonObject
            val table = obj["tableName"]!!.jsonPrimitive.content
            val columns = obj["fields"]!!.jsonArray.map {
                it.jsonObject["columnName"]!!.jsonPrimitive.content
            }.toSet()
            table to columns
        }

        // 表集合一致
        assertEquals(
            "表集合与 ${file.name} 不一致，请重新生成 ExpectedSchema",
            fromRoom.keys,
            ExpectedSchema.knownTables(),
        )

        // 每张表的列集合一致
        fromRoom.forEach { (table, expectedColumns) ->
            val actual = ExpectedSchema.columnsOf(table)
            assertNotNull("ExpectedSchema 缺少表 $table", actual)
            assertEquals(
                "表 $table 的列与 ${file.name} 不一致，请重新生成 ExpectedSchema",
                expectedColumns,
                actual,
            )
        }
    }

    @Test
    fun `workspaces 包含上游的 type 与 ssh_config 两列`() {
        val cols = ExpectedSchema.columnsOf("workspaces")
        assertNotNull(cols)
        // 这两列来自上游 v32 的远程 SSH 工作区；我们不用，但必须声明，
        // 否则恢复上游备份时 Room 会因「库里多出来的列」拒绝打开。
        assertTrue("缺少上游兼容列 type", cols!!.contains("type"))
        assertTrue("缺少上游兼容列 ssh_config", cols.contains("ssh_config"))
    }

    @Test
    fun `我们独有的四张表都在 ExpectedSchema 里`() {
        // 上游备份没有这些表，恢复时要由 Migration_33_34 补建；
        // 若这里缺失，预检会把它们当成「上游新增表」而放行，随后 DAO 访问崩。
        listOf(
            "MemoryEntity",
            "knowledge_sources",
            "knowledge_chunks",
            "knowledge_source_assistants",
        ).forEach { table ->
            assertNotNull("ExpectedSchema 缺少表 $table", ExpectedSchema.columnsOf(table))
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 下面两个测试是 2.5.13 恢复上游备份失败的回归防护。
    //
    // 事故：Migration_33_34 补建 knowledge_* 三张表时**只建了表、没建索引**，
    // 恢复上游备份时报 `Migration didn't properly handle: knowledge_sources`。
    // 原来的测试只比对「列集合」，完全看不到索引，所以 bug 一路漏到了 APK。
    //
    // Room 的 TableInfo.equals 是「列 + 索引 + 外键」三者都比对，
    // 测试必须覆盖同样的维度，否则这个测试就是假的安全感。
    // ─────────────────────────────────────────────────────────────────────

    /** 读最新 schema 里每张表期望的索引名。 */
    private fun expectedIndicesFromLatestSchema(): Map<String, Set<String>> {
        val root = Json.parseToJsonElement(latestSchemaFile().readText()).jsonObject
        return root["database"]!!.jsonObject["entities"]!!.jsonArray.associate { entity ->
            val obj = entity.jsonObject
            val table = obj["tableName"]!!.jsonPrimitive.content
            val indices = obj["indices"]?.jsonArray
                ?.map { it.jsonObject["name"]!!.jsonPrimitive.content }
                ?.toSet()
                ?: emptySet()
            table to indices
        }
    }

    @Test
    fun `Migration_33_34 建表时必须同时建索引`() {
        // 只针对 Migration_33_34 补建的那四张表 —— 它们在上游备份里不存在，
        // 完全依赖这条迁移从零建出来，索引漏了就必然恢复失败。
        val migrationFile = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/java/me/rerere/rikkahub/data/db/migrations/Migration_33_34.kt") }
            .firstOrNull { it.isFile }
            ?: error("找不到 Migration_33_34.kt")

        val source = migrationFile.readText()
        // 抽出源码里出现的所有 CREATE INDEX 索引名
        val createdIndices = Regex("""CREATE\s+INDEX\s+IF\s+NOT\s+EXISTS\s+`?(\w+)`?""", RegexOption.IGNORE_CASE)
            .findAll(source)
            .map { it.groupValues[1] }
            .toSet()

        val expected = expectedIndicesFromLatestSchema()
        val migratedTables = listOf(
            "MemoryEntity",
            "knowledge_sources",
            "knowledge_chunks",
            "knowledge_source_assistants",
        )

        migratedTables.forEach { table ->
            val want = expected[table].orEmpty()
            val missing = want - createdIndices
            assertTrue(
                "Migration_33_34 建表 $table 时漏了索引 $missing —— " +
                    "Room 的 TableInfo 校验会比较索引，恢复上游备份会直接失败。",
                missing.isEmpty(),
            )
        }
    }

    @Test
    fun `Migration_33_34 建表 SQL 与 Room schema 的列定义一致`() {
        val migrationFile = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/java/me/rerere/rikkahub/data/db/migrations/Migration_33_34.kt") }
            .firstOrNull { it.isFile }
            ?: error("找不到 Migration_33_34.kt")
        val source = migrationFile.readText()

        val root = Json.parseToJsonElement(latestSchemaFile().readText()).jsonObject
        val entities = root["database"]!!.jsonObject["entities"]!!.jsonArray
            .map { it.jsonObject }
            .associateBy { it["tableName"]!!.jsonPrimitive.content }

        listOf(
            "MemoryEntity",
            "knowledge_sources",
            "knowledge_chunks",
            "knowledge_source_assistants",
        ).forEach { table ->
            val expectedColumns = entities[table]!!["fields"]!!.jsonArray
                .map { it.jsonObject["columnName"]!!.jsonPrimitive.content }
                .toSet()

            // 在源码里定位这张表的 CREATE TABLE 语句（拼接的字符串字面量）
            val createBlock = Regex(
                """CREATE TABLE IF NOT EXISTS `?$table`?\s*\((.*?)\)\s*""" + '"',
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ).find(source)?.groupValues?.get(1)
                ?: error("Migration_33_34 里找不到 $table 的 CREATE TABLE 语句")

            val declared = Regex("""`(\w+)`""").findAll(createBlock)
                .map { it.groupValues[1] }
                .toSet()

            val missing = expectedColumns - declared
            assertTrue(
                "Migration_33_34 的 $table 建表语句缺少列 $missing（与 34.json 不一致）",
                missing.isEmpty(),
            )
        }
    }
}
