"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const {compileAssetPattern, parseHistoryReleaseLimit, safePathSegment} = require("../release-sync-config");

test("release asset pattern matches APK files by default", () => {
  const pattern = compileAssetPattern();
  assert.equal(pattern.test("amap-companion-a0e390c.apk"), true);
  assert.equal(pattern.test("CHANGELOG.md"), false);
});

test("release asset pattern accepts current and legacy env escaping", () => {
  assert.equal(compileAssetPattern("\\.apk$").test("app.apk"), true);
  assert.equal(compileAssetPattern("\\\\.apk$").test("app.apk"), true);
});

test("release asset pattern accepts slash-delimited expressions", () => {
  assert.equal(compileAssetPattern("/\\.APK$/i").test("app.apk"), true);
});

test("history release limit accepts empty and bounded numeric values", () => {
  assert.equal(parseHistoryReleaseLimit(undefined, 12), 12);
  assert.equal(parseHistoryReleaseLimit("", 12), 12);
  assert.equal(parseHistoryReleaseLimit("5", 12), 5);
  assert.equal(parseHistoryReleaseLimit("5.9", 12), 5);
  assert.equal(parseHistoryReleaseLimit("-2", 12), 0);
  assert.equal(parseHistoryReleaseLimit("bad", 12), 12);
});

test("safe path segment removes unsafe release tag characters", () => {
  assert.equal(safePathSegment("release/v1.2.3+apk", "x"), "release-v1.2.3-apk");
  assert.equal(safePathSegment("  ../  ", "fallback"), "fallback");
});
