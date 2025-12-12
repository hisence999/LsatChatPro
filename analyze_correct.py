#!/usr/bin/env python3
"""Analyze the CORRECT app's database structure."""

import sqlite3
import sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

DB = "The conversion test/example_extracted/rikka_hub.db"

print("=" * 70)
print("CORRECT APP DATABASE STRUCTURE")
print("=" * 70)

conn = sqlite3.connect(DB)

# List all tables
print("\n1. ALL TABLES:")
for row in conn.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"):
    print(f"   {row[0]}")

# Get schema for each table
print("\n2. TABLE SCHEMAS:")
for row in conn.execute("SELECT name, sql FROM sqlite_master WHERE type='table' ORDER BY name"):
    if row[1]:
        print(f"\n--- {row[0]} ---")
        print(row[1])

# Check room hash
print("\n\n3. ROOM MASTER TABLE:")
for row in conn.execute("SELECT * FROM room_master_table"):
    print(f"   {row}")

# Check database version
print("\n4. DATABASE VERSION:")
print(f"   user_version: {conn.execute('PRAGMA user_version').fetchone()[0]}")

# Check record counts
print("\n5. RECORD COUNTS:")
for table in ["ConversationEntity", "MemoryEntity", "ChatEpisodeEntity", "GenMediaEntity"]:
    try:
        count = conn.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]
        print(f"   {table}: {count}")
    except:
        print(f"   {table}: NOT FOUND")

# Sample data
print("\n6. SAMPLE MEMORY DATA:")
try:
    for row in conn.execute("SELECT * FROM MemoryEntity LIMIT 2"):
        print(f"   {row}")
except Exception as e:
    print(f"   Error: {e}")

print("\n7. SAMPLE EPISODE DATA:")
try:
    for row in conn.execute("SELECT * FROM ChatEpisodeEntity LIMIT 2"):
        print(f"   {row}")
except Exception as e:
    print(f"   Error: {e}")

print("\n8. SAMPLE CONVERSATION DATA (first 200 chars of nodes):")
try:
    for row in conn.execute("SELECT id, assistant_id, title, substr(nodes, 1, 200) FROM ConversationEntity LIMIT 1"):
        print(f"   ID: {row[0]}")
        print(f"   AssistantID: {row[1]}")
        print(f"   Title: {row[2]}")
        print(f"   Nodes: {row[3]}...")
except Exception as e:
    print(f"   Error: {e}")

# Check for memory_nodes table
print("\n9. CHECKING FOR OLD MEMORY TABLES:")
for table in ["memory_nodes", "memory_edges"]:
    try:
        conn.execute(f"SELECT 1 FROM {table} LIMIT 1")
        print(f"   {table}: EXISTS")
    except:
        print(f"   {table}: NOT FOUND (good - this is the new format)")

# All indices
print("\n10. ALL INDICES:")
for row in conn.execute("SELECT name FROM sqlite_master WHERE type='index' AND name NOT LIKE 'sqlite_%'"):
    print(f"   {row[0]}")

conn.close()
print("\n" + "=" * 70)
