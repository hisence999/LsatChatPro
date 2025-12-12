#!/usr/bin/env python3
"""
Verification script to validate the converted backup.
"""

import sqlite3
import os
import zipfile
import sys

# Force UTF-8 output
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

OUTPUT_DB = "The conversion test/converted/rikka_hub.db"
OUTPUT_ZIP = "The conversion test/LastChat_converted_backup.zip"
EXPECTED_TABLES = ["ConversationEntity", "MemoryEntity", "GenMediaEntity", "ChatEpisodeEntity", "embedding_cache", "room_master_table"]
EXPECTED_IDENTITY_HASH = "5549436f9c6325af2c133038a459ca63"

def verify_database():
    """Verify the converted database schema and data."""
    print("=" * 60)
    print("Database Verification")
    print("=" * 60)
    
    conn = sqlite3.connect(OUTPUT_DB)
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()
    
    # Check tables exist
    print("\n1. Checking tables...")
    cursor.execute("SELECT name FROM sqlite_master WHERE type='table'")
    tables = [row[0] for row in cursor.fetchall()]
    
    for expected in EXPECTED_TABLES:
        if expected in tables:
            print(f"   [OK] {expected} exists")
        else:
            print(f"   [X] {expected} MISSING!")
    
    # Check Room identity hash
    print("\n2. Checking Room identity hash...")
    cursor.execute("SELECT identity_hash FROM room_master_table WHERE id=42")
    row = cursor.fetchone()
    if row and row[0] == EXPECTED_IDENTITY_HASH:
        print(f"   [OK] Identity hash matches (version 18)")
    else:
        print(f"   [X] Identity hash mismatch! Got: {row[0] if row else 'None'}")
    
    # Check record counts
    print("\n3. Record counts...")
    for table in ["ConversationEntity", "MemoryEntity", "ChatEpisodeEntity", "GenMediaEntity"]:
        cursor.execute(f"SELECT COUNT(*) FROM {table}")
        count = cursor.fetchone()[0]
        print(f"   {table}: {count}")
    
    # Check memory types
    print("\n4. Memory types...")
    cursor.execute("SELECT type, COUNT(*) as cnt FROM MemoryEntity GROUP BY type")
    for row in cursor.fetchall():
        type_name = "CORE" if row[0] == 0 else "EPISODIC" if row[0] == 1 else f"UNKNOWN({row[0]})"
        print(f"   {type_name}: {row[1]}")
    
    # Check assistant distribution
    print("\n5. Assistant memory distribution...")
    cursor.execute("SELECT assistant_id, COUNT(*) as cnt FROM MemoryEntity GROUP BY assistant_id")
    for row in cursor.fetchall():
        print(f"   {row[0][:20]}...: {row[1]}")
    
    # Check episode conversation linking
    print("\n6. Episode conversation linking...")
    cursor.execute("SELECT COUNT(*) FROM ChatEpisodeEntity WHERE conversation_id IS NOT NULL AND conversation_id != ''")
    linked = cursor.fetchone()[0]
    cursor.execute("SELECT COUNT(*) FROM ChatEpisodeEntity WHERE conversation_id IS NULL OR conversation_id = ''")
    unlinked = cursor.fetchone()[0]
    print(f"   Linked: {linked}")
    print(f"   Unlinked: {unlinked}")
    
    # Sample a few memories
    print("\n7. Sample core memories...")
    cursor.execute("SELECT content FROM MemoryEntity WHERE type=0 ORDER BY RANDOM() LIMIT 5")
    for i, row in enumerate(cursor.fetchall(), 1):
        content = row[0][:100] + "..." if len(row[0]) > 100 else row[0]
        print(f"   {i}. {content}")
    
    # Sample a few episodes
    print("\n8. Sample episodic memories...")
    cursor.execute("SELECT content FROM ChatEpisodeEntity ORDER BY RANDOM() LIMIT 3")
    for i, row in enumerate(cursor.fetchall(), 1):
        content = row[0][:150] + "..." if len(row[0]) > 150 else row[0]
        print(f"   {i}. {content}")
    
    # Check for duplicates in memories
    print("\n9. Checking for duplicate memories...")
    cursor.execute("""
        SELECT content, COUNT(*) as cnt 
        FROM MemoryEntity 
        GROUP BY assistant_id, LOWER(content) 
        HAVING cnt > 1
        LIMIT 5
    """)
    dups = cursor.fetchall()
    if dups:
        print(f"   [!] Found {len(dups)} potential duplicates")
        for row in dups:
            print(f"      Count {row[1]}: {row[0][:50]}...")
    else:
        print("   [OK] No exact duplicates found")
    
    conn.close()
    print("\n" + "=" * 60)


def verify_zip():
    """Verify the backup zip file."""
    print("\nBackup ZIP Verification")
    print("=" * 60)
    
    with zipfile.ZipFile(OUTPUT_ZIP, 'r') as zf:
        files = zf.namelist()
        print(f"Files in zip: {len(files)}")
        
        # Check required files
        for required in ["rikka_hub.db", "settings.json"]:
            if required in files:
                print(f"   [OK] {required}")
            else:
                print(f"   [X] {required} MISSING!")
        
        # Count upload files
        upload_files = [f for f in files if f.startswith("upload/")]
        print(f"   [OK] upload/ files: {len(upload_files)}")
    
    print("=" * 60)


if __name__ == "__main__":
    verify_database()
    verify_zip()
    print("\n[OK] Verification complete!")
