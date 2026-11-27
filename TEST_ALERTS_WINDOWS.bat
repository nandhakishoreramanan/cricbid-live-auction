@echo off
title Test Cinema Monitor Alerts
cd /d "%~dp0"

echo ================================================================
echo  Testing Alert System (Voice, Sound, Popup, Browser)
echo ================================================================

python -m pip install -r requirements.txt --quiet
python bms_theatre_monitor.py --test-alert

echo.
echo Test finished! If you heard the voice, chime, and saw the popup, everything is working.
pause
