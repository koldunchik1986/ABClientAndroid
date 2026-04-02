#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Скрипт для добавления FileLogger логирования ко всем Log.w() и Log.d() вызовам в MainPhp.java.
Более точная версия, которая работает с многострочными вызовами.
"""

import re
import sys

def add_filelogger_to_logs(filepath):
    """Добавляет FileLogger вызовы после Log.w/Log.d"""
    
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Паттерны для поиска Log вызовов
    # Log.d(TAG, msg);
    pattern_log_d_direct = r'(\n\s+)(Log\.d\(TAG,\s*msg\);)'
    # Log.w(TAG, msg);
    pattern_log_w_direct = r'(\n\s+)(Log\.w\(TAG,\s*msg\);)'
    # Log.w(TAG, msg, e);
    pattern_log_w_with_e = r'(\n\s+)(Log\.w\(TAG,\s*msg,\s*e\);)'
    
    # android.util.Log.d и android.util.Log.w
    # Нужно быть осторожней с этими, так как они могут быть на одной строке с inline сообщением
    
    updates_count = 0
    
    # Обслуживаем Log.d(TAG, msg);
    def replace_log_d(match):
        nonlocal updates_count
        indent = match.group(1)
        log_call = match.group(2)
        # Проверяем, нет ли уже FileLogger.trace
        remaining = content[match.end():]
        if 'FileLogger.trace(TAG, msg)' not in remaining[:100]:
            updates_count += 1
            return indent + log_call + indent + 'FileLogger.trace(TAG, msg);'
        return match.group(0)
    
    content = re.sub(pattern_log_d_direct, replace_log_d, content)
    
    # Обслуживаем Log.w(TAG, msg);
    def replace_log_w(match):
        nonlocal updates_count
        indent = match.group(1)
        log_call = match.group(2)
        remaining = content[match.end():]
        if 'FileLogger.warn(TAG, msg)' not in remaining[:100]:
            updates_count += 1
            return indent + log_call + indent + 'FileLogger.warn(TAG, msg);'
        return match.group(0)
    
    content = re.sub(pattern_log_w_direct, replace_log_w, content)
    
    # Обслуживаем Log.w(TAG, msg, e);
    def replace_log_w_e(match):
        nonlocal updates_count
        indent = match.group(1)
        log_call = match.group(2)
        # Заменяем Log.w(TAG, msg, e) на Log.w(TAG, msg) и добавляем FileLogger.warn без e
        new_log_call = 'Log.w(TAG, msg);'
        remaining = content[match.end():]
        if 'FileLogger.warn(TAG, msg)' not in remaining[:100]:
            updates_count += 1
            return indent + new_log_call + indent + 'FileLogger.warn(TAG, msg);'
        return match.group(0)
    
    content = re.sub(pattern_log_w_with_e, replace_log_w_e, content)
    
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)
    
    print(f"✅ Обработан файл: {filepath}")
    print(f"📝 Сделано замен: {updates_count}")

if __name__ == '__main__':
    filepath = r'c:\Users\User\AbclientAndroid\app\src\main\java\ru\neverlands\abclient\postfilter\MainPhp.java'
    add_filelogger_to_logs(filepath)
