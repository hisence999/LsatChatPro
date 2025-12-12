#!/usr/bin/env python3
"""
Strip memory tables from the incompatible backup and force compatibility.
"""

import sqlite3
import os
import shutil
import zipfile

INPUT_ZIP = "The conversion test/LastChat_uncompatible_Backup.zip"
WORK_DIR = "The conversion test/stripped_uncompatible"
OUTPUT_ZIP = "The conversion test/LastChat_Stripped_History_Only.zip"

print(f"Processing {INPUT_ZIP}...")

# 1. Extract
if os.path.exists(WORK_DIR):
    shutil.rmtree(WORK_DIR)
os.makedirs(WORK_DIR)

with zipfile.ZipFile(INPUT_ZIP, 'r') as zf:
    zf.extractall(WORK_DIR)

# 2. Modify Database
db_path = os.path.join(WORK_DIR, "rikka_hub.db")
conn = sqlite3.connect(db_path)

# Drop memory-related tables
tables_to_drop = [
    "memory_nodes", 
    "memory_edges", 
    "MemoryEntity", 
    "ChatEpisodeEntity",
    "embedding_cache" # might as well remove this cache
]

print("Dropping tables:")
for table in tables_to_drop:
    try:
        conn.execute(f"DROP TABLE IF EXISTS {table}")
        print(f"  - Dropped {table}")
    except Exception as e:
        print(f"  - Error dropping {table}: {e}")

# Force version 18 (Current App Version)
print("Setting user_version to 18...")
conn.execute("PRAGMA user_version = 18")

# Verify ConversationEntity exists
try:
    count = conn.execute("SELECT COUNT(*) FROM ConversationEntity").fetchone()[0]
    print(f"Retained {count} conversations.")
except Exception as e:
    print(f"WARNING: Issue with ConversationEntity: {e}")

# Check columns in ConversationEntity just for info
print("ConversationEntity columns:")
cols = [info[1] for info in conn.execute("PRAGMA table_info(ConversationEntity)")]
print(cols)

conn.commit()
conn.execute("VACUUM") # Clean up
conn.close()

# Remove any WAL/SHM files if they exist to prevent state mismatch
for ext in ["-wal", "-shm"]:
    f = db_path + ext
    if os.path.exists(f):
        os.remove(f)
        print(f"Removed {f}")

# 3. Zip
print(f"Creating {OUTPUT_ZIP}...")
if os.path.exists(OUTPUT_ZIP):
    os.remove(OUTPUT_ZIP)

with zipfile.ZipFile(OUTPUT_ZIP, 'w', zipfile.ZIP_DEFLATED) as zf:
    for root, dirs, files in os.walk(WORK_DIR):
        for file in files:
            abs_path = os.path.join(root, file)
            arc_name = os.path.relpath(abs_path, WORK_DIR)
            zf.write(abs_path, arc_name)

print("Done.")
