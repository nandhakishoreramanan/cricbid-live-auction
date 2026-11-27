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
    "https://in.bookmyshow.com/movies/chennai/avengers-doomsday/buytickets/ET00526306"
)
DISTRICT_URL = (
    "https://www.district.in/movies/avengers-doomsday-movie-tickets-in-chennai-MV176735"
    "?frmtid=Mm5dpqTqJz"
)
DEFAULT_MOVIES = [
    {
        "id": "avengers_doomsday",
        "title": "Avengers: Doomsday",
        "bms_url": (
            "https://in.bookmyshow.com/movies/chennai/avengers-doomsday/buytickets/ET00526306"
        ),
        "district_url": (
            "https://www.district.in/movies/avengers-doomsday-movie-tickets-in-chennai-MV176735"
            "?frmtid=Mm5dpqTqJz"
        ),
    },
    {
        "id": "dune_part_three",
        "title": "Dune: Part Three",
        "bms_url": (
            "https://in.bookmyshow.com/movies/chennai/dune-part-three/buytickets/ET00491771"
        ),
        "district_url": None,
    },
]
DEFAULT_STATE_FILE = "bms_theatres_state.json"
DEFAULT_NTFY_SERVER = "https://ntfy.sh"
DEFAULT_NTFY_TOPIC = "nandy-bms-avengers-2026"

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
               booking_url: str, args, movie_title: str = "Avengers Doomsday"):
    """Trigger ALL enabled alert channels for newly discovered theatres."""
    names = [t["name"] for t in new_theatres]
    plain_list = ", ".join(names)

    # ── 1. Terminal banner ───────────────────────────────────────────────────
    print("\n" + "=" * 65)
    print(f"{C.G}{C.BOLD}🚨🚨 [{source.upper()}] NEW THEATRES OPENED FOR {movie_title.upper()}! 🚨🚨{C.X}")
    for t in new_theatres:
        shows = ", ".join(t["shows"]) if t["shows"] else "shows pending"
        print(f"{C.B}  • {t['name']}{C.X}")
        print(f"    Shows: {shows}")
    print(f"  Book now → {booking_url}")
    print("=" * 65 + "\n")
    sys.stdout.write("\a\a\a"); sys.stdout.flush()

    # ── 2. macOS desktop notification & DND overrides ───────────────────────
    _macos_notify(
        title=f"🎬 [{source}] {movie_title}: Booking Open!",
        msg=f"{len(names)} theatre(s): {plain_list[:120]}",
        sound="Hero"
    )
    if not args.no_sound:
        _macos_sound()
        _macos_voice(f"Alert! New theatres opened on {source} for {movie_title}!")

    # On-screen modal alert (pops up over all windows, breaks through DND)
    _macos_modal_alert(
        title=f"🚨 [{source}] {movie_title} Booking Open!",
        msg=f"{len(names)} new theatre(s) open for {movie_title}:\n{plain_list}\n\nClick 'Book Now' to open your browser.",
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
        title=f"[{source}] {movie_title}: {len(new_theatres)} new theatre(s)!",
        body="\n".join(ntfy_lines) + f"\n\nTap to book tickets for {movie_title}!",
        url=booking_url,
        server=args.ntfy_server,
    )

    # ── 4. Telegram ──────────────────────────────────────────────────────────
    tg_lines = "\n".join(
        f"• <b>{t['name']}</b> ({len(t['shows'])} shows)" for t in new_theatres
    )
    _telegram(args.telegram_token, args.telegram_chat_id,
              f"🚨 <b>[{source}] {movie_title} BOOKING OPENED!</b>\n\n{tg_lines}\n\n"
              f"🔗 <a href=\"{booking_url}\">Book on {source}</a>")

    # ── 5. Webhook ───────────────────────────────────────────────────────────
    _webhook(args.webhook_url,
             f"🚨 [{source}] {movie_title} BOOKING OPENED: {plain_list}\n{booking_url}")


def fire_date_alert(source: str, new_dates: List[str],
                    booking_url: str, args, movie_title: str = "Avengers Doomsday"):
    """Trigger alerts when new show dates open up on BMS or District."""
    date_str = ", ".join(sorted(new_dates))
    log(f"[{source}] 🗓 {movie_title} — New dates open: {date_str}", C.G)

    _macos_notify(
        title=f"🗓 [{source}] {movie_title}: New Dates!",
        msg=f"Dates: {date_str}",
        sound="Ping"
    )
    if not args.no_sound:
        _macos_sound()
        _macos_voice(f"New dates opened on {source} for {movie_title}!")

    _ntfy(
        topic=args.ntfy_topic,
        title=f"[{source}] {movie_title}: New Dates Open!",
        body=f"🗓️ New dates available: {date_str}\n\nTap to book tickets now!",
        url=booking_url,
        server=args.ntfy_server,
    )

    _telegram(
        args.telegram_token, args.telegram_chat_id,
        f"🗓️ <b>[{source}] {movie_title} NEW DATES OPEN!</b>\n\nDates: {date_str}\n\n"
        f"🔗 <a href=\"{booking_url}\">Book on {source}</a>"
    )

    _webhook(args.webhook_url,
             f"🗓️ [{source}] {movie_title} NEW DATES: {date_str}\n{booking_url}")


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
                    # Group shows by showDateCode
                    date_shows: Dict[str, List[str]] = {}
                    for sec in card.get("showtimesSections", []):
                        for st in sec.get("showtimes", []):
                            t = st.get("title")
                            lang = st.get("subtitleAcronym", "")
                            sd = st.get("additionalData", {}).get("showDateCode") or (dates[0] if dates else "")
                            if t:
                                show_str = f"{t} ({lang})" if lang else t
                                date_shows.setdefault(sd, []).append(show_str)

                    if not date_shows:
                        date_shows[""] = []

                    for sdate, shows in date_shows.items():
                        key = f"{vcode}_{sdate}" if sdate else vcode
                        display_name = f"{vname} [{sdate}]" if sdate else vname
                        theatres[key] = {
                            "name": display_name,
                            "venue_name": vname,
                            "code": vcode,
                            "date": sdate,
                            "shows": shows,
                        }

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

                date_shows: Dict[str, List[str]] = {}
                for sess in sessions:
                    show_time = sess.get("showTimeText", "")
                    sdate = sess.get("showDate") or sess.get("sessionDate") or ""
                    lang = (sess.get("language")
                            or sess.get("languageLabel")
                            or sess.get("subtitle")
                            or "")
                    if lang and len(lang) > 10 and not any(c.isspace() for c in lang):
                        lang = "ENG"
                    if show_time:
                        lbl = f"{show_time} ({lang})" if lang else show_time
                        date_shows.setdefault(sdate, []).append(lbl)

                if not date_shows:
                    date_shows[""] = []

                for sdate, shows in date_shows.items():
                    key = f"{cinema_code}_{sdate}" if sdate else cinema_code
                    display_name = f"{cinema_name} [{sdate}]" if sdate else cinema_name
                    theatres[key] = {
                        "name": display_name,
                        "venue_name": cinema_name,
                        "code": cinema_code,
                        "url": cinema_url,
                        "date": sdate,
                        "shows": shows,
                    }
    except Exception as e:
        log(f"[District] extract error: {e}", C.R)

    return theatres, dates


# ══════════════════════════════════════════════════════════════════════════════
#  STATE PERSISTENCE
# ══════════════════════════════════════════════════════════════════════════════

def load_state(path: str) -> Dict:
    state = {}
    if os.path.exists(path):
        try:
            with open(path) as f:
                data = json.load(f)
                if isinstance(data, dict):
                    # Check if legacy flat state (only had "bms" / "district")
                    if "bms" in data or "district" in data:
                        state["avengers_doomsday"] = {
                            "bms": data.get("bms", {"theatres": {}, "dates": []}),
                            "district": data.get("district", {"theatres": {}, "dates": []})
                        }
                    else:
                        state = data
        except Exception:
            state = {}

    for m in DEFAULT_MOVIES:
        mid = m["id"]
        if mid not in state:
            state[mid] = {
                "bms": {"theatres": {}, "dates": []},
                "district": {"theatres": {}, "dates": []}
            }
        else:
            if "bms" not in state[mid]:
                state[mid]["bms"] = {"theatres": {}, "dates": []}
            if "district" not in state[mid]:
                state[mid]["district"] = {"theatres": {}, "dates": []}
    return state


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

    # Determine movies to monitor:
    # If custom CLI arguments were provided that differ from the Avengers defaults,
    # monitor only that custom single target. Otherwise, monitor all DEFAULT_MOVIES.
    custom_run = (args.bms_url != BMS_URL or args.district_url != DISTRICT_URL)
    if custom_run:
        movies = [{
            "id": "custom",
            "title": "Movie",
            "bms_url": args.bms_url,
            "district_url": args.district_url,
        }]
    else:
        movies = DEFAULT_MOVIES

    # ── Banner ────────────────────────────────────────────────────────────────
    print(f"{C.BOLD}{C.B}" + "=" * 65)
    print("🎬  Cinema Booking Monitor — Chennai (BMS + District)")
    for m in movies:
        print(f"    • {m['title']}")
    print("=" * 65 + C.X)
    print(f"Interval:     {args.interval}s (±3s jitter)")
    if args.ntfy_topic:
        print(f"📱 iPhone:    ntfy topic '{args.ntfy_topic}' on {args.ntfy_server}")
    else:
        print("📱 iPhone:    NOT set  (use --ntfy-topic to enable)")
    for m in movies:
        mid = m["id"]
        m_state = state.get(mid, {"bms": {"theatres": {}, "dates": []}, "district": {"theatres": {}, "dates": []}})
        bms_th = list(m_state["bms"]["theatres"].values())
        dist_th = list(m_state["district"]["theatres"].values())
        bms_summary = f"{len(bms_th)} ({', '.join(bms_th)})" if bms_th else "0 known"
        dist_summary = f"{len(dist_th)} ({', '.join(dist_th)})" if dist_th else "0 known"
        print(f"[{m['title']}] Known BMS: {bms_summary} | District: {dist_summary}")
    print("-" * 65)

    errors = {m["id"]: {"bms": 0, "district": 0} for m in movies}
    start_time = time.time()

    while True:
        try:
            for m in movies:
                mid = m["id"]
                title = m["title"]
                m_state = state.setdefault(mid, {
                    "bms": {"theatres": {}, "dates": []},
                    "district": {"theatres": {}, "dates": []}
                })

                known_bms = set(m_state["bms"]["theatres"].keys())
                known_dates_bms = set(m_state["bms"]["dates"])

                known_dist = set(m_state["district"]["theatres"].keys())
                known_dates_dist = set(m_state["district"]["dates"])

                # ── BookMyShow ────────────────────────────────────────────────
                if m.get("bms_url"):
                    bms_state = _bms_fetch(session, m["bms_url"], args)
                    if bms_state:
                        errors[mid]["bms"] = 0
                        theatres, dates = bms_extract(bms_state)
                        new_codes = set(theatres) - known_bms
                        new_dates = set(dates) - known_dates_bms

                        log(f"[{title} | BMS] {len(theatres)} theatre(s) | dates: {', '.join(dates) or 'N/A'}", C.B)
                        for code, t in theatres.items():
                            pfx = f"  {C.G}{C.BOLD}⭐ [NEW] " if code in new_codes else "  👉 "
                            shows_str = ", ".join(t["shows"]) or "shows pending"
                            print(f"{pfx}{t['name']} ({code}): {shows_str}{C.X}")

                        if new_codes and known_bms:
                            fire_alert("BookMyShow",
                                       [theatres[c] for c in new_codes],
                                       m["bms_url"], args, movie_title=title)
                        elif new_codes:
                            log(f"[{title} | BMS] Baseline set: {len(new_codes)} theatre(s)", C.G)

                        if new_dates and known_dates_bms:
                            fire_date_alert("BookMyShow", list(new_dates), m["bms_url"], args, movie_title=title)

                        known_bms.update(new_codes)
                        known_dates_bms.update(dates)
                        m_state["bms"]["theatres"] = {c: theatres[c]["name"] for c in known_bms if c in theatres}
                        m_state["bms"]["dates"] = list(known_dates_bms)
                    else:
                        errors[mid]["bms"] += 1
                        log(f"[{title} | BMS] Error #{errors[mid]['bms']} — skipping", C.Y)

                # ── District ──────────────────────────────────────────────────
                if m.get("district_url"):
                    dist_nd = _district_fetch(session, m["district_url"], args)
                    if dist_nd:
                        errors[mid]["district"] = 0
                        theatres_d, dates_d = district_extract(dist_nd)
                        new_codes_d = set(theatres_d) - known_dist
                        new_dates_d = set(dates_d) - known_dates_dist

                        log(f"[{title} | District] {len(theatres_d)} theatre(s) | dates: {', '.join(dates_d) or 'N/A'}", C.M)
                        for code, t in theatres_d.items():
                            pfx = f"  {C.G}{C.BOLD}⭐ [NEW] " if code in new_codes_d else "  👉 "
                            shows_str = ", ".join(t["shows"]) or "shows pending"
                            print(f"{pfx}{t['name']} ({code}): {shows_str}{C.X}")

                        if new_codes_d and known_dist:
                            booking_target = f"https://www.district.in{theatres_d[list(new_codes_d)[0]].get('url', '')}" if new_codes_d else m["district_url"]
                            fire_alert("District",
                                       [theatres_d[c] for c in new_codes_d],
                                       booking_target, args, movie_title=title)
                        elif new_codes_d:
                            log(f"[{title} | District] Baseline set: {len(new_codes_d)} theatre(s)", C.G)

                        if new_dates_d and known_dates_dist:
                            fire_date_alert("District", list(new_dates_d), m["district_url"], args, movie_title=title)

                        known_dist.update(new_codes_d)
                        known_dates_dist.update(dates_d)
                        m_state["district"]["theatres"] = {c: theatres_d[c]["name"] for c in known_dist if c in theatres_d}
                        m_state["district"]["dates"] = list(known_dates_dist)
                    else:
                        errors[mid]["district"] += 1
                        log(f"[{title} | District] Error #{errors[mid]['district']} — skipping", C.Y)

            save_state(args.state_file, state)

            if args.once:
                log("Single check done (--once). Exiting.", C.G)
                break

            if getattr(args, "duration", 0) > 0 and (time.time() - start_time) >= args.duration:
                log(f"Reached max duration ({args.duration}s). Exiting cleanly for next runner chain.", C.G)
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
    p.add_argument("--duration", type=int, default=0,
                   help="Max runtime in seconds before exiting (0 = infinite)")
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
                   default=os.environ.get("NTFY_TOPIC", DEFAULT_NTFY_TOPIC),
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
            args,
            movie_title="Dune 3 & Avengers"
        )
        print(f"{C.G}Test alert completed!{C.X}")
        return

    run_monitor(args)


if __name__ == "__main__":
    main()
