#!/usr/bin/env python3
"""Check assistant ID alignment between settings and database."""
import sqlite3
import json
import sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

DB = "The conversion test/converted/rikka_hub.db"
SETTINGS = "The conversion test/converted/settings_fixed.json"

print("="*60)
print("ASSISTANT ID ALIGNMENT CHECK")
print("="*60)

# Settings
with open(SETTINGS, 'r', encoding='utf-8') as f:
    settings = json.load(f)

print("\nASSISTANTS IN SETTINGS:")
for asst in settings['assistants']:
    print(f"  {asst['name']}: {asst['id']}")

print(f"\nCURRENT ASSISTANT ID: {settings['assistantId']}")

# Database
conn = sqlite3.connect(DB)

print("\nASSISTANT IDS IN DATABASE CONVERSATIONS:")
for row in conn.execute("SELECT assistant_id, COUNT(*) as cnt FROM ConversationEntity GROUP BY assistant_id"):
    print(f"  {row[0]}: {row[1]} conversations")

print("\nASSISTANT IDS IN DATABASE MEMORIES:")
for row in conn.execute("SELECT assistant_id, COUNT(*) as cnt FROM MemoryEntity GROUP BY assistant_id"):
    print(f"  {row[0]}: {row[1]} memories")

print("\nASSISTANT IDS IN DATABASE EPISODES:")
for row in conn.execute("SELECT assistant_id, COUNT(*) as cnt FROM ChatEpisodeEntity GROUP BY assistant_id"):
    print(f"  {row[0]}: {row[1]} episodes")

# Check if the current assistant ID is in the database
current_asst = settings['assistantId']
print(f"\n\nCHECK: Is current assistant '{current_asst}' in database?")
convo_count = conn.execute("SELECT COUNT(*) FROM ConversationEntity WHERE assistant_id = ?", (current_asst,)).fetchone()[0]
print(f"  Conversations for current assistant: {convo_count}")

conn.close()
