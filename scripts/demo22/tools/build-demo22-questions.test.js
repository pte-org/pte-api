'use strict';
// Run: node --test scripts/demo22/tools/build-demo22-questions.test.js
// Checks the committed seed (demo22-questions.json + demo22-seed.sql) against the rules the
// API and scoring code rely on, so a bad edit to gap-content.json is caught before it is seeded.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');

const DIR = path.join(__dirname, '..', 'data');
const seed = JSON.parse(fs.readFileSync(path.join(DIR, 'demo22-questions.json'), 'utf8'));
const sql = fs.readFileSync(path.join(DIR, 'demo22-seed.sql'), 'utf8');
const table = JSON.parse(fs.readFileSync(path.join(DIR, 'score-table-v5.json'), 'utf8')).items;
const questions = seed.questions;
const ofType = (key) => questions.filter((q) => q.taskTypeKey === key);

test('3 questions for each of the 22 scored task types, plus 1 Personal Introduction', () => {
  for (const { taskType } of table) assert.equal(ofType(taskType).length, 3, taskType);
  assert.equal(ofType('PERSONAL_INTRODUCTION').length, 1);
  assert.equal(questions.length, 67);
  assert.equal(new Set(questions.map((q) => q.title)).size, questions.length, 'titles must be unique');
});

test('V5 table: each skill column sums to exactly 100', () => {
  for (const skill of ['speaking', 'writing', 'reading', 'listening']) {
    assert.equal(table.reduce((sum, row) => sum + row[skill], 0), 100, skill);
  }
});

test('every audio/image requirement has a usable https url', () => {
  const audioTypes = new Set(['REPEAT_SENTENCE', 'RE_TELL_LECTURE', 'ANSWER_SHORT_QUESTION', 'RESPOND_TO_A_SITUATION',
    'SUMMARIZE_GROUP_DISCUSSION', 'SUMMARIZE_SPOKEN_TEXT', 'MC_LISTENING_SINGLE', 'MC_LISTENING_MULTIPLE',
    'FILL_IN_THE_BLANKS_TYPE_IN', 'HIGHLIGHT_CORRECT_SUMMARY', 'SELECT_MISSING_WORD', 'HIGHLIGHT_INCORRECT_WORDS',
    'WRITE_FROM_DICTATION']);
  for (const q of questions) {
    if (audioTypes.has(q.taskTypeKey)) assert.match(q.audio.url || '', /^https:\/\//, q.key);
    if (q.taskTypeKey === 'DESCRIBE_IMAGE') assert.match(q.image.url || '', /^https:\/\//, q.key);
  }
});

test('Re-order Paragraphs: orderIndex is a permutation of the correct positions', () => {
  for (const q of ofType('RE_ORDER_PARAGRAPHS')) {
    const indexes = q.options.map((o) => o.orderIndex).sort((a, b) => a - b);
    assert.deepEqual(indexes, q.options.map((_, i) => i), q.key);
  }
});

test('single-answer types have exactly one correct option; multi-answer at least one', () => {
  for (const key of ['MC_READING_SINGLE', 'MC_LISTENING_SINGLE']) {
    for (const q of ofType(key)) assert.equal(q.options.filter((o) => o.correct).length, 1, q.key);
  }
  for (const key of ['MC_READING_MULTIPLE', 'MC_LISTENING_MULTIPLE', 'HIGHLIGHT_CORRECT_SUMMARY', 'SELECT_MISSING_WORD']) {
    for (const q of ofType(key)) assert.ok(q.options.some((o) => o.correct), q.key);
  }
});

test('Fill in the Blanks (Dropdown): one correct option per blank, orderIndex unique', () => {
  for (const q of ofType('FILL_IN_THE_BLANKS_DROPDOWN')) {
    assert.equal(new Set(q.options.map((o) => o.orderIndex)).size, q.options.length, q.key);
    const perBlank = new Map();
    for (const o of q.options) perBlank.set(o.blankIndex, (perBlank.get(o.blankIndex) || 0) + (o.correct ? 1 : 0));
    assert.ok([...perBlank.values()].every((n) => n === 1), q.key);
  }
});

test('Fill in the Blanks (Type In): every {{n}} blank has a matching answer', () => {
  for (const q of ofType('FILL_IN_THE_BLANKS_TYPE_IN')) {
    const blanks = [...q.promptText.matchAll(/\{\{(\d+)\}\}/g)].map((m) => Number(m[1]));
    const answers = JSON.parse(q.correctAnswerText);
    assert.deepEqual(blanks, answers.map((_, i) => i), q.key);
    assert.ok(answers.every((a) => a && !a.includes(',')), 'answers must be non-empty and comma-free');
  }
});

test('Highlight Incorrect Words: answers are token positions of the altered words', () => {
  const content = JSON.parse(fs.readFileSync(path.join(DIR, 'gap-content.json'), 'utf8')).highlightIncorrectWords;
  ofType('HIGHLIGHT_INCORRECT_WORDS').forEach((q, i) => {
    const tokens = q.promptText.split(/\s+/);
    const original = content[i].text.split(/\s+/);
    const positions = JSON.parse(q.correctAnswerText);
    const differing = tokens.map((t, idx) => (t !== original[idx] ? idx : -1)).filter((idx) => idx >= 0);
    assert.deepEqual(positions, differing, q.key);
  });
});

test('word-count bounds present where required', () => {
  for (const key of ['SUMMARIZE_WRITTEN_TEXT', 'WRITE_ESSAY', 'SUMMARIZE_SPOKEN_TEXT']) {
    for (const q of ofType(key)) assert.ok(q.minWordCount > 0 && q.maxWordCount >= q.minWordCount, q.key);
  }
});

test('SQL seed is consistent with the JSON and contains no secrets', () => {
  assert.match(sql, new RegExp(`question_count <> ${questions.length}`));
  assert.equal((sql.match(/ON CONFLICT \(public_id\) DO UPDATE/g) || []).length, 3);
  assert.doesNotMatch(sql, /api_secret|api_key|signature=|password/i);
  assert.doesNotMatch(sql, /Expires=|GoogleAccessId|firebase/i, 'no signed third-party urls');
});
