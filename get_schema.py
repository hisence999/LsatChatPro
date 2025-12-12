#!/usr/bin/env python3
"""Get exact schema from example database."""
import sqlite3
import sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

conn = sqlite3.connect("The conversion test/example_extracted/rikka_hub.db")

print("TABLES:")
for r in conn.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"):
    print(f"  {r[0]}")

print("\nVERSION:", conn.execute("PRAGMA user_version").fetchone()[0])
print("\nROOM:", conn.execute("SELECT * FROM room_master_table").fetchone())

print("\n" + "="*60)
print("MemoryEntity SCHEMA:")
print(conn.execute("SELECT sql FROM sqlite_master WHERE name='MemoryEntity'").fetchone()[0])

print("\n" + "="*60)
print("ChatEpisodeEntity SCHEMA:")
print(conn.execute("SELECT sql FROM sqlite_master WHERE name='ChatEpisodeEntity'").fetchone()[0])

print("\n" + "="*60)
print("ConversationEntity SCHEMA:")
print(conn.execute("SELECT sql FROM sqlite_master WHERE name='ConversationEntity'").fetchone()[0])

print("\n" + "="*60)
print("MemoryEntity COLUMNS:")
for col in conn.execute("PRAGMA table_info(MemoryEntity)"):
    print(f"  {col}")

print("\nChatEpisodeEntity COLUMNS:")
for col in conn.execute("PRAGMA table_info(ChatEpisodeEntity)"):
    print(f"  {col}")

print("\nConversationEntity COLUMNS:")
for col in conn.execute("PRAGMA table_info(ConversationEntity)"):
    print(f"  {col}")

conn.close()
