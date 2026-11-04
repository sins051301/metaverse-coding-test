package com.codingtest.run;

import com.codingtest.judge.Judge;
import com.codingtest.judge.JudgeMode;
import com.codingtest.problem.Problem;
import com.codingtest.problem.ProblemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "코드 실행")
@SecurityRequirement(name = "bearer")
@RestController
public class RunController {

	private final ProblemService problemService;
	private final Judge judge;

	public RunController(ProblemService problemService, Judge judge) {
		this.problemService = problemService;
		this.judge = judge;
	}

	// 채점은 수 초가 걸리므로 트랜잭션 밖에서 한다. 문제와 테스트는 각각의 짧은 읽기 트랜잭션으로 먼저 읽어 둔다.
	@Operation(summary = "코드 실행", description = "예제 테스트만 채점하고 결과를 바로 돌려준다. 저장하지 않는다.")
	@PostMapping("/api/problems/{problemId}/runs")
	public RunResponse run(@PathVariable long problemId, @Valid @RequestBody RunRequest request) {
		Problem problem = problemService.getProblem(problemId);
		return RunResponse.from(judge.judge(problem, problemService.getTestCases(problemId),
				request.language(), request.sourceCode(), JudgeMode.RUN));
	}

}
