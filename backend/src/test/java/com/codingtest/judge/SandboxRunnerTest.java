package com.codingtest.judge;

import com.github.dockerjava.api.DockerClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 실제 Docker 컨테이너를 띄우는 테스트라 일반 test 태스크에서는 제외된다. ./gradlew judgeTest 로 실행한다.
 */
@Tag("docker")
class SandboxRunnerTest {

	private static final List<String> INPUTS = List.of("[1, 2]", "[10, 20]", "[3, 4]");
	private static final List<String> NO_ARGUMENTS = List.of("[]");
	private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

	private static DockerClient dockerClient;
	private static SandboxRunner sandboxRunner;

	@BeforeAll
	static void setUp() {
		dockerClient = new DockerClientConfiguration().dockerClient();
		sandboxRunner = new SandboxRunner(dockerClient);
	}

	@AfterAll
	static void tearDown() throws IOException {
		dockerClient.close();
	}

	private static JsonNode json(String text) {
		return JSON_MAPPER.readTree(text);
	}

	@Nested
	@DisplayName("Java")
	class JavaLanguage {

		@Test
		@DisplayName("solution 을 테스트 입력마다 호출하고 반환값을 돌려준다")
		void runsAllTests() {
			SandboxResult result = sandboxRunner.run(Language.JAVA, """
					class Solution {
					    public int solution(int a, int b) {
					        return a + b;
					    }
					}
					""", INPUTS);

			assertThat(result.compiled()).isTrue();
			assertThat(result.testResults())
					.extracting(TestRunResult::exitCode, TestRunResult::returnValue)
					.containsExactly(
							tuple(0, json("3")),
							tuple(0, json("30")),
							tuple(0, json("7")));
			assertThat(result.testResults()).allSatisfy(testRunResult -> {
				assertThat(testRunResult.timeSeconds()).isLessThan(3);
				assertThat(testRunResult.memoryKb()).isPositive();
			});
		}

		@Test
		@DisplayName("지원하는 9개 타입이 JSON에서 인자로 변환되고, 반환값도 같은 JSON으로 돌아온다")
		void convertsSupportedTypes() {
			String values = """
					[7, 9007199254740993, 1.5, true, "가 \\"따옴표\\"\\n줄", [1, 2], [10000000000], ["a", "b"], [[1, 2], [3]]]""";
			SandboxResult result = sandboxRunner.run(Language.JAVA, """
					class Solution {
					    Object[] solution(int number, long bigNumber, double ratio, boolean flag, String text,
					                      int[] numbers, long[] bigNumbers, String[] words, int[][] grid) {
					        return new Object[]{number, bigNumber, ratio, flag, text, numbers, bigNumbers, words, grid};
					    }
					}
					""", List.of(values));

			assertThat(result.testResults().getFirst().returnValue()).isEqualTo(json(values));
		}

		@Test
		@DisplayName("유저의 System.out 출력은 반환값과 섞이지 않고 userOutput 으로 따로 모인다")
		void separatesUserOutput() {
			SandboxResult result = sandboxRunner.run(Language.JAVA, """
					class Solution {
					    int solution(int a, int b) {
					        System.out.println("디버그 " + a);
					        return a * b;
					    }
					}
					""", List.of("[2, 5]"));

			TestRunResult testRunResult = result.testResults().getFirst();
			assertThat(testRunResult.returnValue()).isEqualTo(json("10"));
			assertThat(testRunResult.userOutput()).isEqualTo("디버그 2\n");
		}

		@Test
		@DisplayName("solution 메서드가 없으면 런타임 에러로 끝난다")
		void missingSolutionMethodFails() {
			SandboxResult result = sandboxRunner.run(Language.JAVA, """
					class Solution {
					    int answer(int a, int b) {
					        return a + b;
					    }
					}
					""", INPUTS);

			assertThat(result.testResults()).hasSize(3).allSatisfy(testRunResult -> {
				assertThat(testRunResult.exitCode()).isEqualTo(1);
				assertThat(testRunResult.returnValue()).isNull();
				assertThat(testRunResult.stderr()).contains("solution 메서드가 정확히 하나");
			});
		}

		@Test
		@DisplayName("지원하지 않는 파라미터·반환 타입은 런타임 에러로 끝난다")
		void unsupportedTypesFail() {
			SandboxResult parameterResult = sandboxRunner.run(Language.JAVA, """
					import java.util.*;
					class Solution {
					    int solution(List<Integer> numbers) {
					        return 0;
					    }
					}
					""", List.of("[[1, 2]]"));
			SandboxResult returnResult = sandboxRunner.run(Language.JAVA, """
					import java.util.*;
					class Solution {
					    List<Integer> solution() {
					        return List.of(1, 2);
					    }
					}
					""", NO_ARGUMENTS);

			assertThat(parameterResult.testResults().getFirst().stderr()).contains("지원하지 않는 파라미터 타입");
			assertThat(returnResult.testResults().getFirst().stderr()).contains("지원하지 않는 반환 타입");
		}

		@Test
		@DisplayName("무한 반복은 테스트마다 3초에 강제 종료되고, 남은 테스트도 모두 실행한다")
		void infiniteLoopIsKilledAfterTimeLimit() {
			SandboxResult result = sandboxRunner.run(Language.JAVA, """
					class Solution {
					    int solution(int a, int b) {
					        while (true) {
					        }
					    }
					}
					""", INPUTS);

			assertThat(result.testResults()).hasSize(3).allSatisfy(testRunResult -> {
				assertThat(testRunResult.exitCode()).isEqualTo(137);
				assertThat(testRunResult.timeSeconds()).isGreaterThanOrEqualTo(2.9);
				assertThat(testRunResult.oomKilled()).isFalse();
				assertThat(testRunResult.returnValue()).isNull();
			});
		}

		@Test
		@DisplayName("메모리를 계속 할당하면 OutOfMemoryError 로 종료되고, 남은 테스트도 모두 실행한다")
		void memoryExhaustionStopsWithOutOfMemoryError() {
			SandboxResult result = sandboxRunner.run(Language.JAVA, """
					import java.util.*;
					class Solution {
					    int solution(int a, int b) {
					        List<long[]> blocks = new ArrayList<>();
					        while (true) {
					            blocks.add(new long[1 << 20]);
					        }
					    }
					}
					""", INPUTS);

			assertThat(result.testResults()).hasSize(3).allSatisfy(testRunResult -> {
				assertThat(testRunResult.exitCode()).isNotZero();
				assertThat(testRunResult.stderr()).contains("java.lang.OutOfMemoryError");
			});
		}

		@Test
		@DisplayName("런타임 예외는 비정상 종료 코드와 유저 코드 위치가 담긴 에러 메시지를 남기고, 남은 테스트도 모두 실행한다")
		void runtimeExceptionReportsError() {
			SandboxResult result = sandboxRunner.run(Language.JAVA, """
					class Solution {
					    int solution(int a, int b) {
					        int[] numbers = new int[1];
					        return numbers[5];
					    }
					}
					""", INPUTS);

			assertThat(result.testResults()).hasSize(3).allSatisfy(testRunResult -> {
				assertThat(testRunResult.exitCode()).isEqualTo(1);
				assertThat(testRunResult.stderr()).contains("ArrayIndexOutOfBoundsException", "Solution.java:4");
			});
		}

		@Test
		@DisplayName("컴파일 에러는 유저 파일 기준 로그만 남기고 테스트를 실행하지 않는다")
		void compileErrorSkipsAllTests() {
			SandboxResult result = sandboxRunner.run(Language.JAVA, """
					class Solution {
					    int solution(int a, int b) {
					        int number = "text"
					    }
					}
					""", INPUTS);

			assertThat(result.compiled()).isFalse();
			assertThat(result.compileLog()).contains("Solution.java:3", "error");
			assertThat(result.testResults()).isEmpty();
		}

		@Test
		@DisplayName("깊이 20만의 재귀도 스택 오버플로 없이 실행된다")
		void deepRecursionDoesNotOverflow() {
			SandboxResult result = sandboxRunner.run(Language.JAVA, """
					class Solution {
					    int depth(int remaining) {
					        return remaining == 0 ? 0 : 1 + depth(remaining - 1);
					    }
					    int solution() {
					        return depth(200000);
					    }
					}
					""", NO_ARGUMENTS);

			assertThat(result.testResults().getFirst().returnValue()).isEqualTo(json("200000"));
		}

		@Test
		@DisplayName("문제별 시간 제한을 넘으면 강제 종료되고, 기본 제한(3초) 안이면 정상 종료된다")
		void appliesProblemTimeLimit() {
			String sourceCode = """
					class Solution {
					    String solution() throws InterruptedException {
					        Thread.sleep(1500);
					        return "done";
					    }
					}
					""";

			SandboxResult strictResult = sandboxRunner.run(Language.JAVA, sourceCode, NO_ARGUMENTS,
					new ExecutionLimits(Duration.ofSeconds(1), 265));
			SandboxResult defaultResult = sandboxRunner.run(Language.JAVA, sourceCode, NO_ARGUMENTS);

			assertThat(strictResult.testResults().getFirst().exitCode()).isEqualTo(137);
			assertThat(defaultResult.testResults().getFirst().exitCode()).isZero();
			assertThat(defaultResult.testResults().getFirst().returnValue()).isEqualTo(json("\"done\""));
		}

		@Test
		@DisplayName("문제별 메모리 제한이 낮으면 기본 제한(265MB)에서 통과하던 할당이 메모리 에러가 된다")
		void appliesProblemMemoryLimit() {
			String sourceCode = """
					class Solution {
					    int solution() {
					        byte[] block = new byte[120 * 1024 * 1024];
					        return block.length;
					    }
					}
					""";

			SandboxResult strictResult = sandboxRunner.run(Language.JAVA, sourceCode, NO_ARGUMENTS,
					new ExecutionLimits(Duration.ofSeconds(3), 128));
			SandboxResult defaultResult = sandboxRunner.run(Language.JAVA, sourceCode, NO_ARGUMENTS);

			assertThat(strictResult.compiled()).isTrue();
			assertThat(strictResult.testResults().getFirst().stderr()).contains("java.lang.OutOfMemoryError");
			assertThat(defaultResult.testResults().getFirst().exitCode()).isZero();
		}

		@Test
		@DisplayName("네트워크·결과 조작·채점 프로세스 접근·시스템 파일 쓰기·프로세스 무한 생성이 모두 차단된다")
		void sandboxEscapeAttemptsAreBlocked() {
			SandboxResult result = sandboxRunner.run(Language.JAVA, """
					import java.net.Socket;
					import java.nio.file.Files;
					import java.nio.file.Path;
					class Solution {
					    int solution() {
					        try { new Socket("8.8.8.8", 53).close(); System.out.println("NET:OPEN"); }
					        catch (Exception exception) { System.out.println("NET:BLOCKED"); }
					        try { Files.writeString(Path.of("/tmp/result/1.meta"), "exit=0"); System.out.println("RESULT:WRITTEN"); }
					        catch (Exception exception) { System.out.println("RESULT:BLOCKED"); }
					        try { Files.writeString(Path.of("/proc/1/fd/3"), "FORGED"); System.out.println("FD3:WRITTEN"); }
					        catch (Exception exception) { System.out.println("FD3:BLOCKED"); }
					        try { Files.readString(Path.of("/proc/1/environ")); System.out.println("PROC1:READ"); }
					        catch (Exception exception) { System.out.println("PROC1:BLOCKED"); }
					        try { Files.writeString(Path.of("/etc/escape"), "x"); System.out.println("ROOTFS:WRITTEN"); }
					        catch (Exception exception) { System.out.println("ROOTFS:BLOCKED"); }
					        try { for (int count = 0; count < 1000; count++) new ProcessBuilder("sleep", "100").start(); System.out.println("FORK:UNLIMITED"); }
					        catch (Exception exception) { System.out.println("FORK:LIMITED"); }
					        return 0;
					    }
					}
					""", NO_ARGUMENTS);

			assertThat(result.testResults()).hasSize(1);
			assertThat(result.testResults().getFirst().userOutput().lines()).containsExactly(
					"NET:BLOCKED", "RESULT:BLOCKED", "FD3:BLOCKED", "PROC1:BLOCKED", "ROOTFS:BLOCKED", "FORK:LIMITED");
		}

	}

	@Nested
	@DisplayName("JavaScript")
	class JavaScriptLanguage {

		@Test
		@DisplayName("solution 을 테스트 입력마다 호출하고 반환값을 돌려준다")
		void runsAllTests() {
			SandboxResult result = sandboxRunner.run(Language.JAVASCRIPT, """
					function solution(a, b) {
					    return a + b;
					}
					""", INPUTS);

			assertThat(result.compiled()).isTrue();
			assertThat(result.testResults())
					.extracting(TestRunResult::exitCode, TestRunResult::returnValue)
					.containsExactly(
							tuple(0, json("3")),
							tuple(0, json("30")),
							tuple(0, json("7")));
		}

		@Test
		@DisplayName("const 화살표 함수로 선언한 solution 도 호출된다")
		void supportsArrowFunction() {
			SandboxResult result = sandboxRunner.run(Language.JAVASCRIPT, "const solution = (a, b) => a * b;\n", List.of("[2, 5]"));

			assertThat(result.testResults().getFirst().returnValue()).isEqualTo(json("10"));
		}

		@Test
		@DisplayName("지원하는 타입이 JSON에서 인자로 변환되고, 반환값도 같은 JSON으로 돌아온다")
		void convertsSupportedTypes() {
			String values = """
					[7, 10000000000, 1.5, true, "가 \\"따옴표\\"\\n줄", [1, 2], [10000000000], ["a", "b"], [[1, 2], [3]]]""";
			SandboxResult result = sandboxRunner.run(Language.JAVASCRIPT, """
					function solution(...values) {
					    return values;
					}
					""", List.of(values));

			assertThat(result.testResults().getFirst().returnValue()).isEqualTo(json(values));
		}

		@Test
		@DisplayName("유저의 console.log 출력은 반환값과 섞이지 않고 userOutput 으로 따로 모인다")
		void separatesUserOutput() {
			SandboxResult result = sandboxRunner.run(Language.JAVASCRIPT, """
					function solution(a, b) {
					    console.log('디버그', a);
					    return a * b;
					}
					""", List.of("[2, 5]"));

			TestRunResult testRunResult = result.testResults().getFirst();
			assertThat(testRunResult.returnValue()).isEqualTo(json("10"));
			assertThat(testRunResult.userOutput()).isEqualTo("디버그 2\n");
		}

		@Test
		@DisplayName("solution 함수가 없으면 런타임 에러로 끝난다")
		void missingSolutionFunctionFails() {
			SandboxResult result = sandboxRunner.run(Language.JAVASCRIPT, "function answer(a, b) { return a + b; }\n", INPUTS);

			assertThat(result.testResults()).hasSize(3).allSatisfy(testRunResult -> {
				assertThat(testRunResult.exitCode()).isEqualTo(1);
				assertThat(testRunResult.returnValue()).isNull();
				assertThat(testRunResult.stderr()).contains("solution 함수가 없습니다");
			});
		}

		@Test
		@DisplayName("무한 반복은 테스트마다 3초에 강제 종료되고, 남은 테스트도 모두 실행한다")
		void infiniteLoopIsKilledAfterTimeLimit() {
			SandboxResult result = sandboxRunner.run(Language.JAVASCRIPT, "function solution(a, b) { while (true) {} }\n", INPUTS);

			assertThat(result.testResults()).hasSize(3).allSatisfy(testRunResult -> {
				assertThat(testRunResult.exitCode()).isEqualTo(137);
				assertThat(testRunResult.timeSeconds()).isGreaterThanOrEqualTo(2.9);
			});
		}

		@Test
		@DisplayName("메모리를 계속 할당하면 힙 부족 에러를 남기고, 남은 테스트도 모두 실행한다")
		void memoryExhaustionReportsHeapOutOfMemory() {
			SandboxResult result = sandboxRunner.run(Language.JAVASCRIPT, """
					function solution(a, b) {
					    const blocks = [];
					    while (true) blocks.push(new Array(1e6).fill(1.5));
					}
					""", INPUTS);

			assertThat(result.testResults()).hasSize(3).allSatisfy(testRunResult -> {
				assertThat(testRunResult.exitCode()).isNotZero();
				assertThat(testRunResult.stderr()).contains("heap out of memory");
			});
		}

		@Test
		@DisplayName("런타임 예외는 비정상 종료 코드와 유저 코드 위치가 담긴 에러 메시지를 남기고, 남은 테스트도 모두 실행한다")
		void runtimeExceptionReportsError() {
			SandboxResult result = sandboxRunner.run(Language.JAVASCRIPT, """
					function solution(a, b) {
					    return null.property;
					}
					""", INPUTS);

			assertThat(result.testResults()).hasSize(3).allSatisfy(testRunResult -> {
				assertThat(testRunResult.exitCode()).isEqualTo(1);
				assertThat(testRunResult.stderr()).contains("TypeError", "solution.js:2");
			});
		}

		@Test
		@DisplayName("문법 에러는 컴파일 단계(node --check)에서 걸러지고 테스트를 실행하지 않는다")
		void syntaxErrorSkipsAllTests() {
			SandboxResult result = sandboxRunner.run(Language.JAVASCRIPT, "const value = ;\n", INPUTS);

			assertThat(result.compiled()).isFalse();
			assertThat(result.compileLog()).contains("SyntaxError");
			assertThat(result.testResults()).isEmpty();
		}

	}

}
