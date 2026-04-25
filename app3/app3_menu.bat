@echo off
setlocal EnableExtensions

set "APP3_MENU_DIR=%~dp0"

powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $script=Join-Path $env:APP3_MENU_DIR 'app3_menu.ps1'; $text=[System.Text.Encoding]::UTF8.GetString([System.IO.File]::ReadAllBytes($script)); $block=[ScriptBlock]::Create($text); & $block @args" %*
exit /b %ERRORLEVEL%
