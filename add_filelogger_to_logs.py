#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Скрипт для добавления FileLogger логирования ко всем Log.w() и Log.d() вызовам в MainPhp.java
"""

import re
import sys

def process_file(filepath):
    """Обрабатывает файл и добавляет FileLogger вызовы"""
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    lines = content.split('\n')
    result_lines = []
    i = 0
    
    while i < len(lines):
        line = lines[i]
        result_lines.append(line)
        
        # Проверяем Log.d(TAG, "msg")
        if re.search(r'\bLog\.d\(TAG,\s*"', line):
            # Извлекаем строку сообщения
            match = re.search(r'Log\.d\(TAG,\s*"([^"]*)"', line)
            if match and 'FileLogger.trace' not in line:
                # Нужно проверить, есть ли уже FileLogger на следующей строке
                next_line_idx = i + 1
                if next_line_idx < len(lines) and 'FileLogger.trace' not in lines[next_line_idx]:
                    # Добавляем FileLogger.trace на следующую строку
                    indent = len(line) - len(line.lstrip())
                    filelogger_line = ' ' * indent + 'FileLogger.trace(TAG, "' + match.group(1) + '");'
                    result_lines.append(filelogger_line)
        
        # Проверяем Log.w(TAG, "msg") или Log.w(TAG, "msg", e)
        elif re.search(r'\bLog\.w\(TAG,\s*"', line):
            if 'FileLogger.warn' not in line:
                # Нужно добавить FileLogger.warn
                match = re.search(r'Log\.w\(TAG,\s*"([^"]*)"', line)
                if match and 'FileLogger.warn' not in line:
                    next_line_idx = i + 1
                    if next_line_idx < len(lines) and 'FileLogger.warn' not in lines[next_line_idx]:
                        indent = len(line) - len(line.lstrip())
                        filelogger_line = ' ' * indent + 'FileLogger.warn(TAG, "' + match.group(1) + '");'
                        result_lines.append(filelogger_line)
        
        # Проверяем android.util.Log.d(TAG, ...)
        elif re.search(r'\bandroid\.util\.Log\.d\(TAG,', line):
            if 'FileLogger.trace' not in line and not any('FileLogger.trace' in lines[j] for j in range(max(0, i-1), min(len(lines), i+2))):
                # Извлекаем сообщение
                match = re.search(r'android\.util\.Log\.d\(TAG,\s*"([^"]*)"', line)
                if match:
                    indent = len(line) - len(line.lstrip())
                    filelogger_line = ' ' * indent + 'FileLogger.trace(TAG, "' + match.group(1) + '");'
                    # Проверяем, не на следующей ли строке уже FileLogger
                    next_line_idx = i + 1
                    if next_line_idx < len(lines) and 'FileLogger.trace' not in lines[next_line_idx]:
                        result_lines.append(filelogger_line)
        
        # Проверяем android.util.Log.w(TAG, ...)
        elif re.search(r'\bandroid\.util\.Log\.w\(TAG,', line):
            if 'FileLogger.warn' not in line and not any('FileLogger.warn' in lines[j] for j in range(max(0, i-1), min(len(lines), i+2))):
                # Извлекаем сообщение
                match = re.search(r'android\.util\.Log\.w\(TAG,\s*"([^"]*)"', line)
                if match:
                    indent = len(line) - len(line.lstrip())
                    filelogger_line = ' ' * indent + 'FileLogger.warn(TAG, "' + match.group(1) + '");'
                    next_line_idx = i + 1
                    if next_line_idx < len(lines) and 'FileLogger.warn' not in lines[next_line_idx]:
                        result_lines.append(filelogger_line)
        
        i += 1
    
    # Сохраняем результат
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write('\n'.join(result_lines))
    
    print(f"✅ Обработан файл: {filepath}")

if __name__ == '__main__':
    filepath = r'c:\Users\User\AbclientAndroid\app\src\main\java\ru\neverlands\abclient\postfilter\MainPhp.java'
    process_file(filepath)
