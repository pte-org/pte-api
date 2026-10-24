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
CREATE OR REPLACE VIEW public.session_monitoring_enrollment_membership AS
 SELECT e.tenant_id,
    s.public_id AS exam_session_public_id,
    e.student_public_id,
    e.public_id AS enrollment_public_id
   FROM (public.enrollments e
     JOIN public.exam_sessions s ON ((s.id = e.session_id)))
  WHERE ((e.deleted = false) AND (s.deleted = false) AND (e.tenant_id = s.tenant_id))
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
CREATE UNIQUE INDEX IF NOT EXISTS uq_examiner_score_receipt ON public.examiner_answer_scores USING btree (tenant_id, examiner_public_id, request_public_id) WHERE (request_public_id IS NOT NULL)
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
CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_inbox_contents_event_key ON public.notification_inbox_contents (event_key)
@@
CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_inbox_deliveries_content_recipient ON public.notification_inbox_deliveries (content_public_id, recipient_user_public_id)
@@
CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_inbox_streams_recipient ON public.notification_inbox_streams (recipient_user_public_id)
@@
CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_inbox_items_delivery ON public.notification_inbox_items (delivery_public_id)
@@
CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_inbox_items_content_recipient ON public.notification_inbox_items (content_public_id, recipient_user_public_id)
@@
CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_inbox_items_recipient_sequence ON public.notification_inbox_items (recipient_user_public_id, sequence_no)
@@
ALTER TABLE public.notification_inbox_contents ALTER COLUMN public_id SET DEFAULT gen_random_uuid(), ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP, ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP, ALTER COLUMN deleted SET DEFAULT FALSE
@@
ALTER TABLE public.notification_inbox_announcements ALTER COLUMN public_id SET DEFAULT gen_random_uuid(), ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP, ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP, ALTER COLUMN deleted SET DEFAULT FALSE
@@
ALTER TABLE public.notification_inbox_deliveries ALTER COLUMN public_id SET DEFAULT gen_random_uuid(), ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP, ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP, ALTER COLUMN deleted SET DEFAULT FALSE
@@
ALTER TABLE public.notification_inbox_streams ALTER COLUMN public_id SET DEFAULT gen_random_uuid(), ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP, ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP, ALTER COLUMN deleted SET DEFAULT FALSE
@@
ALTER TABLE public.notification_inbox_items ALTER COLUMN public_id SET DEFAULT gen_random_uuid(), ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP, ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP, ALTER COLUMN deleted SET DEFAULT FALSE
@@
ALTER TABLE public.notification_inbox_announcements ALTER COLUMN version SET DEFAULT 0
@@
ALTER TABLE public.notification_inbox_deliveries ALTER COLUMN status SET DEFAULT 'PENDING', ALTER COLUMN attempts SET DEFAULT 0
@@
ALTER TABLE public.notification_inbox_streams ALTER COLUMN last_sequence SET DEFAULT 0, ALTER COLUMN read_revision SET DEFAULT 0
@@
