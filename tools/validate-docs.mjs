import {
  existsSync,
  readFileSync,
  readdirSync,
  statSync,
} from "node:fs";
import path from "node:path";

const repositoryRoot = process.cwd();
const ignoredDirectories = new Set([
  ".git",
  ".gradle",
  ".idea",
  ".kotlin",
  "build",
]);
const failures = [];

function collectFiles(directory, predicate) {
  const files = [];
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (entry.isDirectory() && ignoredDirectories.has(entry.name)) continue;
    const absolutePath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      files.push(...collectFiles(absolutePath, predicate));
    } else if (predicate(absolutePath)) {
      files.push(absolutePath);
    }
  }
  return files;
}

function relative(file) {
  return path.relative(repositoryRoot, file).replaceAll(path.sep, "/");
}

function githubHeadingSlugs(file) {
  const occurrences = new Map();
  const slugs = new Set();
  for (const line of readFileSync(file, "utf8").split(/\r?\n/)) {
    const heading = /^(#{1,6})\s+(.+?)\s*#*\s*$/.exec(line);
    if (heading == null) continue;
    const base = heading[2]
      .replace(/<[^>]*>/g, "")
      .replace(/!??\[([^\]]+)]\([^)]+\)/g, "$1")
      .replace(/`([^`]*)`/g, "$1")
      .toLowerCase()
      .trim()
      .replace(/[^\p{L}\p{N}\s_-]/gu, "")
      .replace(/\s+/g, "-");
    const occurrence = occurrences.get(base) ?? 0;
    occurrences.set(base, occurrence + 1);
    slugs.add(occurrence === 0 ? base : `${base}-${occurrence}`);
  }
  return slugs;
}

const markdownFiles = collectFiles(
  repositoryRoot,
  (file) => path.extname(file).toLowerCase() === ".md",
).sort();
const slugCache = new Map();

function checkLocalLink(source, rawTarget) {
  let target = rawTarget.trim();
  if (target.startsWith("<") && target.endsWith(">")) {
    target = target.slice(1, -1);
  }
  target = target.replace(/\s+["'][^"']*["']$/, "");
  if (target === "" || /^(https?:|mailto:|tel:|data:)/i.test(target)) return;

  const hashIndex = target.indexOf("#");
  const filePart = hashIndex >= 0 ? target.slice(0, hashIndex) : target;
  const anchor = hashIndex >= 0 ? target.slice(hashIndex + 1) : "";
  const destination = filePart === ""
    ? source
    : path.resolve(path.dirname(source), decodeURIComponent(filePart));

  if (!destination.startsWith(repositoryRoot + path.sep) && destination !== repositoryRoot) {
    failures.push(`${relative(source)}: link escapes repository: ${target}`);
    return;
  }
  if (!existsSync(destination)) {
    failures.push(`${relative(source)}: missing local target: ${target}`);
    return;
  }
  if (anchor === "" || statSync(destination).isDirectory()) return;
  if (path.extname(destination).toLowerCase() !== ".md") return;

  const destinationSlugs = slugCache.get(destination) ?? githubHeadingSlugs(destination);
  slugCache.set(destination, destinationSlugs);
  const decodedAnchor = decodeURIComponent(anchor).toLowerCase();
  if (!destinationSlugs.has(decodedAnchor)) {
    failures.push(`${relative(source)}: missing heading anchor: ${target}`);
  }
}

for (const file of markdownFiles) {
  const markdown = readFileSync(file, "utf8");
  for (const match of markdown.matchAll(/!?\[[^\]]*]\(([^)]+)\)/g)) {
    checkLocalLink(file, match[1]);
  }
  for (const match of markdown.matchAll(/^\s*\[[^\]]+]:\s*(\S+)/gm)) {
    checkLocalLink(file, match[1]);
  }
}

const catalogPath = path.join(
  repositoryRoot,
  "integration/src/main/java/dev/hyperears/integration/ControlAppCatalog.kt",
);
const scopePath = path.join(
  repositoryRoot,
  "system-module/src/main/resources/META-INF/xposed/scope.list",
);
const controlAppDocumentPath = path.join(repositoryRoot, "docs/control-apps.md");
const catalogSource = readFileSync(catalogPath, "utf8");
const controlAppDocument = readFileSync(controlAppDocumentPath, "utf8").replaceAll("\\|", "|");
const controlAppPattern = /val\s+(\w+)\s*=\s*ControlAppSpec\(\s*packageName\s*=\s*"([^"]+)"\s*,\s*displayName\s*=\s*"([^"]+)"/gs;
const controlApps = [...catalogSource.matchAll(controlAppPattern)].map((match) => ({
  symbol: match[1],
  packageName: match[2],
  displayName: match[3],
}));
const coreScopes = new Set(["com.android.bluetooth", "com.milink.service"]);
const declaredScopes = new Set(
  readFileSync(scopePath, "utf8").split(/\r?\n/).map((line) => line.trim()).filter(Boolean),
);
const adapterSources = collectFiles(
  path.join(repositoryRoot, "integration/src/main/java"),
  (file) => path.extname(file) === ".kt" && file !== catalogPath,
).map((file) => readFileSync(file, "utf8")).join("\n");

for (const app of controlApps) {
  if (!controlAppDocument.includes(`\`${app.packageName}\``)) {
    failures.push(`docs/control-apps.md: missing package ${app.packageName}`);
  }
  if (!controlAppDocument.includes(app.displayName)) {
    failures.push(`docs/control-apps.md: missing display name ${app.displayName}`);
  }
  if (!declaredScopes.has(app.packageName)) {
    failures.push(`scope.list: missing optional package ${app.packageName}`);
  }
  if (!adapterSources.includes(`ControlAppCatalog.${app.symbol}`)) {
    failures.push(`Adapter declarations: unused control app ${app.symbol}`);
  }
}

const catalogPackages = new Set(controlApps.map((app) => app.packageName));
for (const scope of declaredScopes) {
  if (!coreScopes.has(scope) && !catalogPackages.has(scope)) {
    failures.push(`ControlAppCatalog: missing scope entry ${scope}`);
  }
}
for (const coreScope of coreScopes) {
  if (!declaredScopes.has(coreScope)) {
    failures.push(`scope.list: missing core package ${coreScope}`);
  }
}

if (failures.length > 0) {
  console.error(failures.join("\n"));
  process.exitCode = 1;
} else {
  console.log(
    `Documentation validation passed: ${markdownFiles.length} Markdown files, ` +
      `${controlApps.length} control applications.`,
  );
}
