@echo off
rem Windows wrapper: runs deploy.ps1 without changing the execution policy.
rem Usage: deploy\scripts\deploy.cmd sandbox^|gpu [-Namespace <ns>] [-LocalSource] [-Delete] [-Force] [-NoWait]
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy.ps1" %*
exit /b %ERRORLEVEL%
