package com.codingtest.run;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RunTestResultRepository extends JpaRepository<RunTestResult, RunTestResult.Id> {

	List<RunTestResult> findByIdRunId(long runId);

}
