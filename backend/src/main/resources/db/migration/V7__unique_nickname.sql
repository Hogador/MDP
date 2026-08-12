-- MDAOPay Backend — V7 Unique Case-Insensitive Nickname
--
-- Task spec assumed users.nickname, but nickname lives on the `nicknames`
-- table (V1). PostgreSQL does not support expression-based UNIQUE constraints,
-- so enforce LOWER(nickname) uniqueness with a unique index. The existing
-- non-unique idx_nicknames_nickname_lower index (V1) becomes redundant and is
-- dropped. Duplicate case-insensitive nicknames will fail this migration —
-- intended fail-fast, no dedup logic.

CREATE UNIQUE INDEX uq_nicknames_nickname ON nicknames (LOWER(nickname));

DROP INDEX idx_nicknames_nickname_lower;
