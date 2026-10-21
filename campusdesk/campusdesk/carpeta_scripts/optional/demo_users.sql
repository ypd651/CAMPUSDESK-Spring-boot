-- =====================================================================================
-- CampusDesk - demo accounts only (optional, development only)
--
-- Creates one account per role so every profile can be tried. It does NOT create tickets and
-- it can be run any number of times (existing accounts are left untouched).
-- Run it after the backend has started once, so the roles exist (pgAdmin Query Tool is enough).
--
-- All demo accounts share this password:  Demo-Pass-123!
--
--   tech.maria@technova.local       TECHNICIAN
--   tech.carlos@technova.local      TECHNICIAN
--   laura.gomez@technova.local      USER
--   diego.martinez@technova.local   USER
--
-- The administrator is the one defined by ADMIN_EMAIL / ADMIN_PASSWORD in your .env.
-- Delete these accounts (or never load this file) in any real environment.
-- =====================================================================================

INSERT INTO users (full_name, email, password_hash, role_name) VALUES
    ('Maria Torres',   'tech.maria@technova.local',     '$2a$10$8zYbDj5cFONFvtJtavUseOinP/r0DBI.iXPjtqYPGD2PVu9jrO0Ca', 'TECHNICIAN'),
    ('Carlos Rivera',  'tech.carlos@technova.local',    '$2a$10$8zYbDj5cFONFvtJtavUseOinP/r0DBI.iXPjtqYPGD2PVu9jrO0Ca', 'TECHNICIAN'),
    ('Laura Gomez',    'laura.gomez@technova.local',    '$2a$10$8zYbDj5cFONFvtJtavUseOinP/r0DBI.iXPjtqYPGD2PVu9jrO0Ca', 'USER'),
    ('Diego Martinez', 'diego.martinez@technova.local', '$2a$10$8zYbDj5cFONFvtJtavUseOinP/r0DBI.iXPjtqYPGD2PVu9jrO0Ca', 'USER')
ON CONFLICT (email) DO NOTHING;

SELECT full_name, email, role_name FROM users ORDER BY id;
