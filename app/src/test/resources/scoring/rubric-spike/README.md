# Rubric stability spike: synthetic dataset

Plan `quang-scoring-rubric-transparency`, phase 4 (decision U2). Everything in this folder is **synthetic data
written by an AI assistant (Claude) on 2026-11-05**. Nothing here is a real candidate's essay, summary, transcript or
recording, and nothing was copied from a real test.

## What it is for

- **Run-to-run stability only.** `RubricStabilitySpikeTest` scores each writing sample three times with the real
  Gemini scorer and measures how often a trait score varies by at most one Pearson level across the runs.
- **Not accuracy.** The `intendedBands` are the bands the author aimed for while writing each sample. Nobody has
  rated the samples, so "within one level of the intended band" is a secondary number in the report and never a
  gate. Do not quote it as evidence that the AI scores correctly.

## Files

| File | Content |
|---|---|
| `writing-synthetic.json` | 20 writing samples: 7 Summarize Written Text, 7 Write Essay, 6 Summarize Spoken Text, plus the passage, essay prompt and lecture transcript they answer (`sources`). |
| `speaking-transcripts-synthetic.json` | 20 speaking transcripts: 5 each for Describe Image, Re-tell Lecture, Respond to a Situation and Summarize Group Discussion, plus the chart description, lecture, situation and discussion they answer. For phase 5 (Gemini Content on the Pearson 0-6 scale). |
| `tts-manifest.json` | Phase 5 only: 12 TTS scripts (4 transcripts x FLUENT / MILD_HESITATION / HEAVY_HESITATION) for checking that Oral Fluency falls monotonically. |
| `generate-tts.ps1` | Generates the WAVs from the manifest with Windows `System.Speech` (16 kHz, 16-bit, mono). |

## How the samples were made

- Each sample targets one band per trait, using the trait keys and maxima of `RubricCatalogImpl`
  (SWT: Content 0-4, Form 0-1, Grammar 0-2, Vocabulary 0-2; WE: Content, Development/Structure/Coherence and
  General Linguistic Range 0-6, Form, Grammar, Vocabulary Range and Spelling 0-2; SST: Content 0-4, Form, Grammar,
  Vocabulary and Spelling 0-2; DI/RL/RTS/SGD: Content 0-6).
- Bands cover low, middle and high levels and the edge cases the phase asks for: off-topic answers, a memorised
  template essay, very short answers, an SWT written as two sentences, SWT and essays in capital letters, essays of
  about 110, 145, 240 and 333 words, and SST summaries of about 35, 45, 60, 80 and 105 words.
- `generationNotes` says what each sample is meant to show.
- The intended **Form** band is not a guess: `SyntheticDatasetTest` checks that it equals
  `FormRule.bandFor(FormCheck.measure(answer))` on the catalog rubric, and that every intended band lies on the
  catalog scale.
- Speaking transcripts carry only Content: Pronunciation and Oral Fluency come from Azure audio assessment, not from
  a transcript.

## Running the spike (needs a Gemini key; spends quota)

```powershell
cd D:\GitHub\pte-org\pte-api
$env:SCORING_AI_SPIKE = 'true'
$env:SCORING_AI_GEMINI_API_KEY = '<key>'            # never commit it
$env:SCORING_AI_GEMINI_MODEL = 'gemini-flash-lite-latest'   # optional
$env:SCORING_AI_SPIKE_OUT = 'D:\spike\stability-single.json' # optional aggregate JSON
.\mvnw.cmd -pl app test -Dtest="RubricStabilitySpikeTest#allWriting"
# Fallback measurement: each run is the median of three calls for the listed traits (or ALL).
$env:SCORING_AI_SPIKE_MEDIAN_TRAITS = 'ALL'
$env:SCORING_AI_SPIKE_OUT = 'D:\spike\stability-median.json'
.\mvnw.cmd -pl app test -Dtest="RubricStabilitySpikeTest#allWriting"
```

Phase 5 adds the speaking transcripts (Content 0-6 of DI, RL, RTS and SGD; the Azure step is stubbed so only Gemini
is measured, and Describe Image uses its reference-description fallback):

```powershell
.\mvnw.cmd -pl app test -Dtest="RubricStabilitySpikeTest#allSpeaking"
# or one task type: #describeImage, #retellLecture, #respondToSituation, #summarizeGroupDiscussion
```

## Generating the TTS audio (phase 5)

WAV files are never committed. Generate them on demand:

```powershell
$env:SCORING_AI_SPIKE_WAV_DIR = 'D:\spike\wav'
powershell -ExecutionPolicy Bypass -File app\src\test\resources\scoring\rubric-spike\generate-tts.ps1
```

Then check that Oral Fluency never rises as hesitation grows (real Azure; needs `SCORING_AI_AZURE_SPEECH_KEY`,
`SCORING_AI_AZURE_SPEECH_REGION`, and `SCORING_AI_SPIKE_DIR`, `SCORING_AI_GEMINI_API_KEY` because the spike class
reads them when it starts). This checks monotonicity only and does not calibrate the Azure band mapping:

```powershell
.\mvnw.cmd -pl app test -Dtest="AllScorersSpikeTest#oralFluencyIsMonotonicOnSyntheticHesitations"
```
