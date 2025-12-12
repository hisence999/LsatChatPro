import sqlite3
import json
from collections import defaultdict

# Connect to the source database
conn = sqlite3.connect('The conversion test/extracted/rikka_hub.db')
conn.row_factory = sqlite3.Row
cursor = conn.cursor()

# Get detailed node types distribution
print("=== MEMORY NODE TYPES & TIERS ===")
cursor.execute("SELECT node_type, tier, COUNT(*) as cnt FROM memory_nodes GROUP BY node_type, tier")
for row in cursor.fetchall():
    print(dict(row))

# Get emotional distributions
print("\n=== EMOTIONAL DISTRIBUTION ===")
cursor.execute("SELECT dominant_emotion, COUNT(*) as cnt FROM memory_nodes GROUP BY dominant_emotion ORDER BY cnt DESC LIMIT 10")
for row in cursor.fetchall():
    print(dict(row))

# Check assistant IDs in memory_nodes
print("\n=== ASSISTANT IDS IN NODES ===")
cursor.execute("SELECT assistant_id, COUNT(*) as cnt FROM memory_nodes GROUP BY assistant_id")
for row in cursor.fetchall():
    print(dict(row))

# Check assistant IDs in MemoryEntity
print("\n=== ASSISTANT IDS IN MEMORY ENTITY ===")
cursor.execute("SELECT assistant_id, COUNT(*) as cnt FROM MemoryEntity GROUP BY assistant_id")
for row in cursor.fetchall():
    print(dict(row))

# Check assistant IDs in ChatEpisodeEntity  
print("\n=== ASSISTANT IDS IN CHAT EPISODE ===")
cursor.execute("SELECT assistant_id, COUNT(*) as cnt FROM ChatEpisodeEntity GROUP BY assistant_id")
for row in cursor.fetchall():
    print(dict(row))

# Examine memory_nodes content format
print("\n=== SAMPLE MEMORY NODES CONTENT ===")
cursor.execute("SELECT id, node_type, content, dominant_emotion FROM memory_nodes LIMIT 5")
for row in cursor.fetchall():
    d = dict(row)
    print(f"ID: {d['id']}, Type: {d['node_type']}, Emotion: {d['dominant_emotion']}")
    print(f"  Content: {d['content'][:300]}..." if d['content'] and len(d['content']) > 300 else f"  Content: {d['content']}")
    print()

# Check existing MemoryEntity type field
print("\n=== EXISTING MEMORY ENTITY TYPES ===")
cursor.execute("SELECT type, COUNT(*) as cnt FROM MemoryEntity GROUP BY type")
for row in cursor.fetchall():
    print(dict(row))

# Check conversation entity to understand linking
print("\n=== CONVERSATION SAMPLE ===")
cursor.execute("SELECT id, assistant_id, title, create_at FROM ConversationEntity LIMIT 3")
for row in cursor.fetchall():
    print(dict(row))

# Check if ChatEpisodeEntity has conversation_id set
print("\n=== CHAT EPISODE WITH CONVERSATION_ID ===")
cursor.execute("SELECT conversation_id, COUNT(*) as cnt FROM ChatEpisodeEntity WHERE conversation_id IS NOT NULL AND conversation_id != '' GROUP BY conversation_id LIMIT 5")
for row in cursor.fetchall():
    print(dict(row))

cursor.execute("SELECT COUNT(*) as cnt FROM ChatEpisodeEntity WHERE conversation_id IS NULL OR conversation_id = ''")
print(f"Episodes without conversation_id: {cursor.fetchone()['cnt']}")

conn.close()
