package com.codingtest.submission;

import com.codingtest.judge.JudgeAcceptedResponse;
import com.codingtest.judge.SourceCodeRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "제출")
@SecurityRequirement(name = "bearer")
@RestController
public class SubmissionController {

	private final SubmissionService submissionService;

	public SubmissionController(SubmissionService submissionService) {
		this.submissionService = submissionService;
	}

	// 202 Accepted: "요청은 받았고 처리는 아직"이라는 뜻의 상태 코드. 처리 결과는 아래 조회 API 로 확인한다.
	@Operation(summary = "제출", description = "예제 + 히든 테스트 전부 채점. 접수만 하고 바로 202 를 돌려준다. 결과는 GET /api/submissions/{id} 로 확인")
	@PostMapping("/api/problems/{problemId}/submissions")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public JudgeAcceptedResponse submit(@AuthenticationPrincipal Jwt token, @PathVariable long problemId,
			@Valid @RequestBody SourceCodeRequest request) {
		Submission submission = submissionService.submit(userId(token), problemId, request);
		return new JudgeAcceptedResponse(submission.getId(), submission.getStatus());
	}

	@Operation(summary = "제출 결과 조회", description = "본인 제출만 조회할 수 있다. status 가 DONE 이 될 때까지 1초쯤 간격으로 다시 부른다 (폴링)")
	@GetMapping("/api/submissions/{submissionId}")
	public SubmissionResponse get(@AuthenticationPrincipal Jwt token, @PathVariable long submissionId) {
		return submissionService.get(userId(token), submissionId);
	}

	// @Min·@Max 를 파라미터에 바로 붙이면 Spring MVC(6.1+)가 메서드 검증을 해서, 범위를 벗어나면 400 을 돌려준다.
	// size 상한은 한 번에 너무 많은 행을 읽지 않게 하려는 것이다.
	@Operation(summary = "제출 내역", description = "이 문제에 낸 내 제출 목록, 최신순. 소스 코드·테스트별 결과는 빠져 있다 (GET /api/submissions/{id} 로 확인). hasNext 가 true 면 page+1 로 다음 페이지를 부른다")
	@GetMapping("/api/problems/{problemId}/submissions")
	public SubmissionHistoryResponse history(@AuthenticationPrincipal Jwt token, @PathVariable long problemId,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
		return submissionService.getHistory(userId(token), problemId, page, size);
	}

	// JWT 의 subject 에 users.id 를 넣어 발급했다 (TokenService).
	private static long userId(Jwt token) {
		return Long.parseLong(token.getSubject());
	}

}
