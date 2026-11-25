DELETE submission_test_results FROM submission_test_results
    JOIN submissions ON submissions.id = submission_test_results.submission_id
    JOIN users ON users.id = submissions.user_id
WHERE users.google_subject LIKE 'load-test-%';

DELETE submissions FROM submissions JOIN users ON users.id = submissions.user_id WHERE users.google_subject LIKE 'load-test-%';
DELETE user_solved_problems FROM user_solved_problems JOIN users ON users.id = user_solved_problems.user_id WHERE users.google_subject LIKE 'load-test-%';
DELETE FROM users WHERE google_subject LIKE 'load-test-%';
