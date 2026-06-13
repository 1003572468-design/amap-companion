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

module.exports = {compileAssetPattern};
