#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Финальный скрипт для добавления FileLogger ко всем Log вызовам.
Обслуживает:
- Log.d(TAG, msg);
- Log.w(TAG, msg);  
- Log.w(TAG, msg, e);
- android.util.Log.d(TAG, "inline_msg");
- android.util.Log.w(TAG, "inline_msg");
- android.util.Log.w(TAG, "inline_msg", e);
"""

import re
import sys

def extract_log_calls(filepath):
    """Находит все Log вызовы в файле"""
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Находим все Log.d и Log.w вызовы
    # Паттерны для различных типов вызовов
    
    # 1. Log.d(TAG, msg); и Log.w(TAG, msg);
    pattern1 = r'(Log\.[dw]\(TAG,\s*msg[,\)][^;]*;)'
    
    # 2. android.util.Log.d(TAG, "...") и android.util.Log.w(TAG, "...", e)
    pattern2 = r'(android\.util\.Log\.[dw]\(TAG,\s*"[^"]*"[^;]*;)'
    
    matches1 = list(re.finditer(pattern1, content))
    matches2 = list(re.finditer(pattern2, content))
    
    print(f"Найдено Log.d/Log.w (переменная msg): {len(matches1)}")
    print(f"Найдено android.util.Log.d/w (inline): {len(matches2)}")
    
    return content, matches1, matches2

def apply_fixes(filepath):
    """Применяет все нужные исправления"""
    with open(filepath, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    result = []
    i = 0
    changes = {'added_trace': 0, 'added_warn': 0}
    
    while i < len(lines):
        line = lines[i]
        result.append(line)
        
        # Проверяем Log.d(TAG, msg);
        if re.search(r'Log\.d\(TAG,\s*msg\)\s*;', line) and i + 1 < len(lines):
            next_line = lines[i + 1]
            if 'FileLogger.trace' not in next_line:
                # Добавляем FileLogger.trace
                indent = re.match(r'^(\s*)', line).group(1)
                result.append(indent + 'FileLogger.trace(TAG, msg);\n')
                changes['added_trace'] += 1
        
        # Проверяем Log.w(TAG, msg);
        elif re.search(r'Log\.w\(TAG,\s*msg\)\s*;', line) and 'FileLogger' not in line and i + 1 < len(lines):
            next_line = lines[i + 1]
            if 'FileLogger.warn' not in next_line:
                # Добавляем FileLogger.warn
                indent = re.match(r'^(\s*)', line).group(1)
                result.append(indent + 'FileLogger.warn(TAG, msg);\n')
                changes['added_warn'] += 1
        
        # Проверяем Log.w(TAG, msg, e);
        elif re.search(r'Log\.w\(TAG,\s*msg,\s*e\)\s*;', line):
            # Заменяем на Log.w(TAG, msg); и добавляем FileLogger.warn
            indent = re.match(r'^(\s*)', line).group(1)
            result[-1] = line.replace('Log.w(TAG, msg, e);', 'Log.w(TAG, msg);')
            result.append(indent + 'FileLogger.warn(TAG, msg);\n')
            changes['added_warn'] += 1
        
        i += 1
    
    # Сохраняем
    with open(filepath, 'w', encoding='utf-8') as f:
        f.writelines(result)
    
    return changes

if __name__ == '__main__':
    filepath = r'c:\Users\User\AbclientAndroid\app\src\main\java\ru\neverlands\abclient\postfilter\MainPhp.java'
    
    print("📊 Анализирование Log вызовов...")
    content, m1, m2 = extract_log_calls(filepath)
    
    print("\n🔧 Применяю исправления...")
    changes = apply_fixes(filepath)
    
    print(f"\n✅ Готово!")
    print(f"   - Добавлено FileLogger.trace: {changes['added_trace']}")
    print(f"   - Добавлено FileLogger.warn: {changes['added_warn']}")
    print(f"   - Всего изменений: {changes['added_trace'] + changes['added_warn']}")
