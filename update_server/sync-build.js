require("dotenv").config({path: require("path").join(__dirname, ".env")});

const crypto = require("crypto");
const fs = require("fs");
const http = require("http");
const https = require("https");
const path = require("path");
const {execFileSync} = require("child_process");
const {compileAssetPattern, parseHistoryReleaseLimit, safePathSegment} = require("./release-sync-config");

const defaultGithubRepo = "zuo-qirun/amap-companion";
const githubRepo = process.env.GITHUB_REPO || detectGithubRepo() || defaultGithubRepo;
const githubToken = process.env.GITHUB_TOKEN || "";
const releaseTag = process.env.RELEASE_TAG || "latest";
const assetPattern = compileAssetPattern(process.env.ASSET_PATTERN);
const manifestAssetName = process.env.MANIFEST_ASSET || "release-update.json";
const changelogAssetName = process.env.CHANGELOG_ASSET || "CHANGELOG.md";
const historyReleaseLimit = parseHistoryReleaseLimit(process.env.HISTORY_RELEASE_LIMIT, 20);
const historyEnabled = historyReleaseLimit > 0;
const publicDir = path.join(__dirname, "public");
const apkDir = path.join(publicDir, "apk");
const historyApkDir = path.join(apkDir, "history");
const apkDest = path.join(apkDir, "amap_companion_signed.apk");
const manifestOut = path.join(publicDir, "update.json");
const versionsOut = path.join(publicDir, "versions.json");
const changelogOut = path.join(publicDir, "CHANGELOG.md");
const changelogFullOut = path.join(publicDir, "CHANGELOG_FULL.md");
const stateDir = path.join(__dirname, "state");
const statePath = path.join(stateDir, "release-state.json");
const forceSync = process.env.FORCE_SYNC === "1" || process.env.FORCE_BUILD === "1" || process.argv.includes("--force");

function log(message) {
  console.log(`[release-sync] ${message}`);
}

function detectGithubRepo() {
  try {
    const remote = execFileSync("git", ["config", "--get", "remote.origin.url"], {
      cwd: path.join(__dirname, ".."),
      encoding: "utf8",
    }).trim();
    const match = remote.match(/github\.com[:/](.+?\/.+?)(?:\.git)?$/);
    return match ? match[1] : "";
  } catch (error) {
    return "";
  }
}

function requestJson(url) {
  return new Promise((resolve, reject) => {
    requestBuffer(url).then((buffer) => {
      try {
        resolve(JSON.parse(buffer.toString("utf8")));
      } catch (error) {
        reject(error);
      }
    }, reject);
  });
}

function requestBuffer(url) {
  return new Promise((resolve, reject) => {
    const client = url.startsWith("https:") ? https : http;
    const headers = {
      "user-agent": "amap-companion-release-sync",
      "accept": "application/vnd.github+json,application/octet-stream,*/*",
    };
    if (githubToken) {
      headers.authorization = `Bearer ${githubToken}`;
    }
    const req = client.get(url, {headers}, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        requestBuffer(new URL(res.headers.location, url).toString()).then(resolve, reject);
        res.resume();
        return;
      }
      if (res.statusCode < 200 || res.statusCode >= 300) {
        reject(new Error(`HTTP ${res.statusCode}: ${url}`));
        res.resume();
        return;
      }
      const chunks = [];
      res.on("data", (chunk) => chunks.push(chunk));
      res.on("end", () => resolve(Buffer.concat(chunks)));
    });
    req.on("error", reject);
  });
}

function readState() {
  if (!fs.existsSync(statePath)) {
    return {};
  }
  return JSON.parse(fs.readFileSync(statePath, "utf8"));
}

function writeState(state) {
  fs.mkdirSync(stateDir, {recursive: true});
  fs.writeFileSync(statePath, JSON.stringify(state, null, 2) + "\n");
}

function findAsset(release, predicate, description) {
  const asset = (release.assets || []).find(predicate);
  if (!asset) {
    throw new Error(`release asset not found: ${description}`);
  }
  return asset;
}

function findOptionalAsset(release, name) {
  return (release.assets || []).find((asset) => asset.name === name);
}

function findReleaseApk(release) {
  return (release.assets || []).find((asset) => assetPattern.test(asset.name));
}

async function downloadAsset(asset, outPath) {
  log(`download ${asset.name}`);
  const buffer = await requestBuffer(asset.browser_download_url);
  fs.mkdirSync(path.dirname(outPath), {recursive: true});
  fs.writeFileSync(outPath, buffer);
}

function sha256(filePath) {
  const hash = crypto.createHash("sha256");
  hash.update(fs.readFileSync(filePath));
  return hash.digest("hex");
}

function fallbackManifest(release, apkAsset) {
  const tagMatch = String(release.tag_name || "").match(/apk-(\d+)-/);
  const versionCode = tagMatch ? Number(tagMatch[1]) : Math.floor(Date.parse(release.published_at || release.created_at || new Date()) / 1000);
  return {
    packageName: "com.autonavi.companion",
    versionCode,
    versionName: release.name || release.tag_name || String(versionCode),
    force: false,
    changelog: [release.body || `GitHub Release ${release.tag_name}`].filter(Boolean),
    commit: release.target_commitish || "",
    builtAt: release.published_at || release.created_at || new Date().toISOString(),
    assetName: apkAsset.name,
  };
}

async function readReleaseManifest(release, apkAsset) {
  const manifestAsset = findOptionalAsset(release, manifestAssetName);
  let releaseManifest = fallbackManifest(release, apkAsset);
  if (manifestAsset) {
    const manifestBuffer = await requestBuffer(manifestAsset.browser_download_url);
    releaseManifest = {...releaseManifest, ...JSON.parse(manifestBuffer.toString("utf8"))};
  }
  return releaseManifest;
}

function writeUpdateFiles(release, releaseManifest, apkAsset, changelogAsset) {
  const manifest = {
    ...releaseManifest,
    packageName: releaseManifest.packageName || "com.autonavi.companion",
    apkPath: "apk/amap_companion_signed.apk",
    githubApkUrl: apkAsset.browser_download_url,
    githubChangelogUrl: changelogAsset ? changelogAsset.browser_download_url : "",
    sha256: sha256(apkDest),
    size: fs.statSync(apkDest).size,
    releaseTag: release.tag_name,
    releaseUrl: release.html_url,
    syncedAt: new Date().toISOString(),
  };
  fs.mkdirSync(publicDir, {recursive: true});
  fs.writeFileSync(manifestOut, JSON.stringify(manifest, null, 2) + "\n");
  if (!fs.existsSync(changelogOut)) {
    const lines = Array.isArray(manifest.changelog) ? manifest.changelog : [String(manifest.changelog || "")];
    fs.writeFileSync(changelogOut, [
      "# AMap Companion 更新日志",
      "",
      `## ${manifest.versionName || release.tag_name}`,
      "",
      ...lines.filter(Boolean).map((item) => `- ${item}`),
      "",
    ].join("\n"));
  }
}

function historyFileName(release, apkAsset) {
  const tag = safePathSegment(release.tag_name || release.name || String(release.id), "release");
  const assetBase = safePathSegment(path.basename(apkAsset.name, path.extname(apkAsset.name)), "apk");
  return `${tag}-${assetBase}.apk`;
}

function localVersionEntry(release, releaseManifest, apkAsset, fileName, filePath) {
  const publishedAt = release.published_at || release.created_at || "";
  return {
    packageName: releaseManifest.packageName || "com.autonavi.companion",
    versionCode: releaseManifest.versionCode,
    versionName: releaseManifest.versionName || release.name || release.tag_name || String(releaseManifest.versionCode),
    force: Boolean(releaseManifest.force),
    changelog: releaseManifest.changelog || [release.body || ""].filter(Boolean),
    commit: releaseManifest.commit || release.target_commitish || "",
    builtAt: releaseManifest.builtAt || publishedAt,
    publishedAt,
    releaseTag: release.tag_name,
    releaseUrl: release.html_url,
    assetName: apkAsset.name,
    apkPath: `apk/history/${fileName}`,
    githubApkUrl: apkAsset.browser_download_url,
    sha256: sha256(filePath),
    size: fs.statSync(filePath).size,
  };
}

function sortVersionEntries(entries) {
  return entries.sort((a, b) => {
    const aCode = Number(a.versionCode) || 0;
    const bCode = Number(b.versionCode) || 0;
    if (bCode !== aCode) return bCode - aCode;
    return Date.parse(b.publishedAt || b.builtAt || 0) - Date.parse(a.publishedAt || a.builtAt || 0);
  });
}

function writeVersionsFile(versions) {
  fs.mkdirSync(publicDir, {recursive: true});
  const manifest = {
    schemaVersion: 1,
    generatedAt: new Date().toISOString(),
    source: "github-releases",
    releaseLimit: historyReleaseLimit,
    versions: sortVersionEntries(versions),
  };
  fs.writeFileSync(versionsOut, JSON.stringify(manifest, null, 2) + "\n");
}

async function listReleases(limit) {
  if (limit <= 0) {
    return [];
  }
  const releases = [];
  let page = 1;
  while (releases.length < limit) {
    const perPage = Math.min(100, limit - releases.length);
    const url = `https://api.github.com/repos/${githubRepo}/releases?per_page=${perPage}&page=${page}`;
    log(`check ${url}`);
    const batch = await requestJson(url);
    if (!Array.isArray(batch) || batch.length === 0) {
      break;
    }
    releases.push(...batch);
    if (batch.length < perPage) {
      break;
    }
    page += 1;
  }
  return releases.slice(0, limit);
}

async function syncHistoryVersions(latestRelease, latestManifest, latestApkAsset) {
  if (!historyEnabled) {
    writeVersionsFile([]);
    log("release history disabled");
    return;
  }
  log(`sync release history, limit=${historyReleaseLimit}`);
  let releases = [];
  try {
    releases = await listReleases(historyReleaseLimit);
  } catch (error) {
    log(`release history list failed: ${error.message}`);
  }
  const selected = releases.length ? releases : [latestRelease];
  const versions = [];

  for (const release of selected) {
    const apkAsset = findReleaseApk(release);
    if (!apkAsset) {
      log(`skip ${release.tag_name || release.id}: APK asset not found`);
      continue;
    }
    const fileName = historyFileName(release, apkAsset);
    const filePath = path.join(historyApkDir, fileName);
    if (!fs.existsSync(filePath) || forceSync) {
      await downloadAsset(apkAsset, filePath);
    } else {
      log(`cached ${fileName}`);
    }
    let releaseManifest;
    if (release.id === latestRelease.id && apkAsset.id === latestApkAsset.id) {
      releaseManifest = latestManifest;
    } else {
      releaseManifest = await readReleaseManifest(release, apkAsset);
    }
    versions.push(localVersionEntry(release, releaseManifest, apkAsset, fileName, filePath));
  }

  writeVersionsFile(versions);
  log(`synced ${versions.length} history version(s)`);
}

function manifestHasGithubChannel() {
  if (!fs.existsSync(manifestOut)) {
    return false;
  }
  try {
    const manifest = JSON.parse(fs.readFileSync(manifestOut, "utf8"));
    return Boolean(manifest.githubApkUrl);
  } catch (error) {
    return false;
  }
}

function historyCacheReady(state) {
  if (!historyEnabled) {
    if (!fs.existsSync(versionsOut)) {
      return false;
    }
    try {
      const manifest = JSON.parse(fs.readFileSync(versionsOut, "utf8"));
      const versions = Array.isArray(manifest.versions) ? manifest.versions : [];
      return versions.length === 0;
    } catch (error) {
      return false;
    }
  }
  if (!fs.existsSync(versionsOut)) {
    return false;
  }
  if (state.historyReleaseLimit !== historyReleaseLimit) {
    return false;
  }
  try {
    const manifest = JSON.parse(fs.readFileSync(versionsOut, "utf8"));
    const versions = Array.isArray(manifest.versions) ? manifest.versions : [];
    return versions.length > 0 && versions.every((item) => {
      const rawPath = String(item.apkPath || "");
      const localPath = path.resolve(publicDir, rawPath.replace(/^\/+/, ""));
      return localPath.startsWith(path.resolve(publicDir) + path.sep)
        && fs.existsSync(localPath)
        && fs.statSync(localPath).isFile();
    });
  } catch (error) {
    return false;
  }
}

async function main() {
  if (!githubRepo) {
    throw new Error("GITHUB_REPO is required, for example: owner/repo");
  }
  const releaseUrl = releaseTag === "latest"
    ? `https://api.github.com/repos/${githubRepo}/releases/latest`
    : `https://api.github.com/repos/${githubRepo}/releases/tags/${encodeURIComponent(releaseTag)}`;
  log(`check ${releaseUrl}`);
  const release = await requestJson(releaseUrl);
  const state = readState();
  if (!forceSync && state.releaseId === release.id && fs.existsSync(apkDest) && manifestHasGithubChannel() && historyCacheReady(state)) {
    log(`already synced ${release.tag_name}`);
    return;
  }

  const apkAsset = findAsset(release, (asset) => assetPattern.test(asset.name), assetPattern.toString());
  const changelogAsset = findOptionalAsset(release, changelogAssetName);

  await downloadAsset(apkAsset, apkDest);
  const releaseManifest = await readReleaseManifest(release, apkAsset);
  if (changelogAsset) {
    await downloadAsset(changelogAsset, changelogOut);
  } else if (fs.existsSync(changelogOut)) {
    fs.unlinkSync(changelogOut);
  }
  // fetch full CHANGELOG.md from repo for website (contains all versions)
  const repoChangelogUrl = `https://raw.githubusercontent.com/${githubRepo}/master/CHANGELOG.md`;
  try {
    log(`fetch full changelog from repo`);
    const changelogBuffer = await requestBuffer(repoChangelogUrl);
    fs.writeFileSync(changelogFullOut, changelogBuffer);
  } catch (err) {
    log(`repo changelog fetch failed: ${err.message}`);
  }
  writeUpdateFiles(release, releaseManifest, apkAsset, changelogAsset);
  await syncHistoryVersions(release, releaseManifest, apkAsset);
  writeState({
    releaseId: release.id,
    releaseTag: release.tag_name,
    apkAsset: apkAsset.name,
    versionCode: releaseManifest.versionCode,
    versionName: releaseManifest.versionName,
    historyReleaseLimit,
    syncedAt: new Date().toISOString(),
  });
  log(`synced ${release.tag_name}: ${releaseManifest.versionName} (${releaseManifest.versionCode})`);
}

main().catch((error) => {
  console.error(error.stack || error.message);
  process.exit(1);
});
