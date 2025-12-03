import sqlite3

def check_table_info(db_path):
    try:
        conn = sqlite3.connect(db_path)
        cursor = conn.cursor()
        print(f"--- Checking tables in {db_path} ---")
        
        # Check MemoryEntity
        print("\n[MemoryEntity Columns]")
        cursor.execute("PRAGMA table_info(MemoryEntity)")
        columns = cursor.fetchall()
        for col in columns:
            print(col)

        conn.close()
    except Exception as e:
        print(f"Error reading {db_path}: {e}")

if __name__ == "__main__":
    check_table_info(r"c:/Users/julia/Documents/LastChat-LastChat/backup example/LastChat_backup_20251203_022840/rikka_hub.db")
