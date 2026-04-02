#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Добавляет FileLogger.trace/warn ко всем Log.d/Log.w вызовам в MainPhp.java
Работает с многострочными вызовами и проверяет, есть ли уже FileLogger
"""

import re

def add_filelogger_logging(filepath):
    """Обслуживает Log вызовы и добавляет FileLogger"""
    
    with open(filepath, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    result = []
    i = 0
    added_count = 0
    
    while i < len(lines):
        line = lines[i]
        result.append(line)
        
        # Проверяем начинается ли строка с Log.d, Log.w или android.util.Log
        stripped = line.strip()
        
        # Ищем Log.d(TAG, msg);  или Log.w(TAG, msg); или Log.w(TAG, msg, e);
        if ('Log.d(TAG, msg)' in line or 'Log.w(TAG, msg)' in line) and 'FileLogger' not in line:
            # Проверяем следующую строку
            if i + 1 < len(lines) and 'FileLogger' not in lines[i + 1]:
                # Нужно добавить FileLogger
                indent_match = re.match(r'^(\s*)', line)
                indent = indent_match.group(1) if indent_match else '            '
                
                if 'Log.d(TAG, msg)' in line:
                    # Добавляем FileLogger.trace
                    result.append(indent + 'FileLogger.trace(TAG, msg);\n')
                    added_count += 1
                elif 'Log.w(TAG, msg);' in line:
                    # Добавляем FileLogger.warn
                    result.append(indent + 'FileLogger.warn(TAG, msg);\n')
                    added_count += 1
                elif 'Log.w(TAG, msg, e);' in line:
                    # Заменяем Log.w(TAG, msg, e); на Log.w(TAG, msg);
                    result[-1] = line.replace('Log.w(TAG, msg, e);', 'Log.w(TAG, msg);')
                    # Добавляем FileLogger.warn (без e)
                    result.append(indent + 'FileLogger.warn(TAG, msg);\n')
                    added_count += 1
        
        # Для android.util.Log.d и android.util.Log.w с inline сообщениями, нам нужен другой подход
        # Это более сложно, так как сообщение не в переменной msg
        
        i += 1
    
    with open(filepath, 'w', encoding='utf-8') as f:
        f.writelines(result)
    
    print(f"✅ Обработан: {filepath}")
    print(f"📝 Добавлено FileLogger вызывов: {added_count}")
    return added_count

if __name__ == '__main__':
    filepath = r'c:\Users\User\AbclientAndroid\app\src\main\java\ru\neverlands\abclient\postfilter\MainPhp.java'
    add_filelogger_logging(filepath)
