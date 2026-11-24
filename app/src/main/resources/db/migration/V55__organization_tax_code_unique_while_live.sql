-- A tax code identifies one legal entity, so it is reserved from the moment an application is submitted until it is
-- rejected, exactly like the code and the name. The old index only covered ACTIVE/SUSPENDED rows.

-- Applications still waiting for review that repeat a tax code held by another live row would make the index fail.
-- Keep the approved one (otherwise the oldest) and reject the rest so they free their code and can be resubmitted.
UPDATE organizations o
SET status = 'REJECTED',
    reject_reason = 'Duplicate tax code: another organization already holds it.',
    reviewed_at = now(),
    updated_at = now()
WHERE o.status = 'PENDING'
  AND o.tax_code IS NOT NULL
  AND EXISTS (
      SELECT 1
      FROM organizations k
      WHERE k.tax_code = o.tax_code
        AND k.id <> o.id
        AND k.status <> 'REJECTED'
        AND (k.status IN ('ACTIVE', 'SUSPENDED') OR k.id < o.id)
  );

DROP INDEX IF EXISTS uq_org_tax_code_approved;

CREATE UNIQUE INDEX uq_org_tax_code_live ON organizations USING btree (tax_code) WHERE ((status)::text <> 'REJECTED'::text);
