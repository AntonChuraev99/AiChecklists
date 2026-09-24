// Run: node --test landing-range.test.mjs   (Node 20+, no deps)
import { test } from "node:test";
import assert from "node:assert/strict";
import { parseRange, serveVideo } from "./landing-range.js";

const SIZE = 10_000;

test("parseRange: closed, open-ended and suffix ranges", () => {
  assert.deepEqual(parseRange("bytes=0-1023", SIZE), { start: 0, end: 1023 });
  assert.deepEqual(parseRange("bytes=9000-", SIZE), { start: 9000, end: 9999 });
  assert.deepEqual(parseRange("bytes=-500", SIZE), { start: 9500, end: 9999 });
  assert.deepEqual(parseRange("bytes=0-1", SIZE), { start: 0, end: 1 }); // Safari's probe
});

test("parseRange: clamps and whole-file suffix", () => {
  assert.deepEqual(parseRange("bytes=9990-20000", SIZE), { start: 9990, end: 9999 });
  assert.deepEqual(parseRange("bytes=-20000", SIZE), { start: 0, end: 9999 });
});

test("parseRange: unsatisfiable → 416", () => {
  assert.equal(parseRange("bytes=99999999-", SIZE), "unsatisfiable");
  assert.equal(parseRange("bytes=10000-", SIZE), "unsatisfiable");
  assert.equal(parseRange("bytes=-0", SIZE), "unsatisfiable");
});

test("parseRange: ignored → full 200", () => {
  assert.equal(parseRange(null, SIZE), null);
  assert.equal(parseRange("bytes=0-1,5-9", SIZE), null); // multi-range
  assert.equal(parseRange("items=0-1", SIZE), null);
  assert.equal(parseRange("bytes=-", SIZE), null);
  assert.equal(parseRange("bytes=500-100", SIZE), null); // inverted
  assert.equal(parseRange("garbage", SIZE), null);
});

const BODY = new Uint8Array(SIZE).map((_, i) => i % 251);
const asset = async (req) => {
  // Mirrors the asset store as observed under wrangler dev: ignores Range, and
  // a HEAD comes back with neither body NOR Content-Length.
  assert.equal(req.headers.get("Range"), null, "Range must be stripped upstream");
  if (req.method === "HEAD") return new Response(null, { status: 200, headers: { "Content-Type": "video/mp4" } });
  const headers = { "Content-Type": "video/mp4", "Content-Length": String(SIZE), ETag: '"x"' };
  return new Response(BODY, { status: 200, headers });
};
const CC = "public, max-age=31536000, immutable";
const req = (range, method = "GET") =>
  new Request("https://gisti-ai.com/video/a.mp4", { method, headers: range ? { Range: range } : {} });

test("serveVideo: 206 slice with headers", async () => {
  const res = await serveVideo(req("bytes=100-199"), asset, CC);
  assert.equal(res.status, 206);
  assert.equal(res.headers.get("Content-Range"), `bytes 100-199/${SIZE}`);
  assert.equal(res.headers.get("Content-Length"), "100");
  assert.equal(res.headers.get("Content-Type"), "video/mp4");
  assert.equal(res.headers.get("Cache-Control"), CC);
  assert.equal(res.headers.get("Accept-Ranges"), "bytes");
  assert.deepEqual(new Uint8Array(await res.arrayBuffer()), BODY.slice(100, 200));
});

test("serveVideo: HEAD with Range → 206, no body", async () => {
  const res = await serveVideo(req("bytes=0-1023", "HEAD"), asset, CC);
  assert.equal(res.status, 206);
  assert.equal(res.headers.get("Content-Range"), `bytes 0-1023/${SIZE}`);
  assert.equal(res.body, null);
});

test("serveVideo: no Range → 200 full + Accept-Ranges", async () => {
  const res = await serveVideo(req(null), asset, CC);
  assert.equal(res.status, 200);
  assert.equal(res.headers.get("Accept-Ranges"), "bytes");
  assert.equal((await res.arrayBuffer()).byteLength, SIZE);
});

test("serveVideo: unsatisfiable → 416 with */size", async () => {
  const res = await serveVideo(req("bytes=99999999-"), asset, CC);
  assert.equal(res.status, 416);
  assert.equal(res.headers.get("Content-Range"), `bytes */${SIZE}`);
});

test("serveVideo: upstream 404 passes through uncached", async () => {
  const res = await serveVideo(req("bytes=0-1"), async () => new Response("nf", { status: 404 }), CC);
  assert.equal(res.status, 404);
  assert.equal(res.headers.get("Cache-Control"), null);
});
