CREATE TABLE users (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    google_subject VARCHAR(255) NOT NULL,
    email          VARCHAR(255) NOT NULL,
    nickname       VARCHAR(30)  NOT NULL,
    created_at     DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_google_subject UNIQUE (google_subject),
    CONSTRAINT uk_users_nickname UNIQUE (nickname)
);

CREATE TABLE problems (
    id              BIGINT       NOT NULL,
    title           VARCHAR(100) NOT NULL,
    level           INT          NOT NULL,
    description     TEXT         NOT NULL,
    return_type     VARCHAR(30)  NOT NULL,
    time_limit_ms   INT          NOT NULL DEFAULT 3000,
    memory_limit_mb INT          NOT NULL DEFAULT 265,
    PRIMARY KEY (id),
    CONSTRAINT ck_problems_level CHECK (level BETWEEN 0 AND 5),
    CONSTRAINT ck_problems_return_type CHECK (return_type IN ('int', 'long', 'double', 'boolean', 'String', 'int[]', 'long[]', 'String[]', 'int[][]')),
    CONSTRAINT ck_problems_time_limit_ms CHECK (time_limit_ms > 0),
    CONSTRAINT ck_problems_memory_limit_mb CHECK (memory_limit_mb >= 128)
);

CREATE TABLE problem_parameters (
    problem_id BIGINT      NOT NULL,
    position   INT         NOT NULL,
    name       VARCHAR(50) NOT NULL,
    type       VARCHAR(30) NOT NULL,
    PRIMARY KEY (problem_id, position),
    CONSTRAINT uk_problem_parameters_name UNIQUE (problem_id, name),
    CONSTRAINT fk_problem_parameters_problem FOREIGN KEY (problem_id) REFERENCES problems (id),
    CONSTRAINT ck_problem_parameters_position CHECK (position >= 1),
    CONSTRAINT ck_problem_parameters_type CHECK (type IN ('int', 'long', 'double', 'boolean', 'String', 'int[]', 'long[]', 'String[]', 'int[][]'))
);

CREATE TABLE test_cases (
    id         BIGINT  NOT NULL AUTO_INCREMENT,
    problem_id BIGINT  NOT NULL,
    sequence   INT     NOT NULL,
    hidden     BOOLEAN NOT NULL,
    input      JSON    NOT NULL,
    expected   JSON    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_test_cases_sequence UNIQUE (problem_id, sequence),
    -- 큰 JSON 행(수 MB)을 ORDER BY 로 정렬하면 정렬 버퍼를 넘어 실패하므로, 인덱스 순서로 읽어 정렬 자체를 없앤다.
    INDEX ix_test_cases_order (problem_id, hidden, sequence),
    CONSTRAINT fk_test_cases_problem FOREIGN KEY (problem_id) REFERENCES problems (id)
);

CREATE TABLE submissions (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    problem_id  BIGINT       NOT NULL,
    language    VARCHAR(20)  NOT NULL,
    source_code MEDIUMTEXT   NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    verdict     VARCHAR(3)   NULL,
    score       DECIMAL(4,1) NULL,
    compile_log TEXT         NULL,
    created_at  DATETIME     NOT NULL,
    judged_at   DATETIME     NULL,
    PRIMARY KEY (id),
    INDEX ix_submissions_status (status),
    INDEX ix_submissions_user_problem (user_id, problem_id),
    CONSTRAINT fk_submissions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_submissions_problem FOREIGN KEY (problem_id) REFERENCES problems (id),
    CONSTRAINT ck_submissions_language CHECK (language IN ('JAVA', 'JAVASCRIPT')),
    CONSTRAINT ck_submissions_status CHECK (status IN ('PENDING', 'JUDGING', 'DONE', 'FAILED')),
    CONSTRAINT ck_submissions_verdict CHECK (verdict IN ('AC', 'WA', 'TLE', 'MLE', 'RE', 'CE')),
    CONSTRAINT ck_submissions_score CHECK (score BETWEEN 0 AND 100),
    CONSTRAINT ck_submissions_done_result CHECK (
        (status = 'DONE' AND verdict IS NOT NULL AND score IS NOT NULL AND judged_at IS NOT NULL)
        OR (status <> 'DONE' AND verdict IS NULL AND score IS NULL AND judged_at IS NULL)),
    CONSTRAINT ck_submissions_compile_log CHECK (
        (verdict = 'CE' AND compile_log IS NOT NULL)
        OR ((verdict IS NULL OR verdict <> 'CE') AND compile_log IS NULL))
);

CREATE TABLE submission_test_results (
    submission_id BIGINT     NOT NULL,
    test_case_id  BIGINT     NOT NULL,
    verdict       VARCHAR(3) NOT NULL,
    time_ms       INT        NOT NULL,
    memory_kb     INT        NOT NULL,
    actual        JSON       NULL,
    user_output   TEXT       NULL,
    error_message TEXT       NULL,
    PRIMARY KEY (submission_id, test_case_id),
    CONSTRAINT fk_submission_test_results_submission FOREIGN KEY (submission_id) REFERENCES submissions (id),
    CONSTRAINT fk_submission_test_results_test_case FOREIGN KEY (test_case_id) REFERENCES test_cases (id),
    CONSTRAINT ck_submission_test_results_verdict CHECK (verdict IN ('AC', 'WA', 'TLE', 'MLE', 'RE')),
    CONSTRAINT ck_submission_test_results_usage CHECK (time_ms >= 0 AND memory_kb >= 0)
);

CREATE TABLE user_solved_problems (
    user_id    BIGINT   NOT NULL,
    problem_id BIGINT   NOT NULL,
    solved_at  DATETIME NOT NULL,
    PRIMARY KEY (user_id, problem_id),
    CONSTRAINT fk_user_solved_problems_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_solved_problems_problem FOREIGN KEY (problem_id) REFERENCES problems (id)
);
