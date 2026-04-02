#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Преобразует android.util.Log.d/w(TAG, "inline msg") на:
1. String msg = "inline msg";
2. Log.d/w(TAG, msg);
3. FileLogger.trace/warn(TAG, msg);
"""

import re

def transform_android_util_logs(filepath):
    """Преобразует android.util.Log на Log с FileLogger"""
    
    with open(filepath, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    result = []
    i = 0
    changes_count = 0
    
    while i < len(lines):
        line = lines[i]
        
        # Ищем android.util.Log.d(TAG, "msg") или android.util.Log.w(TAG, "msg") 
        # или android.util.Log.w(TAG, "msg", e)
        
        # Pattern: android.util.Log.d(TAG, "текст")
        match_d = re.match(r'^(\s*)android\.util\.Log\.d\(TAG,\s*"([^"]*)"([^;]*)\);?\s*$', line)
        
        # Pattern: android.util.Log.w(TAG, "텍ст") или с exception
        match_w = re.match(r'^(\s*)android\.util\.Log\.w\(TAG,\s*"([^"]*)"([^;]*)\);?\s*$', line)
        
        if match_d:
            # Преобразуем Log.d
            indent, msg_text, rest = match_d.groups()
            # Проверяем, есть ли уже FileLogger в следующей строке
            if i + 1 < len(lines) and 'FileLogger' in lines[i + 1]:
                # Уже обработано
                result.append(line)
            else:
                # Добавляем String msg переменную
                result.append(f'{indent}String msg = "{msg_text}";\n')
                result.append(f'{indent}Log.d(TAG, msg);\n')
                result.append(f'{indent}FileLogger.trace(TAG, msg);\n')
                changes_count += 1
        elif match_w:
            # Преобразуем Log.w
            indent, msg_text, rest = match_w.groups()
            has_exception = 'e' in rest
            
            if i + 1 < len(lines) and 'FileLogger' in lines[i + 1]:
                result.append(line)
            else:
                # Добавляем String msg переменную
                result.append(f'{indent}String msg = "{msg_text}";\n')
                if has_exception:
                    result.append(f'{indent}Log.w(TAG, msg, e);\n')
                else:
                    result.append(f'{indent}Log.w(TAG, msg);\n')
                result.append(f'{indent}FileLogger.warn(TAG, msg);\n')
                changes_count += 1
        else:
            # Нет изменений
            result.append(line)
        
        i += 1
    
    # Сохраняем
    with open(filepath, 'w', encoding='utf-8') as f:
        f.writelines(result)
    
    return changes_count

if __name__ == '__main__':
    filepath = r'c:\Users\User\AbclientAndroid\app\src\main\java\ru\neverlands\abclient\postfilter\MainPhp.java'
    
    changes = transform_android_util_logs(filepath)
    print(f"OK: Preobrazovano android.util.Log vyzovov: {changes}")
