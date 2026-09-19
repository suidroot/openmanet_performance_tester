import argparse
import csv
import hashlib
import io
import os
import socket
import sqlite3
import sys
import urllib.parse
from pathlib import Path

from flask import Flask, g, jsonify, request, send_from_directory

BASE = Path(__file__).parent
DB_PATH = Path(os.environ.get("MANET_DB", BASE / "sessions.db"))

app = Flask(__name__, static_folder=str(BASE / "static"), static_url_path="/static")
app.config["MAX_CONTENT_LENGTH"] = 50 * 1024 * 1024

SCHEMA = """
CREATE TABLE IF NOT EXISTS sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    sha256 TEXT NOT NULL UNIQUE,
    uploaded_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS points (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id INTEGER NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    ts TEXT,
    lat REAL NOT NULL,
    lon REAL NOT NULL,
    mode TEXT,
    device TEXT,
    ping_ms REAL,
    throughput REAL,
    signal_dbm REAL,
    quality REAL,
    ip TEXT,
    iperf REAL
);
CREATE INDEX IF NOT EXISTS idx_points_session ON points(session_id, ts);
"""

COLUMNS = {
    "date": "ts", "lat": "lat", "log": "lon", "lon": "lon", "lng": "lon",
    "mode": "mode", "device name": "device", "ping delay": "ping_ms",
    "throughput": "throughput", "signal (dbm)": "signal_dbm",
    "quality": "quality", "ip address": "ip", "iperf measurement": "iperf",
}
NUMERIC = {"lat", "lon", "ping_ms", "throughput", "signal_dbm", "quality", "iperf"}


def db():
    if "db" not in g:
        g.db = sqlite3.connect(DB_PATH)
        g.db.row_factory = sqlite3.Row
        g.db.execute("PRAGMA foreign_keys = ON")
    return g.db


@app.teardown_appcontext
def close_db(_):
    conn = g.pop("db", None)
    if conn:
        conn.close()


def init_db():
    with sqlite3.connect(DB_PATH) as conn:
        conn.executescript(SCHEMA)


def num(v):
    try:
        return float(v) if v not in (None, "") else None
    except ValueError:
        return None


def parse_csv(text):
    reader = csv.DictReader(io.StringIO(text))
    rows = []
    for raw in reader:
        row = {}
        for k, v in raw.items():
            col = COLUMNS.get((k or "").strip().lower())
            if col:
                v = (v or "").strip()
                row[col] = num(v) if col in NUMERIC else (v or None)
        if row.get("lat") is not None and row.get("lon") is not None:
            rows.append(row)
    return rows


def unique_name(conn, name):
    candidate, n = name, 2
    while conn.execute("SELECT 1 FROM sessions WHERE name = ?", (candidate,)).fetchone():
        candidate = f"{name} ({n})"
        n += 1
    return candidate


def ingest(filename, data):
    text = data.decode("utf-8-sig", errors="replace")
    rows = parse_csv(text)
    if not rows:
        raise ValueError("no rows with valid Lat/Log found")
    digest = hashlib.sha256(data).hexdigest()
    conn = db()
    existing = conn.execute("SELECT id, name FROM sessions WHERE sha256 = ?", (digest,)).fetchone()
    if existing:
        return existing["id"], existing["name"], 0
    name = unique_name(conn, Path(filename).stem)
    cur = conn.execute("INSERT INTO sessions (name, sha256) VALUES (?, ?)", (name, digest))
    sid = cur.lastrowid
    fields = ["ts", "lat", "lon", "mode", "device", "ping_ms", "throughput",
              "signal_dbm", "quality", "ip", "iperf"]
    conn.executemany(
        f"INSERT INTO points (session_id, {', '.join(fields)}) VALUES (?, {', '.join('?' * len(fields))})",
        [(sid, *[r.get(f) for f in fields]) for r in rows],
    )
    conn.commit()
    return sid, name, len(rows)


@app.get("/")
def index():
    return send_from_directory(app.static_folder, "index.html")


@app.get("/api/sessions")
def list_sessions():
    rows = db().execute(
        """SELECT s.id, s.name, s.uploaded_at, COUNT(p.id) AS points,
                  MIN(p.ts) AS start, MAX(p.ts) AS end
           FROM sessions s LEFT JOIN points p ON p.session_id = s.id
           GROUP BY s.id ORDER BY start DESC, s.id DESC"""
    ).fetchall()
    return jsonify([dict(r) for r in rows])


@app.get("/api/sessions/<int:sid>")
def get_session(sid):
    conn = db()
    s = conn.execute("SELECT id, name FROM sessions WHERE id = ?", (sid,)).fetchone()
    if not s:
        return jsonify(error="not found"), 404
    pts = conn.execute(
        """SELECT ts, lat, lon, mode, device, ping_ms, throughput, signal_dbm,
                  quality, ip, iperf
           FROM points WHERE session_id = ? ORDER BY ts, id""", (sid,)
    ).fetchall()
    return jsonify(id=s["id"], name=s["name"], points=[dict(p) for p in pts])


@app.delete("/api/sessions/<int:sid>")
def delete_session(sid):
    conn = db()
    conn.execute("DELETE FROM sessions WHERE id = ?", (sid,))
    conn.commit()
    return "", 204


@app.post("/api/upload")
def upload():
    files = request.files.getlist("files")
    if not files:
        return jsonify(error="no files"), 400
    results = []
    for f in files:
        try:
            sid, name, n = ingest(f.filename or "session.csv", f.read())
            results.append(dict(file=f.filename, id=sid, name=name, points=n, duplicate=n == 0))
        except Exception as e:
            results.append(dict(file=f.filename, error=str(e)))
    return jsonify(results)


@app.route("/<path:name>", methods=["PUT", "POST"])
def raw_upload(name):
    """Endpoint for the Android app's Export screen: a plain PUT/POST with the CSV as the body
    (no multipart, no auth). The last URL path segment becomes the session name."""
    data = request.get_data()
    filename = os.path.basename(urllib.parse.unquote(name).rstrip("/")) or "upload.csv"
    try:
        sid, session, n = ingest(filename, data)
    except Exception as e:
        print(f"[upload] {request.remote_addr} {filename} rejected: {e}", file=sys.stderr, flush=True)
        return str(e), 400
    print(f"[upload] {request.remote_addr} {request.method} /{name} ({len(data)} bytes) -> "
          f"session '{session}' ({'duplicate, ignored' if n == 0 else f'{n} points'})", flush=True)
    return "", 200


def local_ip():
    """Best-effort LAN IP for a ready-to-paste URL; sends nothing, just asks the OS which
    interface would reach the internet."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


init_db()

if __name__ == "__main__":
    ap = argparse.ArgumentParser(description="MANET session viewer and CSV upload receiver")
    ap.add_argument("--host", default=os.environ.get("HOST", "127.0.0.1"),
                    help="bind address (default 127.0.0.1)")
    ap.add_argument("--lan", action="store_true",
                    help="listen on all interfaces (0.0.0.0) so a phone can upload; no auth!")
    ap.add_argument("--port", type=int, default=int(os.environ.get("PORT", 5000)))
    args = ap.parse_args()
    host = "0.0.0.0" if args.lan else args.host

    shown = local_ip() if host == "0.0.0.0" else host
    print(f"Viewer:       http://{shown}:{args.port}/")
    print(f"App endpoint: http://{shown}:{args.port}/session_log.csv  "
          "(paste into the app's Export screen 'Endpoint URL')")
    if host == "0.0.0.0":
        print("WARNING: listening on all interfaces with no authentication.")
    app.run(host=host, port=args.port, threaded=True, debug=False)
