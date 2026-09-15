-- Fixes the admin login.
--
-- V2 seeded a BCrypt hash copied from a Spring Security documentation example
-- and labelled it "changeme". It does not decode to "changeme" or to anything
-- else usable, so the admin account could never authenticate.
--
-- V2 is not edited, because it has already run on existing databases and Flyway
-- validates its checksum. This corrects the row in place instead.
--
-- Login after this migration:  admin / changeme
-- Change it before this system issues a real bill.

INSERT INTO users (username, password_hash, display_name, role, active)
VALUES ('admin',
        '$2a$10$tr0SEoN/zPz59xa68PwwPeXMygy5yQ//qcxj1298aYBPh52/uYnLC',
        'Administrator',
        'ADMIN',
        TRUE)
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    role          = VALUES(role),
    active        = VALUES(active);
