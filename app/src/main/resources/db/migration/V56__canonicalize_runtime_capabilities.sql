-- Keep capability arrays deterministic across the legacy comma-separated
-- profile projection and the new TEXT[] contract projection. Runtime
-- validation rejects malformed/duplicate values; this migration only
-- canonicalizes the known release-owned rows.
UPDATE task_runtime_profiles profile
   SET required_client_capabilities = COALESCE((
       SELECT string_agg(trim(capability), ',' ORDER BY trim(capability))
         FROM unnest(string_to_array(profile.required_client_capabilities, ',')) AS capability
        WHERE trim(capability) <> ''
   ), '');

UPDATE task_runtime_contracts contract
   SET required_client_capabilities = COALESCE((
       SELECT array_agg(trim(capability) ORDER BY trim(capability))
         FROM unnest(contract.required_client_capabilities) AS capability
        WHERE trim(capability) <> ''
   ), ARRAY[]::text[]);

UPDATE question_types definition
   SET runtime_required_capabilities = COALESCE((
       SELECT array_agg(trim(capability) ORDER BY trim(capability))
         FROM unnest(definition.runtime_required_capabilities) AS capability
        WHERE trim(capability) <> ''
   ), ARRAY[]::text[]);
