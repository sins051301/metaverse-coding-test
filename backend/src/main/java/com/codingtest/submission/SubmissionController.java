package com.codingtest.submission;

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

	// JWT 의 subject 에 users.id 를 넣어 발급했다 (TokenService).
	private static long userId(Jwt token) {
		return Long.parseLong(token.getSubject());
	}

}
