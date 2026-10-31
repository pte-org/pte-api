import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const skills = ['speakingWeight', 'writingWeight', 'readingWeight', 'listeningWeight'];

// Largest-remainder allocation in hundredths of a percent: preserves zeros and ratios,
// produces exactly 100.00 per skill, and never relies on floating-point totals.
export function normalizeWeights(source) {
  const items = source.map((item) => ({ ...item }));
  for (const skill of skills) {
    const units = items.map((item) => Math.round(Number(item[skill]) * 100));
    if (units.some((value) => !Number.isSafeInteger(value) || value < 0)) throw Error(`Invalid ${skill}`);
    const total = units.reduce((sum, value) => sum + value, 0);
    if (!total) throw Error(`Empty ${skill}`);
    if (total === 10000) continue;
    const allocation = units.map((value, index) => ({ index,
      amount: Math.floor(value * 10000 / total), remainder: value * 10000 % total,
    }));
    let remaining = 10000 - allocation.reduce((sum, row) => sum + row.amount, 0);
    for (const row of [...allocation].sort((a, b) => b.remainder - a.remainder || a.index - b.index)) {
      if (!remaining) break;
      row.amount++; remaining--;
    }
    for (const row of allocation) items[row.index][skill] = row.amount / 100;
  }
  return items;
}

const totals = (items) => Object.fromEntries(skills.map((skill) => [skill,
  items.reduce((sum, item) => sum + Math.round(Number(item[skill]) * 100), 0) / 100,
]));

async function main() {
  const base = process.env.PTE_BASE_URL || 'http://localhost:8080';
  if (!['localhost', '127.0.0.1'].includes(new URL(base).hostname)) throw Error('Local API required');
  if (!process.env.PTE_ADMIN_PASSWORD) throw Error('PTE_ADMIN_PASSWORD is required');
  let token;
  async function api(route, method = 'GET', body) {
    const response = await fetch(base + route, { method,
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    });
    const result = await response.json();
    if (!response.ok || result.success === false) throw Error(JSON.stringify({ route, status: response.status,
      code: result.code, message: result.message, errors: result.errors }));
    return result.data;
  }
  token = (await api('/api/v1/auth/login', 'POST', {
    username: process.env.PTE_ADMIN_USERNAME || 'admin@test.local', password: process.env.PTE_ADMIN_PASSWORD,
  })).accessToken;
  const templates = await api('/api/v1/exam-definitions');
  const target = templates.find((row) => row.code === 'PTE_Score_Template' && row.version === 2);
  if (!target) throw Error('Standard template V2 not found');
  const route = `/api/v1/exam-definitions/${target.publicId}`;
  if (target.status === 'ACTIVE') {
    const readiness = await api(`${route}/feasibility`);
    console.log(JSON.stringify({ status: 'ACTIVE', publicId: target.publicId, name: target.name,
      taskTypes: target.items.length, totals: totals(target.items), questionBankReady: readiness.ready, changed: false,
      activeTemplates: templates.filter((row) => row.status === 'ACTIVE').length,
    }));
    return;
  }
  if (target.status !== 'DRAFT' || target.items.length !== 22 || new Set(target.items.map((item) => item.taskTypeKey)).size !== 22) {
    throw Error('Expected the 22-task standard draft V2');
  }
  const before = totals(target.items);
  const normalized = normalizeWeights(target.items);
  const fields = ['taskType', 'taskTypeKey', 'section', 'sequence', 'minCount', 'maxCount',
    'prepSeconds', 'responseSeconds', ...skills];
  const saved = await api(`${route}/items`, 'PUT', {
    name: target.name, templatePolicy: target.templatePolicy,
    items: normalized.map((item) => Object.fromEntries(fields.map((field) => [field, item[field]]))),
  });
  const feasibility = await api(`${route}/feasibility`);
  if (!feasibility.ready) throw Error(`Question readiness blocked: ${JSON.stringify(feasibility.slots.filter((slot) => !slot.ready))}`);
  const activated = await api(`${route}/status`, 'PATCH', { action: 'ACTIVATE' });
  // The host-only /active route is intentionally unavailable to a platform admin.
  const active = await api(route);
  if (active.publicId !== target.publicId || activated.status !== 'ACTIVE') throw Error('Activation verification failed');
  console.log(JSON.stringify({ name: active.name, code: active.code, version: active.version,
    publicId: active.publicId, status: active.status, taskTypes: active.items.length,
    before, after: totals(saved.items), questionBankReady: feasibility.ready,
    previousActive: templates.filter((row) => row.status === 'ACTIVE').map((row) => ({ code: row.code, version: row.version })),
  }));
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch((error) => { console.error(error.message); process.exitCode = 1; });
}
