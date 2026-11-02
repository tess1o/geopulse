"""Deterministic Nominatim substitute, reachable only on the benchmark's internal network."""

import json
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, urlsplit
from urllib.request import Request, urlopen


class Geocoder(BaseHTTPRequestHandler):
    calls = 0
    lock = threading.Lock()

    def proxy(self):
        payload = self.rfile.read(int(self.headers.get("Content-Length", 0))) if self.command == "POST" else None
        headers = {key: self.headers[key] for key in ("Authorization", "Content-Type") if key in self.headers}
        req = Request("http://backend:8080" + self.path, data=payload, headers=headers, method=self.command)
        try:
            response = urlopen(req, timeout=60)
        except HTTPError as error:
            response = error
        except URLError:
            self.send_error(502, "Backend is not ready")
            return
        with response:
            body = response.read()
            self.send_response(response.status)
            self.send_header("Content-Type", response.headers.get("Content-Type", "application/json"))
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

    def do_GET(self):
        if self.path.startswith("/api/"):
            self.proxy()
            return
        if self.path == "/benchmark/stats":
            body = json.dumps({"calls": self.calls}).encode()
            self.send_response(200)
            self.end_headers()
            self.wfile.write(body)
            return
        with self.lock:
            type(self).calls += 1
        query = parse_qs(urlsplit(self.path).query)
        lat = float(query.get("lat", [50])[0])
        lon = float(query.get("lon", [30])[0])
        label = f"Benchmark {lat:.2f},{lon:.2f}"
        body = json.dumps({
            "place_id": 1, "osm_id": 1, "osm_type": "node", "type": "house",
            "lat": str(lat), "lon": str(lon), "display_name": label, "name": label,
            "address": {"city": "Benchmark", "country": "Test", "country_code": "zz"},
        }).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        self.proxy()

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8080), Geocoder).serve_forever()
