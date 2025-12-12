#!/usr/bin/env python3
"""
Extract all Clara Nightingale conversations for analysis.
"""

import sqlite3
import json
import os

SOURCE_DB = "The conversion test/extracted/rikka_hub.db"
CLARA_ASSISTANT_ID = "52c1179c-29e4-49b5-becf-024efe9f5f3e"

conn = sqlite3.connect(SOURCE_DB)
conn.row_factory = sqlite3.Row
cursor = conn.cursor()

# Get all Clara conversations
cursor.execute("""
    SELECT id, title, nodes, create_at, update_at 
    FROM ConversationEntity 
    WHERE assistant_id = ?
    ORDER BY create_at
""", (CLARA_ASSISTANT_ID,))

conversations = cursor.fetchall()
print(f"Found {len(conversations)} Clara conversations")

# Output all conversations for analysis
output_file = "The conversion test/clara_conversations.jsonl"
with open(output_file, 'w', encoding='utf-8') as f:
    for conv in conversations:
        data = {
            'id': conv['id'],
            'title': conv['title'],
            'nodes': conv['nodes'],
            'create_at': conv['create_at'],
            'update_at': conv['update_at']
        }
        f.write(json.dumps(data, ensure_ascii=False) + '\n')

print(f"Wrote conversations to {output_file}")

# Also show some sample to understand the node format
print("\n=== SAMPLE CONVERSATION NODES FORMAT ===")
sample = conversations[0]
nodes_data = json.loads(sample['nodes'])
print(f"Title: {sample['title']}")
print(f"Nodes type: {type(nodes_data)}")
if isinstance(nodes_data, list) and len(nodes_data) > 0:
    print(f"First node keys: {nodes_data[0].keys() if isinstance(nodes_data[0], dict) else 'not a dict'}")
    print(f"Sample node: {json.dumps(nodes_data[0], indent=2, ensure_ascii=False)[:1000]}")

conn.close()
