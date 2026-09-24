// Byte-range support for the landing worker's /video/* assets.
//
// With run_worker_first + env.ASSETS.fetch(request), Static Assets answers a
// Range request with a full 200 (verified on prod 2026-09-24). Safari/iOS will
// not play an mp4 without 206 Partial Content, so the worker slices the file
// itself. Kept in its own module so the parser is testable without workerd
// (landing-range.test.mjs, `node --test`).

/**
 * Parse a Range header against a known file size.
 * @returns {{start:number,end:number}} single satisfiable range (end inclusive)
 *   | "unsatisfiable" → 416
 *   | null → ignore the header and serve the full 200 (absent, malformed,
 *     non-bytes unit, or multi-range)
 */
export function parseRange(header, size) {
  if (!header) return null;
  const m = /^\s*bytes\s*=\s*(\d*)\s*-\s*(\d*)\s*$/i.exec(header);
  if (!m) return null; // includes multi-range ("0-1,5-9") and other units
  const [, a, b] = m;
  if (a === "" && b === "") return null; // "bytes=-" is not a range

  if (a === "") {
    // Suffix: last N bytes. "-0" is unsatisfiable; N > size → the whole file.
    const n = Number(b);
    if (n === 0 || size === 0) return "unsatisfiable";
    return { start: Math.max(0, size - n), end: size - 1 };
  }

  const start = Number(a);
  if (start >= size) return "unsatisfiable";
  let end = b === "" ? size - 1 : Number(b);
  if (end < start) return null; // inverted bounds: RFC 9110 says ignore → 200
  if (end >= size) end = size - 1; // clamp an end beyond EOF
  return { start, end };
}

/**
 * Serve a /video/* asset with an immutable cache and Range support.
 * `fetchAsset(request)` is env.ASSETS.fetch.
 */
export async function serveVideo(request, fetchAsset, cacheControl) {
  const rangeHeader = request.headers.get("Range");

  // Ask the asset store for the whole file (it ignores Range anyway) and
  // without If-Range, which would otherwise be forwarded meaninglessly.
  // A ranged HEAD is fetched upstream as GET: the asset store answers HEAD with
  // no Content-Length, so the size is only knowable from the body.
  const headers = new Headers(request.headers);
  headers.delete("Range");
  headers.delete("If-Range");
  const isHead = request.method === "HEAD";
  const method = isHead && rangeHeader ? "GET" : request.method;
  const upstream = await fetchAsset(new Request(request.url, { method, headers }));

  // 404, 304 (If-None-Match hit) and anything else pass through untouched —
  // never cache a miss.
  if (upstream.status !== 200) return upstream;

  const out = new Headers(upstream.headers);
  out.set("Cache-Control", cacheControl);
  out.set("Accept-Ranges", "bytes");

  if (!rangeHeader) {
    return new Response(upstream.body, { status: 200, headers: out });
  }

  const buf = await upstream.arrayBuffer(); // files are ≤ 3 MB
  const size = buf.byteLength;
  out.set("Content-Length", String(size));

  const range = parseRange(rangeHeader, size);
  if (range === null) {
    return new Response(isHead ? null : buf, { status: 200, headers: out });
  }
  if (range === "unsatisfiable") {
    out.delete("Content-Length");
    out.delete("Content-Type");
    out.delete("Cache-Control"); // an error answer must not be pinned for a year
    out.set("Content-Range", `bytes */${size}`);
    return new Response(null, { status: 416, headers: out });
  }

  const { start, end } = range;
  out.set("Content-Range", `bytes ${start}-${end}/${size}`);
  out.set("Content-Length", String(end - start + 1));
  return new Response(isHead ? null : buf.slice(start, end + 1), { status: 206, headers: out });
}
