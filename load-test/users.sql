INSERT IGNORE INTO users (google_subject, email, nickname, created_at)
WITH RECURSIVE sequence (number) AS (
    SELECT 1
    UNION ALL
    SELECT number + 1 FROM sequence WHERE number < 1000
)
SELECT CONCAT('load-test-', number), CONCAT('load-test-', number, '@example.invalid'), CONCAT('load-test-', number), UTC_TIMESTAMP()
FROM sequence;

SET SESSION group_concat_max_len = 100000;
SELECT GROUP_CONCAT(id ORDER BY id) FROM users WHERE google_subject LIKE 'load-test-%';
