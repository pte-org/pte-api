#!/usr/bin/env node
/*
 * Builds scripts/demo22/data/demo22-questions.json: 3 questions for each of
 * the 22 scored PTE task types (66 total).
 *
 *  - 17 types are picked from pte-doc/data/question-import/by-task-type/*.json
 *    (existing audio stays an external URL).
 *  - 4 types with no usable source data come from gap-content.json:
 *    FILL_IN_THE_BLANKS_TYPE_IN, HIGHLIGHT_INCORRECT_WORDS,
 *    SUMMARIZE_GROUP_DISCUSSION, DESCRIBE_IMAGE (plus 1 PERSONAL_INTRODUCTION).
 *    SUMMARIZE_SPOKEN_TEXT comes from the source and only gets word-count bounds added.
 *  - Media for the gap types is a local file name; scripts/demo22/tools/publish-demo22-media.ps1
 *    uploads it to Cloudinary and records the public url/publicId/duration in demo22-media.json.
 *
 * Usage: node scripts/demo22/tools/build-demo22-questions.js [--source <question-import dir>]
 * Outputs (scripts/demo22/data/): demo22-questions.json always; demo22-seed.sql once every
 * local file has an entry in demo22-media.json (written by publish-demo22-media.ps1).
 */
'use strict';
const fs = require('fs');
const path = require('path');
const { buildSql } = require('./demo22-sql');

const DEMO_DIR = path.join(__dirname, '..', 'data');
const OUTPUT = path.join(DEMO_DIR, 'demo22-questions.json');
const SQL_OUTPUT = path.join(DEMO_DIR, 'demo22-seed.sql');
const GAP_CONTENT = path.join(DEMO_DIR, 'gap-content.json');
const PER_TYPE = 3;
const SST_WORD_LIMITS = { minWordCount: 50, maxWordCount: 70 };

const args = process.argv.slice(2);
const argValue = (name) => { const i = args.indexOf(name); return i >= 0 ? args[i + 1] : null; };
const sourceDir = argValue('--source')
  || path.join(__dirname, '..', '..', '..', '..', 'pte-doc', 'data', 'question-import');

// Requirements mirror V44__seed_standard_task_type_catalog.sql (question_types flags).
// The generator adds an implicit PERSONAL_INTRODUCTION (unscored, 1 question) at the start of the
// Speaking section for STANDARD_PTE templates, so the bank needs one even though it is not one of
// the 22 scored types.
const COUNT_OVERRIDES = { PERSONAL_INTRODUCTION: 1 };
const TASK_TYPES = [
  ['PERSONAL_INTRODUCTION', 'SPEAKING', { text: 1 }],
  ['READ_ALOUD', 'SPEAKING', { text: 1 }],
  ['REPEAT_SENTENCE', 'SPEAKING', { audio: 1 }],
  ['DESCRIBE_IMAGE', 'SPEAKING', { image: 1 }],
  ['RE_TELL_LECTURE', 'SPEAKING', { audio: 1 }],
  ['ANSWER_SHORT_QUESTION', 'SPEAKING', { audio: 1, answer: 1 }],
  ['SUMMARIZE_GROUP_DISCUSSION', 'SPEAKING', { audio: 1 }],
  ['RESPOND_TO_A_SITUATION', 'SPEAKING', { audio: 1, text: 1 }],
  ['SUMMARIZE_WRITTEN_TEXT', 'WRITING', { text: 1, words: 1 }],
  ['WRITE_ESSAY', 'WRITING', { text: 1, words: 1 }],
  ['FILL_IN_THE_BLANKS_DROPDOWN', 'READING', { text: 1, options: 1, answer: 1 }],
  ['MC_READING_MULTIPLE', 'READING', { text: 1, options: 1, answer: 1 }],
  ['RE_ORDER_PARAGRAPHS', 'READING', { options: 1 }],
  ['FILL_IN_THE_BLANKS_DRAG_AND_DROP', 'READING', { text: 1, options: 1, answer: 1 }],
  ['MC_READING_SINGLE', 'READING', { text: 1, options: 1, answer: 1, single: 1 }],
  ['SUMMARIZE_SPOKEN_TEXT', 'LISTENING', { audio: 1, words: 1 }],
  ['MC_LISTENING_MULTIPLE', 'LISTENING', { audio: 1, options: 1, answer: 1 }],
  ['FILL_IN_THE_BLANKS_TYPE_IN', 'LISTENING', { audio: 1, text: 1, answer: 1 }],
  ['HIGHLIGHT_CORRECT_SUMMARY', 'LISTENING', { audio: 1, options: 1, answer: 1 }],
  ['MC_LISTENING_SINGLE', 'LISTENING', { audio: 1, options: 1, answer: 1, single: 1 }],
  ['SELECT_MISSING_WORD', 'LISTENING', { audio: 1, options: 1, answer: 1 }],
  ['HIGHLIGHT_INCORRECT_WORDS', 'LISTENING', { audio: 1, text: 1, answer: 1 }],
  ['WRITE_FROM_DICTATION', 'LISTENING', { audio: 1, answer: 1 }]
];

const wordCount = (text) => (text || '').trim().split(/\s+/).filter(Boolean).length;
const gapContent = JSON.parse(fs.readFileSync(GAP_CONTENT, 'utf8'));

// Written by publish-demo22-media.ps1: { "<localFile>": { url, publicId, assetId, sizeBytes, durationSeconds } }
const MEDIA_MAP = path.join(DEMO_DIR, 'demo22-media.json');
const published = fs.existsSync(MEDIA_MAP)
  ? JSON.parse(fs.readFileSync(MEDIA_MAP, 'utf8').replace(/^﻿/, '')) // tolerate a PowerShell-written BOM
  : {};

function localMedia(key, kind, file) {
  const keep = published[file] || {};
  return {
    localFile: file,
    contentType: file.endsWith('.png') ? 'image/png' : 'audio/wav',
    url: keep.url || null,
    publicId: keep.publicId || null,
    assetId: keep.assetId || null,
    sizeBytes: keep.sizeBytes || null,
    durationSeconds: keep.durationSeconds || null
  };
}

function externalAudio(url) {
  return { localFile: null, contentType: 'audio/mpeg', url, publicId: null, assetId: null, sizeBytes: null, durationSeconds: null };
}

function normalizeOptions(record) {
  const dropdown = record.taskTypeKey === 'FILL_IN_THE_BLANKS_DROPDOWN';
  return (record.options || []).map((option, index) => ({
    text: option.text,
    correct: Boolean(option.correct),
    // QuestionValidationHelper needs orderIndex unique per question; dropdown source data is per-blank.
    orderIndex: dropdown ? index : (Number.isInteger(option.orderIndex) ? option.orderIndex : index),
    blankIndex: option.blankIndex ?? null,
    correctGapIndex: option.correctGapIndex ?? null
  }));
}

function baseQuestion(taskTypeKey, n) {
  return {
    key: `${taskTypeKey}-${n}`,
    taskTypeKey,
    title: `DEMO22 - ${taskTypeKey} - ${n}`,
    promptText: null,
    referenceAnswerText: null,
    correctAnswerText: null,
    minWordCount: null,
    maxWordCount: null,
    options: [],
    audio: null,
    image: null
  };
}

/**
 * RE_ORDER_PARAGRAPHS: the question bank's own orderIndex is the display order;
 * correctAnswerText ("2, 3, 4, 1") gives the correct 1-based position of each
 * displayed paragraph. The API needs orderIndex to BE the correct position
 * (ItembankService.deliveryOrder shuffles for students), so rewrite it here.
 */
function reorderOptions(record) {
  const options = normalizeOptions(record);
  const positions = (record.correctAnswerText || '').split(',').map((v) => Number(v.trim()));
  const valid = positions.length === options.length
    && [...positions].sort((a, b) => a - b).every((v, i) => v === i + 1);
  if (!valid) return null;
  return options.map((option, i) => ({ ...option, orderIndex: positions[i] - 1 }));
}

function fromSource(taskTypeKey, n, record) {
  const q = baseQuestion(taskTypeKey, n);
  q.promptText = record.promptText || null;
  q.referenceAnswerText = record.referenceAnswerText || null;
  q.correctAnswerText = record.correctAnswerText || null;
  q.minWordCount = record.minWordCount ?? null;
  q.maxWordCount = record.maxWordCount ?? null;
  q.options = taskTypeKey === 'RE_ORDER_PARAGRAPHS' ? (reorderOptions(record) || []) : normalizeOptions(record);
  if (record.audioPromptUrl) q.audio = externalAudio(record.audioPromptUrl);
  if (taskTypeKey === 'SUMMARIZE_SPOKEN_TEXT') Object.assign(q, SST_WORD_LIMITS);
  return q;
}

function validate(q, requirements) {
  const problems = [];
  if (requirements.audio && !(q.audio && (q.audio.url || q.audio.localFile))) problems.push('audio missing');
  if (requirements.image && !(q.image && (q.image.url || q.image.localFile))) problems.push('image missing');
  if (requirements.text && !(q.promptText || '').trim()) problems.push('promptText missing');
  if (requirements.words && !(q.minWordCount && q.maxWordCount && q.minWordCount <= q.maxWordCount)) problems.push('word count missing');
  if (requirements.options) {
    if (!q.options.length) problems.push('options missing');
    const indexes = new Set(q.options.map((o) => o.orderIndex));
    if (indexes.size !== q.options.length || q.options.some((o) => !Number.isInteger(o.orderIndex) || o.orderIndex < 0)) problems.push('orderIndex not unique');
    if (q.options.some((o) => o.blankIndex !== null && o.correctGapIndex !== null)) problems.push('blankIndex and correctGapIndex both set');
    if (requirements.answer && !q.options.some((o) => o.correct)) problems.push('no correct option');
    if (requirements.single && q.options.filter((o) => o.correct).length !== 1) problems.push('needs exactly one correct option');
    if (q.taskTypeKey === 'FILL_IN_THE_BLANKS_DROPDOWN') {
      const perBlank = new Map();
      q.options.forEach((o) => perBlank.set(o.blankIndex, (perBlank.get(o.blankIndex) || 0) + (o.correct ? 1 : 0)));
      if ([...perBlank.values()].some((c) => c !== 1)) problems.push('dropdown needs one correct option per blank');
    }
  } else if (requirements.answer && !(q.correctAnswerText || '').trim()) {
    problems.push('correctAnswerText missing');
  }
  return problems;
}

/** Shortest-first selection would otherwise pick truncated source rows (e.g. a 1-word essay prompt). */
function isDemoWorthy(q) {
  const words = wordCount(q.promptText);
  switch (q.taskTypeKey) {
    case 'READ_ALOUD': return words >= 30 && words <= 70;
    case 'WRITE_ESSAY': return words >= 10 && q.minWordCount > 0;
    case 'FILL_IN_THE_BLANKS_DROPDOWN': {
      const blanks = new Set(q.options.map((o) => o.blankIndex)).size;
      return words >= 40 && blanks >= 4 && q.options.length >= blanks * 3;
    }
    case 'FILL_IN_THE_BLANKS_DRAG_AND_DROP': return words >= 30 && q.options.some((o) => o.correctGapIndex !== null);
    default: return true;
  }
}

function selectFromSource(taskTypeKey, requirements) {
  const file = path.join(sourceDir, 'by-task-type', `${taskTypeKey}.json`);
  const seenAudio = new Set();
  const candidates = JSON.parse(fs.readFileSync(file, 'utf8'))
    .filter((record) => (record.importWarnings || [])
      .every((w) => w === 'EXTERNAL_AUDIO_URL_REQUIRES_BACKEND_SUPPORT' || w === 'WORD_COUNT_MISSING'))
    .map((record) => ({ record, size: wordCount(record.promptText) + (record.options || []).length * 4 }))
    .sort((a, b) => a.size - b.size);
  const picked = [];
  for (const { record } of candidates) {
    const q = fromSource(taskTypeKey, picked.length + 1, record);
    if (validate(q, requirements).length) continue;
    if (!isDemoWorthy(q)) continue;
    if (q.audio && seenAudio.has(q.audio.url)) continue;
    if (q.audio) seenAudio.add(q.audio.url);
    picked.push(q);
    if (picked.length === PER_TYPE) break;
  }
  return picked;
}

function fillInTheBlanks(items) {
  return items.map((item, i) => {
    const q = baseQuestion('FILL_IN_THE_BLANKS_TYPE_IN', i + 1);
    let cursor = 0;
    let text = item.text;
    item.blanks.forEach((word, blankIndex) => {
      const match = new RegExp(`\\b${word}\\b`, 'i').exec(text.slice(cursor));
      if (!match) throw new Error(`${item.key}: blank "${word}" not found in order`);
      const start = cursor + match.index;
      text = `${text.slice(0, start)}{{${blankIndex}}}${text.slice(start + word.length)}`;
      cursor = start + `{{${blankIndex}}}`.length;
    });
    q.promptText = text;
    q.correctAnswerText = JSON.stringify(item.blanks.map((w) => w.toLowerCase()));
    q.audio = localMedia(q.key, 'audio', `demo22-${item.key}.wav`);
    return q;
  });
}

function highlightIncorrectWords(items) {
  return items.map((item, i) => {
    const q = baseQuestion('HIGHLIGHT_INCORRECT_WORDS', i + 1);
    const tokens = item.text.trim().split(/\s+/);
    const positions = [];
    for (const [original, replacement] of Object.entries(item.replacements)) {
      const index = tokens.findIndex((t) => t.replace(/^\W+|\W+$/g, '').toLowerCase() === original);
      if (index < 0) throw new Error(`${item.key}: word "${original}" not found`);
      tokens[index] = tokens[index].replace(new RegExp(original, 'i'), replacement);
      positions.push(index);
    }
    q.promptText = tokens.join(' ');
    q.correctAnswerText = JSON.stringify(positions.sort((a, b) => a - b));
    q.audio = localMedia(q.key, 'audio', `demo22-${item.key}.wav`);
    return q;
  });
}

function groupDiscussion(items) {
  return items.map((item, i) => {
    const q = baseQuestion('SUMMARIZE_GROUP_DISCUSSION', i + 1);
    q.referenceAnswerText = item.turns.map(([, line]) => line).join(' ');
    q.audio = localMedia(q.key, 'audio', `demo22-${item.key}.wav`);
    return q;
  });
}

function describeImage(items) {
  return items.map((item, i) => {
    const q = baseQuestion('DESCRIBE_IMAGE', i + 1);
    q.referenceAnswerText = item.reference;
    q.image = localMedia(q.key, 'image', `demo22-${item.key}.png`);
    return q;
  });
}

function personalIntroduction(items) {
  return items.map((item, i) => {
    const q = baseQuestion('PERSONAL_INTRODUCTION', i + 1);
    q.promptText = item.text;
    return q;
  });
}

const generated = {
  PERSONAL_INTRODUCTION: () => personalIntroduction(gapContent.personalIntroduction),
  FILL_IN_THE_BLANKS_TYPE_IN: () => fillInTheBlanks(gapContent.fillInTheBlanksTypeIn),
  HIGHLIGHT_INCORRECT_WORDS: () => highlightIncorrectWords(gapContent.highlightIncorrectWords),
  SUMMARIZE_GROUP_DISCUSSION: () => groupDiscussion(gapContent.summarizeGroupDiscussion),
  DESCRIBE_IMAGE: () => describeImage(gapContent.describeImage)
};

const questions = [];
const failures = [];
for (const [taskTypeKey, section, requirements] of TASK_TYPES) {
  const picked = generated[taskTypeKey] ? generated[taskTypeKey]() : selectFromSource(taskTypeKey, requirements);
  const expected = COUNT_OVERRIDES[taskTypeKey] ?? PER_TYPE;
  if (picked.length !== expected) failures.push(`${taskTypeKey}: ${picked.length} usable question(s), expected ${expected}`);
  for (const q of picked) {
    const problems = validate(q, requirements);
    if (problems.length) failures.push(`${q.key}: ${problems.join(', ')}`);
    questions.push({ section, ...q });
  }
}
if (failures.length) {
  console.error(`Seed file NOT written:\n  ${failures.join('\n  ')}`);
  process.exit(1);
}

const output = {
  schemaVersion: 'demo22-questions-v1',
  perTaskType: PER_TYPE,
  note: 'Demo data for an internal class demo. External audio URLs come from pte-doc/data/question-import; local media is published by publish-demo22-media.ps1. Contains no secrets.',
  questions
};
fs.writeFileSync(OUTPUT, `${JSON.stringify(output, null, 2)}\n`, 'utf8');
const unpublished = questions.filter((q) => ['audio', 'image'].some((k) => q[k] && q[k].localFile && !q[k].url)).length;
console.log(`Wrote ${questions.length} questions to ${path.relative(process.cwd(), OUTPUT)} (${unpublished} with local media not yet published).`);

if (unpublished > 0) {
  console.log('SQL seed NOT written: publish the local media first (scripts/demo22/tools/publish-demo22-media.ps1), then rerun this builder.');
} else {
  fs.writeFileSync(SQL_OUTPUT, buildSql(questions), 'utf8');
  console.log(`Wrote ${path.relative(process.cwd(), SQL_OUTPUT)}.`);
}
