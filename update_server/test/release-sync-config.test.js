"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const {compileAssetPattern} = require("../release-sync-config");

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
