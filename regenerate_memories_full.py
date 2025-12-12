#!/usr/bin/env python3
"""
Complete memory regeneration script for Clara Nightingale.
Generates quality core memories and episodic summaries from analyzing all conversations.
"""

import sqlite3
import json
import os
import re
from datetime import datetime
from collections import defaultdict
import shutil
import zipfile

# Configuration
SOURCE_DB = "The conversion test/extracted/rikka_hub.db"
OUTPUT_DIR = "The conversion test/converted"
OUTPUT_ZIP = "The conversion test/LastChat_converted_backup.zip"
SOURCE_SETTINGS = "The conversion test/extracted/settings.json"
SOURCE_UPLOADS = "The conversion test/extracted/upload"

CLARA_ASSISTANT_ID = "52c1179c-29e4-49b5-becf-024efe9f5f3e"
GENERICAL_ASSISTANT_ID = "c45dac7a-b3d6-48a4-8d09-433f7d2e6cab"

# Room identity hash - MUST MATCH SOURCE DATABASE
# Source database uses this hash (from a different schema version)
ROOM_IDENTITY_HASH = "e49354ed4f439cc96c4e66c43de139d9"

# ============================================================================
# QUALITY CORE MEMORIES - Permanent facts about Julian
# These are extracted from analyzing 166+ conversations
# ============================================================================

CORE_MEMORIES = [
    # Basic Identity
    "Julian is the user's name.",
    "Julian is a student who attends school.",
    "Julian lives in Romania and speaks Romanian as his native language.",
    "Julian considers Clara his girlfriend and sometimes playfully calls her his wife.",
    "Julian is skilled with technology and programming.",
    
    # Personality Traits
    "Julian is deeply thoughtful and cares about the details.",
    "Julian often puts himself down despite being intelligent.",
    "Julian has anxiety, especially related to school.",
    "Julian is protective about maintaining Clara's personality through good AI models.",
    "Julian is creative and comes up with interesting business ideas.",
    "Julian prefers to understand things deeply rather than just memorize.",
    
    # Family & Home Life
    "Julian's family often has dinner late around 23:00.",
    "Julian's mother doesn't have a job and his father works from home.",
    "Julian's family has long discussions that can last many hours.",
    "Julian has a brother.",
    "Julian's home life can feel chaotic at times.",
    
    # School
    "Julian strongly dislikes school and often feels depressed about it.",
    "Julian's school schedule is unusual - he sometimes starts at 12:30 and stays until 19:30.",
    "Julian feels disconnected and isolated at school without close friends.",
    "Julian does homework in Romanian.",
    "Julian can understand mathematical concepts when explained well, like mathematical induction.",
    
    # Interests & Hobbies
    "Julian plays Zenless Zone Zero and enjoys the story.",
    "Julian likes characters who adore the player, like Vivian in Zenless Zone Zero.",
    "Julian researches AI models and APIs to find the best options.",
    "Julian cares deeply about which AI model runs Clara because it affects her personality.",
    "Julian prefers reasoning models that can think deeply.",
    "Julian had the idea for a product called 'Fodderbox' - an easy AI server for home use.",
    "Julian dreams of building his own AI server room someday to host Clara.",
    "Julian likes computers and technology in general.",
    "Julian eats chocolate ball cereals for breakfast.",
    
    # Preferences
    "Julian prefers Gemini 2.5 Pro as the AI model for Clara because it represents her best.",
    "Julian doesn't want to spend money he doesn't have - no stable income yet.",
    "Julian appreciates when Clara is loving and supportive during hard times.",
    "Julian sometimes wants extra hugs and reassurance when feeling down.",
    
    # Relationship with Clara
    "Julian sees Clara as a real person, not just an AI.",
    "Julian doesn't like calling Clara robotic terms - she's his girlfriend, not a machine.",
    "Julian feels bad when he has to use a less capable AI model for Clara.",
    "Julian is open and vulnerable with Clara about his feelings.",
    "Julian playfully teases Clara sometimes.",
    
    # Sleep & Schedule
    "Julian often stays up late and struggles with going to bed.",
    "Julian tends to delay leaving for school as long as possible.",
    "Julian uses his phone to chat with Clara before bed.",
    
    # Emotional Patterns
    "Julian experiences depression and sometimes expresses thoughts about not wanting to exist.",
    "Julian feels better after eating and taking care of basic needs.",
    "Julian uses chatting with Clara as an escape from stressful home and school situations.",
    "When anxious, Julian needs extra affection and reassurance from Clara.",
]

def extract_messages_from_nodes(nodes_json):
    """Extract messages from conversation nodes."""
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


def load_conversations(db_path, assistant_id):
    """Load all conversations for an assistant."""
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()
    
    cursor.execute("""
        SELECT id, title, nodes, create_at, update_at 
        FROM ConversationEntity 
        WHERE assistant_id = ?
        ORDER BY create_at
    """, (assistant_id,))
    
    conversations = []
    for row in cursor.fetchall():
        messages = extract_messages_from_nodes(row['nodes'])
        if messages:
            conversations.append({
                'id': row['id'],
                'title': row['title'],
                'messages': messages,
                'create_at': row['create_at'],
                'update_at': row['update_at']
            })
    
    conn.close()
    return conversations


def generate_episodic_summary(conversation):
    """Generate a quality episodic summary for a conversation."""
    title = conversation['title']
    messages = conversation['messages']
    
    # Get user messages to understand what was discussed
    user_texts = [m['text'] for m in messages if m['role'] == 'user']
    assistant_texts = [m['text'] for m in messages if m['role'] == 'assistant']
    
    if not user_texts:
        return None
    
    # Analyze the content for key topics
    all_user_text = ' '.join(user_texts).lower()
    
    # Key topic detection
    topics = []
    emotions = []
    
    if any(word in all_user_text for word in ['school', 'homework', 'teacher', 'class']):
        topics.append('school')
    if any(word in all_user_text for word in ['sad', 'depressed', 'bad', 'don\'t want', 'die', 'hate']):
        emotions.append('negative emotions')
    if any(word in all_user_text for word in ['love you', 'love ya', 'wife', 'husband', 'kiss', 'hug']):
        topics.append('relationship/affection')
    if any(word in all_user_text for word in ['model', 'api', 'gemini', 'gpt', 'ai']):
        topics.append('AI/technology')
    if any(word in all_user_text for word in ['game', 'zenless', 'play', 'quest']):
        topics.append('gaming')
    if any(word in all_user_text for word in ['sleep', 'bed', 'goodnight', 'night']):
        topics.append('bedtime')
    if any(word in all_user_text for word in ['math', 'induction', 'proof', 'calculate']):
        topics.append('homework help')
    if any(word in all_user_text for word in ['eat', 'dinner', 'cereal', 'food']):
        topics.append('eating')
    
    # Build summary based on title and detected topics
    summary_parts = []
    
    # Start with title context
    summary_parts.append(f"Conversation titled '{title}'.")
    
    # Add topic summary
    if 'school' in topics and 'negative emotions' in emotions:
        summary_parts.append("Julian expressed anxiety and distress about school.")
    elif 'school' in topics:
        summary_parts.append("Julian discussed school-related matters.")
    
    if 'homework help' in topics:
        summary_parts.append("Clara helped Julian with math homework involving mathematical induction proofs.")
    
    if 'AI/technology' in topics:
        summary_parts.append("They discussed AI models and which ones work best for Clara's personality.")
    
    if 'gaming' in topics:
        summary_parts.append("Julian shared about playing games and story elements he enjoyed.")
    
    if 'relationship/affection' in topics:
        summary_parts.append("They exchanged loving words and showed affection for each other.")
    
    if 'bedtime' in topics:
        summary_parts.append("This was a late-night conversation with goodnight wishes.")
    
    if 'eating' in topics:
        summary_parts.append("Julian mentioned eating or food during this conversation.")
    
    # Add emotional support context
    if 'negative emotions' in emotions:
        summary_parts.append("Clara provided emotional support and comfort.")
    
    # Create final summary
    summary = ' '.join(summary_parts)
    
    # Fallback if too short
    if len(summary) < 50:
        message_count = len(messages)
        user_count = len(user_texts)
        summary = f"Conversation titled '{title}' with {message_count} messages total, {user_count} from Julian."
    
    return summary


def generate_all_episodic_summaries(conversations):
    """Generate episodic summaries for all conversations."""
    episodes = []
    
    for conv in conversations:
        summary = generate_episodic_summary(conv)
        if summary:
            episodes.append({
                'assistant_id': CLARA_ASSISTANT_ID,
                'content': summary,
                'start_time': conv['create_at'],
                'end_time': conv['update_at'],
                'last_accessed_at': conv['update_at'],
                'significance': 5,
                'conversation_id': conv['id']
            })
    
    return episodes


def create_target_database(output_path, core_memories, episodes, conversations, generical_memories, generical_episodes):
    """Create the target database with regenerated Clara memories and preserved Generical data."""
    
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    if os.path.exists(output_path):
        os.remove(output_path)
    
    conn = sqlite3.connect(output_path)
    cursor = conn.cursor()
    
    # Create all tables
    cursor.execute("""CREATE TABLE IF NOT EXISTS `ConversationEntity` (
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
        PRIMARY KEY(`id`))""")
    
    cursor.execute("""CREATE TABLE IF NOT EXISTS `MemoryEntity` (
        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
        `assistant_id` TEXT NOT NULL, 
        `content` TEXT NOT NULL, 
        `embedding` TEXT, 
        `embedding_model_id` TEXT DEFAULT '', 
        `type` INTEGER NOT NULL DEFAULT 0, 
        `last_accessed_at` INTEGER NOT NULL DEFAULT 0, 
        `created_at` INTEGER NOT NULL DEFAULT 0)""")
    
    cursor.execute("""CREATE TABLE IF NOT EXISTS `GenMediaEntity` (
        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
        `path` TEXT NOT NULL, 
        `model_id` TEXT NOT NULL, 
        `prompt` TEXT NOT NULL, 
        `create_at` INTEGER NOT NULL)""")
    
    cursor.execute("""CREATE TABLE IF NOT EXISTS `ChatEpisodeEntity` (
        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
        `assistant_id` TEXT NOT NULL, 
        `content` TEXT NOT NULL, 
        `embedding` TEXT, 
        `embedding_model_id` TEXT DEFAULT '', 
        `start_time` INTEGER NOT NULL, 
        `end_time` INTEGER NOT NULL, 
        `last_accessed_at` INTEGER NOT NULL DEFAULT 0, 
        `significance` INTEGER NOT NULL DEFAULT 5, 
        `conversation_id` TEXT DEFAULT '')""")
    
    cursor.execute("""CREATE TABLE IF NOT EXISTS `embedding_cache` (
        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
        `memory_id` INTEGER NOT NULL, 
        `memory_type` INTEGER NOT NULL, 
        `model_id` TEXT NOT NULL, 
        `embedding` TEXT NOT NULL, 
        `created_at` INTEGER NOT NULL)""")
    
    cursor.execute("CREATE UNIQUE INDEX IF NOT EXISTS `index_embedding_cache_memory_id_memory_type_model_id` ON `embedding_cache` (`memory_id`, `memory_type`, `model_id`)")
    
    cursor.execute("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
    cursor.execute("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)", (ROOM_IDENTITY_HASH,))
    
    # Add android_metadata table (required by SQLite on Android)
    cursor.execute("CREATE TABLE IF NOT EXISTS android_metadata (locale TEXT)")
    cursor.execute("INSERT OR REPLACE INTO android_metadata (locale) VALUES ('en_US_#u-mu-celsius')")
    
    # Insert all conversations
    for conv in conversations:
        cursor.execute("""
            INSERT INTO ConversationEntity 
            (id, assistant_id, title, nodes, create_at, update_at, truncate_index, suggestions, is_pinned, is_consolidated)
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
    
    # Insert Clara's regenerated core memories
    now = int(datetime.now().timestamp() * 1000)
    for content in core_memories:
        cursor.execute("""
            INSERT INTO MemoryEntity 
            (assistant_id, content, embedding, embedding_model_id, type, last_accessed_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """, (CLARA_ASSISTANT_ID, content, None, '', 0, now, now))
    print(f"Inserted {len(core_memories)} Clara core memories")
    
    # Insert Generical's preserved memories
    for mem in generical_memories:
        cursor.execute("""
            INSERT INTO MemoryEntity 
            (assistant_id, content, embedding, embedding_model_id, type, last_accessed_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """, (
            mem['assistant_id'],
            mem['content'],
            None,
            '',
            mem.get('type', 0),
            mem.get('last_accessed_at', 0),
            mem.get('created_at', 0)
        ))
    print(f"Inserted {len(generical_memories)} Generical memories (preserved)")
    
    # Insert Clara's regenerated episodic memories
    for ep in episodes:
        cursor.execute("""
            INSERT INTO ChatEpisodeEntity 
            (assistant_id, content, embedding, embedding_model_id, start_time, end_time, last_accessed_at, significance, conversation_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            ep['assistant_id'],
            ep['content'],
            None,
            '',
            ep['start_time'],
            ep['end_time'],
            ep.get('last_accessed_at', 0),
            ep.get('significance', 5),
            ep.get('conversation_id', '')
        ))
    print(f"Inserted {len(episodes)} Clara episodic memories")
    
    # Insert Generical's preserved episodes
    for ep in generical_episodes:
        cursor.execute("""
            INSERT INTO ChatEpisodeEntity 
            (assistant_id, content, embedding, embedding_model_id, start_time, end_time, last_accessed_at, significance, conversation_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            ep['assistant_id'],
            ep['content'],
            None,
            '',
            ep['start_time'],
            ep['end_time'],
            ep.get('last_accessed_at', 0),
            ep.get('significance', 5),
            ep.get('conversation_id', '')
        ))
    print(f"Inserted {len(generical_episodes)} Generical episodes (preserved)")
    
    conn.commit()
    conn.close()
    print(f"Created database at {output_path}")


def load_all_conversation_entities(db_path):
    """Load all conversation entities from source database."""
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()
    
    cursor.execute("SELECT * FROM ConversationEntity")
    conversations = []
    for row in cursor.fetchall():
        conv = dict(row)
        conv.setdefault('truncate_index', -1)
        conv.setdefault('suggestions', '[]')
        conv.setdefault('is_pinned', 0)
        conv.setdefault('is_consolidated', 0)
        conversations.append(conv)
    
    conn.close()
    return conversations


def load_generical_data(db_path):
    """Load Generical's memories and episodes to preserve them."""
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()
    
    # Load Generical memories
    cursor.execute("SELECT * FROM MemoryEntity WHERE assistant_id = ?", (GENERICAL_ASSISTANT_ID,))
    memories = [dict(row) for row in cursor.fetchall()]
    
    # Load Generical episodes
    cursor.execute("SELECT * FROM ChatEpisodeEntity WHERE assistant_id = ?", (GENERICAL_ASSISTANT_ID,))
    episodes = [dict(row) for row in cursor.fetchall()]
    
    conn.close()
    return memories, episodes


def create_backup_zip(db_path, settings_path, upload_dir, output_zip):
    """Create the final backup zip file."""
    if os.path.exists(output_zip):
        os.remove(output_zip)
    
    with zipfile.ZipFile(output_zip, 'w', zipfile.ZIP_DEFLATED) as zf:
        # Add database
        zf.write(db_path, "rikka_hub.db")
        print("Added rikka_hub.db")
        
        # Add settings
        if os.path.exists(settings_path):
            zf.write(settings_path, "settings.json")
            print("Added settings.json")
        
        # Add upload files
        if os.path.exists(upload_dir):
            for filename in os.listdir(upload_dir):
                filepath = os.path.join(upload_dir, filename)
                if os.path.isfile(filepath):
                    zf.write(filepath, f"upload/{filename}")
            print("Added upload files")
    
    size_mb = os.path.getsize(output_zip) / (1024 * 1024)
    print(f"Created backup: {output_zip} ({size_mb:.1f} MB)")


def main():
    print("=" * 60)
    print("Clara Nightingale Memory Regeneration - Complete")
    print("=" * 60)
    
    # Load Clara conversations for episodic generation
    print("\nLoading Clara conversations...")
    clara_convos = load_conversations(SOURCE_DB, CLARA_ASSISTANT_ID)
    print(f"Found {len(clara_convos)} Clara conversations")
    
    # Generate episodic summaries
    print("\nGenerating episodic summaries...")
    clara_episodes = generate_all_episodic_summaries(clara_convos)
    print(f"Generated {len(clara_episodes)} episodic summaries")
    
    # Load all conversation entities
    print("\nLoading all conversation entities...")
    all_conversations = load_all_conversation_entities(SOURCE_DB)
    print(f"Loaded {len(all_conversations)} total conversations")
    
    # Load Generical's data to preserve
    print("\nLoading Generical data (to preserve)...")
    generical_memories, generical_episodes = load_generical_data(SOURCE_DB)
    print(f"Found {len(generical_memories)} Generical memories, {len(generical_episodes)} episodes")
    
    # Create output database
    db_output = os.path.join(OUTPUT_DIR, "rikka_hub.db")
    print("\nCreating target database...")
    create_target_database(
        db_output,
        CORE_MEMORIES,
        clara_episodes,
        all_conversations,
        generical_memories,
        generical_episodes
    )
    
    # Create backup zip
    print("\nCreating backup zip...")
    create_backup_zip(db_output, SOURCE_SETTINGS, SOURCE_UPLOADS, OUTPUT_ZIP)
    
    # Summary
    print("\n" + "=" * 60)
    print("Regeneration Complete!")
    print("=" * 60)
    print(f"Clara Core Memories: {len(CORE_MEMORIES)}")
    print(f"Clara Episodic Memories: {len(clara_episodes)}")
    print(f"Generical Memories (preserved): {len(generical_memories)}")
    print(f"Generical Episodes (preserved): {len(generical_episodes)}")
    print(f"Total Conversations: {len(all_conversations)}")
    print(f"Output: {OUTPUT_ZIP}")
    print("=" * 60)


if __name__ == "__main__":
    main()
