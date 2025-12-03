import sqlite3
import json

db_path = r"c:/Users/julia/Documents/LastChat-LastChat/backup example/LastChat_backup_20251203_022840/rikka_hub.db"

try:
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()
    
    cursor.execute("SELECT id, nodes FROM ConversationEntity LIMIT 1")
    row = cursor.fetchone()
    
    if row:
        print(f"Conversation ID: {row[0]}")
        nodes_json = row[1]
        try:
            nodes = json.loads(nodes_json)
            print(json.dumps(nodes, indent=2))
        except json.JSONDecodeError as e:
            print(f"JSON Decode Error: {e}")
            print(nodes_json[:500]) # Print first 500 chars
    else:
        print("No conversations found.")
        
    conn.close()

except Exception as e:
    print(f"Error: {e}")
