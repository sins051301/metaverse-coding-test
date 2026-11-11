-- [코드 실행] 기록. 제출(submissions)과 분리한 이유는 README 2장 "실행 기록 저장".
-- 이미 적용된 V1 은 고치지 않는다: Flyway 는 적용한 파일의 체크섬을 flyway_schema_history 에 기록해 두고,
-- 파일이 바뀌면 "적용된 것과 다르다"며 서버 시작을 막는다. 그래서 바꿀 내용은 항상 새 번호의 파일로 추가한다.

CREATE TABLE runs (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    problem_id  BIGINT      NOT NULL,
    language    VARCHAR(20) NOT NULL,
    source_code MEDIUMTEXT  NOT NULL,
    status      VARCHAR(20) NOT NULL,
    verdict     VARCHAR(3)  NULL,
    compile_log TEXT        NULL,
    created_at  DATETIME    NOT NULL,
    judged_at   DATETIME    NULL,
    PRIMARY KEY (id),
    -- 서버가 다시 켜질 때 끝나지 않은 실행(PENDING, JUDGING)을 찾아 다시 채점하는 조회용
    INDEX ix_runs_status (status),
    INDEX ix_runs_user_problem (user_id, problem_id),
    CONSTRAINT fk_runs_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_runs_problem FOREIGN KEY (problem_id) REFERENCES problems (id),
    CONSTRAINT ck_runs_language CHECK (language IN ('JAVA', 'JAVASCRIPT')),
    CONSTRAINT ck_runs_status CHECK (status IN ('PENDING', 'JUDGING', 'DONE', 'FAILED')),
    CONSTRAINT ck_runs_verdict CHECK (verdict IN ('AC', 'WA', 'TLE', 'MLE', 'RE', 'CE')),
    -- 채점이 끝난(DONE) 실행만 판정과 완료 시각을 가진다
    CONSTRAINT ck_runs_done_result CHECK (
        (status = 'DONE' AND verdict IS NOT NULL AND judged_at IS NOT NULL)
        OR (status <> 'DONE' AND verdict IS NULL AND judged_at IS NULL)),
    -- 컴파일 로그는 CE 일 때만, CE 면 반드시 있다
    CONSTRAINT ck_runs_compile_log CHECK (
        (verdict = 'CE' AND compile_log IS NOT NULL)
        OR ((verdict IS NULL OR verdict <> 'CE') AND compile_log IS NULL))
);

CREATE TABLE run_test_results (
    run_id        BIGINT     NOT NULL,
    test_case_id  BIGINT     NOT NULL,
    verdict       VARCHAR(3) NOT NULL,
    time_ms       INT        NOT NULL,
    memory_kb     INT        NOT NULL,
    actual        JSON       NULL,
    user_output   TEXT       NULL,
    error_message TEXT       NULL,
    PRIMARY KEY (run_id, test_case_id),
    CONSTRAINT fk_run_test_results_run FOREIGN KEY (run_id) REFERENCES runs (id),
    CONSTRAINT fk_run_test_results_test_case FOREIGN KEY (test_case_id) REFERENCES test_cases (id),
    CONSTRAINT ck_run_test_results_verdict CHECK (verdict IN ('AC', 'WA', 'TLE', 'MLE', 'RE')),
    CONSTRAINT ck_run_test_results_usage CHECK (time_ms >= 0 AND memory_kb >= 0)
);
