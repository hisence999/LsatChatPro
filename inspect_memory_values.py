import sqlite3

def check_memory_values(db_path):
    try:
        conn = sqlite3.connect(db_path)
        cursor = conn.cursor()
        print(f"--- Checking MemoryEntity values in {db_path} ---")
        
        cursor.execute("SELECT id, type FROM MemoryEntity")
        rows = cursor.fetchall()
        print(f"Found {len(rows)} memories.")
        for row in rows:
            print(f"ID: {row[0]}, Type: {row[1]}")

        conn.close()
    except Exception as e:
        print(f"Error reading {db_path}: {e}")

if __name__ == "__main__":
    check_memory_values(r"c:/Users/julia/Documents/LastChat-LastChat/backup example/LastChat_backup_20251203_022840/rikka_hub.db")
