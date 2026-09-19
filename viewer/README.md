# MANET Session Viewer

A small desktop web tool for reviewing field-test results from the Android app. It ingests the
session CSVs the app exports, stores them in SQLite, and plots each session on an interactive
map so you can see where signal, throughput and latency degraded along the route.

It is separate from the Android app: it runs on your laptop, not the phone, and needs no network
access to the mesh.

## Running

```sh
cd viewer
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
.venv/bin/python app.py
```

Then open <http://127.0.0.1:5000>. To receive uploads from the phone, start it with `--lan`
instead (see [Receiving uploads from the app](#receiving-uploads-from-the-app)). Requires Python 3.9+ and Flask; Leaflet and the OpenStreetMap
tiles load from the internet, so the browser needs connectivity for the map itself.

| Option / env var          | Default        | Purpose                                        |
|---------------------------|----------------|------------------------------------------------|
| `--port` / `PORT`         | `5000`         | Port to listen on                              |
| `--host` / `HOST`         | `127.0.0.1`    | Bind address                                   |
| `--lan`                   | off            | Shorthand for `--host 0.0.0.0` (phone uploads) |
| `MANET_DB`                | `sessions.db`  | Path to the SQLite database                    |

## Using it

- **Upload CSV...** ingests one or more files. Each file becomes one *session*, named after the
  filename (a numeric suffix is added if the name is already taken). Uploading a file with
  identical contents again is ignored.
- **Session dropdown** (upper left) lists every stored session with its start time and point
  count. The newest is selected by default and the selection is kept in the URL hash, so a
  reload or shared link returns to the same session.
- **Color by** recolors the track and points by Signal (dBm), Throughput, Ping delay or Quality
  (red = poor, green = good; grey = no data, e.g. a ping timeout). A legend shows the scale.
  The ranges are the `METRICS` table at the top of the script in `static/index.html`.
- **Map** shows the route as colored segments, a green start marker and a red end marker. Click
  any point for its full record.
- **Measure** - click it, then click two places on the map. You get the great-circle distance
  (m/ft, or km/mi when long) and bearing. If both clicks land on session points, the popup also
  shows the time between them and the change in signal, throughput and ping. A third click
  starts over; Esc or clicking Measure again exits.
- **Delete** removes the selected session from the database.

## Receiving uploads from the app

This tool also replaces the old `scripts/upload_test_server.py`. The Android app's Export screen
uploads with a plain HTTP `PUT` (no multipart, no auth header) of the CSV as the body; the viewer
accepts that (and `POST`) on any path and ingests it straight into the database - no intermediate
file.

```sh
.venv/bin/python app.py --lan
```

It prints a ready-to-paste URL such as `http://192.168.1.20:5000/session_log.csv`; put that in the
Export screen's "Endpoint URL" field. The last path segment is used as the session name. The
phone needs internet/LAN reachability to this machine (the mesh itself usually has none, so
upload after leaving the mesh network). A duplicate upload returns 200 and is ignored; an
unparseable body returns 400 with the reason. Uploads are logged to the terminal, and the page
refreshes its session list when the browser tab regains focus.

`--lan` listens on every interface with **no authentication**, including the delete endpoint -
use it on a trusted network for as long as you need it, then stop the server.

## CSV format

Header row required; columns are matched by name, case-insensitively, and unknown columns are
ignored. Rows without a valid `Lat`/`Log` are skipped; empty numeric cells are stored as NULL.

```
Date,Lat,Log,mode,Device Name,Ping delay,Throughput,Signal (dbm),quality,ip address,iperf measurement
2026-09-19 17:06:56,43.6470809,-70.2664491,general,man-gate,429.0,7.1,-46,-46,10.41.0.1,
```

## Storage

Two tables: `sessions` (id, name, sha256 of the file, upload time) and `points` (one row per CSV
row, with `session_id` foreign key and ON DELETE CASCADE). The database file and `.venv/` are
gitignored.

## API

| Method   | Path                  | Description                                   |
|----------|-----------------------|-----------------------------------------------|
| `GET`    | `/api/sessions`       | List sessions (id, name, start, end, points)  |
| `GET`    | `/api/sessions/<id>`  | One session with all its points               |
| `DELETE` | `/api/sessions/<id>`  | Delete a session                              |
| `POST`   | `/api/upload`         | Multipart upload, field name `files`          |
| `PUT`/`POST` | `/<any path>`     | Raw CSV body (what the Android app sends)     |

There is no authentication. The server binds to localhost unless you pass `--lan`/`--host`.
