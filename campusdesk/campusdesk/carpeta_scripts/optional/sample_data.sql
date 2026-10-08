-- =====================================================================================
-- CampusDesk - demo data (optional)
--
-- Run it AFTER the backend has started once (the backend creates the roles and the first
-- administrator from your environment variables):
--   psql -d campusdesk -f database/sample_data.sql
--
-- Demo accounts (fake users, development only). All of them share this demo password:
--   Demo-Pass-123!
--   tech.maria@technova.local   TECHNICIAN      tech.carlos@technova.local  TECHNICIAN
--   laura.gomez@technova.local  USER            diego.martinez@technova.local  USER
-- Delete them (or never load this file) in any real environment.
-- =====================================================================================

DO $$
DECLARE
    admin_id BIGINT;
    maria    BIGINT;
    carlos   BIGINT;
    laura    BIGINT;
    diego    BIGINT;
    t        BIGINT;
BEGIN
    SELECT id INTO admin_id FROM users WHERE role_name = 'ADMIN' ORDER BY id LIMIT 1;
    IF admin_id IS NULL THEN
        RAISE EXCEPTION 'No administrator found. Start the backend once so it creates the first administrator.';
    END IF;
    IF EXISTS (SELECT 1 FROM tickets) THEN
        RAISE NOTICE 'Tickets already exist; sample data was not loaded.';
        RETURN;
    END IF;

    INSERT INTO users (full_name, email, password_hash, role_name) VALUES
        ('Maria Torres',  'tech.maria@technova.local',     '$2a$10$8zYbDj5cFONFvtJtavUseOinP/r0DBI.iXPjtqYPGD2PVu9jrO0Ca', 'TECHNICIAN'),
        ('Carlos Rivera', 'tech.carlos@technova.local',    '$2a$10$8zYbDj5cFONFvtJtavUseOinP/r0DBI.iXPjtqYPGD2PVu9jrO0Ca', 'TECHNICIAN'),
        ('Laura Gomez',   'laura.gomez@technova.local',    '$2a$10$8zYbDj5cFONFvtJtavUseOinP/r0DBI.iXPjtqYPGD2PVu9jrO0Ca', 'USER'),
        ('Diego Martinez','diego.martinez@technova.local', '$2a$10$8zYbDj5cFONFvtJtavUseOinP/r0DBI.iXPjtqYPGD2PVu9jrO0Ca', 'USER')
    ON CONFLICT (email) DO NOTHING;

    SELECT id INTO maria  FROM users WHERE email = 'tech.maria@technova.local';
    SELECT id INTO carlos FROM users WHERE email = 'tech.carlos@technova.local';
    SELECT id INTO laura  FROM users WHERE email = 'laura.gomez@technova.local';
    SELECT id INTO diego  FROM users WHERE email = 'diego.martinez@technova.local';

    -- 1. OPEN
    INSERT INTO tickets (title, description, category, priority, status, requester_id, created_at, updated_at)
    VALUES ('Laptop does not boot after the update', 'The laptop shows a black screen right after the Windows update finished last night.',
            'HARDWARE', 'HIGH', 'OPEN', laura, NOW() - INTERVAL '2 hours', NOW() - INTERVAL '2 hours') RETURNING id INTO t;
    INSERT INTO status_history (ticket_id, previous_status, new_status, changed_by_id, changed_at, note)
    VALUES (t, NULL, 'OPEN', laura, NOW() - INTERVAL '2 hours', 'Ticket created');

    -- 2. OPEN (critical)
    INSERT INTO tickets (title, description, category, priority, status, requester_id, created_at, updated_at)
    VALUES ('Printer on the 3rd floor is offline', 'Nobody on the 3rd floor can print. The printer shows an error code E-503.',
            'HARDWARE', 'CRITICAL', 'OPEN', diego, NOW() - INTERVAL '1 hour', NOW() - INTERVAL '1 hour') RETURNING id INTO t;
    INSERT INTO status_history (ticket_id, previous_status, new_status, changed_by_id, changed_at, note)
    VALUES (t, NULL, 'OPEN', diego, NOW() - INTERVAL '1 hour', 'Ticket created');

    -- 3. ASSIGNED
    INSERT INTO tickets (title, description, category, priority, status, requester_id, technician_id, created_at, updated_at)
    VALUES ('VPN disconnects every few minutes', 'Working from home, the corporate VPN drops about every 5 minutes and I have to log in again.',
            'NETWORK', 'HIGH', 'ASSIGNED', diego, maria, NOW() - INTERVAL '1 day', NOW() - INTERVAL '20 hours') RETURNING id INTO t;
    INSERT INTO status_history (ticket_id, previous_status, new_status, changed_by_id, changed_at, note) VALUES
        (t, NULL, 'OPEN', diego, NOW() - INTERVAL '1 day', 'Ticket created'),
        (t, 'OPEN', 'ASSIGNED', admin_id, NOW() - INTERVAL '20 hours', 'Assigned to Maria Torres');

    -- 4. IN_PROGRESS with comments
    INSERT INTO tickets (title, description, category, priority, status, requester_id, technician_id, created_at, updated_at)
    VALUES ('Outlook crashes when opening attachments', 'Outlook closes by itself every time I open a PDF attachment. It started this week.',
            'SOFTWARE', 'MEDIUM', 'IN_PROGRESS', laura, maria, NOW() - INTERVAL '2 days', NOW() - INTERVAL '1 day') RETURNING id INTO t;
    INSERT INTO status_history (ticket_id, previous_status, new_status, changed_by_id, changed_at, note) VALUES
        (t, NULL, 'OPEN', laura, NOW() - INTERVAL '2 days', 'Ticket created'),
        (t, 'OPEN', 'ASSIGNED', admin_id, NOW() - INTERVAL '47 hours', 'Assigned to Maria Torres'),
        (t, 'ASSIGNED', 'IN_PROGRESS', maria, NOW() - INTERVAL '1 day', 'Reproduced the crash, testing a repair');
    INSERT INTO comments (content, ticket_id, author_id, created_at) VALUES
        ('I reproduced the problem. The PDF preview add-in is the cause. I am disabling it.', t, maria, NOW() - INTERVAL '23 hours'),
        ('Thanks! Let me know when I can test again.', t, laura, NOW() - INTERVAL '22 hours');

    -- 5. RESOLVED
    INSERT INTO tickets (title, description, category, priority, status, requester_id, technician_id, created_at, updated_at)
    VALUES ('Cannot open the shared finance folder', 'I get "access denied" when opening the Finance shared folder since my team change.',
            'ACCESS', 'HIGH', 'RESOLVED', diego, carlos, NOW() - INTERVAL '3 days', NOW() - INTERVAL '5 hours') RETURNING id INTO t;
    INSERT INTO status_history (ticket_id, previous_status, new_status, changed_by_id, changed_at, note) VALUES
        (t, NULL, 'OPEN', diego, NOW() - INTERVAL '3 days', 'Ticket created'),
        (t, 'OPEN', 'ASSIGNED', admin_id, NOW() - INTERVAL '70 hours', 'Assigned to Carlos Rivera'),
        (t, 'ASSIGNED', 'IN_PROGRESS', carlos, NOW() - INTERVAL '60 hours', NULL),
        (t, 'IN_PROGRESS', 'RESOLVED', carlos, NOW() - INTERVAL '5 hours', 'Added Diego to the Finance group');
    INSERT INTO comments (content, ticket_id, author_id, created_at) VALUES
        ('Your account was added to the Finance group. Please sign out and in again, then confirm.', t, carlos, NOW() - INTERVAL '5 hours');

    -- 6. CLOSED
    INSERT INTO tickets (title, description, category, priority, status, requester_id, technician_id, created_at, updated_at)
    VALUES ('Monitor flickers on the left display', 'The left monitor flickers every few seconds, mostly when I use the video call app.',
            'HARDWARE', 'LOW', 'CLOSED', laura, carlos, NOW() - INTERVAL '6 days', NOW() - INTERVAL '4 days') RETURNING id INTO t;
    INSERT INTO status_history (ticket_id, previous_status, new_status, changed_by_id, changed_at, note) VALUES
        (t, NULL, 'OPEN', laura, NOW() - INTERVAL '6 days', 'Ticket created'),
        (t, 'OPEN', 'ASSIGNED', admin_id, NOW() - INTERVAL '5 days 20 hours', 'Assigned to Carlos Rivera'),
        (t, 'ASSIGNED', 'IN_PROGRESS', carlos, NOW() - INTERVAL '5 days', NULL),
        (t, 'IN_PROGRESS', 'RESOLVED', carlos, NOW() - INTERVAL '4 days 3 hours', 'Replaced the HDMI cable'),
        (t, 'RESOLVED', 'CLOSED', laura, NOW() - INTERVAL '4 days', 'Works fine now');
END
$$;
