#!/usr/bin/env python3
"""Compare source and converted databases to find missing elements."""

import sqlite3
import sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

SOURCE = "The conversion test/extracted/rikka_hub.db"
CONVERTED = "The conversion test/converted/rikka_hub.db"

print("=" * 60)
print("COMPARING SOURCE VS CONVERTED DATABASE")
print("=" * 60)

# Connect to both
src_conn = sqlite3.connect(SOURCE)
conv_conn = sqlite3.connect(CONVERTED)

# List all tables in source
print("\n1. TABLES IN SOURCE:")
src_tables = src_conn.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").fetchall()
for t in src_tables:
    print(f"   {t[0]}")

print("\n2. TABLES IN CONVERTED:")
conv_tables = conv_conn.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").fetchall()
for t in conv_tables:
    print(f"   {t[0]}")

# Check what tables are missing
src_set = set(t[0] for t in src_tables)
conv_set = set(t[0] for t in conv_tables)
missing = src_set - conv_set
if missing:
    print(f"\n[!] MISSING TABLES IN CONVERTED: {missing}")
else:
    print("\n[OK] All tables present")

# Check android_metadata
print("\n3. ANDROID_METADATA TABLE:")
try:
    src_conn.execute("SELECT * FROM android_metadata")
    print("   Source has android_metadata")
    for row in src_conn.execute("SELECT * FROM android_metadata"):
        print(f"   -> {row}")
except:
    print("   Source does NOT have android_metadata")

try:
    conv_conn.execute("SELECT * FROM android_metadata")
    print("   Converted has android_metadata")
except:
    print("   [!] Converted does NOT have android_metadata")

# Compare schema for key tables
print("\n4. SCHEMA COMPARISON:")
for table in ["ConversationEntity", "MemoryEntity", "ChatEpisodeEntity"]:
    print(f"\n   {table}:")
    src_schema = src_conn.execute(f"SELECT sql FROM sqlite_master WHERE name='{table}'").fetchone()
    conv_schema = conv_conn.execute(f"SELECT sql FROM sqlite_master WHERE name='{table}'").fetchone()
    
    if src_schema and conv_schema:
        if src_schema[0] == conv_schema[0]:
            print(f"      [OK] Schema matches")
        else:
            print(f"      [!] Schema DIFFERS!")
            print(f"      Source: {src_schema[0][:200]}...")
            print(f"      Converted: {conv_schema[0][:200]}...")

# Check indices
print("\n5. INDICES:")
print("   Source:")
for idx in src_conn.execute("SELECT name FROM sqlite_master WHERE type='index' AND name NOT LIKE 'sqlite_%'").fetchall():
    print(f"      {idx[0]}")
print("   Converted:")
for idx in conv_conn.execute("SELECT name FROM sqlite_master WHERE type='index' AND name NOT LIKE 'sqlite_%'").fetchall():
    print(f"      {idx[0]}")

# Check if room_master matches exactly
print("\n6. ROOM_MASTER_TABLE:")
for row in src_conn.execute("SELECT * FROM room_master_table"):
    print(f"   Source: {row}")
for row in conv_conn.execute("SELECT * FROM room_master_table"):
    print(f"   Converted: {row}")

src_conn.close()
conv_conn.close()
print("\n" + "=" * 60)
