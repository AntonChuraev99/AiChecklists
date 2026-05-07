"""Tiny static HTTP server with COOP/COEP/CORP headers for wasmJs.

Serves composeApp/build/dist/wasmJs/developmentExecutable on port 9091.
Used to bypass webpack-dev-server when diagnosing wasm streaming issues.

Usage:
  python e2e/serve_static.py
"""
import http.server
import os
import socketserver
import sys

PORT = 9091
ROOT = os.path.abspath(
    os.path.join(
        os.path.dirname(__file__),
        "..",
        "composeApp",
        "build",
        "dist",
        "wasmJs",
        "developmentExecutable",
    )
)


class HeadersHandler(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Cross-Origin-Opener-Policy", "same-origin")
        self.send_header("Cross-Origin-Embedder-Policy", "require-corp")
        self.send_header("Cross-Origin-Resource-Policy", "cross-origin")
        super().end_headers()

    def guess_type(self, path):
        if path.endswith(".wasm"):
            return "application/wasm"
        if path.endswith(".js") or path.endswith(".mjs"):
            return "text/javascript"
        return super().guess_type(path)


if __name__ == "__main__":
    os.chdir(ROOT)
    print(f"Serving {ROOT} at http://localhost:{PORT}", flush=True)
    with socketserver.ThreadingTCPServer(("", PORT), HeadersHandler) as httpd:
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            sys.exit(0)
