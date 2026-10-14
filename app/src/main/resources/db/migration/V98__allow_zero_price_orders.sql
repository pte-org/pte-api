ALTER TABLE orders
    DROP CONSTRAINT chk_orders_amount;

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_amount CHECK (amount >= 0);
