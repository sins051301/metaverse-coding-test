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
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
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

tasks.test {
	useJUnitPlatform {
		excludeTags("docker")
	}
}

tasks.register<Test>("judgeTest") {
	group = "verification"
	description = "채점 이미지를 빌드하고 실제 Docker 컨테이너로 샌드박스 테스트를 실행한다."
	testClassesDirs = sourceSets.test.get().output.classesDirs
	classpath = sourceSets.test.get().runtimeClasspath
	useJUnitPlatform {
		includeTags("docker")
	}
	dependsOn(buildJudgeImages)
}
