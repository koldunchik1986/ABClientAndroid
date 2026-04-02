#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Парсер для многострочных Log вызовов
Обслуживает:
- Log.d(TAG, msg);
- Log.w(TAG, msg);
- android.util.Log.d(TAG, "msg");
- android.util.Log.w(TAG, "msg");
- Многострочные варианты
"""

import re

def add_filelogger_comprehensive(filepath):
    """Добавляет FileLogger для всех Log вызовов"""
    
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    changes = 0
    
    # 1. Log.d(TAG, msg); ==> Log.d(TAG, msg); + FileLogger.trace(TAG, msg);
    # Найти паттерн: <indent>Log.d(TAG, msg);<newline><next_line>
    # Если на следующей строке нет FileLogger.trace, добавляем
    
    pattern_log_d_msg = r'(\n\s+)(Log\.d\(TAG,\s*msg\);)(?!\n\s+FileLogger\.trace)'
    replacement_log_d = lambda m: m.group(1) + m.group(2) + m.group(1) + 'FileLogger.trace(TAG, msg);'
    
    new_content = re.sub(pattern_log_d_msg, replacement_log_d, content)
    if new_content != content:
        changes += new_content.count('FileLogger.trace(TAG, msg);') - content.count('FileLogger.trace(TAG, msg);')
        content = new_content
    
    # 2. Log.w(TAG, msg); (без exception)
    pattern_log_w_msg = r'(\n\s+)(Log\.w\(TAG,\s*msg\);)(?!\n\s+FileLogger\.warn)'
    replacement_log_w = lambda m: m.group(1) + m.group(2) + m.group(1) + 'FileLogger.warn(TAG, msg);'
    
    new_content = re.sub(pattern_log_w_msg, replacement_log_w, content)
    if new_content != content:
        changes += new_content.count('FileLogger.warn(TAG, msg);') - content.count('FileLogger.warn(TAG, msg);')
        content = new_content
    
    # 3. Log.w(TAG, msg, e); ==> Log.w(TAG, msg); + FileLogger.warn(TAG, msg);
    pattern_log_w_e = r'(\n\s+)(Log\.w\(TAG,\s*msg,\s*e\);)(?!\n\s+FileLogger\.warn)'
    def replacement_log_w_e(m):
        indent = m.group(1)
        return indent + 'Log.w(TAG, msg);' + indent + 'FileLogger.warn(TAG, msg);'
    
    new_content = re.sub(pattern_log_w_e, replacement_log_w_e, content)
    if new_content != content:
        changes += new_content.count('FileLogger.warn') - content.count('FileLogger.warn')
        content = new_content
    
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)
    
    return changes

if __name__ == '__main__':
    filepath = r'c:\Users\User\AbclientAndroid\app\src\main\java\ru\neverlands\abclient\postfilter\MainPhp.java'
    
    changes = add_filelogger_comprehensive(filepath)
    print(f"Changes made: {changes}")
