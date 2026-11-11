package com.codingtest.run;

import com.codingtest.judge.JudgeAcceptedResponse;
import com.codingtest.judge.SourceCodeRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "코드 실행")
@SecurityRequirement(name = "bearer")
@RestController
public class RunController {

	private final RunService runService;

	public RunController(RunService runService) {
		this.runService = runService;
	}

	@Operation(summary = "코드 실행", description = "예제 테스트만 채점. 접수만 하고 바로 202 를 돌려준다. 결과는 GET /api/runs/{id} 로 확인")
	@PostMapping("/api/problems/{problemId}/runs")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public JudgeAcceptedResponse run(@AuthenticationPrincipal Jwt token, @PathVariable long problemId,
			@Valid @RequestBody SourceCodeRequest request) {
		Run run = runService.run(userId(token), problemId, request);
		return new JudgeAcceptedResponse(run.getId(), run.getStatus());
	}

	@Operation(summary = "실행 결과 조회", description = "본인 실행만 조회할 수 있다. status 가 DONE 이 될 때까지 다시 부른다 (폴링)")
	@GetMapping("/api/runs/{runId}")
	public RunResponse get(@AuthenticationPrincipal Jwt token, @PathVariable long runId) {
		return runService.get(userId(token), runId);
	}

	private static long userId(Jwt token) {
		return Long.parseLong(token.getSubject());
	}

}
