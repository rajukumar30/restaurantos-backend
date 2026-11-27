-- Match the case-insensitive authentication lookup without changing email uniqueness.
CREATE INDEX IF NOT EXISTS idx_app_users_lower_email ON app_users (lower(email));
