"use strict";

function compileAssetPattern(value) {
  let source = String(value || "\\.apk$").trim();
  let flags = "";

  const literalMatch = source.match(/^\/(.+)\/([a-z]*)$/i);
  if (literalMatch) {
    source = literalMatch[1];
    flags = literalMatch[2];
  }

  // Older env.example versions escaped the backslash twice. dotenv preserves
  // that value, so normalize it before compiling the regular expression.
  source = source.replace(/\\\\([.^$*+?()[\]{}|])/g, "\\$1");
  return new RegExp(source, flags);
}

function parseHistoryReleaseLimit(value, fallback = 20) {
  if (value === undefined || value === null || value === "") {
    return fallback;
  }
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) {
    return fallback;
  }
  return Math.max(0, Math.floor(parsed));
}

function safePathSegment(value, fallback = "release") {
  const cleaned = String(value || "")
    .trim()
    .replace(/[^A-Za-z0-9._-]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 96);
  if (/^[.]+$/.test(cleaned)) {
    return fallback;
  }
  return cleaned || fallback;
}

module.exports = {compileAssetPattern, parseHistoryReleaseLimit, safePathSegment};
