import json
import sqlite3

SETTINGS = "The conversion test/after_import_extracted/settings.json"
DB = "The conversion test/after_import_extracted/rikka_hub.db"

with open(SETTINGS, 'r', encoding='utf-8') as f:
    s = json.load(f)

print("Settings Assistants:")
for a in s['assistants']:
    print(f"  {a['name']}: {a['id']}")
print(f"Current Assistant: {s['assistantId']}")

conn = sqlite3.connect(DB)
print("\nDB Conversations Assistants:")
for row in conn.execute("SELECT assistant_id, COUNT(*) FROM ConversationEntity GROUP BY assistant_id"):
    print(f"  {row[0]}: {row[1]}")
conn.close()
