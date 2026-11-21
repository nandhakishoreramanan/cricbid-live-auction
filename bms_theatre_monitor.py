#!/usr/bin/env python3
"""
Avengers: Doomsday — Cinema Booking Monitor
--------------------------------------------
Monitors BOTH BookMyShow AND District (district.in) for new theatres
opening for booking in Chennai for Avengers: Doomsday (Dec 21, 2026).

Alerts immediately via:
  • Terminal banner + audio chime
  • macOS desktop notification
  • iPhone push notification (ntfy — free, no account needed)
  • Telegram bot (optional)
  • Discord/Slack webhook (optional)

QUICKSTART:
  1. Install ntfy app on iPhone → subscribe to your chosen topic
  2. cd ~/python/LoginSystem
  3. python3 bms_theatre_monitor.py --ntfy-topic <your-topic>

iPhone Notifications (ntfy — FREE):
  - Install "ntfy" from the App Store
  - Choose a unique topic (e.g. "yourname-bms-avengers-2026")
  - In ntfy app → tap "+" → subscribe to: https://ntfy.sh/<your-topic>
  - Run: python3 bms_theatre_monitor.py --ntfy-topic <your-topic>
"""

import os
import sys
import time
import json
import random
import argparse
import subprocess
from datetime import datetime
from typing import Dict, List, Set, Any, Optional, Tuple

try:
    from curl_cffi import requests
except ImportError:
    print("[ERROR] 'curl_cffi' is required. Install: pip install curl_cffi")
    sys.exit(1)

# ── Default configuration ──────────────────────────────────────────────────────
BMS_URL = (
    "https://in.bookmyshow.com/movies/chennai/avengers-doomsday/buytickets/"
    "ET00526306/20261221?etCodes=*&language=english&refEventCode=ET00526306"
)
DISTRICT_URL = (
    "https://www.district.in/movies/avengers-doomsday-movie-tickets-in-chennai-MV176735"
    "?frmtid=Mm5dpqTqJz&fromdate=2026-12-21"
)
DEFAULT_STATE_FILE = "bms_theatres_state.json"
DEFAULT_NTFY_SERVER = "https://ntfy.sh"

# ── Terminal colours — auto-disabled in CI / non-TTY environments ──────────────
_NO_COLOR = not sys.stdout.isatty() or os.environ.get("NO_COLOR")

class C:
    G = "" if _NO_COLOR else "\033[92m"   # green
    R = "" if _NO_COLOR else "\033[91m"   # red
    Y = "" if _NO_COLOR else "\033[93m"   # yellow
    B = "" if _NO_COLOR else "\033[96m"   # cyan/blue
    M = "" if _NO_COLOR else "\033[95m"   # magenta
    BOLD = "" if _NO_COLOR else "\033[1m"
    X = "" if _NO_COLOR else "\033[0m"    # reset


# ── Logging ───────────────────────────────────────────────────────────────────
def log(msg: str, color: str = ""):
    ts = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    if color:
        print(f"{color}[{ts}] {msg}{C.X}", flush=True)
    else:
        print(f"[{ts}] {msg}", flush=True)


# ══════════════════════════════════════════════════════════════════════════════
#  ALERT CHANNELS
# ══════════════════════════════════════════════════════════════════════════════

def _macos_notify(title: str, msg: str, sound: str = "Glass"):
    try:
        script = f'display notification "{msg}" with title "{title}" sound name "{sound}"'
        subprocess.run(["osascript", "-e", script],
                       check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    except Exception:
        pass


def _macos_sound(path: str = "/System/Library/Sounds/Glass.aiff"):
    """Plays audio chime directly through macOS audio engine (bypasses DND)."""
    try:
        subprocess.Popen(["afplay", path],
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    except Exception:
        pass


def _macos_voice(text: str):
    """Speaks through macOS TTS synthesizer aloud (bypasses DND)."""
    try:
        subprocess.Popen(["say", "-v", "Samantha", text],
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    except Exception:
        pass


def _macos_modal_alert(title: str, msg: str, url: str):
    """
    Displays a prominent on-screen modal alert that breaks through Do Not Disturb.
    Has a 'Book Now' button that opens the booking website directly in your browser.
    """
    def _run():
        try:
            safe_title = title.replace('"', '\\"')
            safe_msg = msg.replace('"', '\\"')
            script = f'''
            tell application "System Events"
                activate
                set userChoice to button returned of (display alert "{safe_title}" message "{safe_msg}" as critical buttons {{"Dismiss", "Book Now"}} default button "Book Now" giving up after 60)
                if userChoice is "Book Now" then
                    open location "{url}"
                end if
            end tell
            '''
            subprocess.run(["osascript", "-e", script], check=False,
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        except Exception:
            pass

    import threading
    t = threading.Thread(target=_run, daemon=True)
    t.start()


def _ntfy(topic: Optional[str], title: str, body: str, url: str,
          server: Optional[str] = None):
    """Send iPhone push via ntfy.sh (free, no account needed)."""
    if not topic:
        return
    try:
        base_server = (server or DEFAULT_NTFY_SERVER).rstrip("/")
        endpoint = f"{base_server}/{topic}"
        safe_title = title.encode("ascii", errors="ignore").decode("ascii").strip() or "BMS Alert"
        headers = {
            "Title": safe_title,
            "Priority": "urgent",
            "Tags": "rotating_light,tickets,movie_camera",
            "Actions": f"view, Book Now, {url}",
            "Content-Type": "text/plain; charset=utf-8",
        }
        resp = requests.post(endpoint, data=body.encode("utf-8"),
                             headers=headers, timeout=10)
        if resp.status_code == 200:
            log(f"📱 ntfy push sent → topic '{topic}'", C.G)
        else:
            log(f"ntfy failed: HTTP {resp.status_code}", C.Y)
    except Exception as e:
        log(f"ntfy error: {e}", C.Y)


def _telegram(token: Optional[str], chat_id: Optional[str], text: str):
    if not token or not chat_id:
        return
    try:
        api = f"https://api.telegram.org/bot{token}/sendMessage"
        resp = requests.post(api, json={
            "chat_id": chat_id, "text": text,
            "parse_mode": "HTML", "disable_web_page_preview": False
        }, timeout=10)
        if resp.status_code != 200:
            log(f"Telegram failed: HTTP {resp.status_code}", C.Y)
    except Exception as e:
        log(f"Telegram error: {e}", C.Y)


def _webhook(url: Optional[str], text: str):
    if not url:
        return
    try:
        payload = {"content": text} if "discord" in url else {"text": text}
        requests.post(url, json=payload, timeout=10)
    except Exception as e:
        log(f"Webhook error: {e}", C.Y)


def fire_alert(source: str, new_theatres: List[Dict[str, Any]],
               booking_url: str, args):
    """Trigger ALL enabled alert channels for newly discovered theatres."""
    names = [t["name"] for t in new_theatres]
    plain_list = ", ".join(names)

    # ── 1. Terminal banner ───────────────────────────────────────────────────
    print("\n" + "=" * 65)
    print(f"{C.G}{C.BOLD}🚨🚨 [{source.upper()}] NEW THEATRES OPENED FOR BOOKING! 🚨🚨{C.X}")
    for t in new_theatres:
        shows = ", ".join(t["shows"]) if t["shows"] else "shows pending"
        print(f"{C.B}  • {t['name']}{C.X}")
        print(f"    Shows: {shows}")
    print(f"  Book now → {booking_url}")
    print("=" * 65 + "\n")
    sys.stdout.write("\a\a\a"); sys.stdout.flush()

    # ── 2. macOS desktop notification & DND overrides ───────────────────────
    _macos_notify(
        title=f"🎬 [{source}] Booking Open!",
        msg=f"{len(names)} theatre(s): {plain_list[:120]}",
        sound="Hero"
    )
    if not args.no_sound:
        _macos_sound()
        _macos_voice(f"Alert! New theatres opened on {source} for Avengers Doomsday!")

    # On-screen modal alert (pops up over all windows, breaks through DND)
    _macos_modal_alert(
        title=f"🚨 [{source}] Booking Open!",
        msg=f"{len(names)} new theatre(s) open:\n{plain_list}\n\nClick 'Book Now' to open your browser.",
        url=booking_url
    )

    if getattr(args, "auto_open", False):
        try:
            subprocess.Popen(["open", booking_url])
        except Exception:
            pass

    # ── 3. iPhone via ntfy ───────────────────────────────────────────────────
    ntfy_lines = [f"• {t['name']} → {', '.join(t['shows']) or 'shows pending'}"
                  for t in new_theatres]
    _ntfy(
        topic=args.ntfy_topic,
        title=f"[{source}] Avengers Doomsday: {len(new_theatres)} new theatre(s)!",
        body="\n".join(ntfy_lines) + "\n\nTap 'Book Now' to open the site.",
        url=booking_url,
        server=args.ntfy_server,
    )

    # ── 4. Telegram ──────────────────────────────────────────────────────────
    tg_lines = "\n".join(
        f"• <b>{t['name']}</b> ({len(t['shows'])} shows)" for t in new_theatres
    )
    _telegram(args.telegram_token, args.telegram_chat_id,
              f"🚨 <b>[{source}] BOOKING OPENED!</b>\n\n{tg_lines}\n\n"
              f"🔗 <a href=\"{booking_url}\">Book on {source}</a>")

    # ── 5. Webhook ───────────────────────────────────────────────────────────
    _webhook(args.webhook_url,
             f"🚨 [{source}] BOOKING OPENED: {plain_list}\n{booking_url}")


# ══════════════════════════════════════════════════════════════════════════════
#  BOOKMYSHOW SCRAPER
# ══════════════════════════════════════════════════════════════════════════════

BROWSER_HEADERS = {
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.9,ta;q=0.8",
    "Sec-Ch-Ua": '"Google Chrome";v="131", "Chromium";v="131", "Not_A Brand";v="24"',
    "Sec-Ch-Ua-Mobile": "?0",
    "Sec-Ch-Ua-Platform": '"macOS"',
    "Sec-Fetch-Dest": "document",
    "Sec-Fetch-Mode": "navigate",
    "Sec-Fetch-Site": "none",
    "Sec-Fetch-User": "?1",
    "Upgrade-Insecure-Requests": "1",
}


def _fetch_html(session: requests.Session, url: str, args) -> Optional[str]:
    """
    Fetches raw HTML from target URL, supporting:
      1. Direct requests with browser headers
      2. ScraperAPI (supports multiple keys with automatic failover if tokens run out)
      3. ZenRows (if --zenrows-key is provided, uses proxy_country=in)
      4. Custom HTTP/SOCKS5 proxy (if --proxy-url is provided)
    """
    import urllib.parse
    scraperapi_key = getattr(args, "scraperapi_key", None)
    backup_key = getattr(args, "scraperapi_backup_key", None)
    zenrows_key = getattr(args, "zenrows_key", None)
    proxy_url = getattr(args, "proxy_url", None)

    # Collect all ScraperAPI keys (supports comma-separated list or backup key)
    keys = []
    if scraperapi_key:
        keys.extend([k.strip() for k in scraperapi_key.split(",") if k.strip()])
    if backup_key:
        keys.extend([k.strip() for k in backup_key.split(",") if k.strip()])

    try:
        if keys:
            for idx, key in enumerate(keys):
                fetch_url = f"https://api.scraperapi.com?api_key={key}&country_code=in&url={urllib.parse.quote(url)}"
                resp = session.get(fetch_url, timeout=45)
                if resp.status_code == 200:
                    return resp.text
                elif resp.status_code in (401, 402, 403, 429) and len(keys) > 1 and idx < len(keys) - 1:
                    log(f"[ScraperAPI] Key #{idx+1} out of credits (HTTP {resp.status_code}). Switching to backup key...", C.Y)
                    continue
                else:
                    log(f"[ScraperAPI] HTTP {resp.status_code} for {url[:55]}...", C.R)
                    return None
            return None
        elif zenrows_key:
            fetch_url = f"https://api.zenrows.com/v1/?apikey={zenrows_key}&url={urllib.parse.quote(url)}&premium_proxy=true&proxy_country=in"
            resp = session.get(fetch_url, timeout=45)
        elif proxy_url:
            resp = session.get(url, headers=BROWSER_HEADERS, proxies={"http": proxy_url, "https": proxy_url}, timeout=25)
        else:
            resp = session.get(url, headers=BROWSER_HEADERS, timeout=20)

        if resp.status_code != 200:
            log(f"HTTP {resp.status_code} for {url[:55]}...", C.R)
            return None
        return resp.text
    except Exception as e:
        log(f"Fetch error: {e}", C.R)
        return None


def _bms_fetch(session: requests.Session, url: str, args) -> Optional[Dict]:
    html = _fetch_html(session, url, args)
    if not html:
        return None
    try:
        idx = html.find("window.__INITIAL_STATE__ = ")
        if idx == -1:
            log("[BMS] __INITIAL_STATE__ not found in page", C.R)
            return None
        start = idx + len("window.__INITIAL_STATE__ = ")
        state, _ = json.JSONDecoder().raw_decode(html[start:])
        return state
    except Exception as e:
        log(f"[BMS] JSON parse error: {e}", C.R)
        return None


def bms_extract(state: Dict) -> Tuple[Dict[str, Dict], List[str]]:
    """Return (theatres_dict, available_dates) from BMS __INITIAL_STATE__."""
    theatres: Dict[str, Dict] = {}
    dates: List[str] = []

    queries = state.get("showtimesFunctionalApi", {}).get("queries", {})
    for qk, qv in queries.items():
        if "fetchPrimaryDynamic" not in qk:
            continue
        dyn = qv.get("data", {}).get("data", {})

        # Date carousel
        for w in dyn.get("topStickyWidgets", []):
            for item in w.get("data", []):
                dc = item.get("cta", {}).get("additionalData", {}).get("dateCode")
                if dc and dc not in dates:
                    dates.append(dc)

        # Venue cards
        for w in dyn.get("showtimeWidgets", []):
            if w.get("type") != "groupList":
                continue
            for group in w.get("data", []):
                for card in group.get("data", []):
                    ad = card.get("additionalData", {})
                    vname = ad.get("venueName")
                    vcode = ad.get("venueCode")
                    if not vname or not vcode:
                        continue
                    shows = []
                    for sec in card.get("showtimesSections", []):
                        for st in sec.get("showtimes", []):
                            t = st.get("title")
                            lang = st.get("subtitleAcronym", "")
                            if t:
                                shows.append(f"{t} ({lang})" if lang else t)
                    theatres[vcode] = {"name": vname, "code": vcode, "shows": shows}

    return theatres, dates


# ══════════════════════════════════════════════════════════════════════════════
#  DISTRICT SCRAPER
# ══════════════════════════════════════════════════════════════════════════════

def _district_fetch(session: requests.Session, url: str, args) -> Optional[Dict]:
    html = _fetch_html(session, url, args)
    if not html:
        return None
    try:
        import re
        m = re.search(
            r'<script id="__NEXT_DATA__" type="application/json">(.*?)</script>',
            html, re.DOTALL
        )
        if not m:
            log("[District] __NEXT_DATA__ not found in page", C.R)
            return None
        return json.loads(m.group(1))
    except Exception as e:
        log(f"[District] JSON parse error: {e}", C.R)
        return None


def district_extract(nd: Dict) -> Tuple[Dict[str, Dict], List[str]]:
    """Return (theatres_dict, available_dates) from District __NEXT_DATA__."""
    theatres: Dict[str, Dict] = {}
    dates: List[str] = []

    try:
        pp = nd["props"]["pageProps"]
        server_state = pp.get("data", {}).get("serverState", {})
        movie_sessions = server_state.get("movieSessions", {})

        for _key, session_obj in movie_sessions.items():
            # Available dates
            session_dates = session_obj.get("data", {}).get("sessionDates", [])
            for d in session_dates:
                if d not in dates:
                    dates.append(d)

            # Cinemas with arranged sessions
            arranged = session_obj.get("arrangedSessions", [])
            for item in arranged:
                cinema_name = item.get("entityName", "")
                cinema_code = str(item.get("entityCode", ""))
                cinema_url  = item.get("entityUrl", "")
                sessions    = item.get("sessions", [])

                if not cinema_name or not cinema_code:
                    continue

                shows = []
                # Build a human-readable language label from the session data
                _lang_map = {}  # Build from filterBuckets if available
                for sess in sessions:
                    show_time = sess.get("showTimeText", "")
                    # Prefer human label; fall back to format code
                    lang = (sess.get("language")
                            or sess.get("languageLabel")
                            or sess.get("subtitle")
                            or "")
                    # If lang is still the opaque key (Mm5dpqTqJz-style), replace with "ENG"
                    if lang and len(lang) > 10 and not any(c.isspace() for c in lang):
                        lang = "ENG"
                    if show_time:
                        shows.append(f"{show_time} ({lang})" if lang else show_time)

                theatres[cinema_code] = {
                    "name": cinema_name,
                    "code": cinema_code,
                    "url": cinema_url,
                    "shows": shows,
                }
    except Exception as e:
        log(f"[District] extract error: {e}", C.R)

    return theatres, dates


# ══════════════════════════════════════════════════════════════════════════════
#  STATE PERSISTENCE
# ══════════════════════════════════════════════════════════════════════════════

def load_state(path: str) -> Dict:
    default_state = {
        "bms": {"theatres": {}, "dates": []},
        "district": {"theatres": {}, "dates": []}
    }
    if os.path.exists(path):
        try:
            with open(path) as f:
                data = json.load(f)
                if isinstance(data, dict):
                    if "bms" in data and "district" in data:
                        return data
                    if "theatres" in data:
                        default_state["bms"]["theatres"] = data.get("theatres", {})
                        default_state["bms"]["dates"] = data.get("dates", [])
                        return default_state
        except Exception:
            pass
    return default_state


def save_state(path: str, data: Dict):
    try:
        with open(path, "w") as f:
            json.dump(data, f, indent=2)
    except Exception as e:
        log(f"State save failed: {e}", C.Y)


# ══════════════════════════════════════════════════════════════════════════════
#  MAIN MONITOR LOOP
# ══════════════════════════════════════════════════════════════════════════════

def run_monitor(args):
    # Rotate Chrome profile per run → different TLS fingerprint each time
    _chrome = random.choice(["chrome120", "chrome123", "chrome124", "chrome131"])
    log(f"Chrome impersonation profile: {_chrome}")
    session = requests.Session(impersonate=_chrome)

    if args.reset and os.path.exists(args.state_file):
        os.remove(args.state_file)
        log(f"Reset state file: {args.state_file}", C.Y)

    state = load_state(args.state_file)
    known = {
        "bms":      set(state["bms"]["theatres"].keys()),
        "district": set(state["district"]["theatres"].keys()),
    }
    known_dates = {
        "bms":      set(state["bms"]["dates"]),
        "district": set(state["district"]["dates"]),
    }

    # ── Banner ────────────────────────────────────────────────────────────────
    print(f"{C.BOLD}{C.B}" + "=" * 65)
    print("🎬  Avengers: Doomsday — Cinema Booking Monitor")
    print("    Watching: BookMyShow  +  District")
    print("=" * 65 + C.X)
    print(f"BMS URL:      {args.bms_url}")
    print(f"District URL: {args.district_url}")
    print(f"Interval:     {args.interval}s (±3s jitter)")
    if args.ntfy_topic:
        print(f"📱 iPhone:    ntfy topic '{args.ntfy_topic}' on {args.ntfy_server}")
    else:
        print("📱 iPhone:    NOT set  (use --ntfy-topic to enable)")
    if known["bms"]:
        print(f"BMS known theatres ({len(known['bms'])}): "
              + ", ".join(state["bms"]["theatres"].values()))
    if known["district"]:
        print(f"District known theatres ({len(known['district'])}): "
              + ", ".join(state["district"]["theatres"].values()))
    print("-" * 65)

    errors = {"bms": 0, "district": 0}

    while True:
        try:
            # ── BookMyShow ────────────────────────────────────────────────────
            bms_state = _bms_fetch(session, args.bms_url, args)
            if bms_state:
                errors["bms"] = 0
                theatres, dates = bms_extract(bms_state)
                new_codes = set(theatres) - known["bms"]
                new_dates = set(dates) - known_dates["bms"]

                log(f"[BMS] {len(theatres)} theatre(s) | dates: {', '.join(dates) or 'N/A'}", C.B)
                for code, t in theatres.items():
                    pfx = f"  {C.G}{C.BOLD}⭐ [NEW] " if code in new_codes else "  👉 "
                    shows_str = ", ".join(t["shows"]) or "shows pending"
                    print(f"{pfx}{t['name']} ({code}): {shows_str}{C.X}")

                if new_codes and known["bms"]:
                    fire_alert("BookMyShow",
                               [theatres[c] for c in new_codes],
                               args.bms_url, args)
                elif new_codes:
                    log(f"[BMS] Baseline set: {len(new_codes)} theatre(s)", C.G)

                if new_dates and known_dates["bms"]:
                    log(f"[BMS] 🗓 New dates: {', '.join(new_dates)}", C.G)
                    _macos_notify("🗓 BMS: New Dates!", f"Dates: {', '.join(new_dates)}", "Ping")
                    _ntfy(args.ntfy_topic, "BMS: New dates open!",
                          f"New dates: {', '.join(new_dates)}",
                          args.bms_url, args.ntfy_server)

                known["bms"].update(new_codes)
                known_dates["bms"].update(dates)
                state["bms"]["theatres"] = {c: theatres[c]["name"] for c in known["bms"]
                                             if c in theatres}
                state["bms"]["dates"] = list(known_dates["bms"])
            else:
                errors["bms"] += 1
                log(f"[BMS] Error #{errors['bms']} — skipping", C.Y)

            # ── District ──────────────────────────────────────────────────────
            dist_nd = _district_fetch(session, args.district_url, args)
            if dist_nd:
                errors["district"] = 0
                theatres_d, dates_d = district_extract(dist_nd)
                new_codes_d = set(theatres_d) - known["district"]
                new_dates_d = set(dates_d) - known_dates["district"]

                log(f"[District] {len(theatres_d)} theatre(s) | dates: {', '.join(dates_d) or 'N/A'}", M := C.M)
                for code, t in theatres_d.items():
                    pfx = f"  {C.G}{C.BOLD}⭐ [NEW] " if code in new_codes_d else "  👉 "
                    shows_str = ", ".join(t["shows"]) or "shows pending"
                    print(f"{pfx}{t['name']} ({code}): {shows_str}{C.X}")

                if new_codes_d and known["district"]:
                    fire_alert("District",
                               [theatres_d[c] for c in new_codes_d],
                               f"https://www.district.in{theatres_d[list(new_codes_d)[0]].get('url', '')}",
                               args)
                elif new_codes_d:
                    log(f"[District] Baseline set: {len(new_codes_d)} theatre(s)", C.G)

                if new_dates_d and known_dates["district"]:
                    log(f"[District] 🗓 New dates: {', '.join(new_dates_d)}", C.G)
                    _macos_notify("🗓 District: New Dates!", f"Dates: {', '.join(new_dates_d)}", "Ping")
                    _ntfy(args.ntfy_topic, "District: New dates open!",
                          f"New dates: {', '.join(new_dates_d)}",
                          args.district_url, args.ntfy_server)

                known["district"].update(new_codes_d)
                known_dates["district"].update(dates_d)
                state["district"]["theatres"] = {c: theatres_d[c]["name"] for c in known["district"]
                                                  if c in theatres_d}
                state["district"]["dates"] = list(known_dates["district"])
            else:
                errors["district"] += 1
                log(f"[District] Error #{errors['district']} — skipping", C.Y)

            save_state(args.state_file, state)

            if args.once:
                log("Single check done (--once). Exiting.", C.G)
                break

            jitter = random.uniform(-3.0, 3.0)
            time.sleep(max(5.0, args.interval + jitter))

        except KeyboardInterrupt:
            print(f"\n{C.Y}Stopped by user. Goodbye!{C.X}")
            break
        except Exception as e:
            log(f"Unexpected error: {e}", C.R)
            time.sleep(args.interval)


# ══════════════════════════════════════════════════════════════════════════════
#  CLI
# ══════════════════════════════════════════════════════════════════════════════

def main():
    p = argparse.ArgumentParser(
        description="Avengers: Doomsday booking monitor — BookMyShow + District"
    )
    p.add_argument("--bms-url",      default=BMS_URL,
                   help="BookMyShow showtimes URL")
    p.add_argument("--district-url", default=DISTRICT_URL,
                   help="District movie URL")
    p.add_argument("-i", "--interval", type=int, default=60,
                   help="Poll interval in seconds (default: 60)")
    p.add_argument("--once",   action="store_true",
                   help="Run one check and exit (useful for testing / CI)")
    p.add_argument("--reset",  action="store_true",
                   help="Wipe saved state and treat current theatres as new baseline")
    p.add_argument("--no-sound", action="store_true",
                   help="Suppress audio chime on alerts")
    p.add_argument("--state-file", default=DEFAULT_STATE_FILE,
                   help=f"Path to state JSON file (default: {DEFAULT_STATE_FILE})")
    # ntfy (iPhone)
    p.add_argument("--ntfy-topic",
                   default=os.environ.get("NTFY_TOPIC"),
                   help="ntfy topic for iPhone push (env: NTFY_TOPIC). "
                        "Install 'ntfy' on iPhone → subscribe to https://ntfy.sh/<topic>")
    p.add_argument("--ntfy-server",
                   default=os.environ.get("NTFY_SERVER", DEFAULT_NTFY_SERVER),
                   help=f"ntfy server URL (default: {DEFAULT_NTFY_SERVER})")
    # Telegram
    p.add_argument("--telegram-token",
                   default=os.environ.get("TELEGRAM_BOT_TOKEN"),
                   help="Telegram bot token (env: TELEGRAM_BOT_TOKEN)")
    p.add_argument("--telegram-chat-id",
                   default=os.environ.get("TELEGRAM_CHAT_ID"),
                   help="Telegram chat ID (env: TELEGRAM_CHAT_ID)")
    # Webhook
    p.add_argument("--webhook-url",
                   default=os.environ.get("WEBHOOK_URL"),
                   help="Discord/Slack webhook URL (env: WEBHOOK_URL)")
    # Cloud Proxy / Scraper API for bypassing datacenter IP blocks
    p.add_argument("--scraperapi-key",
                   default=os.environ.get("SCRAPERAPI_KEY"),
                   help="ScraperAPI key (env: SCRAPERAPI_KEY) to route cloud requests through Indian residential IPs")
    p.add_argument("--scraperapi-backup-key",
                   default=os.environ.get("SCRAPERAPI_BACKUP_KEY"),
                   help="Backup ScraperAPI key (env: SCRAPERAPI_BACKUP_KEY) to auto-switch if primary runs out of tokens")
    p.add_argument("--zenrows-key",
                   default=os.environ.get("ZENROWS_KEY"),
                   help="ZenRows API key (env: ZENROWS_KEY) to route cloud requests through Indian residential IPs")
    p.add_argument("--proxy-url",
                   default=os.environ.get("PROXY_URL"),
                   help="Custom HTTP/SOCKS5 proxy URL (env: PROXY_URL)")

    p.add_argument("--auto-open", action="store_true",
                   help="Automatically open the booking page in default browser when new theatres open")
    p.add_argument("--test-alert", action="store_true",
                   help="Fire a sample alert immediately (banner, sound, voice, popup, ntfy) to verify DND bypass")

    args = p.parse_args()
    if args.test_alert:
        print(f"{C.G}Firing sample alert to test Mac notifications & DND overrides...{C.X}")
        fire_alert(
            "Test (BMS)",
            [
                {"name": "PVR: VR Chennai, Anna Nagar", "shows": ["10:00 AM (ENG)", "01:30 PM (ENG)", "08:30 PM (ENG)"]},
                {"name": "Sathyam Cinemas (SPI), Royapettah", "shows": ["11:00 AM (ENG)", "03:15 PM (ENG)"]}
            ],
            BMS_URL,
            args
        )
        print(f"{C.G}Test alert completed!{C.X}")
        return

    run_monitor(args)


if __name__ == "__main__":
    main()
