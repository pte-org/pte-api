# Local EXAM question import from pte-doc

Run with the local API and `PTE_ADMIN_PASSWORD` supplied through the environment:

```powershell
node scripts/seed-local-doc-question-bank.mjs
node scripts/seed-local-doc-question-bank.mjs --check-only
```

Optional environment settings: `PTE_BASE_URL` (localhost only),
`PTE_ADMIN_USERNAME`, and `PTE_DOC_ROOT`.

The importer reads the standard template V2 feasibility endpoint and imports
the implicit unscored Personal Introduction question used by standard PTE
generation, in addition to the 22 scored task types. It imports
only missing counts from `pte-doc/data/question-import/by-task-type` and
`pte-doc/data/additionalMockData`. It creates and publishes through the normal
question APIs; media is downloaded from each source URL and uploaded through
the media API. It never activates the template or changes its counts.

Deterministic source-based titles identify previously imported questions.
Reruns reuse existing questions and skip task types that are already ready.
Mock supplement questions retain `[MOCK]` in their titles: some use illustrative
transcripts or monologue audio, as documented in the source records. These are
local workflow fixtures, not validated assessment material.

Exit code 0 means feasibility is ready; 2 means some requirements remain unmet;
1 means the import could not run. The final JSON lists every remaining blocker.
The checked-in Describe Image supplement has four image-backed questions. Two
additional local test illustrations are reconstructed from the canonical
`Height of Tree` and `Electricity Generation` reference figures. Their titles
retain `[MOCK]`; they are not the original exam images. PNG rendering uses
`sharp` from the installed sibling `pte-web` Next.js dependencies. This supplies
six distinct Describe Image questions without changing the template counts.
