// Local authoring import: uses pte-doc source content and the normal create/publish APIs.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { describeImageSupplements } from './fixtures/doc-describe-image-supplements.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const docs = path.resolve(process.env.PTE_DOC_ROOT || path.join(root, '..', 'pte-doc'));
const base = process.env.PTE_BASE_URL || 'http://localhost:8080';
if (!['localhost', '127.0.0.1'].includes(new URL(base).hostname)) throw Error('Local API required');
if (!process.env.PTE_ADMIN_PASSWORD) throw Error('PTE_ADMIN_PASSWORD is required');
let token;
const mediaCache = new Map();
const read = (file) => fs.existsSync(file) ? JSON.parse(fs.readFileSync(file, 'utf8')) : [];
const digest = (value) => crypto.createHash('sha256').update(value).digest('hex').slice(0, 16);

async function api(route, method = 'GET', body) {
  for (let attempt = 0; attempt < 7; attempt++) {
    const response = await fetch(base + route, {
      method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    });
    if (response.status === 429 && attempt < 6) {
      await new Promise((resolve) => setTimeout(resolve, 1000 * (attempt + 1)));
      continue;
    }
    const json = await response.json();
    if (!response.ok || json.success === false) {
      throw Error(`${method} ${route}: ${response.status} ${json.code || 'API_ERROR'}`);
    }
    return json;
  }
}

async function media(url, kind) {
  if (mediaCache.has(url)) return mediaCache.get(url);
  const response = await fetch(url, { signal: AbortSignal.timeout(30000) });
  if (!response.ok) throw Error(`Source media HTTP ${response.status}`);
  const bytes = await response.arrayBuffer();
  const extension = url.startsWith('data:image/png;') ? '.png' : path.extname(new URL(url).pathname).toLowerCase();
  const contentType = kind === 'IMAGE_PROMPT'
    ? (extension === '.png' ? 'image/png' : 'image/jpeg')
    : (extension === '.wav' ? 'audio/wav' : extension === '.m4a' ? 'audio/mp4' : 'audio/mpeg');
  const { data: grant } = await api('/api/v1/objects', 'POST', { contentType, assetKind: kind, sizeBytes: bytes.byteLength });
  const form = new FormData();
  form.append('file', new Blob([bytes], { type: contentType }), `source${extension || '.mp3'}`);
  for (const [key, value] of Object.entries({ api_key: grant.apiKey, timestamp: grant.timestamp,
    signature: grant.signature, folder: grant.folder, public_id: grant.mediaPublicId })) form.append(key, String(value));
  const uploaded = await fetch(grant.uploadUrl, { method: 'POST', body: form, signal: AbortSignal.timeout(60000) });
  if (!uploaded.ok) throw Error(`Media upload HTTP ${uploaded.status}`);
  const asset = await uploaded.json();
  await api(`/api/v1/objects/${grant.mediaPublicId}/complete`, 'POST', {
    publicId: asset.public_id, assetId: asset.asset_id, secureUrl: asset.secure_url,
    resourceType: asset.resource_type, format: asset.format, bytes: asset.bytes,
    version: asset.version, signature: asset.signature,
    ...(asset.duration == null ? {} : { durationSeconds: Math.ceil(asset.duration) }),
  });
  mediaCache.set(url, grant.mediaPublicId);
  return grant.mediaPublicId;
}

async function records(key) {
  const canonical = read(path.join(docs, 'data/question-import/by-task-type', `${key}.json`));
  const extra = read(path.join(docs, 'data/additionalMockData', `${key}.json`));
  const illustrations = key === 'DESCRIBE_IMAGE' ? await describeImageSupplements(canonical, root) : [];
  return [...canonical, ...extra, ...illustrations].filter((record) => {
    const warnings = record.importWarnings || [];
    return !warnings.some((warning) => ['IMAGE_URL_MISSING', 'PROMPT_TEXT_MISSING', 'CORRECT_ANSWER_MISSING'].includes(warning));
  });
}

async function bodyFor(record, title) {
  const body = { pteTaskType: record.taskTypeKey, taskTypeKey: record.taskTypeKey, title,
    pool: 'EXAM', availableForExam: true, availableForPractice: false,
    promptText: record.promptText, referenceAnswerText: record.referenceAnswerText,
    correctAnswerText: record.correctAnswerText, minWordCount: record.minWordCount,
    maxWordCount: record.maxWordCount,
    options: (record.options || []).map((option, index) => ({
      text: option.text, correct: Boolean(option.correct), orderIndex: index,
      blankIndex: option.blankIndex ?? null, correctGapIndex: option.correctGapIndex ?? null,
    })),
  };
  // Response bounds are task policy, not invented question/answer content.
  const bounds = { SUMMARIZE_SPOKEN_TEXT: [50, 70], SUMMARIZE_WRITTEN_TEXT: [5, 75], WRITE_ESSAY: [200, 300] }[record.taskTypeKey];
  if (bounds) { body.minWordCount ??= bounds[0]; body.maxWordCount ??= bounds[1]; }
  if (record.taskTypeKey === 'READ_ALOUD') body.referenceAnswerText ||= record.promptText;
  if (record.audioPromptUrl) body.audioPromptRef = await media(record.audioPromptUrl, 'AUDIO_PROMPT');
  if (record.imagePromptUrl) body.imagePromptRef = await media(record.imagePromptUrl, 'IMAGE_PROMPT');
  return body;
}

async function readiness(template) {
  const result = (await api(`/api/v1/exam-definitions/${template.publicId}/feasibility`)).data;
  // Standard exam generation adds this unscored task implicitly, beyond the 22 template rows.
  if (template.templatePolicy === 'STANDARD_PTE' && template.items.some((item) => item.section === 'SPEAKING')) {
    const page = await api('/api/v1/questions?q=DOC22%20PERSONAL_INTRODUCTION&size=100&pool=EXAM');
    const entries = Array.isArray(page.data) ? page.data : page.data?.content || page.data?.data || [];
    const available = entries.filter((row) => row.taskTypeKey === 'PERSONAL_INTRODUCTION' && row.pool === 'EXAM' && row.status === 'APPROVED').length;
    result.slots.push({ taskType: 'PERSONAL_INTRODUCTION', section: 'SPEAKING', required: 1,
      available, ready: available >= 1, reason: available >= 1 ? null : 'INSUFFICIENT_POOL' });
    result.ready &&= available >= 1;
  }
  return result;
}

async function main() {
  token = (await api('/api/v1/auth/login', 'POST', {
    username: process.env.PTE_ADMIN_USERNAME || 'admin@test.local', password: process.env.PTE_ADMIN_PASSWORD,
  })).data.accessToken;
  const templates = (await api('/api/v1/exam-definitions')).data;
  const template = templates.find((row) => row.code === 'PTE_Score_Template' && row.version === 2);
  if (!template) throw Error('Standard template V2 was not found');
  const requirements = await readiness(template);
  if (process.argv.includes('--check-only')) {
    console.log(JSON.stringify({ template: template.publicId, ready: requirements.ready,
      slots: requirements.slots }));
    process.exitCode = requirements.ready ? 0 : 2;
    return;
  }
  let created = 0;
  for (const slot of requirements.slots) {
    if (slot.ready) continue;
    let remaining = Math.max(0, slot.required - slot.available);
    for (const record of (await records(slot.taskType)).slice(0, Math.max(20, remaining * 3))) {
      if (remaining === 0) break;
      const source = JSON.stringify(record.source || {}) + JSON.stringify([record.promptText, record.audioPromptUrl, record.imagePromptUrl]);
      const title = `DOC22 ${slot.taskType} ${digest(source)} | ${record.source?.kind === 'mock' ? '[MOCK] ' : ''}${record.title}`;
      const result = await api(`/api/v1/questions?q=${encodeURIComponent(title)}&size=100&pool=EXAM`);
      const entries = Array.isArray(result.data) ? result.data : result.data?.content || result.data?.data || [];
      let existing = entries.find((row) => row.title === title && row.taskTypeKey === slot.taskType && row.pool === 'EXAM');
      if (existing?.status === 'APPROVED') continue;
      try {
        if (!existing) existing = (await api('/api/v1/questions', 'POST', await bodyFor(record, title))).data;
        await api(`/api/v1/questions/${existing.publicId}/status`, 'PATCH', { action: 'PUBLISH' });
        created++; remaining--;
        console.log(JSON.stringify({ task: slot.taskType, imported: created, remaining, source: record.source?.file }));
      } catch (error) {
        console.log(JSON.stringify({ task: slot.taskType, skipped: record.title, error: error.message }));
      }
    }
    if (remaining) console.log(JSON.stringify({ task: slot.taskType, stillMissing: remaining }));
  }
  const final = await readiness(template);
  console.log(JSON.stringify({ template: template.publicId, created, ready: final.ready,
    blocked: final.slots.filter((slot) => !slot.ready) }));
  process.exitCode = final.ready ? 0 : 2;
}
main().catch((error) => { console.error(error.message); process.exitCode = 1; });
