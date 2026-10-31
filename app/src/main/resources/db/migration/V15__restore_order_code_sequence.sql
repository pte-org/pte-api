-- Native order-code allocation is not part of Hibernate's entity schema.
-- Restore the sequence omitted from the consolidated baseline without changing V1.
CREATE SEQUENCE IF NOT EXISTS public.order_code_seq
    AS bigint INCREMENT BY 1 START WITH 1000000000;

LOCK TABLE public.orders IN SHARE ROW EXCLUSIVE MODE;
-- ALTER holds a sequence lock until commit, so concurrent nextval calls cannot
-- allocate a value between reading its state and selecting the restart value.
ALTER SEQUENCE public.order_code_seq INCREMENT BY 1;

DO $migration$
DECLARE
    sequence_value bigint;
    sequence_called boolean;
    highest_order_code bigint;
BEGIN
    SELECT last_value, is_called INTO sequence_value, sequence_called
        FROM public.order_code_seq;
    SELECT max(order_code) INTO highest_order_code FROM public.orders;

    -- Preserve the next unused value on upgraded/repaired databases, even if
    -- an earlier allocation was rolled back before an order could be saved.
    EXECUTE format('ALTER SEQUENCE public.order_code_seq RESTART WITH %s',
        greatest(1000000000::bigint,
                 coalesce(highest_order_code + 1, 1000000000::bigint),
                 sequence_value + CASE WHEN sequence_called THEN 1 ELSE 0 END));
END $migration$;
