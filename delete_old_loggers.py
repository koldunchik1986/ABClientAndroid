#!/usr/bin/env python3
import os
import sys

files_to_delete = [
    r"app\src\main\java\ru\neverlands\abclient\utils\DebugLogger.java",
    r"app\src\main\java\ru\neverlands\abclient\utils\CustomDebugLogger.java",
    r"app\src\main\java\ru\neverlands\abclient\utils\AppLogger.java",
]

deleted_count = 0
for file_path in files_to_delete:
    full_path = os.path.join(r"C:\Users\User\AbclientAndroid", file_path)
    try:
        if os.path.exists(full_path):
            os.remove(full_path)
            print(f"✅ Deleted: {file_path}")
            deleted_count += 1
        else:
            print(f"⚠️  Not found: {file_path}")
    except Exception as e:
        print(f"❌ Error deleting {file_path}: {e}")

print(f"\n✅ Successfully deleted {deleted_count} files")
sys.exit(0)
