-- V14: Add username column for username-or-email login
ALTER TABLE users ADD COLUMN username VARCHAR(30);
ALTER TABLE users ADD CONSTRAINT uq_users_username UNIQUE (username);
