#!/usr/bin/env python3
"""Standalone HTTP server for receiving the app's CSV export upload.

Matches data/export/UploadService.upload(): a plain HTTP PUT with a text/csv body (no
multipart, no auth header) to whatever endpoint URL is typed into the app's Export screen.
Run this on any machine reachable from wherever the phone has internet, paste the printed URL
into the app, and Ctrl+C when done - this is a throwaway dev/field-test tool, not a service
meant to run long-term.

Usage:
    python3 scripts/upload_test_server.py [--port 8080] [--out-dir uploads]
"""
import argparse
import datetime
import http.server
import os
import socket
import socketserver
import sys
import urllib.parse


class UploadHandler(http.server.BaseHTTPRequestHandler):
    out_dir = "uploads"

    def _save(self) -> tuple[str, int]:
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length) if length else b""

        path = urllib.parse.unquote(urllib.parse.urlsplit(self.path).path)
        # basename() twice: once to drop any directory components from the URL path, and the
        # inner call again in case that first pass still leaves '..' - belt and suspenders
        # against a crafted path escaping out_dir.
        basename = os.path.basename(os.path.basename(path.rstrip("/"))) or "upload.csv"
        timestamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
        dest = os.path.join(self.out_dir, f"{timestamp}_{basename}")

        os.makedirs(self.out_dir, exist_ok=True)
        with open(dest, "wb") as f:
            f.write(body)

        print(f"[{timestamp}] {self.client_address[0]} {self.command} {self.path} "
              f"({len(body)} bytes) -> {dest}")
        return dest, len(body)

    def do_PUT(self) -> None:
        try:
            self._save()
        except Exception as e:
            self.send_response(500)
            self.end_headers()
            self.wfile.write(str(e).encode())
            print(f"ERROR handling {self.command} {self.path}: {e}", file=sys.stderr)
            return
        self.send_response(200)
        self.send_header("Content-Length", "0")
        self.end_headers()

    # UploadService always sends PUT, but some presigned-URL-style endpoints expect POST -
    # accept either so this doubles as a stand-in for that shape of endpoint too.
    do_POST = do_PUT

    def do_GET(self) -> None:
        body = b"Upload receiver is running.\n"
        self.send_response(200)
        self.send_header("Content-Type", "text/plain")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format: str, *args) -> None:
        pass  # superseded by the explicit print() in _save()


class ThreadingHTTPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True


def local_ip() -> str:
    """Best-effort LAN IP for printing a ready-to-paste URL - doesn't actually send anything,
    just asks the OS which interface it would use to reach the internet."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="0.0.0.0", help="bind address (default: 0.0.0.0)")
    parser.add_argument("--port", type=int, default=8080, help="bind port (default: 8080)")
    parser.add_argument("--out-dir", default="uploads", help="directory to save uploads into")
    args = parser.parse_args()

    # Otherwise stdout is fully buffered (not line-buffered) whenever it's redirected to a file
    # or pipe instead of a terminal, so upload log lines wouldn't appear until the process exits.
    sys.stdout.reconfigure(line_buffering=True)

    UploadHandler.out_dir = args.out_dir
    os.makedirs(args.out_dir, exist_ok=True)

    server = ThreadingHTTPServer((args.host, args.port), UploadHandler)
    url = f"http://{local_ip()}:{args.port}/session_log.csv"
    print(f"Listening on {args.host}:{args.port}, saving uploads to '{args.out_dir}/'")
    print(f"Paste this into the app's Export screen 'Endpoint URL' field:\n  {url}")
    print("Ctrl+C to stop.\n")

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down.")
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
