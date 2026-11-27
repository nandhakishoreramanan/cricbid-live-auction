#!/bin/bash
cd "$(dirname "$0")"

echo "================================================================"
echo "🧪 Testing Alert System (Voice, Sound, Popup, Browser)..."
echo "================================================================"

python3 -m pip install -r requirements.txt --quiet
python3 bms_theatre_monitor.py --test-alert

echo ""
echo "Test finished! If you heard the voice, chime, and saw the popup, everything is working."
read -p "Press [Enter] to close..."
