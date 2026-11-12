import { execFileSync } from 'node:child_process';
import { appendFileSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const [outputDir] = process.argv.slice(2);
if (!outputDir) throw new Error('Usage: node tools/prepare-windows-release.mjs OUTPUT_DIR');

const read = path => readFileSync(path, 'utf8');
const git = (...args) => execFileSync('git', args, { encoding: 'utf8' }).trim();
const gradleVersion = read('composeApp/build.gradle.kts').match(/^val appVersion = "(\d+\.\d+\.\d+)"\s*$/m)?.[1];
const uiVersion = read('composeApp/src/jvmMain/kotlin/com/jay/parser/main.kt')
  .match(/^private const val APP_VERSION = "(\d+\.\d+\.\d+)"\s*$/m)?.[1];
if (!gradleVersion || gradleVersion !== uiVersion) {
  throw new Error('The installer and About box must declare the same X.Y.Z version.');
}

const tag = `POParserV${gradleVersion}`;
const previousTag = git('tag', '--merged', 'HEAD', '--list', 'POParserV*', '--sort=-version:refname')
  .split('\n').find(value => /^POParserV\d+\.\d+\.\d+$/.test(value) && value !== tag);
const commits = git('log', '--no-merges', '--format=%s', previousTag ? `${previousTag}..HEAD` : '-1');
const date = process.env.SUPPORT_DATE?.trim() || git('show', '-s', '--format=%cs', 'HEAD');
const parsedDate = new Date(`${date}T00:00:00Z`);
if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || !Number.isFinite(parsedDate.valueOf()) ||
    parsedDate.toISOString().slice(0, 10) !== date) {
  throw new Error('The support date must be a real calendar date in YYYY-MM-DD format.');
}

const activeIds = JSON.parse(read('composeApp/src/jvmMain/resources/data/customers.json'))
  .map(({ id }) => id.trim().toUpperCase());
const supported = read('tools/supported-customers.txt').split(/\r?\n/)
  .map(line => line.trim()).filter(line => line && !line.startsWith('#'))
  .map(line => line.split('|').map(part => part.trim()))
  .filter(([label, id = label]) => {
    const key = id.toUpperCase();
    return key === 'TSA-*' ? activeIds.some(active => active.startsWith('TSA-')) : activeIds.includes(key);
  })
  .map(([label]) => label);
if (!supported.length) throw new Error('No supported customers matched the bundled master.');

const bullet = value => `- ${value.replace(/[\\`*_~\[\]<>]/g, '\\$&')}`;
const displayDate = new Intl.DateTimeFormat('en-US', { dateStyle: 'short', timeZone: 'UTC' }).format(parsedDate);
const notes = [
  `# PO Parser v${gradleVersion}`,
  '',
  '## Commit notes',
  '',
  ...([...new Set(commits.split('\n').filter(Boolean))].map(bullet)),
  '',
  `## Supported Customers (${displayDate})`,
  '',
  ...supported.map(bullet),
  '',
].join('\n');

mkdirSync(outputDir, { recursive: true });
writeFileSync(join(outputDir, 'release-notes.md'), notes);
if (process.env.GITHUB_OUTPUT) {
  appendFileSync(process.env.GITHUB_OUTPUT, `version=${gradleVersion}\ntag=${tag}\n`);
}
console.log(`Prepared ${tag}: ${supported.length} supported customers (previous tag: ${previousTag || 'none'}).`);
