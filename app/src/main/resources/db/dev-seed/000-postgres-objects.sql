-- Local-schema profile only (SPRING_PROFILES_ACTIVE=local-schema): Postgres objects that JPA does not generate.
-- Idempotent; runs after Hibernate DDL. Statements are separated by a line containing only @@.
CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public
@@
CREATE OR REPLACE FUNCTION public.questions_pool_immutable() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.pool IS DISTINCT FROM OLD.pool THEN
        RAISE EXCEPTION 'questions.pool is immutable (question %)', OLD.public_id;
    END IF;
    RETURN NEW;
END;
$$
@@
CREATE OR REPLACE FUNCTION public.retain_question_publication_history() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.status = 'APPROVED' OR (TG_OP = 'UPDATE' AND OLD.ever_published IS TRUE) THEN
        NEW.ever_published := TRUE;
    END IF;
    RETURN NEW;
END;
$$
@@
CREATE OR REPLACE VIEW public.identity_staff_student_search_view AS
 SELECT om.organization_id AS tenant_id,
    u.public_id AS student_public_id,
    COALESCE(NULLIF(TRIM(BOTH FROM u.full_name), ''::text), (u.username)::text, (u.email)::text) AS display_name,
    u.email
   FROM (public.users u
     JOIN public.organization_members om ON ((om.user_id = u.id) AND (om.deleted = false)))
  WHERE (u.deleted = false)
@@
CREATE OR REPLACE VIEW public.identity_student_directory AS
 SELECT u.id,
    u.public_id,
    u.email,
    u.full_name,
    u.student_code,
    u.phone,
    om.status,
    om.created_at,
    om.organization_id AS tenant_id,
    u.deleted,
    u.username,
    u.must_change_password
   FROM ((public.users u
     JOIN public.organization_members om ON ((om.user_id = u.id) AND (om.deleted = false)))
     JOIN public.organization_member_roles omr ON ((omr.member_id = om.id) AND ((omr.role)::text = 'STUDENT'::text)))
@@
DROP TRIGGER IF EXISTS question_publication_history ON public.questions
@@
CREATE TRIGGER question_publication_history BEFORE INSERT OR UPDATE ON public.questions FOR EACH ROW EXECUTE FUNCTION public.retain_question_publication_history()
@@
DROP TRIGGER IF EXISTS trg_questions_pool_immutable ON public.questions
@@
CREATE TRIGGER trg_questions_pool_immutable BEFORE UPDATE OF pool ON public.questions FOR EACH ROW EXECUTE FUNCTION public.questions_pool_immutable()
@@
ALTER TABLE public.exam_sessions DROP CONSTRAINT IF EXISTS no_overlap_per_subscription
@@
ALTER TABLE public.exam_sessions ADD CONSTRAINT no_overlap_per_subscription EXCLUDE USING gist (subscription_id WITH =, tstzrange(opens_at, closes_at) WITH &&) WHERE (((status)::text = ANY ((ARRAY['SCHEDULED'::character varying, 'OPEN'::character varying])::text[])))
@@
CREATE UNIQUE INDEX IF NOT EXISTS uq_examiner_score_receipt ON public.answer_evaluations USING btree (tenant_id, examiner_public_id, request_public_id) WHERE (request_public_id IS NOT NULL)
@@
CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_logs_dedupe_key ON public.notification_logs USING btree (dedupe_key) WHERE (dedupe_key IS NOT NULL)
@@
CREATE UNIQUE INDEX IF NOT EXISTS uq_questions_active_revision_per_source ON public.questions USING btree (supersedes_public_id) WHERE (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING_APPROVAL'::character varying])::text[])) AND (deleted = false))
@@
CREATE UNIQUE INDEX IF NOT EXISTS uq_questions_current_revision_group ON public.questions USING btree (revision_group_public_id) WHERE ((is_current = true) AND (deleted = false))
@@
CREATE UNIQUE INDEX IF NOT EXISTS ux_orders_pending_tenant_plan ON public.orders USING btree (tenant_id, plan_id) WHERE (((status)::text = 'PENDING'::text) AND (deleted = false))
@@
CREATE UNIQUE INDEX IF NOT EXISTS ux_exam_definitions_single_active ON public.exam_definitions USING btree (status) WHERE ((status)::text = 'ACTIVE'::text)
@@
CREATE UNIQUE INDEX IF NOT EXISTS uq_org_code_live ON public.organizations (code) WHERE status <> 'REJECTED'
@@
CREATE UNIQUE INDEX IF NOT EXISTS uq_org_name_live ON public.organizations (name) WHERE status <> 'REJECTED'
@@
CREATE UNIQUE INDEX IF NOT EXISTS uq_org_tax_code_approved ON public.organizations (tax_code) WHERE status IN ('ACTIVE', 'SUSPENDED')
@@
@@
CREATE UNIQUE INDEX IF NOT EXISTS uq_notifications_event_key ON public.notifications (event_key)
@@
CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_recipient_streams_recipient ON public.notification_recipient_streams (recipient_user_public_id)
@@
ALTER TABLE public.notifications ALTER COLUMN public_id SET DEFAULT gen_random_uuid(), ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP, ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP, ALTER COLUMN deleted SET DEFAULT FALSE
@@
ALTER TABLE public.notification_announcements ALTER COLUMN public_id SET DEFAULT gen_random_uuid(), ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP, ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP, ALTER COLUMN deleted SET DEFAULT FALSE
@@
ALTER TABLE public.notification_recipients ALTER COLUMN public_id SET DEFAULT gen_random_uuid(), ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP, ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP, ALTER COLUMN deleted SET DEFAULT FALSE
@@
ALTER TABLE public.notification_recipient_streams ALTER COLUMN public_id SET DEFAULT gen_random_uuid(), ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP, ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP, ALTER COLUMN deleted SET DEFAULT FALSE
@@
ALTER TABLE public.notification_announcements ALTER COLUMN version SET DEFAULT 0
@@
ALTER TABLE public.notification_recipients ALTER COLUMN status SET DEFAULT 'PENDING', ALTER COLUMN attempts SET DEFAULT 0
@@
ALTER TABLE public.notification_recipient_streams ALTER COLUMN last_sequence SET DEFAULT 0, ALTER COLUMN read_revision SET DEFAULT 0
@@
CREATE TABLE IF NOT EXISTS public.notification_inbox_snapshots (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    recipient_user_public_id UUID NOT NULL REFERENCES public.users(public_id),
    tenant_id UUID,
    upper_sequence BIGINT NOT NULL CHECK (upper_sequence >= 0),
    read_revision BIGINT NOT NULL CHECK (read_revision >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL CHECK (expires_at > created_at)
)
@@
CREATE INDEX IF NOT EXISTS idx_inbox_snapshot_owner ON public.notification_inbox_snapshots (recipient_user_public_id, tenant_id, expires_at)
@@
