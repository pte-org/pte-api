-- Hosts may withdraw their own ticket while it is still OPEN (SupportTicket.close()).
ALTER TABLE support_ticket DROP CONSTRAINT chk_support_ticket_status;

ALTER TABLE support_ticket
    ADD CONSTRAINT chk_support_ticket_status
        CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'));
