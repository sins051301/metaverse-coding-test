plugins {
	java
	id("org.springframework.boot") version "4.1.2"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.codingtest"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	// 라운지 실시간 동기화: WebSocket + STOMP (Spring simple broker). 버전은 Spring Boot 가 관리한다.
	implementation("org.springframework.boot:spring-boot-starter-websocket")
	// 지표: Micrometer 가 만든 값을 Actuator 가 /actuator/prometheus 로 내놓고, Prometheus 가 주기적으로 가져간다
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	runtimeOnly("io.micrometer:micrometer-registry-prometheus")
	implementation("org.flywaydb:flyway-mysql")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.0")
	runtimeOnly("com.mysql:mysql-connector-j")
	implementation("com.github.docker-java:docker-java-core:3.7.1")
	implementation("com.github.docker-java:docker-java-transport-httpclient5:3.7.1")
	implementation("org.apache.commons:commons-compress:1.28.0")
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-mysql")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	// 비동기 채점 테스트에서 "DONE 이 될 때까지 최대 N초 기다림"을 쓰기 위한 테스트 전용 라이브러리. 버전은 Spring Boot 가 관리한다.
	testImplementation("org.awaitility:awaitility")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testCompileOnly("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testAnnotationProcessor("org.projectlombok:lombok")
}

val judgeDockerDirectory = layout.projectDirectory.dir("../docker/judge")
val judgeImages = mapOf("Java" to "java", "Javascript" to "javascript")

val buildJudgeImages = tasks.register("buildJudgeImages") {
	group = "judge"
	description = "docker/judge 의 채점 이미지를 모두 빌드한다."
}

judgeImages.forEach { (taskSuffix, language) ->
	val buildImage = tasks.register<Exec>("buildJudgeImage$taskSuffix") {
		group = "judge"
		workingDir = judgeDockerDirectory.asFile
		commandLine("docker", "build", "-q", "-f", "$language.Dockerfile", "-t", "coding-test/judge-$language", ".")
	}
	buildJudgeImages { dependsOn(buildImage) }
}

// 비밀값은 환경 변수로만 받는다(공개 레포). 테스트는 실제 Google을 거치지 않으므로 가짜 값을 넣는다.
tasks.withType<Test> {
	environment("GOOGLE_CLIENT_ID", "test-client-id")
	environment("GOOGLE_CLIENT_SECRET", "test-client-secret")
	environment("JWT_SECRET", "test-jwt-secret-that-is-at-least-32-bytes-long")
	// CI 로그만 보고 실패 원인을 알 수 있게 예외 메시지와 원인(Caused by)을 끝까지 출력한다
	testLogging {
		exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
	}
}

tasks.test {
	useJUnitPlatform {
		excludeTags("docker", "concurrency", "benchmark")
	}
}

tasks.register<Test>("judgeTest") {
	group = "verification"
	description = "채점 이미지를 빌드하고 실제 Docker 컨테이너로 샌드박스 테스트를 실행한다."
	testClassesDirs = sourceSets.test.get().output.classesDirs
	classpath = sourceSets.test.get().runtimeClasspath
	useJUnitPlatform {
		includeTags("docker")
		excludeTags("concurrency")
	}
	dependsOn(buildJudgeImages)
}

// 동시 제출 테스트는 컨테이너를 한꺼번에 여러 개 띄워 무겁고 오래 걸려서 judgeTest 에서 빼고 따로 돌린다.
tasks.register<Test>("concurrencyTest") {
	group = "verification"
	description = "제출 여러 건을 동시에 보내 모두 정상 판정되는지와 걸린 시간을 측정한다 (대기열 도입 전후 비교용)."
	testClassesDirs = sourceSets.test.get().output.classesDirs
	classpath = sourceSets.test.get().runtimeClasspath
	useJUnitPlatform {
		includeTags("concurrency")
	}
	testLogging {
		showStandardStreams = true
	}
	// -PjudgeConcurrency=N 으로 동시 채점 수를 바꿔 같은 테스트를 비교한다. 없으면 application.properties 값.
	providers.gradleProperty("judgeConcurrency").orNull?.let { systemProperty("judge.concurrency", it) }
	// -PjudgeVirtualThreads=false 로 채점 작업 스레드를 플랫폼 스레드로 바꿔 비교한다. NMT 는 스레드 스택 메모리를 재려고 켠다.
	providers.gradleProperty("judgeVirtualThreads").orNull?.let { systemProperty("judge.virtual-threads", it) }
	jvmArgs("-XX:NativeMemoryTracking=summary")
	dependsOn(buildJudgeImages)
}

// 채점 실행기 구조(작업마다 스레드 + 세마포어)만 떼어 Docker 없이 작업 수를 늘려 가며 가상/플랫폼 스레드를 비교한다.
tasks.register<Test>("threadBenchmark") {
	group = "verification"
	description = "채점 실행기를 가상 스레드와 플랫폼 스레드로 바꿔 작업 수별 스레드 수·메모리·시간을 측정한다."
	testClassesDirs = sourceSets.test.get().output.classesDirs
	classpath = sourceSets.test.get().runtimeClasspath
	useJUnitPlatform {
		includeTags("benchmark")
	}
	testLogging {
		showStandardStreams = true
	}
	providers.gradleProperty("judgeVirtualThreads").orNull?.let { systemProperty("judge.virtual-threads", it) }
	jvmArgs("-XX:NativeMemoryTracking=summary")
}
