import sqlite3
import uuid

def is_valid_uuid(val):
    try:
        uuid.UUID(str(val))
        return True
    except ValueError:
        return False

def check_uuids(db_path):
    try:
        conn = sqlite3.connect(db_path)
        cursor = conn.cursor()
        print(f"--- Checking UUIDs in {db_path} ---")
        
        cursor.execute("SELECT id, assistant_id FROM ConversationEntity")
        rows = cursor.fetchall()
        
        print(f"Checking {len(rows)} rows...")
        for row in rows:
            id_val = row[0]
            assistant_id = row[1]
            
            if not is_valid_uuid(id_val):
                print(f"INVALID ID: {id_val}")
            
            if not is_valid_uuid(assistant_id):
                print(f"INVALID ASSISTANT_ID: {assistant_id}")

        print("Check complete.")
        conn.close()
    except Exception as e:
        print(f"Error reading {db_path}: {e}")

if __name__ == "__main__":
    check_uuids(r"c:/Users/julia/Documents/LastChat-LastChat/backup example/LastChat_backup_20251203_022840/rikka_hub.db")
