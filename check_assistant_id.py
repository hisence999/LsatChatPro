import zipfile
import json
import uuid

backup_path = r"c:/Users/julia/Documents/LastChat-LastChat/backup example/LastChat_backup_20251203_022840.zip"

def is_valid_uuid(val):
    try:
        uuid.UUID(str(val))
        return True
    except ValueError:
        return False

try:
    with zipfile.ZipFile(backup_path, 'r') as z:
        if "settings.json" in z.namelist():
            with z.open("settings.json") as f:
                settings = json.load(f)
                aid = settings.get("assistantId")
                print(f"Assistant ID: {aid}")
                if is_valid_uuid(aid):
                    print("Assistant ID is valid.")
                else:
                    print("Assistant ID is INVALID.")
        else:
            print("settings.json not found.")
            
except Exception as e:
    print(f"Error: {e}")
