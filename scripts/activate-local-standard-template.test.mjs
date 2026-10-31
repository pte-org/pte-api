import assert from 'node:assert/strict';
import { test } from 'node:test';
import { normalizeWeights, skills } from './activate-local-standard-template.mjs';

test('normalizes 101-percent seed columns to exactly 100 while retaining zero weights', () => {
  const rows = [
    { speakingWeight: 9, writingWeight: 100, readingWeight: 0, listeningWeight: 0 },
    { speakingWeight: 92, writingWeight: 0, readingWeight: 101, listeningWeight: 101 },
  ];
  const before = structuredClone(rows);
  const fixed = normalizeWeights(rows);
  for (const skill of skills) assert.equal(fixed.reduce((sum, row) => sum + Math.round(row[skill] * 100), 0), 10000);
  assert.equal(fixed[0].readingWeight, 0);
  assert.equal(fixed[0].writingWeight, 100);
  assert.deepEqual(rows, before);
  assert.deepEqual(normalizeWeights(fixed), fixed);
});

test('allocates rounding remainder deterministically rather than losing 0.01 percent', () => {
  const rows = Array.from({ length: 3 }, () => Object.fromEntries(skills.map((skill) => [skill, 1])));
  const fixed = normalizeWeights(rows);
  assert.deepEqual(fixed.map((row) => row.speakingWeight), [33.34, 33.33, 33.33]);
});

test('rejects a missing or negative skill allocation', () => {
  assert.throws(() => normalizeWeights([{ speakingWeight: 0 }]));
  assert.throws(() => normalizeWeights([{ speakingWeight: -1 }]));
});
