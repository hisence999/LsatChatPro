#!/usr/bin/env python3
"""
EXHAUSTIVE comparison of Example database vs Converted database.
Check EVERYTHING: schema, pragmas, metadata, data format, etc.
"""

import sqlite3
import json
import sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

EXAMPLE_DB = "The conversion test/example_extracted/rikka_hub.db"
CONVERTED_DB = "The conversion test/converted/rikka_hub.db"

def full_compare():
    print("="*70)
    print("EXHAUSTIVE DATABASE COMPARISON")
    print("="*70)
    
    ex_conn = sqlite3.connect(EXAMPLE_DB)
    cv_conn = sqlite3.connect(CONVERTED_DB)
    
    # 1. All PRAGMAs
    print("\n1. PRAGMA COMPARISON:")
    pragmas = [
        "user_version", "schema_version", "journal_mode", 
        "auto_vacuum", "page_size", "encoding", "foreign_keys",
        "synchronous", "cache_size", "application_id"
    ]
    for p in pragmas:
        ex_val = ex_conn.execute(f"PRAGMA {p}").fetchone()[0]
        cv_val = cv_conn.execute(f"PRAGMA {p}").fetchone()[0]
        match = "[OK]" if ex_val == cv_val else "[!!!]"
        print(f"  {match} {p}: example={ex_val}, converted={cv_val}")
    
    # 2. Room master table
    print("\n2. ROOM_MASTER_TABLE:")
    ex_room = ex_conn.execute("SELECT * FROM room_master_table").fetchall()
    cv_room = cv_conn.execute("SELECT * FROM room_master_table").fetchall()
    print(f"  Example: {ex_room}")
    print(f"  Converted: {cv_room}")
    print(f"  {'[OK]' if ex_room == cv_room else '[!!!] MISMATCH'}")
    
    # 3. All tables and exact CREATE statements
    print("\n3. TABLE SCHEMAS (character-by-character):")
    ex_tables = {r[0]: r[1] for r in ex_conn.execute(
        "SELECT name, sql FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name"
    ).fetchall()}
    cv_tables = {r[0]: r[1] for r in cv_conn.execute(
        "SELECT name, sql FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name"
    ).fetchall()}
    
    all_tables = set(ex_tables.keys()) | set(cv_tables.keys())
    for table in sorted(all_tables):
        ex_sql = ex_tables.get(table)
        cv_sql = cv_tables.get(table)
        
        if ex_sql == cv_sql:
            print(f"  [OK] {table}")
        elif ex_sql is None:
            print(f"  [!!!] {table}: MISSING IN EXAMPLE")
        elif cv_sql is None:
            print(f"  [!!!] {table}: MISSING IN CONVERTED")
        else:
            print(f"  [!!!] {table}: SCHEMA DIFFERS")
            print(f"       Example: {ex_sql[:100]}...")
            print(f"       Converted: {cv_sql[:100]}...")
    
    # 4. All indices
    print("\n4. INDICES:")
    ex_idx = {r[0] for r in ex_conn.execute(
        "SELECT name FROM sqlite_master WHERE type='index' AND name NOT LIKE 'sqlite_%'"
    ).fetchall()}
    cv_idx = {r[0] for r in cv_conn.execute(
        "SELECT name FROM sqlite_master WHERE type='index' AND name NOT LIKE 'sqlite_%'"
    ).fetchall()}
    
    print(f"  Example indices: {sorted(ex_idx)}")
    print(f"  Converted indices: {sorted(cv_idx)}")
    if ex_idx != cv_idx:
        print(f"  [!!!] MISMATCH: extra in example={ex_idx-cv_idx}, extra in converted={cv_idx-ex_idx}")
    else:
        print(f"  [OK] All indices match")
    
    # 5. android_metadata
    print("\n5. ANDROID_METADATA:")
    ex_meta = ex_conn.execute("SELECT * FROM android_metadata").fetchall()
    cv_meta = cv_conn.execute("SELECT * FROM android_metadata").fetchall()
    print(f"  Example: {ex_meta}")
    print(f"  Converted: {cv_meta}")
    print(f"  {'[OK]' if ex_meta == cv_meta else '[!!!] MISMATCH'}")
    
    # 6. Column info for each table
    print("\n6. COLUMN INFO (order matters!):")
    for table in ["ConversationEntity", "MemoryEntity", "ChatEpisodeEntity"]:
        print(f"\n  --- {table} ---")
        ex_cols = list(ex_conn.execute(f"PRAGMA table_info({table})").fetchall())
        cv_cols = list(cv_conn.execute(f"PRAGMA table_info({table})").fetchall())
        
        if ex_cols == cv_cols:
            print(f"  [OK] All columns match")
        else:
            print(f"  [!!!] COLUMN MISMATCH")
            for i, (ex, cv) in enumerate(zip(ex_cols, cv_cols)):
                if ex != cv:
                    print(f"       Col {i}: example={ex}, converted={cv}")
            if len(ex_cols) != len(cv_cols):
                print(f"       Column count: example={len(ex_cols)}, converted={len(cv_cols)}")
    
    # 7. Sample data format check
    print("\n7. SAMPLE DATA FORMAT:")
    
    # ConversationEntity first row comparison
    ex_conv = ex_conn.execute("SELECT * FROM ConversationEntity LIMIT 1").fetchone()
    cv_conv = cv_conn.execute("SELECT * FROM ConversationEntity LIMIT 1").fetchone()
    
    if ex_conv:
        print(f"\n  ConversationEntity example row types:")
        for i, val in enumerate(ex_conv):
            print(f"    Col {i}: type={type(val).__name__}, sample={str(val)[:50]}")
    
    # 8. sqlite_sequence check
    print("\n8. SQLITE_SEQUENCE:")
    ex_seq = ex_conn.execute("SELECT * FROM sqlite_sequence").fetchall()
    cv_seq = cv_conn.execute("SELECT * FROM sqlite_sequence").fetchall()
    print(f"  Example: {ex_seq}")
    print(f"  Converted: {cv_seq}")
    
    # 9. Check embedding_cache structure
    print("\n9. EMBEDDING_CACHE:")
    ex_cache_sql = ex_conn.execute("SELECT sql FROM sqlite_master WHERE name='embedding_cache'").fetchone()
    cv_cache_sql = cv_conn.execute("SELECT sql FROM sqlite_master WHERE name='embedding_cache'").fetchone()
    print(f"  Example: {ex_cache_sql[0] if ex_cache_sql else 'N/A'}")
    print(f"  Converted: {cv_cache_sql[0] if cv_cache_sql else 'N/A'}")
    
    # 10. GenMediaEntity
    print("\n10. GENMEDIAENTITY:")
    ex_gen = ex_conn.execute("SELECT sql FROM sqlite_master WHERE name='GenMediaEntity'").fetchone()
    cv_gen = cv_conn.execute("SELECT sql FROM sqlite_master WHERE name='GenMediaEntity'").fetchone()
    print(f"  Example: {ex_gen[0] if ex_gen else 'N/A'}")
    print(f"  Converted: {cv_gen[0] if cv_gen else 'N/A'}")
    
    ex_conn.close()
    cv_conn.close()
    
    print("\n" + "="*70)
    print("COMPARISON COMPLETE")
    print("="*70)

full_compare()
