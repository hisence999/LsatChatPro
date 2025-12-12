#!/usr/bin/env python3
"""
Deep investigation of database differences.
"""

import sqlite3
import sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

SOURCE = "The conversion test/extracted/rikka_hub.db"
CONVERTED = "The conversion test/converted/rikka_hub.db"

print("=" * 70)
print("DEEP DATABASE INVESTIGATION")
print("=" * 70)

src = sqlite3.connect(SOURCE)
conv = sqlite3.connect(CONVERTED)

# Get EXACT schema for all tables
print("\n1. EXACT SCHEMA COMPARISON:")
for table in ["ConversationEntity", "MemoryEntity", "ChatEpisodeEntity", "room_master_table", "android_metadata"]:
    print(f"\n--- {table} ---")
    
    try:
        src_sql = src.execute(f"SELECT sql FROM sqlite_master WHERE name='{table}'").fetchone()
        print(f"SOURCE:")
        print(src_sql[0] if src_sql else "NOT FOUND")
    except Exception as e:
        print(f"SOURCE ERROR: {e}")
    
    try:
        conv_sql = conv.execute(f"SELECT sql FROM sqlite_master WHERE name='{table}'").fetchone()
        print(f"\nCONVERTED:")
        print(conv_sql[0] if conv_sql else "NOT FOUND")
    except Exception as e:
        print(f"CONVERTED ERROR: {e}")

# Check all column info
print("\n\n2. PRAGMA TABLE_INFO COMPARISON:")
for table in ["MemoryEntity", "ChatEpisodeEntity"]:
    print(f"\n--- {table} ---")
    print("SOURCE COLUMNS:")
    for col in src.execute(f"PRAGMA table_info({table})"):
        print(f"  {col}")
    print("CONVERTED COLUMNS:")
    for col in conv.execute(f"PRAGMA table_info({table})"):
        print(f"  {col}")

# Check for foreign keys
print("\n\n3. FOREIGN KEYS:")
print("SOURCE:")
for fk in src.execute("PRAGMA foreign_key_list(ConversationEntity)"):
    print(f"  {fk}")
print("CONVERTED:")
for fk in conv.execute("PRAGMA foreign_key_list(ConversationEntity)"):
    print(f"  {fk}")

# Check database version
print("\n\n4. DATABASE VERSION PRAGMA:")
print(f"SOURCE user_version: {src.execute('PRAGMA user_version').fetchone()}")
print(f"CONVERTED user_version: {conv.execute('PRAGMA user_version').fetchone()}")

# Check all sqlite_master entries
print("\n\n5. ALL SQLITE_MASTER ENTRIES:")
print("\nSOURCE:")
for row in src.execute("SELECT type, name, tbl_name FROM sqlite_master ORDER BY type, name"):
    print(f"  {row}")

print("\nCONVERTED:")
for row in conv.execute("SELECT type, name, tbl_name FROM sqlite_master ORDER BY type, name"):
    print(f"  {row}")

# Check if source MemoryEntity has embedding_model_id column
print("\n\n6. MemoryEntity COLUMN CHECK:")
print("SOURCE columns:")
for col in src.execute("PRAGMA table_info(MemoryEntity)"):
    print(f"  {col[1]}: {col[2]} (notnull={col[3]}, default={col[4]})")

print("\nCONVERTED columns:")
for col in conv.execute("PRAGMA table_info(MemoryEntity)"):
    print(f"  {col[1]}: {col[2]} (notnull={col[3]}, default={col[4]})")

src.close()
conv.close()

print("\n" + "=" * 70)
