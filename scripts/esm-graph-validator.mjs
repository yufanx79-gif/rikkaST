import fs from 'node:fs';
import path from 'node:path';

/**
 * Strip JS comments and string literals (preserving module specifiers in import/export statements
 * via targeted parsing before stripping).
 */
export function stripComments(code) {
    let out = '';
    let i = 0;
    const len = code.length;
    let state = 'code'; // code | single | double | template | line_comment | block_comment
    while (i < len) {
        const ch = code[i];
        const next = code[i + 1];
        if (state === 'code') {
            if (ch === '/' && next === '/') {
                state = 'line_comment';
                out += '  ';
                i += 2;
                continue;
            }
            if (ch === '/' && next === '*') {
                state = 'block_comment';
                out += '  ';
                i += 2;
                continue;
            }
            if (ch === "'") { state = 'single'; out += ch; i++; continue; }
            if (ch === '"') { state = 'double'; out += ch; i++; continue; }
            if (ch === '`') { state = 'template'; out += ch; i++; continue; }
            out += ch;
            i++;
        } else if (state === 'line_comment') {
            if (ch === '\n') { state = 'code'; out += '\n'; }
            else { out += ' '; }
            i++;
        } else if (state === 'block_comment') {
            if (ch === '*' && next === '/') { state = 'code'; out += '  '; i += 2; }
            else { out += (ch === '\n' ? '\n' : ' '); i++; }
        } else if (state === 'single') {
            out += ch;
            if (ch === '\\') { if (i + 1 < len) out += code[i + 1]; i += 2; continue; }
            if (ch === "'") state = 'code';
            i++;
        } else if (state === 'double') {
            out += ch;
            if (ch === '\\') { if (i + 1 < len) out += code[i + 1]; i += 2; continue; }
            if (ch === '"') state = 'code';
            i++;
        } else if (state === 'template') {
            out += ch;
            if (ch === '\\') { if (i + 1 < len) out += code[i + 1]; i += 2; continue; }
            if (ch === '`') state = 'code';
            i++;
        }
    }
    return out;
}

function lineNumberAt(text, index) {
    let line = 1;
    for (let i = 0; i < index && i < text.length; i++) {
        if (text.charCodeAt(i) === 10) line++;
    }
    return line;
}

export function parseEsmModule(filePath) {
    const raw = fs.readFileSync(filePath, 'utf8');
    const clean = stripComments(raw);
    const imports = [];
    const directExports = new Set();
    const reExports = []; // { specifier, names: [{ imported, exported }] | '*', line }
    let hasDefaultExport = false;

    // 1. Static imports: import ... from '...' or import '...'
    const importRe = /\bimport\s+(?:([\s\S]*?)\s+from\s+)?['"]([^'"]+)['"]/g;
    let m;
    while ((m = importRe.exec(clean)) !== null) {
        // Ensure 'import' is not followed immediately by '(' (dynamic import)
        const clause = (m[1] || '').trim();
        const specifier = m[2];
        const line = lineNumberAt(raw, m.index);
        if (clause.startsWith('(')) continue;
        const named = [];
        let defaultImport = null;
        let namespaceImport = null;

        if (clause) {
            const braceMatch = clause.match(/\{([\s\S]*?)\}/);
            if (braceMatch) {
                const items = braceMatch[1].split(',').map(s => s.trim()).filter(Boolean);
                for (const item of items) {
                    const parts = item.split(/\s+as\s+/);
                    const imported = parts[0].trim();
                    const local = (parts[1] || parts[0]).trim();
                    if (imported) named.push({ imported, local });
                }
            }
            const nsMatch = clause.match(/\*\s+as\s+([A-Za-z0-9_$]+)/);
            if (nsMatch) {
                namespaceImport = nsMatch[1];
            }
            const beforeCommaOrBrace = clause.split(/[,\{]/)[0].trim();
            if (beforeCommaOrBrace && !beforeCommaOrBrace.startsWith('*') && !beforeCommaOrBrace.startsWith('{')) {
                defaultImport = beforeCommaOrBrace;
            }
        }
        imports.push({ type: 'static', specifier, named, defaultImport, namespaceImport, line });
    }

    // 2. Dynamic imports with literal string: import('...')
    const dynImportRe = /\bimport\s*\(\s*['"]([^'"]+)['"]\s*\)/g;
    while ((m = dynImportRe.exec(clean)) !== null) {
        imports.push({
            type: 'dynamic',
            specifier: m[1],
            named: [],
            defaultImport: null,
            namespaceImport: null,
            line: lineNumberAt(raw, m.index),
        });
    }

    // 3. Exports
    // 3a. export default
    if (/\bexport\s+default\b/.test(clean)) {
        hasDefaultExport = true;
        directExports.add('default');
    }

    // 3b. export const/let/var/function/class/async function
    const declRe = /\bexport\s+(?:async\s+)?(?:function\s*\*?\s*|class\s+|const\s+|let\s+|var\s+)([A-Za-z0-9_$]+)/g;
    while ((m = declRe.exec(clean)) !== null) {
        directExports.add(m[1]);
    }
    // Also handle comma-separated export const a = 1, b = 2; or destructuring export const { a, b } = ...
    const destructExportRe = /\bexport\s+(?:const|let|var)\s+\{([^}]+)\}/g;
    while ((m = destructExportRe.exec(clean)) !== null) {
        for (const part of m[1].split(',')) {
            const p = part.trim();
            if (!p) continue;
            const colonParts = p.split(':');
            const name = (colonParts[1] || colonParts[0]).split('=')[0].trim();
            if (/^[A-Za-z0-9_$]+$/.test(name)) directExports.add(name);
        }
    }

    // 3c. export * from '...' or export * as ns from '...'
    const exportAllRe = /\bexport\s+\*\s*(?:as\s+([A-Za-z0-9_$]+)\s+)?from\s+['"]([^'"]+)['"]/g;
    while ((m = exportAllRe.exec(clean)) !== null) {
        const line = lineNumberAt(raw, m.index);
        if (m[1]) {
            directExports.add(m[1]);
            reExports.push({ specifier: m[2], names: [], starNamespace: m[1], line });
        } else {
            reExports.push({ specifier: m[2], names: '*', line });
        }
    }

    // 3d. export { ... } [from '...']
    const exportNamedRe = /\bexport\s*\{([\s\S]*?)\}(?:\s*from\s*['"]([^'"]+)['"])?/g;
    while ((m = exportNamedRe.exec(clean)) !== null) {
        const listStr = m[1];
        const fromSpec = m[2] || null;
        const line = lineNumberAt(raw, m.index);
        const pairs = [];
        for (const item of listStr.split(',')) {
            const trimmed = item.trim();
            if (!trimmed) continue;
            const parts = trimmed.split(/\s+as\s+/);
            const orig = parts[0].trim();
            const exported = (parts[1] || parts[0]).trim();
            if (exported === 'default') hasDefaultExport = true;
            if (fromSpec) {
                pairs.push({ imported: orig, exported });
            } else {
                directExports.add(exported);
            }
        }
        if (fromSpec) {
            reExports.push({ specifier: fromSpec, names: pairs, line });
        }
    }

    return { filePath, imports, directExports, reExports, hasDefaultExport };
}

/**
 * Verify exact case-sensitive filename match on disk.
 */
export function existsExactCase(targetPath, baseRoot = process.cwd()) {
    if (!fs.existsSync(targetPath)) return { exists: false, reason: 'missing' };
    const resolved = path.resolve(targetPath);
    const resolvedBase = baseRoot ? path.resolve(baseRoot) : null;
    let cur = path.sep;
    let parts = resolved.split(path.sep).filter(Boolean);
    if (resolvedBase && (resolved === resolvedBase || resolved.startsWith(resolvedBase + path.sep)) && fs.existsSync(resolvedBase)) {
        cur = resolvedBase;
        const rel = path.relative(resolvedBase, resolved);
        parts = rel ? rel.split(path.sep).filter(Boolean) : [];
    }
    for (const part of parts) {
        const entries = fs.readdirSync(cur);
        if (!entries.includes(part)) {
            const ci = entries.find(e => e.toLowerCase() === part.toLowerCase());
            return { exists: false, reason: ci ? `case_mismatch (found '${ci}', expected '${part}')` : 'missing' };
        }
        cur = path.join(cur, part);
    }
    const stat = fs.statSync(resolved);
    return { exists: stat.isFile(), reason: stat.isFile() ? 'ok' : 'not_a_file' };
}

/**
 * Resolve specifier within st-runtime (supports relative ./ ../ and root-mapped /script.js, /scripts/...).
 */
export function resolveStRuntimeSpecifier(fromFile, specifier, stRuntimeRoot) {
    if (specifier.startsWith('./') || specifier.startsWith('../')) {
        return path.resolve(path.dirname(fromFile), specifier);
    }
    if (specifier === '/script.js') {
        return path.join(stRuntimeRoot, 'st-compat', 'script.js');
    }
    if (specifier === '/lib.js') {
        return path.join(stRuntimeRoot, 'st-compat', 'lib.js');
    }
    if (specifier.startsWith('/scripts/')) {
        return path.join(stRuntimeRoot, 'st-compat', specifier.slice('/scripts/'.length));
    }
    if (specifier.startsWith('/st-runtime/')) {
        return path.join(stRuntimeRoot, specifier.slice('/st-runtime/'.length));
    }
    return null;
}

export function scanStRuntimeGraph(stRuntimeRoot) {
    const parsedCache = new Map();
    const errors = [];

    function getParsed(absPath) {
        if (!parsedCache.has(absPath)) {
            parsedCache.set(absPath, parseEsmModule(absPath));
        }
        return parsedCache.get(absPath);
    }

    function getEffectiveExports(absPath, visited = new Set()) {
        if (visited.has(absPath)) return new Set();
        visited.add(absPath);
        const mod = getParsed(absPath);
        const out = new Set(mod.directExports);
        for (const re of mod.reExports) {
            const target = resolveStRuntimeSpecifier(absPath, re.specifier, stRuntimeRoot);
            if (!target) continue;
            const check = existsExactCase(target);
            if (!check.exists) continue;
            const targetExports = getEffectiveExports(target, new Set(visited));
            if (re.names === '*') {
                for (const exp of targetExports) {
                    if (exp !== 'default') out.add(exp);
                }
            } else if (Array.isArray(re.names)) {
                for (const { imported, exported } of re.names) {
                    if (imported === '*' || targetExports.has(imported)) {
                        out.add(exported);
                    }
                }
            }
        }
        return out;
    }

    // Collect HTML entrypoints and all .js/.mjs files under st-runtime (excluding node-host CommonJS and mvu/bundle.js)
    const htmlEntries = ['index.html', 'panel.html', 'extension-settings.html']
        .map(f => path.join(stRuntimeRoot, f))
        .filter(f => fs.existsSync(f));

    for (const htmlFile of htmlEntries) {
        const html = fs.readFileSync(htmlFile, 'utf8');
        const scriptRe = /<script\b[^>]*\bsrc=['"]([^'"]+)['"][^>]*>/gi;
        let sm;
        while ((sm = scriptRe.exec(html)) !== null) {
            const src = sm[1];
            const target = resolveStRuntimeSpecifier(htmlFile, src, stRuntimeRoot);
            if (target) {
                const check = existsExactCase(target);
                if (!check.exists) {
                    errors.push({
                        kind: 'missing_html_script',
                        file: path.relative(stRuntimeRoot, htmlFile),
                        line: lineNumberAt(html, sm.index),
                        specifier: src,
                        reason: check.reason,
                    });
                }
            }
        }
    }

    function walkJsFiles(dir, list = []) {
        for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
            const full = path.join(dir, entry.name);
            const rel = path.relative(stRuntimeRoot, full);
            if (entry.isDirectory()) {
                if (rel === 'node-host' || rel === 'mvu' || rel.startsWith('vendor/katex/fonts') || rel.startsWith('vendor/data')) continue;
                walkJsFiles(full, list);
            } else if (entry.isFile()) {
                if (rel.startsWith('vendor/lib/') && !entry.name.endsWith('.esm.js')) continue;
                if (entry.name === 'jquery.nanogallery2.min.js') continue;
                if (entry.name.endsWith('.js') || entry.name.endsWith('.mjs') || entry.name === '+esm') {
                    list.push(full);
                }
            }
        }
        return list;
    }

    const allModuleFiles = walkJsFiles(stRuntimeRoot);
    for (const modFile of allModuleFiles) {
        const mod = getParsed(modFile);
        const relFrom = path.relative(stRuntimeRoot, modFile);

        for (const imp of mod.imports) {
            const target = resolveStRuntimeSpecifier(modFile, imp.specifier, stRuntimeRoot);
            if (!target) continue;
            const check = existsExactCase(target);
            if (!check.exists) {
                errors.push({
                    kind: 'missing_import_file',
                    file: relFrom,
                    line: imp.line,
                    specifier: imp.specifier,
                    reason: check.reason,
                });
                continue;
            }
            const targetExports = getEffectiveExports(target);
            if (imp.defaultImport && !targetExports.has('default')) {
                errors.push({
                    kind: 'missing_default_export',
                    file: relFrom,
                    line: imp.line,
                    specifier: imp.specifier,
                    exportName: 'default',
                    localName: imp.defaultImport,
                });
            }
            for (const { imported } of imp.named) {
                if (!targetExports.has(imported)) {
                    errors.push({
                        kind: 'missing_named_export',
                        file: relFrom,
                        line: imp.line,
                        specifier: imp.specifier,
                        exportName: imported,
                        availableExports: [...targetExports].sort(),
                    });
                }
            }
        }

        for (const re of mod.reExports) {
            const target = resolveStRuntimeSpecifier(modFile, re.specifier, stRuntimeRoot);
            if (!target) continue;
            const check = existsExactCase(target);
            if (!check.exists) {
                errors.push({
                    kind: 'missing_reexport_file',
                    file: relFrom,
                    line: re.line,
                    specifier: re.specifier,
                    reason: check.reason,
                });
                continue;
            }
            if (Array.isArray(re.names)) {
                const targetExports = getEffectiveExports(target);
                for (const { imported } of re.names) {
                    if (imported !== '*' && !targetExports.has(imported)) {
                        errors.push({
                            kind: 'missing_reexport_symbol',
                            file: relFrom,
                            line: re.line,
                            specifier: re.specifier,
                            exportName: imported,
                            availableExports: [...targetExports].sort(),
                        });
                    }
                }
            }
        }
    }

    return {
        scannedFilesCount: allModuleFiles.length,
        htmlEntriesCount: htmlEntries.length,
        errors,
    };
}
