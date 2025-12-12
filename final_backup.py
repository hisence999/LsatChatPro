#!/usr/bin/env python3
"""
Create backup with proper WAL/SHM files by copying from example.
Actually - better approach: use the EXAMPLE database as template and inject our data,
ensuring WAL files are properly generated.
"""

import sqlite3
import os
import shutil
import zipfile
import json
from datetime import datetime

EXAMPLE_DIR = "The conversion test/example_full"
CONVERTED_DB = "The conversion test/converted/rikka_hub.db"
OUTPUT_DIR = "The conversion test/final_output"
SETTINGS_FILE = "The conversion test/extracted/settings.json"
UPLOADS = "The conversion test/extracted/upload"
OUTPUT_ZIP = "The conversion test/LastChat_converted_backup.zip"

CLARA_ASSISTANT_ID = "52c1179c-29e4-49b5-becf-024efe9f5f3e"
GENERICAL_ASSISTANT_ID = "c45dac7a-b3d6-48a4-8d09-433f7d2e6cab"

print("="*60)
print("FINAL APPROACH: Use example as base, inject data")
print("="*60)

# Create fresh output directory
os.makedirs(OUTPUT_DIR, exist_ok=True)

# Copy example files to output directory
print("\n1. Copying example files as base...")
for f in ["rikka_hub.db", "rikka_hub-wal", "rikka_hub-shm"]:
    src = os.path.join(EXAMPLE_DIR, f)
    dst = os.path.join(OUTPUT_DIR, f)
    if os.path.exists(src):
        shutil.copy2(src, dst)
        print(f"   Copied {f}")

# Now open the output DB and inject our data
output_db = os.path.join(OUTPUT_DIR, "rikka_hub.db")
conv_conn = sqlite3.connect(CONVERTED_DB)
conv_conn.row_factory = sqlite3.Row
out_conn = sqlite3.connect(output_db)

print("\n2. Clearing example data...")
out_conn.execute("DELETE FROM ConversationEntity")
out_conn.execute("DELETE FROM MemoryEntity")
out_conn.execute("DELETE FROM ChatEpisodeEntity")
out_conn.execute("DELETE FROM GenMediaEntity")
out_conn.execute("DELETE FROM embedding_cache")
out_conn.commit()

print("\n3. Copying conversations from converted...")
rows = conv_conn.execute("SELECT * FROM ConversationEntity").fetchall()
for row in rows:
    out_conn.execute("""
        INSERT INTO ConversationEntity 
        (id, assistant_id, title, nodes, create_at, update_at, truncate_index, suggestions, is_pinned, is_consolidated)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, (row['id'], row['assistant_id'], row['title'], row['nodes'], 
          row['create_at'], row['update_at'], row['truncate_index'],
          row['suggestions'], row['is_pinned'], row['is_consolidated']))
out_conn.commit()
print(f"   Inserted {len(rows)} conversations")

print("\n4. Copying memories from converted...")
rows = conv_conn.execute("SELECT * FROM MemoryEntity").fetchall()
for row in rows:
    out_conn.execute("""
        INSERT INTO MemoryEntity 
        (assistant_id, content, embedding, embedding_model_id, type, last_accessed_at, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
    """, (row['assistant_id'], row['content'], row['embedding'], 
          row['embedding_model_id'], row['type'], row['last_accessed_at'], row['created_at']))
out_conn.commit()
print(f"   Inserted {len(rows)} memories")

print("\n5. Copying episodes from converted...")
rows = conv_conn.execute("SELECT * FROM ChatEpisodeEntity").fetchall()
for row in rows:
    out_conn.execute("""
        INSERT INTO ChatEpisodeEntity 
        (assistant_id, content, embedding, embedding_model_id, start_time, end_time, last_accessed_at, significance, conversation_id)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, (row['assistant_id'], row['content'], row['embedding'],
          row['embedding_model_id'], row['start_time'], row['end_time'],
          row['last_accessed_at'], row['significance'], row['conversation_id']))
out_conn.commit()
print(f"   Inserted {len(rows)} episodes")

conv_conn.close()

# Verify
print("\n6. Verification:")
conv = out_conn.execute("SELECT COUNT(*) FROM ConversationEntity").fetchone()[0]
mem = out_conn.execute("SELECT COUNT(*) FROM MemoryEntity").fetchone()[0]
ep = out_conn.execute("SELECT COUNT(*) FROM ChatEpisodeEntity").fetchone()[0]
print(f"   Conversations: {conv}")
print(f"   Memories: {mem}")
print(f"   Episodes: {ep}")

# Make sure WAL is synced
out_conn.execute("PRAGMA wal_checkpoint(PASSIVE)")
out_conn.close()

# Check file sizes
print("\n7. Output files:")
for f in os.listdir(OUTPUT_DIR):
    size = os.path.getsize(os.path.join(OUTPUT_DIR, f))
    print(f"   {f}: {size} bytes")

# Create backup ZIP
print("\n8. Creating backup ZIP...")
if os.path.exists(OUTPUT_ZIP):
    os.remove(OUTPUT_ZIP)

with zipfile.ZipFile(OUTPUT_ZIP, 'w', zipfile.ZIP_DEFLATED) as zf:
    # Same order as example: settings, db, wal, shm
    zf.write(SETTINGS_FILE, "settings.json")
    zf.write(os.path.join(OUTPUT_DIR, "rikka_hub.db"), "rikka_hub.db")
    
    wal = os.path.join(OUTPUT_DIR, "rikka_hub-wal")
    if os.path.exists(wal) and os.path.getsize(wal) > 0:
        zf.write(wal, "rikka_hub-wal")
        print("   Added rikka_hub-wal")
    
    shm = os.path.join(OUTPUT_DIR, "rikka_hub-shm")
    if os.path.exists(shm) and os.path.getsize(shm) > 0:
        zf.write(shm, "rikka_hub-shm")
        print("   Added rikka_hub-shm")
    
    for f in os.listdir(UPLOADS):
        fp = os.path.join(UPLOADS, f)
        if os.path.isfile(fp):
            zf.write(fp, f"upload/{f}")

print(f"\n9. Created: {OUTPUT_ZIP} ({os.path.getsize(OUTPUT_ZIP)/1024/1024:.1f} MB)")
print("="*60)
