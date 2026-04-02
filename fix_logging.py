#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import re
import sys

def fix_file(filepath, is_mainphp=False):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original = content
    
    # Pattern 1: Log.d(TAG, "simple message");
    # Before: Log.d(TAG, "message");
    # After: String msg = "message"; Log.d(TAG, msg); FileLogger.trace(TAG, msg);
    pattern1 = r'(\s+)Log\.d\(TAG,\s+"([^"]+)"\);'
    replacement1 = r'\1String msg = "\2";\n\1Log.d(TAG, msg);\n\1FileLogger.trace(TAG, msg);'
    content = re.sub(pattern1, replacement1, content)
    
    # Pattern 2: Log.d(TAG, "msg" + var + "more");
    pattern2 = r'(\s+)Log\.d\(TAG,\s+"([^"]+)"\s*\+\s*(.+?)\);'
    replacement2 = r'\1String msg = "\2" + \3;\n\1Log.d(TAG, msg);\n\1FileLogger.trace(TAG, msg);'
    content = re.sub(pattern2, replacement2, content)
    
    if is_mainphp:
        # Pattern 3 for MainPhp: Log.w(TAG, "msg");
        pattern3 = r'(\s+)Log\.w\(TAG,\s+"([^"]+)"\);'
        replacement3 = r'\1String msg = "\2";\n\1Log.w(TAG, msg);\n\1FileLogger.warn(TAG, msg);'
        content = re.sub(pattern3, replacement3, content)
        
        # Pattern 4: Log.w(TAG, "msg", exception);
        pattern4 = r'(\s+)Log\.w\(TAG,\s+"([^"]+)",\s*([a-zA-Z_]\w*)\);'
        replacement4 = r'\1String msg = "\2";\n\1Log.w(TAG, msg, \3);\n\1FileLogger.warn(TAG, msg);'
        content = re.sub(pattern4, replacement4, content)
        
        # Pattern 5: Log.e(TAG, "msg", exception);
        pattern5 = r'(\s+)Log\.e\(TAG,\s+"([^"]+)",\s*([a-zA-Z_]\w*)\);'
        replacement5 = r'\1String msg = "\2";\n\1Log.e(TAG, msg, \3);\n\1FileLogger.error(TAG, msg, \3);'
        content = re.sub(pattern5, replacement5, content)
    
    if content != original:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        return True
    return False

if __name__ == '__main__':
    files = [
        ('app/src/main/java/ru/neverlands/abclient/postfilter/FightAuto.java', False),
        ('app/src/main/java/ru/neverlands/abclient/postfilter/MainPhp.java', True),
    ]
    
    for filepath, is_mainphp in files:
        try:
            if fix_file(filepath, is_mainphp):
                print(f"✓ Updated: {filepath}")
            else:
                print(f"~ No changes: {filepath}")
        except Exception as e:
            print(f"✗ Error in {filepath}: {e}", file=sys.stderr)
            sys.exit(1)
    
    print("\nDone! Now run: gradlew assembleDebug")
