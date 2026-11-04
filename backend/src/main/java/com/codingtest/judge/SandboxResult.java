package com.codingtest.judge;

import java.util.List;

public record SandboxResult(int compileExitCode, String compileLog, List<TestRunResult> testResults) {

	public boolean compiled() {
		return compileExitCode == 0;
	}

}
