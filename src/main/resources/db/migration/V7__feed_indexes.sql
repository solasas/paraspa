-- Feeds: newest posts first across all authors.
--
-- GET /api/feed filters "author IN (people I follow + me)" and pages with a (created_at, id) cursor. With this
-- index Postgres walks posts newest-first and stops as soon as it has a page, so a page costs about the same
-- whether you follow 20 people or 300 and whether it is the first page or the hundredth (measured at 300k posts:
-- 0.6-3.5 ms per page; without it, 15-17 ms for heavy followers).
-- It also serves the 7-day window scan of GET /api/explore.
-- The per-author index idx_posts_author_created_id (V5) stays for profile post lists.
CREATE INDEX IF NOT EXISTS idx_posts_created_id ON posts (created_at DESC, id DESC);
