@echo off
rem Thin wrapper around generate-keys.ps1 — the PEM/.env text surgery needs
rem real regex and multi-line string handling, which batch does not have
rem without falling into fragile echo-heredoc quoting. See that file for
rem what actually happens.
rem
rem Usage: generate-keys.bat [path\to\.env]
rem Default target: the pte-api root .env (one level up from this script).

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0generate-keys.ps1" %*
exit /b %ERRORLEVEL%
