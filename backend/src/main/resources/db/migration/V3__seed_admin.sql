-- V3: seed the initial administrator.
-- The admin is created here (not via the API) so the role cannot be self-assigned by users.
-- The password is stored as a BCrypt hash (cost 10, matching BCryptPasswordEncoder's default),
-- never in plain text. Idempotent: re-running on an existing DB is a no-op.
--
-- Dev login credentials are documented in .env.example.
-- In production, replace this hash with one generated from a strong password.
-- The email is stored lower-cased to satisfy chk_users_email_lower.

INSERT INTO users (email, password_hash, role)
VALUES (
    'admin@shop.local',
    '$2y$10$RM.kHjiCOMLvZmoc5fA/Hu/NxMleQSxbvtY7V6msDo788VXYs2hSy',
    'ADMIN'
)
ON CONFLICT (email) DO NOTHING;
