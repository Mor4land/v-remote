@echo off
title V-Remote Control Server
cd /d "%~dp0"

echo Checking Python...
python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Python is not installed or not in PATH!
    pause
    exit /b 1
)

echo Starting V-Remote Control Server...
python server.py
if %errorlevel% neq 0 (
    echo.
    echo Server stopped with error.
    pause
)
