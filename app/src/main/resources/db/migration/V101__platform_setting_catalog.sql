INSERT INTO platform_settings (
        public_id, created_at, updated_at, deleted, setting_key, value, description)
SELECT '00000000-0000-0000-0000-000000000003', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
       FALSE, 'tenant_application_response_sla_hours', '72',
       'Response window in hours for tenant application notifications'
WHERE NOT EXISTS (
    SELECT 1
    FROM platform_settings
    WHERE setting_key = 'tenant_application_response_sla_hours'
);
