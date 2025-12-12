#!/usr/bin/env python3
"""
Inject proper memories and conversations into the user's working 'after_import_example' backup.
"""

import sqlite3
import os
import shutil
import zipfile
import json

# Inputs
WORKING_BACKUP_DIR = "The conversion test/after_import_extracted"
SOURCE_DB = "The conversion test/converted/rikka_hub.db"
UPLOADS_DIR = "The conversion test/extracted/upload"
OUTPUT_ZIP = "The conversion test/LastChat_Final_Injected.zip"

print("="*60)
print("INJECTING DATA INTO WORKING BACKUP")
print("="*60)

db_path = os.path.join(WORKING_BACKUP_DIR, "rikka_hub.db")
wal_path = os.path.join(WORKING_BACKUP_DIR, "rikka_hub-wal")
shm_path = os.path.join(WORKING_BACKUP_DIR, "rikka_hub-shm")

# 1. Open the working DB (this processes its WAL file automatically)
print("\n1. Opening working DB...")
target_conn = sqlite3.connect(db_path)
target_conn.execute("PRAGMA journal_mode=WAL") # Ensure we stay in WAL mode

# 2. Clear existing data (the 1 conversation/5 memories)
print("   Clearing existing data...")
target_conn.execute("DELETE FROM ConversationEntity")
target_conn.execute("DELETE FROM MemoryEntity")
target_conn.execute("DELETE FROM ChatEpisodeEntity")
target_conn.execute("DELETE FROM embedding_cache")
target_conn.commit()

# 3. Attach the source DB containing our full converted data
print("\n2. Attaching source DB...")
target_conn.execute(f"ATTACH DATABASE '{SOURCE_DB}' AS source")

# 4. Copy data over
print("   Copying 181 conversations...")
target_conn.execute("""
    INSERT INTO main.ConversationEntity 
    SELECT * FROM source.ConversationEntity
""")

print("   Copying 47 memories...")
target_conn.execute("""
    INSERT INTO main.MemoryEntity 
    SELECT * FROM source.MemoryEntity
""")

print("   Copying 167 episodes...")
target_conn.execute("""
    INSERT INTO main.ChatEpisodeEntity 
    SELECT * FROM source.ChatEpisodeEntity
""")

target_conn.commit()

# 5. Verify counts
print("\n3. Verification:")
conv = target_conn.execute("SELECT COUNT(*) FROM ConversationEntity").fetchone()[0]
mem = target_conn.execute("SELECT COUNT(*) FROM MemoryEntity").fetchone()[0]
ep = target_conn.execute("SELECT COUNT(*) FROM ChatEpisodeEntity").fetchone()[0]
print(f"   Conversations: {conv}")
print(f"   Memories: {mem}")
print(f"   Episodes: {ep}")

# 6. Checkpoint
print("\n4. Performing WAL Checkpoint (TRUNCATE)...")
# This pushes all data to rikka_hub.db and truncates the WAL file
target_conn.execute("PRAGMA wal_checkpoint(TRUNCATE)")
target_conn.close()

# 7. Create Zip
print("\n5. Creating Final ZIP...")
if os.path.exists(OUTPUT_ZIP):
    os.remove(OUTPUT_ZIP)

with zipfile.ZipFile(OUTPUT_ZIP, 'w', zipfile.ZIP_DEFLATED) as zf:
    # Add DB files
    zf.write(db_path, "rikka_hub.db")
    
    # Check if WAL/SHM exist and are significant, otherwise we might skip them
    # But for safety, if they exist, include them (they should be small after checkpoint)
    if os.path.exists(wal_path):
        print(f"   Adding WAL ({os.path.getsize(wal_path)} bytes)")
        zf.write(wal_path, "rikka_hub-wal")
    if os.path.exists(shm_path):
        print(f"   Adding SHM ({os.path.getsize(shm_path)} bytes)")
        zf.write(shm_path, "rikka_hub-shm")
        
    # Add Settings (from the working backup!)
    zf.write(os.path.join(WORKING_BACKUP_DIR, "settings.json"), "settings.json")
    print("   Adding settings.json")
    
    # Add Uploads (Avatars, etc.)
    count = 0
    for f in os.listdir(UPLOADS_DIR):
        fp = os.path.join(UPLOADS_DIR, f)
        if os.path.isfile(fp):
            zf.write(fp, f"upload/{f}")
            count += 1
    print(f"   Adding {count} upload files")

print(f"\nDone! Created {OUTPUT_ZIP} ({os.path.getsize(OUTPUT_ZIP)/1024/1024:.1f} MB)")
print("="*60)
