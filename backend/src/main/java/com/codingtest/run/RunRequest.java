package com.codingtest.run;

import com.codingtest.judge.Language;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RunRequest(
		@NotNull Language language,
		@NotBlank @Size(max = 65_536) String sourceCode) {
}
