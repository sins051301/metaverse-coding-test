package com.codingtest.judge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * [코드 실행]과 [제출]의 요청 본문. 검증에 실패하면 채점을 시작하지 않고 400 으로 답한다.
 */
public record SourceCodeRequest(
		@NotNull Language language,
		@NotBlank @Size(max = 65_536) String sourceCode) {
}
