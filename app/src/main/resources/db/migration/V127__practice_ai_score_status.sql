alter table practice_session_items
    add column if not exists ai_score_status varchar(32);
