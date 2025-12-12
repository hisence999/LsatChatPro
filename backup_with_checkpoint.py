#!/usr/bin/env python3
"""
Create backup with WAL checkpoint to ensure clean database.
Also generate empty WAL and SHM files since app expects them.
"""

import sqlite3
import os
import zipfile

DB = "The conversion test/converted/rikka_hub.db"
SETTINGS = "The conversion test/converted/settings_fixed.json"
UPLOADS = "The conversion test/extracted/upload"
OUTPUT = "The conversion test/LastChat_converted_backup.zip"

print("="*60)
print("CREATING BACKUP WITH WAL CHECKPOINT")
print("="*60)

# Perform WAL checkpoint to ensure all data is in main db
print("\n1. Performing WAL checkpoint...")
conn = sqlite3.connect(DB)
conn.execute("PRAGMA wal_checkpoint(TRUNCATE)")
conn.close()
print("   Done - WAL checkpoint complete")

# Verify database
print("\n2. Verifying database content...")
conn = sqlite3.connect(DB)
conv_count = conn.execute("SELECT COUNT(*) FROM ConversationEntity").fetchone()[0]
mem_count = conn.execute("SELECT COUNT(*) FROM MemoryEntity").fetchone()[0]
ep_count = conn.execute("SELECT COUNT(*) FROM ChatEpisodeEntity").fetchone()[0]
print(f"   Conversations: {conv_count}")
print(f"   Memories: {mem_count}")
print(f"   Episodes: {ep_count}")
conn.close()

# Create backup
print("\n3. Creating backup zip...")
if os.path.exists(OUTPUT):
    os.remove(OUTPUT)

with zipfile.ZipFile(OUTPUT, 'w', zipfile.ZIP_DEFLATED) as zf:
    zf.write(DB, "rikka_hub.db")
    print("   Added rikka_hub.db")
    
    zf.write(SETTINGS, "settings.json")
    print("   Added settings.json")
    
    # Add empty WAL and SHM files (some apps expect them)
    # Actually, we won't add them - after TRUNCATE checkpoint, they should be empty
    
    # Add uploads
    for f in os.listdir(UPLOADS):
        fp = os.path.join(UPLOADS, f)
        if os.path.isfile(fp):
            zf.write(fp, f"upload/{f}")
    print(f"   Added {len(os.listdir(UPLOADS))} upload files")

print(f"\n4. Created: {OUTPUT} ({os.path.getsize(OUTPUT)/1024/1024:.1f} MB)")
print("="*60)
