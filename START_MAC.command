#!/bin/bash
cd "$(dirname "$0")"

echo "================================================================"
echo "🎬  Starting Chennai Cinema Ticket Monitor (BMS + District)"
echo "    Monitoring Avengers: Doomsday & Dune: Part Three"
echo "================================================================"

# Install dependencies if not present
python3 -m pip install -r requirements.txt --quiet

echo "Running with caffeinate (keeps checking even if display sleeps)..."
echo "Press Ctrl+C to stop."
echo ""

caffeinate -ims python3 bms_theatre_monitor.py --interval 60
