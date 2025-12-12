#!/usr/bin/env python3
"""
Backup Conversion Script
Converts an incompatible backup from a related app (with memory_nodes knowledge graph)
to be compatible with the target LastChat app's simpler MemoryEntity/ChatEpisodeEntity format.
"""

import sqlite3
import json
import os
import shutil
import zipfile
import re
from datetime import datetime
from collections import defaultdict

# Configuration
SOURCE_BACKUP_DIR = "The conversion test/extracted"
OUTPUT_DIR = "The conversion test/converted"
OUTPUT_ZIP = "The conversion test/LastChat_converted_backup.zip"

# Target database schema version 18
CREATE_SQL = {
    "ConversationEntity": """CREATE TABLE IF NOT EXISTS `ConversationEntity` (
        `id` TEXT NOT NULL, 
        `assistant_id` TEXT NOT NULL DEFAULT '0950e2dc-9bd5-4801-afa3-aa887aa36b4e', 
        `title` TEXT NOT NULL, 
        `nodes` TEXT NOT NULL, 
        `create_at` INTEGER NOT NULL, 
        `update_at` INTEGER NOT NULL, 
        `truncate_index` INTEGER NOT NULL DEFAULT -1, 
        `suggestions` TEXT NOT NULL DEFAULT '[]', 
        `is_pinned` INTEGER NOT NULL DEFAULT 0, 
        `is_consolidated` INTEGER NOT NULL DEFAULT 0, 
        PRIMARY KEY(`id`))""",
    
    "MemoryEntity": """CREATE TABLE IF NOT EXISTS `MemoryEntity` (
        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
        `assistant_id` TEXT NOT NULL, 
        `content` TEXT NOT NULL, 
        `embedding` TEXT, 
        `embedding_model_id` TEXT DEFAULT '', 
        `type` INTEGER NOT NULL DEFAULT 0, 
        `last_accessed_at` INTEGER NOT NULL DEFAULT 0, 
        `created_at` INTEGER NOT NULL DEFAULT 0)""",
    
    "GenMediaEntity": """CREATE TABLE IF NOT EXISTS `GenMediaEntity` (
        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
        `path` TEXT NOT NULL, 
        `model_id` TEXT NOT NULL, 
        `prompt` TEXT NOT NULL, 
        `create_at` INTEGER NOT NULL)""",
    
    "ChatEpisodeEntity": """CREATE TABLE IF NOT EXISTS `ChatEpisodeEntity` (
        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
        `assistant_id` TEXT NOT NULL, 
        `content` TEXT NOT NULL, 
        `embedding` TEXT, 
        `embedding_model_id` TEXT DEFAULT '', 
        `start_time` INTEGER NOT NULL, 
        `end_time` INTEGER NOT NULL, 
        `last_accessed_at` INTEGER NOT NULL DEFAULT 0, 
        `significance` INTEGER NOT NULL DEFAULT 5, 
        `conversation_id` TEXT DEFAULT '')""",
    
    "embedding_cache": """CREATE TABLE IF NOT EXISTS `embedding_cache` (
        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
        `memory_id` INTEGER NOT NULL, 
        `memory_type` INTEGER NOT NULL, 
        `model_id` TEXT NOT NULL, 
        `embedding` TEXT NOT NULL, 
        `created_at` INTEGER NOT NULL)""",
    
    "embedding_cache_index": """CREATE UNIQUE INDEX IF NOT EXISTS 
        `index_embedding_cache_memory_id_memory_type_model_id` 
        ON `embedding_cache` (`memory_id`, `memory_type`, `model_id`)""",
    
    "room_master": """CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)"""
}

# Room identity hash for version 18
ROOM_IDENTITY_HASH = "5549436f9c6325af2c133038a459ca63"


def normalize_text(text):
    """Normalize text for deduplication comparison."""
    if not text:
        return ""
    # Lowercase, strip punctuation, collapse whitespace
    text = text.lower()
    text = re.sub(r'[^\w\s]', '', text)
    text = re.sub(r'\s+', ' ', text)
    return text.strip()


def is_duplicate(content1, content2, threshold=0.9):
    """Check if two contents are duplicates using simple heuristics."""
    norm1 = normalize_text(content1)
    norm2 = normalize_text(content2)
    
    # Exact match
    if norm1 == norm2:
        return True
    
    # Substring containment
    if norm1 in norm2 or norm2 in norm1:
        return True
    
    # Very simple similarity check (word overlap)
    words1 = set(norm1.split())
    words2 = set(norm2.split())
    if not words1 or not words2:
        return False
    
    intersection = len(words1 & words2)
    union = len(words1 | words2)
    if union > 0 and intersection / union > threshold:
        return True
    
    return False


def get_richer_content(content1, content2, ts1, ts2):
    """Return the richer/more recent content."""
    # Prefer longer content
    if len(content1) > len(content2):
        return content1, ts1 if ts1 > ts2 else ts2
    elif len(content2) > len(content1):
        return content2, ts2 if ts2 > ts1 else ts1
    else:
        # Same length, prefer newer
        return (content1, ts1) if ts1 >= ts2 else (content2, ts2)


def format_memory_node_to_natural_language(node):
    """Convert memory_node to natural language content."""
    content = node['content']
    if not content:
        return None
    
    # The content is already mostly in natural language
    # Add emotional context if relevant
    emotion = node.get('dominant_emotion')
    if emotion and emotion not in ['neutral', 'None', None]:
        # Only add emotion context if it adds meaningful information
        if emotion in ['frustration', 'anxiety', 'fear', 'sadness', 'anger']:
            content = f"{content} (This tends to cause {emotion}.)"
        elif emotion in ['excitement', 'joy', 'happiness', 'pride', 'comfort']:
            content = f"{content} (This brings {emotion}.)"
    
    return content


def load_source_data(db_path):
    """Load all relevant data from the source database."""
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()
    
    data = {
        'memory_nodes': [],
        'memory_entity': [],
        'chat_episodes': [],
        'conversations': [],
        'gen_media': []
    }
    
    # Load memory_nodes (knowledge graph entries)
    try:
        cursor.execute("""
            SELECT id, assistant_id, node_type, content, embedding, embedding_model_id,
                   created_at, last_accessed_at, dominant_emotion, confidence
            FROM memory_nodes WHERE content IS NOT NULL AND content != ''
        """)
        data['memory_nodes'] = [dict(row) for row in cursor.fetchall()]
        print(f"Loaded {len(data['memory_nodes'])} memory_nodes")
    except Exception as e:
        print(f"No memory_nodes table or error: {e}")
    
    # Load existing MemoryEntity
    try:
        cursor.execute("""
            SELECT id, assistant_id, content, embedding, embedding_model_id, 
                   type, last_accessed_at, created_at
            FROM MemoryEntity
        """)
        data['memory_entity'] = [dict(row) for row in cursor.fetchall()]
        print(f"Loaded {len(data['memory_entity'])} existing MemoryEntity")
    except Exception as e:
        print(f"Error loading MemoryEntity: {e}")
    
    # Load ChatEpisodeEntity
    try:
        cursor.execute("""
            SELECT id, assistant_id, content, embedding, embedding_model_id,
                   start_time, end_time, last_accessed_at, significance, conversation_id
            FROM ChatEpisodeEntity
        """)
        data['chat_episodes'] = [dict(row) for row in cursor.fetchall()]
        print(f"Loaded {len(data['chat_episodes'])} ChatEpisodeEntity")
    except Exception as e:
        print(f"Error loading ChatEpisodeEntity: {e}")
    
    # Load ConversationEntity
    try:
        cursor.execute("""
            SELECT id, assistant_id, title, nodes, create_at, update_at,
                   truncate_index, suggestions, is_pinned, is_consolidated
            FROM ConversationEntity
        """)
        # Handle missing columns gracefully
        rows = cursor.fetchall()
        for row in rows:
            row_dict = dict(row)
            # Ensure all required columns have defaults
            row_dict.setdefault('truncate_index', -1)
            row_dict.setdefault('suggestions', '[]')
            row_dict.setdefault('is_pinned', 0)
            row_dict.setdefault('is_consolidated', 0)
            data['conversations'].append(row_dict)
        print(f"Loaded {len(data['conversations'])} ConversationEntity")
    except Exception as e:
        print(f"Error loading ConversationEntity: {e}")
        # Try loading with minimal columns
        try:
            cursor.execute("SELECT * FROM ConversationEntity")
            for row in cursor.fetchall():
                row_dict = dict(row)
                row_dict.setdefault('truncate_index', -1)
                row_dict.setdefault('suggestions', '[]')
                row_dict.setdefault('is_pinned', 0)
                row_dict.setdefault('is_consolidated', 0)
                data['conversations'].append(row_dict)
            print(f"Loaded {len(data['conversations'])} ConversationEntity (fallback)")
        except Exception as e2:
            print(f"Error loading ConversationEntity (fallback): {e2}")
    
    # Load GenMediaEntity
    try:
        cursor.execute("SELECT id, path, model_id, prompt, create_at FROM GenMediaEntity")
        data['gen_media'] = [dict(row) for row in cursor.fetchall()]
        print(f"Loaded {len(data['gen_media'])} GenMediaEntity")
    except Exception as e:
        print(f"Error loading GenMediaEntity: {e}")
    
    conn.close()
    return data


def convert_and_deduplicate_memories(data):
    """Convert memory_nodes to MemoryEntity format and deduplicate with existing memories."""
    
    # Group by assistant_id
    memories_by_assistant = defaultdict(list)
    
    # First, add converted memory_nodes
    for node in data['memory_nodes']:
        content = format_memory_node_to_natural_language(node)
        if content:
            memories_by_assistant[node['assistant_id']].append({
                'content': content,
                'created_at': node.get('created_at', 0) or 0,
                'last_accessed_at': node.get('last_accessed_at', 0) or 0,
                'source': 'memory_nodes',
                'type': 0  # CORE
            })
    
    # Then, add existing MemoryEntity entries
    for mem in data['memory_entity']:
        memories_by_assistant[mem['assistant_id']].append({
            'content': mem['content'],
            'created_at': mem.get('created_at', 0) or 0,
            'last_accessed_at': mem.get('last_accessed_at', 0) or 0,
            'source': 'memory_entity',
            'type': mem.get('type', 0)
        })
    
    # Deduplicate within each assistant
    deduplicated = []
    total_before = sum(len(mems) for mems in memories_by_assistant.values())
    
    for assistant_id, memories in memories_by_assistant.items():
        # Sort by created_at to process older first (newer will override)
        memories.sort(key=lambda x: x['created_at'])
        
        unique_memories = []
        seen_normalized = {}
        
        for mem in memories:
            normalized = normalize_text(mem['content'])
            
            # Skip empty content
            if not normalized:
                continue
            
            # Check if we've seen something similar
            is_dup = False
            for seen_norm, idx in seen_normalized.items():
                if is_duplicate(mem['content'], unique_memories[idx]['content'], threshold=0.85):
                    # Keep the richer/newer one
                    existing = unique_memories[idx]
                    new_content, new_ts = get_richer_content(
                        existing['content'], mem['content'],
                        existing['created_at'], mem['created_at']
                    )
                    unique_memories[idx]['content'] = new_content
                    unique_memories[idx]['created_at'] = new_ts
                    unique_memories[idx]['last_accessed_at'] = max(
                        existing['last_accessed_at'], 
                        mem['last_accessed_at']
                    )
                    is_dup = True
                    break
            
            if not is_dup:
                seen_normalized[normalized] = len(unique_memories)
                unique_memories.append({
                    'assistant_id': assistant_id,
                    'content': mem['content'],
                    'created_at': mem['created_at'],
                    'last_accessed_at': mem['last_accessed_at'],
                    'type': mem['type']
                })
        
        deduplicated.extend(unique_memories)
    
    # Sort by created_at
    deduplicated.sort(key=lambda x: x['created_at'])
    
    print(f"Deduplicated memories: {total_before} -> {len(deduplicated)}")
    return deduplicated


def process_episodes(data):
    """Process and order episodic memories."""
    episodes = []
    
    for ep in data['chat_episodes']:
        episodes.append({
            'assistant_id': ep['assistant_id'],
            'content': ep['content'],
            'embedding': None,  # Skip embeddings
            'embedding_model_id': '',
            'start_time': ep['start_time'],
            'end_time': ep['end_time'],
            'last_accessed_at': ep.get('last_accessed_at', 0) or 0,
            'significance': ep.get('significance', 5),
            'conversation_id': ep.get('conversation_id', '') or ''
        })
    
    # Sort by start_time
    episodes.sort(key=lambda x: x['start_time'])
    
    print(f"Processed {len(episodes)} episodes")
    return episodes


def try_link_orphan_episodes(episodes, conversations):
    """Try to link episodes without conversation_id to their conversations based on time overlap."""
    # Build a lookup of conversations by time range
    conv_by_id = {c['id']: c for c in conversations}
    
    linked_count = 0
    for ep in episodes:
        if ep['conversation_id']:
            continue  # Already linked
        
        # Try to find a matching conversation by time overlap
        for conv in conversations:
            if conv['assistant_id'] != ep['assistant_id']:
                continue
            
            # Check if episode time overlaps with conversation time
            conv_start = conv.get('create_at', 0)
            conv_end = conv.get('update_at', 0)
            
            if ep['start_time'] >= conv_start and ep['start_time'] <= conv_end:
                ep['conversation_id'] = conv['id']
                linked_count += 1
                break
    
    if linked_count > 0:
        print(f"Linked {linked_count} orphan episodes to conversations")


def create_target_database(output_path, memories, episodes, conversations, gen_media):
    """Create the target database with converted data."""
    
    if os.path.exists(output_path):
        os.remove(output_path)
    
    conn = sqlite3.connect(output_path)
    cursor = conn.cursor()
    
    # Create tables
    for name, sql in CREATE_SQL.items():
        cursor.execute(sql)
    
    # Insert Room master table identity hash
    cursor.execute(
        "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)",
        (ROOM_IDENTITY_HASH,)
    )
    
    # Insert conversations
    for conv in conversations:
        cursor.execute("""
            INSERT INTO ConversationEntity 
            (id, assistant_id, title, nodes, create_at, update_at, truncate_index, 
             suggestions, is_pinned, is_consolidated)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            conv['id'],
            conv['assistant_id'],
            conv['title'],
            conv['nodes'],
            conv['create_at'],
            conv['update_at'],
            conv.get('truncate_index', -1),
            conv.get('suggestions', '[]'),
            1 if conv.get('is_pinned') else 0,
            1 if conv.get('is_consolidated') else 0
        ))
    print(f"Inserted {len(conversations)} conversations")
    
    # Insert memories
    for mem in memories:
        cursor.execute("""
            INSERT INTO MemoryEntity 
            (assistant_id, content, embedding, embedding_model_id, type, last_accessed_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """, (
            mem['assistant_id'],
            mem['content'],
            None,  # Skip embedding
            '',
            mem.get('type', 0),
            mem.get('last_accessed_at', 0),
            mem.get('created_at', 0)
        ))
    print(f"Inserted {len(memories)} memories")
    
    # Insert episodes
    for ep in episodes:
        cursor.execute("""
            INSERT INTO ChatEpisodeEntity 
            (assistant_id, content, embedding, embedding_model_id, start_time, end_time, 
             last_accessed_at, significance, conversation_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            ep['assistant_id'],
            ep['content'],
            None,  # Skip embedding
            '',
            ep['start_time'],
            ep['end_time'],
            ep.get('last_accessed_at', 0),
            ep.get('significance', 5),
            ep.get('conversation_id', '')
        ))
    print(f"Inserted {len(episodes)} episodes")
    
    # Insert gen_media
    for media in gen_media:
        cursor.execute("""
            INSERT INTO GenMediaEntity 
            (path, model_id, prompt, create_at)
            VALUES (?, ?, ?, ?)
        """, (
            media['path'],
            media['model_id'],
            media['prompt'],
            media['create_at']
        ))
    print(f"Inserted {len(gen_media)} gen_media")
    
    conn.commit()
    conn.close()
    print(f"Created target database at {output_path}")


def create_backup_zip(output_dir, output_zip, settings_path=None):
    """Create the final backup zip file."""
    
    # Remove existing zip
    if os.path.exists(output_zip):
        os.remove(output_zip)
    
    with zipfile.ZipFile(output_zip, 'w', zipfile.ZIP_DEFLATED) as zf:
        # Add database file
        db_path = os.path.join(output_dir, "rikka_hub.db")
        zf.write(db_path, "rikka_hub.db")
        print(f"Added rikka_hub.db to zip")
        
        # Add settings.json from source
        if settings_path and os.path.exists(settings_path):
            zf.write(settings_path, "settings.json")
            print(f"Added settings.json to zip")
        
        # Add upload folder contents
        upload_src = os.path.join(SOURCE_BACKUP_DIR, "upload")
        if os.path.exists(upload_src):
            for filename in os.listdir(upload_src):
                file_path = os.path.join(upload_src, filename)
                if os.path.isfile(file_path):
                    zf.write(file_path, f"upload/{filename}")
            print(f"Added upload files to zip")
    
    print(f"Created backup zip at {output_zip}")
    
    # Get file size
    size_mb = os.path.getsize(output_zip) / (1024 * 1024)
    print(f"Backup size: {size_mb:.2f} MB")


def main():
    print("=" * 60)
    print("Backup Conversion Script")
    print("=" * 60)
    
    # Ensure output directory exists
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    # Load source data
    source_db = os.path.join(SOURCE_BACKUP_DIR, "rikka_hub.db")
    print(f"\nLoading source data from {source_db}...")
    data = load_source_data(source_db)
    
    # Convert and deduplicate memories
    print("\nConverting and deduplicating memories...")
    memories = convert_and_deduplicate_memories(data)
    
    # Process episodes
    print("\nProcessing episodic memories...")
    episodes = process_episodes(data)
    
    # Try to link orphan episodes
    print("\nLinking orphan episodes to conversations...")
    try_link_orphan_episodes(episodes, data['conversations'])
    
    # Create target database
    output_db = os.path.join(OUTPUT_DIR, "rikka_hub.db")
    print(f"\nCreating target database...")
    create_target_database(
        output_db,
        memories,
        episodes,
        data['conversations'],
        data['gen_media']
    )
    
    # Create backup zip
    settings_path = os.path.join(SOURCE_BACKUP_DIR, "settings.json")
    print(f"\nCreating backup zip...")
    create_backup_zip(OUTPUT_DIR, OUTPUT_ZIP, settings_path)
    
    # Summary
    print("\n" + "=" * 60)
    print("Conversion Complete!")
    print("=" * 60)
    print(f"Output: {OUTPUT_ZIP}")
    print(f"Core memories: {len(memories)}")
    print(f"Episodic memories: {len(episodes)}")
    print(f"Conversations: {len(data['conversations'])}")
    print("=" * 60)


if __name__ == "__main__":
    main()
