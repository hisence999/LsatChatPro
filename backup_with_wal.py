#!/usr/bin/env python3
"""
Create backup with proper WAL and SHM files included.
The Example backup includes these files, so we need to include them too.
"""

import sqlite3
import os
import zipfile
import struct

DB = "The conversion test/converted/rikka_hub.db"
SETTINGS = "The conversion test/converted/settings_fixed.json"
UPLOADS = "The conversion test/extracted/upload"
OUTPUT = "The conversion test/LastChat_converted_backup.zip"

print("="*60)
print("CREATING BACKUP WITH WAL/SHM FILES")
print("="*60)

# First, let's use SQLite to generate proper WAL/SHM files
# by opening the database in WAL mode and doing a write operation

db_dir = os.path.dirname(DB)
wal_file = DB + "-wal"
shm_file = DB + "-shm"

# Delete any existing WAL/SHM files
for f in [wal_file, shm_file]:
    if os.path.exists(f):
        os.remove(f)

print("\n1. Opening database to generate WAL/SHM files...")
conn = sqlite3.connect(DB)
# Force WAL mode
conn.execute("PRAGMA journal_mode=WAL")
# Do a dummy write to ensure WAL files are created
conn.execute("CREATE TABLE IF NOT EXISTS _temp_dummy (id INTEGER)")
conn.execute("DROP TABLE IF EXISTS _temp_dummy")
conn.commit()
conn.close()

# Check if WAL files exist
print(f"\n2. Checking for WAL files:")
print(f"   wal exists: {os.path.exists(wal_file)}")
print(f"   shm exists: {os.path.exists(shm_file)}")

# If they don't exist, create minimal ones
if not os.path.exists(shm_file):
    print("\n   Creating minimal SHM file...")
    # SHM file is typically 32768 bytes of zeros with some header info
    with open(shm_file, 'wb') as f:
        f.write(b'\x00' * 32768)

if not os.path.exists(wal_file):
    print("   Creating minimal WAL file...")
    # WAL header: magic (4), version (4), page size (4), checkpoint seq (4), 
    # salt1 (4), salt2 (4), checksum1 (4), checksum2 (4) = 32 bytes
    # An empty WAL file still needs a proper header
    with open(wal_file, 'wb') as f:
        # SQLite WAL magic number
        header = struct.pack('>I', 0x377f0682)  # WAL magic
        header += struct.pack('>I', 3007000)    # Format version
        header += struct.pack('>I', 4096)       # Page size
        header += struct.pack('>I', 0)          # Checkpoint sequence
        header += struct.pack('>I', 0)          # Salt1
        header += struct.pack('>I', 0)          # Salt2  
        header += struct.pack('>I', 0)          # Checksum1
        header += struct.pack('>I', 0)          # Checksum2
        f.write(header)

# Verify database still works
print("\n3. Verifying database...")
conn = sqlite3.connect(DB)
conv_count = conn.execute("SELECT COUNT(*) FROM ConversationEntity").fetchone()[0]
print(f"   Conversations: {conv_count}")
conn.close()

# Create backup with WAL files
print("\n4. Creating backup zip...")
if os.path.exists(OUTPUT):
    os.remove(OUTPUT)

with zipfile.ZipFile(OUTPUT, 'w', zipfile.ZIP_DEFLATED) as zf:
    # Add in the same order as Example.zip: settings, db, wal, shm
    zf.write(SETTINGS, "settings.json")
    print("   Added settings.json")
    
    zf.write(DB, "rikka_hub.db")
    print(f"   Added rikka_hub.db ({os.path.getsize(DB)} bytes)")
    
    if os.path.exists(wal_file):
        zf.write(wal_file, "rikka_hub-wal")
        print(f"   Added rikka_hub-wal ({os.path.getsize(wal_file)} bytes)")
    
    if os.path.exists(shm_file):
        zf.write(shm_file, "rikka_hub-shm")
        print(f"   Added rikka_hub-shm ({os.path.getsize(shm_file)} bytes)")
    
    # Add uploads
    for f in os.listdir(UPLOADS):
        fp = os.path.join(UPLOADS, f)
        if os.path.isfile(fp):
            zf.write(fp, f"upload/{f}")
    print(f"   Added {len(os.listdir(UPLOADS))} upload files")

print(f"\n5. Created: {OUTPUT} ({os.path.getsize(OUTPUT)/1024/1024:.1f} MB)")
print("="*60)
