#!/usr/bin/env python3
"""Verify the regenerated memories quality."""

import sqlite3
import sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

DB = "The conversion test/converted/rikka_hub.db"

conn = sqlite3.connect(DB)
conn.row_factory = sqlite3.Row
cursor = conn.cursor()

print("=" * 60)
print("VERIFICATION OF REGENERATED MEMORIES")
print("=" * 60)

# Count by type
print("\n1. RECORD COUNTS")
for table in ["ConversationEntity", "MemoryEntity", "ChatEpisodeEntity"]:
    cursor.execute(f"SELECT COUNT(*) FROM {table}")
    print(f"   {table}: {cursor.fetchone()[0]}")

# Memory distribution by assistant
print("\n2. MEMORY DISTRIBUTION BY ASSISTANT")
cursor.execute("SELECT assistant_id, COUNT(*) as cnt FROM MemoryEntity GROUP BY assistant_id")
for row in cursor.fetchall():
    print(f"   {row['assistant_id'][:8]}...: {row['cnt']}")

# Episode distribution by assistant
print("\n3. EPISODE DISTRIBUTION BY ASSISTANT")
cursor.execute("SELECT assistant_id, COUNT(*) as cnt FROM ChatEpisodeEntity GROUP BY assistant_id")
for row in cursor.fetchall():
    print(f"   {row['assistant_id'][:8]}...: {row['cnt']}")

# Sample core memories
print("\n4. SAMPLE CORE MEMORIES (Clara)")
cursor.execute("SELECT content FROM MemoryEntity WHERE assistant_id = '52c1179c-29e4-49b5-becf-024efe9f5f3e' LIMIT 10")
for i, row in enumerate(cursor.fetchall(), 1):
    print(f"   {i}. {row['content']}")

# Sample episodic memories
print("\n5. SAMPLE EPISODIC SUMMARIES (Clara)")
cursor.execute("""
    SELECT content, conversation_id 
    FROM ChatEpisodeEntity 
    WHERE assistant_id = '52c1179c-29e4-49b5-becf-024efe9f5f3e'
    LIMIT 5
""")
for i, row in enumerate(cursor.fetchall(), 1):
    content = row['content'][:150] + "..." if len(row['content']) > 150 else row['content']
    print(f"   {i}. {content}")
    print(f"      ConvID: {row['conversation_id'][:8]}...")
    print()

# Check Generical preserved
print("6. GENERICAL DATA (PRESERVED)")
cursor.execute("SELECT content FROM MemoryEntity WHERE assistant_id = 'c45dac7a-b3d6-48a4-8d09-433f7d2e6cab'")
for row in cursor.fetchall():
    content = row['content'][:100] + "..." if len(row['content']) > 100 else row['content']
    print(f"   Memory: {content}")

cursor.execute("SELECT content FROM ChatEpisodeEntity WHERE assistant_id = 'c45dac7a-b3d6-48a4-8d09-433f7d2e6cab'")
for row in cursor.fetchall():
    content = row['content'][:100] + "..." if len(row['content']) > 100 else row['content']
    print(f"   Episode: {content}")

# Room hash check
print("\n7. DATABASE VERSION CHECK")
cursor.execute("SELECT identity_hash FROM room_master_table WHERE id=42")
row = cursor.fetchone()
expected = "5549436f9c6325af2c133038a459ca63"
if row and row[0] == expected:
    print(f"   [OK] Room hash matches (version 18)")
else:
    print(f"   [X] Hash mismatch!")

conn.close()
print("\n" + "=" * 60)
print("[OK] Verification complete!")
