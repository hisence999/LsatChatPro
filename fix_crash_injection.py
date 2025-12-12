#!/usr/bin/env python3
"""
Fix the crash by injecting conversations into a CLEAN, VALID database structure.
This ensures no extra columns or schema violations exist.
"""

import sqlite3
import os
import shutil
import zipfile
import json

# Inputs
VALID_BASE_DB = "The conversion test/after_import_extracted/rikka_hub.db" 
# ^ This is the DB from the app itself, so it's guaranteed to be correct schema

INCOMPATIBLE_SOURCE_DB = "The conversion test/stripped_uncompatible/rikka_hub.db"
# ^ This has the 181 conversations but slightly wrong schema

WORK_DIR = "The conversion test/fix_crash_work"
OUTPUT_ZIP = "The conversion test/LastChat_Fixed_History.zip"
SETTINGS_FILE = "The conversion test/after_import_extracted/settings.json" # Users working settings
UPLOADS_DIR = "The conversion test/extracted/upload"

print("="*60)
print("FIXING CRASH: CLEAN INJECTION")
print("="*60)

# 1. Prepare Workspace
if os.path.exists(WORK_DIR):
    shutil.rmtree(WORK_DIR)
os.makedirs(WORK_DIR)

# Copy the valid base DB to work dir
db_path = os.path.join(WORK_DIR, "rikka_hub.db")
shutil.copy2(VALID_BASE_DB, db_path)

# 2. Open DB and Inject Data
print("\n1. Opening valid base database...")
conn = sqlite3.connect(db_path)
conn.execute("PRAGMA journal_mode=WAL")

# Clear any placeholder data in base DB
conn.execute("DELETE FROM ConversationEntity")
conn.execute("DELETE FROM MemoryEntity")
conn.execute("DELETE FROM ChatEpisodeEntity")
conn.commit()

# Attach source
print("2. Attaching source database...")
conn.execute(f"ATTACH DATABASE '{INCOMPATIBLE_SOURCE_DB}' AS source")

# 3. Inject Transactions (Column Mapping)
# We explicitly select ONLY the columns that exist in the target V18 schema
# ignoring 'consolidation_retry_count' and 'consolidation_last_error'
print("3. Injecting conversations (mapping columns)...")
conn.execute("""
    INSERT INTO main.ConversationEntity 
    (id, assistant_id, title, nodes, create_at, update_at, truncate_index, suggestions, is_pinned, is_consolidated)
    SELECT 
        id, 
        assistant_id, 
        title, 
        nodes, 
        create_at, 
        update_at, 
        truncate_index, 
        suggestions, 
        is_pinned, 
        is_consolidated 
    FROM source.ConversationEntity
""")
count = conn.execute("SELECT COUNT(*) FROM ConversationEntity").fetchone()[0]
print(f"   Injected {count} conversations.")

# We do NOT inject memories or episodes, as requested due to issues.
print("   Skipping memories/episodes as requested (tables remain empty).")

conn.commit()

# 4. Checkpoint
print("4. Checkpointing WAL...")
conn.execute("PRAGMA wal_checkpoint(TRUNCATE)")
conn.close()

# 5. Create Backup Zip
print("\n5. Creating Backup Zip...")
if os.path.exists(OUTPUT_ZIP):
    os.remove(OUTPUT_ZIP)

with zipfile.ZipFile(OUTPUT_ZIP, 'w', zipfile.ZIP_DEFLATED) as zf:
    # DB
    zf.write(db_path, "rikka_hub.db")
    
    # Check for WAL/SHM (should be small/empty after restart but included for safety if present)
    wal = db_path + "-wal"
    shm = db_path + "-shm"
    if os.path.exists(wal): zf.write(wal, "rikka_hub-wal")
    if os.path.exists(shm): zf.write(shm, "rikka_hub-shm")
    
    # Settings (from working backup)
    zf.write(SETTINGS_FILE, "settings.json")
    
    # Uploads (from original source)
    for f in os.listdir(UPLOADS_DIR):
        fp = os.path.join(UPLOADS_DIR, f)
        if os.path.isfile(fp):
            zf.write(fp, f"upload/{f}")

print(f"Done: {OUTPUT_ZIP} ({os.path.getsize(OUTPUT_ZIP)/1024/1024:.1f} MB)")
