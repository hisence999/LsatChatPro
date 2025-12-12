#!/usr/bin/env python3
"""Compare conversation nodes format between source and example."""

import sqlite3
import json
import sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

SOURCE = "The conversion test/extracted/rikka_hub.db"
EXAMPLE = "The conversion test/example_extracted/rikka_hub.db"
CONVERTED = "The conversion test/converted/rikka_hub.db"

def analyze_nodes(db_path, label):
    print(f"\n{'='*60}")
    print(f"ANALYZING: {label}")
    print(f"{'='*60}")
    
    conn = sqlite3.connect(db_path)
    row = conn.execute("SELECT nodes FROM ConversationEntity LIMIT 1").fetchone()
    
    if not row:
        print("No conversations found!")
        conn.close()
        return
    
    nodes_json = row[0]
    print(f"\nRaw JSON length: {len(nodes_json)} chars")
    print(f"\nFirst 500 chars of nodes:")
    print(nodes_json[:500])
    
    try:
        nodes = json.loads(nodes_json)
        print(f"\n\nParsed successfully. Type: {type(nodes)}")
        print(f"Number of nodes: {len(nodes)}")
        
        if nodes:
            first_node = nodes[0]
            print(f"\nFirst node keys: {first_node.keys()}")
            
            if 'messages' in first_node and first_node['messages']:
                first_msg = first_node['messages'][0]
                print(f"\nFirst message keys: {first_msg.keys()}")
                print(f"\nFirst message sample:")
                print(json.dumps(first_msg, indent=2, ensure_ascii=False)[:1000])
            
    except Exception as e:
        print(f"\nJSON PARSE ERROR: {e}")
    
    conn.close()

analyze_nodes(EXAMPLE, "EXAMPLE (correct format)")
analyze_nodes(SOURCE, "SOURCE (incompatible backup)")
analyze_nodes(CONVERTED, "CONVERTED (what we created)")
