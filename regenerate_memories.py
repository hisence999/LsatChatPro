#!/usr/bin/env python3
"""
Comprehensive script to regenerate all Clara Nightingale memories.
Reads all conversations, generates episodic summaries, and extracts core memories.
"""

import sqlite3
import json
import os
import re
from datetime import datetime
from collections import defaultdict

SOURCE_DB = "The conversion test/extracted/rikka_hub.db"
OUTPUT_DIR = "The conversion test/converted"
CLARA_ASSISTANT_ID = "52c1179c-29e4-49b5-becf-024efe9f5f3e"
GENERICAL_ASSISTANT_ID = "c45dac7a-b3d6-48a4-8d09-433f7d2e6cab"

def extract_messages_from_nodes(nodes_json):
    """Extract user and assistant messages from conversation nodes."""
    try:
        nodes = json.loads(nodes_json)
    except:
        return []
    
    messages = []
    for node in nodes:
        if 'messages' not in node:
            continue
        for msg in node['messages']:
            role = msg.get('role', '')
            parts = msg.get('parts', [])
            text_parts = []
            for part in parts:
                if isinstance(part, dict):
                    text = part.get('text', '')
                    if text:
                        text_parts.append(text)
            if text_parts:
                full_text = ' '.join(text_parts)
                created_at = msg.get('createdAt', '')
                messages.append({
                    'role': role,
                    'text': full_text,
                    'created_at': created_at
                })
    return messages


def load_all_clara_conversations(db_path):
    """Load all Clara Nightingale conversations."""
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()
    
    cursor.execute("""
        SELECT id, title, nodes, create_at, update_at 
        FROM ConversationEntity 
        WHERE assistant_id = ?
        ORDER BY create_at
    """, (CLARA_ASSISTANT_ID,))
    
    conversations = []
    for row in cursor.fetchall():
        messages = extract_messages_from_nodes(row['nodes'])
        if messages:  # Only include conversations with messages
            conversations.append({
                'id': row['id'],
                'title': row['title'],
                'messages': messages,
                'create_at': row['create_at'],
                'update_at': row['update_at']
            })
    
    conn.close()
    return conversations


def extract_user_info_from_conversation(conversation):
    """Extract potential user information from a conversation."""
    user_messages = [m['text'] for m in conversation['messages'] if m['role'] == 'user']
    all_user_text = ' '.join(user_messages)
    
    info = {
        'mentioned_names': set(),
        'mentioned_places': set(),
        'mentioned_preferences': [],
        'mentioned_activities': [],
        'emotional_context': [],
        'facts_shared': []
    }
    
    return all_user_text


def output_conversations_for_review(conversations, output_path):
    """Output all conversations in readable format for manual review."""
    with open(output_path, 'w', encoding='utf-8') as f:
        f.write("=" * 80 + "\n")
        f.write("CLARA NIGHTINGALE CONVERSATIONS - FOR MEMORY EXTRACTION\n")
        f.write("=" * 80 + "\n\n")
        
        for i, conv in enumerate(conversations, 1):
            f.write(f"\n{'='*80}\n")
            f.write(f"CONVERSATION {i}: {conv['title']}\n")
            f.write(f"ID: {conv['id']}\n")
            f.write(f"Date: {datetime.fromtimestamp(conv['create_at']/1000).strftime('%Y-%m-%d %H:%M')}\n")
            f.write(f"{'='*80}\n\n")
            
            for msg in conv['messages']:
                role_label = "Julian" if msg['role'] == 'user' else "Clara"
                f.write(f"[{role_label}]:\n")
                # Truncate very long messages
                text = msg['text']
                if len(text) > 2000:
                    text = text[:2000] + "... [truncated]"
                f.write(f"{text}\n\n")
            
            f.write("\n")
    
    print(f"Wrote {len(conversations)} conversations to {output_path}")


def main():
    print("=" * 60)
    print("Clara Nightingale Memory Regeneration")
    print("=" * 60)
    
    # Load all conversations
    print("\nLoading conversations...")
    conversations = load_all_clara_conversations(SOURCE_DB)
    print(f"Loaded {len(conversations)} conversations with messages")
    
    # Calculate stats
    total_messages = sum(len(c['messages']) for c in conversations)
    user_messages = sum(len([m for m in c['messages'] if m['role'] == 'user']) for c in conversations)
    print(f"Total messages: {total_messages}")
    print(f"User messages: {user_messages}")
    
    # Output for review
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    output_conversations_for_review(conversations, "The conversion test/clara_conversations_readable.txt")
    
    # Also output as JSON for processing
    with open("The conversion test/clara_conversations_full.json", 'w', encoding='utf-8') as f:
        json.dump(conversations, f, indent=2, ensure_ascii=False)
    
    print("\nConversations extracted. Ready for memory generation.")


if __name__ == "__main__":
    main()
