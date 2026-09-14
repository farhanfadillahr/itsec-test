-- Bootstrap account. There is no other way to get a SUPER_ADMIN into a fresh
-- database, since registration always yields a VIEWER.
--
--   username : superadmin
--   email    : superadmin@itsec-test.local
--   password : SuperAdmin#2026
--
-- Change the password immediately outside a local demo. MFA is switched off on
-- this one account so a reviewer can obtain a token without configuring SMTP.
INSERT INTO users (fullname, username, email, password, role, status, mfa_enabled)
VALUES (
    'Library Super Admin',
    'superadmin',
    'superadmin@itsec-test.local',
    '$2y$10$fSp.TXAF2udybq.nh1333OHNbVVDfyMcuOHHXd/Ox/wQQhQ6r/kJG',
    'SUPER_ADMIN',
    'ACTIVE',
    FALSE
);
