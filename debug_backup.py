#!/usr/bin/env python3
"""Debug the backup - check what's actually in it."""

import sqlite3
import zipfile
import os
import sys
import tempfile
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

BACKUP_ZIP = "The conversion test/LastChat_converted_backup.zip"
DB_OUT = "The conversion test/converted/rikka_hub.db"

print("=" * 60)
print("BACKUP DEBUG")
print("=" * 60)

# Check the zip file contents
print("\n1. ZIP FILE CONTENTS:")
with zipfile.ZipFile(BACKUP_ZIP, 'r') as zf:
    for info in zf.infolist():
        print(f"   {info.filename}: {info.file_size} bytes")

# Check direct database file
print("\n2. CHECKING CONVERTED DATABASE DIRECTLY:")
conn = sqlite3.connect(DB_OUT)
conn.row_factory = sqlite3.Row
cursor = conn.cursor()

# List all tables
cursor.execute("SELECT name FROM sqlite_master WHERE type='table'")
tables = [row[0] for row in cursor.fetchall()]
print(f"   Tables: {tables}")

# Check conversation data
print("\n3. CONVERSATION ENTITY CHECK:")
cursor.execute("SELECT COUNT(*) FROM ConversationEntity")
print(f"   Total conversations: {cursor.fetchone()[0]}")

cursor.execute("SELECT id, title, LENGTH(nodes) as node_len FROM ConversationEntity LIMIT 3")
for row in cursor.fetchall():
    print(f"   ID: {row['id'][:20]}..., Title: {row['title']}, Nodes length: {row['node_len']}")

# Check if nodes are valid JSON
print("\n4. NODES FORMAT CHECK:")
cursor.execute("SELECT id, nodes FROM ConversationEntity LIMIT 1")
row = cursor.fetchone()
import json
try:
    nodes = json.loads(row['nodes'])
    print(f"   Nodes is valid JSON: True")
    print(f"   Nodes type: {type(nodes)}")
    print(f"   First node keys: {nodes[0].keys() if nodes else 'empty'}")
except Exception as e:
    print(f"   [ERROR] Nodes parsing failed: {e}")

# Check memories
print("\n5. MEMORY ENTITY CHECK:")
cursor.execute("SELECT COUNT(*) FROM MemoryEntity")
print(f"   Total memories: {cursor.fetchone()[0]}")
cursor.execute("SELECT id, assistant_id, content FROM MemoryEntity LIMIT 2")
for row in cursor.fetchall():
    print(f"   ID: {row['id']}, AssistantID: {row['assistant_id'][:10]}..., Content: {row['content'][:50]}")

# Check episodes
print("\n6. EPISODE ENTITY CHECK:")
cursor.execute("SELECT COUNT(*) FROM ChatEpisodeEntity")
print(f"   Total episodes: {cursor.fetchone()[0]}")

# Check room master
print("\n7. ROOM MASTER CHECK:")
cursor.execute("SELECT * FROM room_master_table")
for row in cursor.fetchall():
    print(f"   {dict(row)}")

conn.close()

# Now extract from the ZIP and check the database inside
print("\n8. CHECKING DATABASE INSIDE ZIP:")
with zipfile.ZipFile(BACKUP_ZIP, 'r') as zf:
    with tempfile.TemporaryDirectory() as tmpdir:
        zf.extract("rikka_hub.db", tmpdir)
        db_path = os.path.join(tmpdir, "rikka_hub.db")
        
        conn2 = sqlite3.connect(db_path)
        cursor2 = conn2.cursor()
        
        cursor2.execute("SELECT name FROM sqlite_master WHERE type='table'")
        tables2 = [row[0] for row in cursor2.fetchall()]
        print(f"   Tables in ZIP DB: {tables2}")
        
        cursor2.execute("SELECT COUNT(*) FROM ConversationEntity")
        print(f"   Conversations in ZIP DB: {cursor2.fetchone()[0]}")
        
        cursor2.execute("SELECT COUNT(*) FROM MemoryEntity")
        print(f"   Memories in ZIP DB: {cursor2.fetchone()[0]}")
        
        conn2.close()

print("\n" + "=" * 60)
