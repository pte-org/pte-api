# DEMO22 demo data

Demo data for the 22 PTE task types in two flows (PRACTICE and OFFICIAL_EXAM). Internal class demo
only. Full instructions: the section "Demo data: 22 task types" in `pte-api/README.md`.

```powershell
.\scripts\demo22\seed-demo22.ps1      # one command, idempotent
```

| Folder / file | What it is |
|---|---|
| `seed-demo22*.ps1`, `verify-demo22.ps1`, `bootstrap-local-admin.ps1` | scripts you run |
| `lib/` | shared PowerShell helpers (API calls, tenant/account helpers) |
| `data/` | the seed: `demo22-seed.sql` (generated), `demo22-questions.json`, `demo22-media.json`, `gap-content.json`, `score-table-v5.json` |
| `tools/` | maintainer tools: builder + tests, media generator and Cloudinary publisher |

## Cleaning up

1. Local database: `docker compose --env-file .env.local -f docker-compose.yml -f docker-compose.services.yml down -v`
   (deletes ALL local data), or remove only the demo rows with the `DEMO22` tenant/questions.
2. Cloudinary: delete the `pte/demo22` folder (the uploaded demo audio and charts).
3. Repo: delete this `scripts/demo22/` folder, the "Demo data: 22 task types" section of `pte-api/README.md`
   and the `PTE_*` lines in `.env.example`.
4. Plan: `pte-doc/projects/plans/ninh-seed-demo-practice-official-22-task-types/`.
