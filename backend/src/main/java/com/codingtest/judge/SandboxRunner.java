package com.codingtest.judge;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.api.model.Ulimit;
import com.github.dockerjava.api.model.Volume;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 유저의 solution 코드를 채점 이미지(docker/judge) 컨테이너에서 실행하고, 테스트별 실행 결과를 돌려준다.
 * inputs 는 테스트마다 solution 에 넘길 인자 JSON 배열이다 (예: "[[1,2,3],5]"). 정답 비교(AC/WA 판정)는 하지 않는다.
 */
@Component
public class SandboxRunner {

	private static final long ONE_CPU_IN_NANOS = 1_000_000_000L;
	private static final long PIDS_LIMIT = 128L;
	private static final String TMPFS_OPTIONS = "rw,noexec,nosuid,nodev,size=32m,mode=1777";
	private static final Duration COMPILE_TIMEOUT = Duration.ofSeconds(10);
	private static final Duration CONTAINER_OVERHEAD = Duration.ofSeconds(15);
	private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

	private final DockerClient dockerClient;

	public SandboxRunner(DockerClient dockerClient) {
		this.dockerClient = dockerClient;
	}

	public SandboxResult run(Language language, String sourceCode, List<String> inputs) {
		return run(language, sourceCode, inputs, ExecutionLimits.DEFAULT);
	}

	public SandboxResult run(Language language, String sourceCode, List<String> inputs, ExecutionLimits limits) {
		Path inputDirectory = writeInputDirectory(language, sourceCode, inputs);
		try {
			String containerId = createContainer(language, inputDirectory, limits);
			try {
				byte[] resultArchive = startAndCollectStdout(containerId, containerTimeout(limits, inputs.size()));
				return toResult(extractFiles(resultArchive), inputs.size());
			} finally {
				dockerClient.removeContainerCmd(containerId).withForce(true).exec();
			}
		} finally {
			deleteDirectory(inputDirectory);
		}
	}

	private Path writeInputDirectory(Language language, String sourceCode, List<String> inputs) {
		try {
			Path directory = Files.createTempDirectory("judge-");
			Files.writeString(directory.resolve(language.sourceFileName()), sourceCode);
			Path testsDirectory = Files.createDirectory(directory.resolve("tests"));
			for (int index = 0; index < inputs.size(); index++) {
				Files.writeString(testsDirectory.resolve((index + 1) + ".in"), inputs.get(index));
			}
			return directory;
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	private String createContainer(Language language, Path inputDirectory, ExecutionLimits limits) {
		long memoryLimitBytes = limits.memoryLimitMb() * 1024L * 1024L;
		HostConfig hostConfig = HostConfig.newHostConfig()
				.withNetworkMode("none")
				.withReadonlyRootfs(true)
				.withTmpFs(Map.of("/tmp", TMPFS_OPTIONS))
				.withMemory(memoryLimitBytes)
				.withMemorySwap(memoryLimitBytes)
				.withNanoCPUs(ONE_CPU_IN_NANOS)
				.withPidsLimit(PIDS_LIMIT)
				.withUlimits(List.of(new Ulimit("core", 0L, 0L)))
				.withCapDrop(Capability.ALL)
				.withCapAdd(Capability.SETUID, Capability.SETGID, Capability.KILL)
				.withSecurityOpts(List.of("no-new-privileges"))
				.withBinds(new Bind(inputDirectory.toAbsolutePath().toString(), new Volume("/judge/in"), AccessMode.ro));
		return dockerClient.createContainerCmd(language.image())
				.withHostConfig(hostConfig)
				.withEnv(
						"COMPILE_TIMEOUT=" + toTimeoutArgument(COMPILE_TIMEOUT),
						"RUN_TIMEOUT=" + toTimeoutArgument(limits.timeLimit()),
						"MEMORY_LIMIT_MB=" + limits.memoryLimitMb())
				.exec()
				.getId();
	}

	private static String toTimeoutArgument(Duration duration) {
		return BigDecimal.valueOf(duration.toMillis(), 3).stripTrailingZeros().toPlainString();
	}

	private static Duration containerTimeout(ExecutionLimits limits, int testCount) {
		return COMPILE_TIMEOUT.plus(limits.timeLimit().multipliedBy(testCount)).plus(CONTAINER_OVERHEAD);
	}

	private byte[] startAndCollectStdout(String containerId, Duration containerTimeout) {
		ByteArrayOutputStream stdout = new ByteArrayOutputStream();
		ResultCallback.Adapter<Frame> callback = new ResultCallback.Adapter<>() {
			@Override
			public void onNext(Frame frame) {
				if (frame.getStreamType() == StreamType.STDOUT) {
					stdout.writeBytes(frame.getPayload());
				}
			}
		};
		try {
			dockerClient.attachContainerCmd(containerId)
					.withStdOut(true)
					.withFollowStream(true)
					.exec(callback)
					.awaitStarted();
			dockerClient.startContainerCmd(containerId).exec();
			if (!callback.awaitCompletion(containerTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
				throw new IllegalStateException("채점 컨테이너가 " + containerTimeout + " 안에 끝나지 않았습니다: " + containerId);
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("채점 대기 중 인터럽트되었습니다: " + containerId, exception);
		}
		return stdout.toByteArray();
	}

	private Map<String, String> extractFiles(byte[] archive) {
		Map<String, String> files = new HashMap<>();
		try (TarArchiveInputStream tar = new TarArchiveInputStream(new ByteArrayInputStream(archive))) {
			TarArchiveEntry entry;
			while ((entry = tar.getNextEntry()) != null) {
				if (entry.isFile()) {
					String fileName = Path.of(entry.getName()).getFileName().toString();
					files.put(fileName, new String(tar.readAllBytes(), StandardCharsets.UTF_8));
				}
			}
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
		return files;
	}

	private SandboxResult toResult(Map<String, String> files, int testCount) {
		int compileExitCode = Integer.parseInt(files.get("compile.exit").trim());
		List<TestRunResult> testResults = new ArrayList<>();
		for (int testNumber = 1; testNumber <= testCount; testNumber++) {
			String meta = files.get(testNumber + ".meta");
			if (meta == null) {
				break;
			}
			Map<String, String> values = parseMeta(meta);
			int exitCode = Integer.parseInt(values.get("exit"));
			JsonNode wrapperOutput = exitCode == 0 ? parseWrapperOutput(files.get(testNumber + ".out")) : null;
			testResults.add(new TestRunResult(
					exitCode,
					Double.parseDouble(values.get("time_seconds")),
					Long.parseLong(values.get("memory_kb")),
					Boolean.parseBoolean(values.get("oom_killed")),
					wrapperOutput == null ? null : wrapperOutput.get("returnValue"),
					wrapperOutput == null ? "" : wrapperOutput.path("userOutput").asString(),
					files.get(testNumber + ".err")));
		}
		return new SandboxResult(compileExitCode, files.get("compile.log"), testResults);
	}

	// 유저 코드가 래퍼보다 먼저 System.exit(0) 으로 끝내면 정상 종료여도 결과 JSON이 없거나 깨져 있을 수 있다.
	private JsonNode parseWrapperOutput(String stdout) {
		try {
			JsonNode output = JSON_MAPPER.readTree(stdout);
			return output.isObject() ? output : null;
		} catch (JacksonException exception) {
			return null;
		}
	}

	private Map<String, String> parseMeta(String meta) {
		Map<String, String> values = new HashMap<>();
		for (String line : meta.strip().split("\n")) {
			String[] keyAndValue = line.split("=", 2);
			values.put(keyAndValue[0], keyAndValue[1]);
		}
		return values;
	}

	private void deleteDirectory(Path directory) {
		try {
			FileSystemUtils.deleteRecursively(directory);
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

}
