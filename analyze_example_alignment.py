#!/usr/bin/env python3
"""Check how example backup's assistant IDs align."""
import sqlite3
import json
import sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

print("="*60)
print("EXAMPLE BACKUP ANALYSIS")
print("="*60)

# Settings
with open("The conversion test/example_extracted/settings.json", 'r', encoding='utf-8') as f:
    settings = json.load(f)

print("\nSETTINGS:")
print(f"  Current assistantId: {settings['assistantId']}")
print(f"  Assistants defined: {len(settings['assistants'])}")
for a in settings['assistants']:
    print(f"    - {a['name']}: {a['id']}")

# Database
conn = sqlite3.connect("The conversion test/example_extracted/rikka_hub.db")
print("\nDATABASE:")
print("  Conversation assistant_ids:")
for row in conn.execute("SELECT assistant_id, COUNT(*) FROM ConversationEntity GROUP BY assistant_id"):
    print(f"    - {row[0]}: {row[1]} convos")

print("\n  Memory assistant_ids:")
for row in conn.execute("SELECT assistant_id, COUNT(*) FROM MemoryEntity GROUP BY assistant_id"):
    print(f"    - {row[0]}: {row[1]} memories")

# THE KEY CHECK: Is the current assistantId in the conversations?
current = settings['assistantId']
match_count = conn.execute("SELECT COUNT(*) FROM ConversationEntity WHERE assistant_id=?", (current,)).fetchone()[0]
print(f"\n  Convos matching current assistantId: {match_count}")

# Check what assistant the conversations belong to
print("\n  Conversations belong to which defined assistant?")
for a in settings['assistants']:
    count = conn.execute("SELECT COUNT(*) FROM ConversationEntity WHERE assistant_id=?", (a['id'],)).fetchone()[0]
    print(f"    - {a['name']} ({a['id']}): {count}")

conn.close()

print("\n" + "="*60)
print("NOW CHECKING CONVERTED BACKUP")
print("="*60)

with open("The conversion test/converted/settings_fixed.json", 'r', encoding='utf-8') as f:
    cv_settings = json.load(f)

print("\nSETTINGS:")
print(f"  Current assistantId: {cv_settings['assistantId']}")
print(f"  Assistants defined: {len(cv_settings['assistants'])}")
for a in cv_settings['assistants']:
    print(f"    - {a['name']}: {a['id']}")

conn = sqlite3.connect("The conversion test/converted/rikka_hub.db")
print("\nDATABASE:")
print("  Conversations belong to which defined assistant?")
for a in cv_settings['assistants']:
    count = conn.execute("SELECT COUNT(*) FROM ConversationEntity WHERE assistant_id=?", (a['id'],)).fetchone()[0]
    print(f"    - {a['name']} ({a['id']}): {count}")

current = cv_settings['assistantId']
match_count = conn.execute("SELECT COUNT(*) FROM ConversationEntity WHERE assistant_id=?", (current,)).fetchone()[0]
print(f"\n  Convos matching current assistantId: {match_count}")

conn.close()
