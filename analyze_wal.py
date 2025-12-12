#!/usr/bin/env python3
"""Analyze the example WAL file and what happens to the data."""
import os
import sqlite3

DIR = "The conversion test/example_full"

print("="*60)
print("EXAMPLE FULL EXTRACTION ANALYSIS")
print("="*60)

print("\n1. Files extracted:")
for f in os.listdir(DIR):
    size = os.path.getsize(os.path.join(DIR, f))
    print(f"  {f}: {size} bytes")

db_path = os.path.join(DIR, "rikka_hub.db")
wal_path = os.path.join(DIR, "rikka_hub-wal")
shm_path = os.path.join(DIR, "rikka_hub-shm")

print(f"\n2. Opening DB (this will read WAL):")
conn = sqlite3.connect(db_path)
conv = conn.execute("SELECT COUNT(*) FROM ConversationEntity").fetchone()[0]
mem = conn.execute("SELECT COUNT(*) FROM MemoryEntity").fetchone()[0]
ep = conn.execute("SELECT COUNT(*) FROM ChatEpisodeEntity").fetchone()[0]
print(f"  Conversations: {conv}")
print(f"  Memories: {mem}")
print(f"  Episodes: {ep}")
conn.close()

print(f"\n3. WAL file header (first 32 bytes):")
if os.path.exists(wal_path):
    with open(wal_path, 'rb') as f:
        header = f.read(32)
        print(f"  Hex: {header.hex()}")
        print(f"  Size: {os.path.getsize(wal_path)} bytes")
else:
    print("  WAL file doesn't exist!")

# Check if WAL can be checkpointed
print("\n4. Checkpointing WAL into main DB:")
conn = sqlite3.connect(db_path)
conn.execute("PRAGMA wal_checkpoint(FULL)")
conn.close()

print(f"\n5. After checkpoint, file sizes:")
for f in os.listdir(DIR):
    size = os.path.getsize(os.path.join(DIR, f))
    print(f"  {f}: {size} bytes")
