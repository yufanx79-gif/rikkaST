import json
import os
import re
import sqlite3
import sys

repo = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
schema_dir = os.path.join(repo, "app/schemas/me.rerere.rikkahub.data.db.AppDatabase")
migrations_dir = os.path.join(repo, "app/src/main/java/me/rerere/rikkahub/data/db/migrations")

with open(os.path.join(schema_dir, "32.json"), encoding="utf-8") as f:
    s32 = json.load(f)["database"]

expected_tables = {}
expected_indices = {}
for ent in s32["entities"]:
    tname = ent["tableName"]
    cols = {f["columnName"]: (f["affinity"], bool(f.get("notNull", False))) for f in ent["fields"]}
    expected_tables[tname] = cols
    expected_indices[tname] = {idx["name"] for idx in ent.get("indices", [])}


def extract_kotlin_migration_sql(kt_filename: str) -> list[str]:
    return extract_kotlin_execsql_statements(kt_filename)


def extract_kotlin_execsql_statements(kt_filename: str) -> list[str]:
    """
    Parses actual db.execSQL(...) string literals (including multi-line concatenated or raw strings)
    from a Kotlin Room Migration file so host SQLite tests execute the exact SQL authored in Kotlin.
    """
    path = os.path.join(migrations_dir, kt_filename)
    with open(path, encoding="utf-8") as f:
        source = f.read()

    statements = []
    pos = 0
    while True:
        idx = source.find("db.execSQL(", pos)
        if idx == -1:
            break
        cursor = idx + len("db.execSQL(")
        depth = 1
        in_str = False
        triple = False
        escaped = False
        start_arg = cursor
        while cursor < len(source) and depth > 0:
            ch = source[cursor]
            if in_str:
                if triple:
                    if source[cursor:cursor + 3] == '"""':
                        in_str = False
                        triple = False
                        cursor += 3
                        continue
                else:
                    if escaped:
                        escaped = False
                    elif ch == "\\":
                        escaped = True
                    elif ch == '"':
                        in_str = False
            else:
                if source[cursor:cursor + 3] == '"""':
                    in_str = True
                    triple = True
                    cursor += 3
                    continue
                elif ch == '"':
                    in_str = True
                elif ch == "(":
                    depth += 1
                elif ch == ")":
                    depth -= 1
                    if depth == 0:
                        break
            cursor += 1
        arg_block = source[start_arg:cursor]
        pos = cursor + 1

        # Skip parameterized statements that pass arrayOf(...) as second argument
        if "arrayOf" in arg_block:
            continue

        # Extract either triple-quoted raw strings or concatenated double-quoted strings
        triple_match = re.search(r'"""([\s\S]*?)"""', arg_block)
        if triple_match:
            sql = triple_match.group(1).strip()
            if sql:
                statements.append(sql)
        else:
            parts = re.findall(r'"((?:[^"\\]|\\.)*)"', arg_block)
            if parts:
                sql = "".join(p.encode("utf-8").decode("unicode_escape") for p in parts).strip()
                if sql:
                    statements.append(sql)
    return statements


SQL_28_29 = extract_kotlin_execsql_statements("Migration_28_29.kt")
SQL_29_32 = extract_kotlin_execsql_statements("Migration_29_32.kt")
SQL_30_31 = extract_kotlin_execsql_statements("Migration_30_31.kt")

assert len(SQL_28_29) == 1, f"Expected 1 SQL statement in Migration_28_29.kt, got {len(SQL_28_29)}"
assert len(SQL_29_32) >= 21, f"Expected >=21 SQL statements in Migration_29_32.kt, got {len(SQL_29_32)}"
assert len(SQL_30_31) == 3, f"Expected 3 SQL statements in Migration_30_31.kt, got {len(SQL_30_31)}"


def create_schema_db(ver: int) -> sqlite3.Connection:
    with open(os.path.join(schema_dir, f"{ver}.json"), encoding="utf-8") as f:
        s = json.load(f)["database"]
    conn = sqlite3.connect(":memory:")
    conn.execute("PRAGMA foreign_keys = ON")
    for ent in s["entities"]:
        tname = ent["tableName"]
        sql = ent["createSql"].replace("${TABLE_NAME}", tname)
        conn.execute(sql)
        for idx in ent.get("indices", []):
            isql = idx["createSql"].replace("${TABLE_NAME}", tname)
            conn.execute(isql)
    return conn


def has_column(conn: sqlite3.Connection, table: str, col: str) -> bool:
    cur = conn.execute(f"PRAGMA table_info('{table}')")
    return any(r[1] == col for r in cur.fetchall())


def run_migration_28_29(conn: sqlite3.Connection) -> None:
    if not has_column(conn, "workspaces", "shell_compatibility_mode"):
        conn.execute(SQL_28_29[0])


def run_migration_30_31(conn: sqlite3.Connection) -> None:
    for sql in SQL_30_31:
        conn.execute(sql)


def run_upgrade_to_32(conn: sqlite3.Connection) -> None:
    for sql in SQL_29_32:
        if sql.startswith("ALTER TABLE"):
            m = re.search(r"ALTER TABLE `([^`]+)` ADD COLUMN `([^`]+)`", sql)
            if m and has_column(conn, m.group(1), m.group(2)):
                continue
        if sql.lstrip().startswith("INSERT INTO usage_record"):
            cnt = conn.execute("SELECT COUNT(*) FROM usage_record").fetchone()[0]
            if cnt > 0:
                continue
        conn.execute(sql)

    # Also execute the parameterized MemoryEntity -> memory_file backfill from Migration_29_32.kt
    cur = conn.execute("SELECT assistant_id, content FROM MemoryEntity ORDER BY id")
    memories = {}
    for aid, content in cur.fetchall():
        memories.setdefault(aid, []).append(content)
    for aid, items in memories.items():
        lines = [" ".join(line.strip() for line in x.splitlines()).strip() for x in items]
        lines = [line for line in lines if line]
        if lines:
            profile = (
                "---\nname: profile\ndescription: Who the user is\n---\n\n"
                + "\n".join(f"- [stated] {line}" for line in lines)
                + "\n"
            )
            conn.execute(
                "INSERT OR IGNORE INTO memory_file (assistant_id, path, content, updated_at) VALUES (?, ?, ?, ?)",
                (aid, "profile.md", profile, 1700000000000),
            )


def verify_against_32(conn: sqlite3.Connection, path_label: str) -> None:
    for tname, exp_cols in expected_tables.items():
        rows = conn.execute(f"PRAGMA table_info('{tname}')").fetchall()
        assert rows, f"[{path_label}] Missing table {tname}"
        actual_cols = {r[1]: (r[2], bool(r[3])) for r in rows}
        assert actual_cols == exp_cols, f"[{path_label}] Column mismatch on {tname}: actual={actual_cols} vs expected={exp_cols}"
        idx_rows = conn.execute(f"PRAGMA index_list('{tname}')").fetchall()
        actual_idx = {r[1] for r in idx_rows if not r[1].startswith("sqlite_autoindex")}
        assert expected_indices[tname].issubset(actual_idx), (
            f"[{path_label}] Missing indices on {tname}: expected {expected_indices[tname]}, got {actual_idx}"
        )

    fk_errors = conn.execute("PRAGMA foreign_key_check").fetchall()
    assert not fk_errors, f"[{path_label}] PRAGMA foreign_key_check failed: {fk_errors}"
    integrity = conn.execute("PRAGMA integrity_check").fetchone()[0]
    assert integrity == "ok", f"[{path_label}] PRAGMA integrity_check failed: {integrity}"


sample_messages_json = json.dumps([
    {
        "role": "assistant",
        "modelId": "gpt-4o",
        "createdAt": "2026-10-20T12:00:00Z",
        "usage": {"promptTokens": 120, "completionTokens": 45, "cachedTokens": 10},
    }
])

migration_paths = [
    ("28->29->32", 28, [run_migration_28_29, run_upgrade_to_32]),
    ("29->32", 29, [run_upgrade_to_32]),
    ("30->31->32", 30, [run_migration_30_31, run_upgrade_to_32]),
    ("30->32", 30, [run_upgrade_to_32]),
    ("31->32", 31, [run_upgrade_to_32]),
]

verified_labels = []
for path_label, start_ver, steps in migration_paths:
    conn = create_schema_db(start_ver)
    if start_ver in (28, 29):
        conn.execute(
            "INSERT INTO ConversationEntity (id, assistant_id, title, nodes, create_at, update_at, st_parent_conversation_id) "
            "VALUES ('c1', 'a1', 'Legacy Chat', '[]', 1000, 2000, 'parent-conv-1')"
        )
        conn.execute(
            "INSERT INTO knowledge_sources (id, name, type, assistant_id, file_path, file_size, chunk_count, created_at, tags) "
            "VALUES ('k1', 'Legacy Knowledge', 'FILE', 'a1', '/tmp/doc.txt', 512, 1, 1000, 'tag1')"
        )
        conn.execute("INSERT INTO MemoryEntity (assistant_id, content) VALUES ('a1', 'Likes coffee')")
    else:
        conn.execute(
            "INSERT INTO ConversationEntity (id, assistant_id, title, nodes, create_at, update_at) "
            "VALUES ('c1', 'a1', 'Legacy Chat', '[]', 1000, 2000)"
        )
    conn.execute(
        "INSERT INTO message_node (id, conversation_id, node_index, messages, select_index) VALUES (?, ?, ?, ?, ?)",
        ("n1", "c1", 0, sample_messages_json, 0),
    )

    for step_fn in steps:
        step_fn(conn)

    verify_against_32(conn, path_label)

    conv_row = conn.execute("SELECT title, st_parent_conversation_id, config FROM ConversationEntity WHERE id='c1'").fetchone()
    assert conv_row[0] == "Legacy Chat"
    if start_ver in (28, 29):
        assert conv_row[1] == "parent-conv-1"
        assert conn.execute("SELECT COUNT(*) FROM knowledge_sources WHERE id='k1'").fetchone()[0] == 1
        assert conn.execute("SELECT COUNT(*) FROM MemoryEntity WHERE assistant_id='a1'").fetchone()[0] == 1
        assert conn.execute("SELECT COUNT(*) FROM memory_file WHERE assistant_id='a1'").fetchone()[0] == 1
    assert conv_row[2] == ""
    assert conn.execute("SELECT consolidated_at FROM memory_consolidation WHERE conversation_id='c1'").fetchone()[0] == 2000
    usage_rows = conn.execute(
        "SELECT prompt_tokens, completion_tokens, cached_tokens FROM usage_record WHERE conversation_id='c1'"
    ).fetchall()
    assert usage_rows == [(120, 45, 10)], f"Unexpected usage_rows for {path_label}: {usage_rows}"
    conn.close()
    verified_labels.append(path_label)


def test_idempotency_fts_and_crud():
    conn = create_schema_db(29)
    conn.execute(
        "INSERT INTO ConversationEntity (id, assistant_id, title, nodes, create_at, update_at, st_parent_conversation_id) "
        "VALUES ('c1', 'a1', 'Legacy Chat', '[]', 1000, 2000, 'parent-conv-1')"
    )
    conn.execute("INSERT INTO MemoryEntity (assistant_id, content) VALUES ('a1', 'Likes coffee')")
    conn.execute(
        "INSERT INTO message_node (id, conversation_id, node_index, messages, select_index) VALUES (?, ?, ?, ?, ?)",
        ("n1", "c1", 0, sample_messages_json, 0),
    )
    run_upgrade_to_32(conn)
    # Run a second time to verify idempotency (no duplicate usage_record or memory_file rows)
    run_upgrade_to_32(conn)
    verify_against_32(conn, "29->32-idempotent")

    assert conn.execute("SELECT COUNT(*) FROM usage_record WHERE conversation_id='c1'").fetchone()[0] == 1
    assert conn.execute("SELECT COUNT(*) FROM memory_file WHERE assistant_id='a1'").fetchone()[0] == 1

    # Verify FTS5 virtual tables (knowledge_fts and message_fts)
    conn.execute(
        "CREATE VIRTUAL TABLE IF NOT EXISTS `knowledge_fts` USING fts5(`text`, `chunk_id` UNINDEXED, `source_id` UNINDEXED, tokenize='unicode61')"
    )
    conn.execute(
        "INSERT INTO knowledge_fts (text, chunk_id, source_id) VALUES ('SillyTavern regex parity guide', 'chk-1', 'k1')"
    )
    fts_hits = conn.execute("SELECT chunk_id FROM knowledge_fts WHERE knowledge_fts MATCH 'SillyTavern'").fetchall()
    assert fts_hits == [("chk-1",)], f"Unexpected FTS hits: {fts_hits}"

    # Verify post-upgrade CRUD across newly added 3.1.0 tables
    conn.execute(
        "INSERT INTO media_creation_session (id, title, draft, create_at, update_at) VALUES ('ms1', 'Draw Cat', '{}', 100, 200)"
    )
    conn.execute(
        "INSERT INTO media_creation_node (id, session_id, selected_record_id, create_at) VALUES ('mn1', 'ms1', 'mr1', 100)"
    )
    conn.execute(
        "INSERT INTO media_creation_record (id, session_id, node_id, provider_id, provider_name, model_id, kind, prompt, params, inputs, status, task_id, error, outputs, create_at, update_at) "
        "VALUES ('mr1', 'ms1', 'mn1', 'p1', 'OpenAI', 'gpt-image-1', 'IMAGE', 'A cat', '{}', '[]', 'SUCCESS', NULL, NULL, '[]', 100, 200)"
    )
    conn.execute(
        "INSERT INTO translation_history (id, source_text, translated_text, target_language, model_id, create_at) "
        "VALUES ('th1', 'Hello', '你好', 'zh-CN', 'gpt-4o', 100)"
    )
    verify_against_32(conn, "29->32-post-crud")
    conn.close()



def test_interrupted_migration_recovery():
    # 1. Verify empty DB migrations across all 5 supported paths
    for path_label, start_ver, steps in migration_paths:
        conn = create_schema_db(start_ver)
        for step_fn in steps:
            step_fn(conn)
        verify_against_32(conn, f"empty:{path_label}")
        conn.close()

    # 2. Verify multi-swipe usage_record backfill and GenMediaEntity preservation
    conn = create_schema_db(29)
    conn.execute(
        "INSERT INTO ConversationEntity (id, assistant_id, title, nodes, create_at, update_at, st_parent_conversation_id) "
        "VALUES ('c-multi', 'a1', 'Multi Swipe Chat', '[]', 1000, 2000, 'parent-conv-1')"
    )
    conn.execute("INSERT INTO MemoryEntity (assistant_id, content) VALUES ('a1', 'Likes coffee\nand tea')")
    two_swipes_json = json.dumps([
        {
            "role": "assistant",
            "modelId": "gpt-4o",
            "modelSnapshot": {"modelId": "gpt-4o-2024-11-20"},
            "createdAt": "2026-10-20T12:00:00Z",
            "parts": [{"type": "text", "text": "Swipe 0"}],
            "usage": {"promptTokens": 120, "completionTokens": 45, "cachedTokens": 10},
        },
        {
            "role": "assistant",
            "modelId": "claude-3-7-sonnet",
            "createdAt": "2026-10-20T12:01:00Z",
            "parts": [{"type": "text", "text": "Swipe 1"}],
            "usage": {"promptTokens": 80, "completionTokens": 30, "cachedTokens": 0},
        },
    ])
    conn.execute(
        "INSERT INTO message_node (id, conversation_id, node_index, messages, select_index) VALUES (?, ?, ?, ?, ?)",
        ("n-multi", "c-multi", 0, two_swipes_json, 1),
    )
    conn.execute(
        "INSERT INTO GenMediaEntity (id, path, model_id, prompt, create_at, type, source_paths) "
        "VALUES (1, 'images/test.png', 'gpt-image-1', '1girl', 1500, 'image_generation', NULL)"
    )
    run_upgrade_to_32(conn)
    verify_against_32(conn, "29->32-multi-swipe")
    usage_rows = conn.execute(
        "SELECT prompt_tokens, completion_tokens, cached_tokens FROM usage_record WHERE conversation_id='c-multi' ORDER BY prompt_tokens DESC"
    ).fetchall()
    assert usage_rows == [(120, 45, 10), (80, 30, 0)], f"Unexpected multi-swipe usage_rows: {usage_rows}"
    mem_file = conn.execute("SELECT content FROM memory_file WHERE assistant_id='a1' AND path='profile.md'").fetchone()
    assert mem_file and "- [stated] Likes coffee and tea" in mem_file[0]
    assert conn.execute("SELECT COUNT(*) FROM GenMediaEntity WHERE id=1").fetchone()[0] == 1
    conn.close()

    # 3. Verify interrupted migration recovery: simulate partial migration state (some ALTER TABLE and CREATE TABLE already ran before crash/abort)
    conn = create_schema_db(29)
    conn.execute(
        "INSERT INTO ConversationEntity (id, assistant_id, title, nodes, create_at, update_at, st_parent_conversation_id) "
        "VALUES ('c-recover', 'a1', 'Interrupted Upgrade Chat', '[]', 1000, 2000, 'parent-conv-1')"
    )
    conn.execute("INSERT INTO MemoryEntity (assistant_id, content) VALUES ('a1', 'Resilient memory')")
    conn.execute(
        "INSERT INTO message_node (id, conversation_id, node_index, messages, select_index) VALUES (?, ?, ?, ?, ?)",
        ("n-rec", "c-recover", 0, sample_messages_json, 0),
    )
    conn.execute("ALTER TABLE `ConversationEntity` ADD COLUMN `config` TEXT NOT NULL DEFAULT ''")
    conn.execute(
        "CREATE TABLE IF NOT EXISTS `memory_file` (`assistant_id` TEXT NOT NULL, `path` TEXT NOT NULL, "
        "`content` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`assistant_id`, `path`))"
    )
    conn.execute(
        "CREATE TABLE IF NOT EXISTS `memory_consolidation` (`conversation_id` TEXT NOT NULL, "
        "`consolidated_at` INTEGER NOT NULL, PRIMARY KEY(`conversation_id`), "
        "FOREIGN KEY(`conversation_id`) REFERENCES `ConversationEntity`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
    )
    conn.execute("INSERT OR IGNORE INTO memory_consolidation (conversation_id, consolidated_at) SELECT id, update_at FROM ConversationEntity")
    run_upgrade_to_32(conn)
    verify_against_32(conn, "29->32-interrupted-recovery")
    assert conn.execute("SELECT COUNT(*) FROM usage_record WHERE conversation_id='c-recover'").fetchone()[0] == 1
    assert conn.execute("SELECT COUNT(*) FROM memory_file WHERE assistant_id='a1'").fetchone()[0] == 1
    assert conn.execute("SELECT COUNT(*) FROM memory_consolidation WHERE conversation_id='c-recover'").fetchone()[0] == 1
    conn.close()
    print("INTERRUPTED_MIGRATION_RECOVERY_VERIFIED")

test_idempotency_fts_and_crud()
test_interrupted_migration_recovery()
print("KOTLIN_MIGRATION_SQL_PARSED_AND_VERIFIED")
print("PATHS_VERIFIED=" + ",".join(verified_labels))
print("SQLITE_MIGRATION_VERIFIED_OK")
