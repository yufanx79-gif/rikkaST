package me.rerere.rikkahub.data.st.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * URL 安装归一化测试（修复「导入说缺少 json 文件」的核心逻辑）：
 * - 仓库主页（GitHub / GitLab / Codeberg）→ REPO 计划 + 分支自动探测（main/master）；
 * - manifest.json 直链 → MANIFEST 计划 + 仓库名推导；
 * - zip 直链 / archive 端点 → ZIP 计划；
 * - manifest 资源清单 / JS 相对导入提取 / 相对路径归一化。
 */
class StUrlInstallTest {

    // ========== 仓库主页（REPO） ==========

    @Test
    fun `github repo auto detects main and master`() {
        val plan = planUrlInstall("https://github.com/n0vi028/JS-Slash-Runner")
        assertEquals(StUrlKind.REPO, plan?.kind)
        assertEquals("JS-Slash-Runner", plan?.folderName)
        val zips = plan!!.zipUrls
        // main 2 候选（heads + codeload）+ master 2 候选；未显式指定分支时不动 tag 端点
        assertEquals(4, zips.size)
        assertTrue(zips[0].contains("/refs/heads/main.zip"))
        assertTrue(zips[1].contains("codeload"))
        assertTrue(zips[2].contains("/refs/heads/master.zip"))
    }

    @Test
    fun `github repo explicit branch adds tag candidate`() {
        val plan = planUrlInstall("https://github.com/muyoou/st-memory-enhancement", branch = "dev")
        assertEquals(StUrlKind.REPO, plan?.kind)
        val zips = plan!!.zipUrls
        assertEquals(3, zips.size)
        assertTrue(zips[0].contains("/refs/heads/dev.zip"))
        assertTrue(zips[2].contains("/refs/tags/dev.zip"))
    }

    @Test
    fun `github repo subpath is truncated to repo`() {
        val plan = planUrlInstall("https://github.com/n0vi028/JS-Slash-Runner/tree/main")
        assertEquals(StUrlKind.REPO, plan?.kind)
        assertEquals("JS-Slash-Runner", plan?.folderName)
    }

    @Test
    fun `gitlab repo uses archive endpoint`() {
        val plan = planUrlInstall("https://gitlab.com/novi028/JS-Slash-Runner")
        assertEquals(StUrlKind.REPO, plan?.kind)
        assertEquals("JS-Slash-Runner", plan?.folderName)
        val zips = plan!!.zipUrls
        assertEquals(2, zips.size)
        assertTrue(zips[0].contains("gitlab.com/novi028/JS-Slash-Runner/-/archive/main/JS-Slash-Runner-main.zip"))
    }

    @Test
    fun `gitlab nested group keeps owner path`() {
        val ref = parseRepoUrl("https://gitlab.com/group/sub/my-ext")
        assertEquals("group/sub", ref?.ownerPath)
        assertEquals("my-ext", ref?.repo)
    }

    @Test
    fun `codeberg repo uses forgejo archive endpoints`() {
        val plan = planUrlInstall("https://codeberg.org/zonde306/ST-Prompt-Template")
        assertEquals(StUrlKind.REPO, plan?.kind)
        assertEquals("ST-Prompt-Template", plan?.folderName)
        val zips = plan!!.zipUrls
        assertEquals(4, zips.size)
        assertTrue(zips[0].contains("codeberg.org/zonde306/ST-Prompt-Template/archive/main.zip"))
    }

    // ========== manifest.json 直链（MANIFEST） ==========

    @Test
    fun `raw github manifest link`() {
        val plan = planUrlInstall("https://raw.githubusercontent.com/N0VI028/JS-Slash-Runner/main/manifest.json")
        assertEquals(StUrlKind.MANIFEST, plan?.kind)
        assertEquals("JS-Slash-Runner", plan?.folderName)
        assertEquals("https://raw.githubusercontent.com/N0VI028/JS-Slash-Runner/main/manifest.json", plan?.manifestUrl)
    }

    @Test
    fun `gitlab raw manifest link`() {
        val plan = planUrlInstall("https://gitlab.com/novi028/JS-Slash-Runner/-/raw/main/manifest.json")
        assertEquals(StUrlKind.MANIFEST, plan?.kind)
        assertEquals("JS-Slash-Runner", plan?.folderName)
    }

    @Test
    fun `github raw manifest link derives repo name`() {
        val plan = planUrlInstall("https://github.com/muyoou/st-memory-enhancement/raw/master/manifest.json")
        assertEquals(StUrlKind.MANIFEST, plan?.kind)
        assertEquals("st-memory-enhancement", plan?.folderName)
    }

    // ========== zip 直链（ZIP） ==========

    @Test
    fun `zip direct link`() {
        val plan = planUrlInstall("https://example.com/ext/foo.zip")
        assertEquals(StUrlKind.ZIP, plan?.kind)
        assertEquals(listOf("https://example.com/ext/foo.zip"), plan?.zipUrls)
    }

    @Test
    fun `github release asset is zip`() {
        val plan = planUrlInstall("https://github.com/user/repo/releases/download/v1.0/ext.zip")
        assertEquals(StUrlKind.ZIP, plan?.kind)
        assertEquals("repo", plan?.folderName)
    }

    @Test
    fun `codeload and archive endpoints are zip`() {
        assertEquals(StUrlKind.ZIP, planUrlInstall("https://codeload.github.com/o/r/zip/refs/heads/main")?.kind)
        assertEquals(StUrlKind.ZIP, planUrlInstall("https://github.com/o/r/archive/refs/heads/main.zip")?.kind)
        assertEquals(StUrlKind.ZIP, planUrlInstall("https://gitlab.com/g/r/-/archive/main/r-main.zip")?.kind)
    }

    // ========== 非法输入 ==========

    @Test
    fun `invalid inputs are rejected`() {
        assertNull(planUrlInstall("ftp://github.com/a/b"))
        assertNull(planUrlInstall("https://github.com/only-one-segment"))
        assertNull(planUrlInstall("not a url"))
    }

    // ========== manifest 资源清单 ==========

    @Test
    fun `collect manifest resources covers js css and i18n`() {
        val manifest = """
            {
              "display_name": "酒馆助手",
              "js": "dist/index.js",
              "css": "dist/index.css",
              "i18n": {"en": "i18n/en.json"}
            }
        """.trimIndent()
        assertEquals(
            listOf("dist/index.js", "dist/index.css", "i18n/en.json"),
            collectManifestResources(manifest)
        )
    }

    @Test
    fun `collect manifest resources skips absolute urls`() {
        val manifest = """{"js": "https://cdn.example.com/x.js", "css": "style.css"}"""
        assertEquals(listOf("style.css"), collectManifestResources(manifest))
    }

    // ========== JS 相对导入提取（源码型扩展必需） ==========

    @Test
    fun `extract relative imports from js`() {
        val js = """
            import { a } from './core/manager.js';
            import b from "./scripts/settings/load.js";
            export { c } from '../shared/util.js';
            const lazy = import('./plugins/x.mjs');
            const remote = import('https://cdn.example.com/y.js');
            const notAnImport = './decoy.js';
        """.trimIndent()
        val imports = extractRelativeImports(js)
        assertTrue(imports.contains("./core/manager.js"))
        assertTrue(imports.contains("./scripts/settings/load.js"))
        assertTrue(imports.contains("../shared/util.js"))
        assertTrue(imports.contains("./plugins/x.mjs"))
        // 绝对 URL 不抓
        assertFalse(imports.any { it.contains("://") })
    }

    // ========== 相对路径归一化 ==========

    @Test
    fun `normalize rel path resolves parents`() {
        assertEquals("dist/index.js", normalizeRelPath("", "./dist/index.js"))
        assertEquals("core/x.js", normalizeRelPath("dist", "../core/x.js"))
        assertEquals("a/b/c.js", normalizeRelPath("a", "./b/c.js"))
    }

    @Test
    fun `normalize rel path rejects escaping root`() {
        assertNull(normalizeRelPath("", "../../etc/passwd"))
        assertNull(normalizeRelPath("dist", "../../escape.js"))
    }
}