@echo off
title Chennai Cinema Ticket Monitor
cd /d "%~dp0"

echo ================================================================
echo  Starting Chennai Cinema Ticket Monitor (BMS + District)
echo  Monitoring Avengers: Doomsday and Dune: Part Three
echo ================================================================

python -m pip install -r requirements.txt --quiet

echo Running monitor every 60 seconds...
echo Press Ctrl+C to stop.
echo.

python bms_theatre_monitor.py --interval 60
pause
