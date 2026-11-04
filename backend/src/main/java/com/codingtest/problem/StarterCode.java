package com.codingtest.problem;

import com.codingtest.judge.Language;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 에디터에 처음 채워 줄 언어별 코드. 래퍼가 찾는 이름(Java: Solution 클래스의 solution 메서드, JavaScript: solution 함수)을 따른다.
 */
public final class StarterCode {

	private StarterCode() {
	}

	public static Map<Language, String> of(Problem problem) {
		Map<Language, String> starterCodes = new EnumMap<>(Language.class);
		starterCodes.put(Language.JAVA, java(problem));
		starterCodes.put(Language.JAVASCRIPT, javascript(problem));
		return starterCodes;
	}

	private static String java(Problem problem) {
		String parameters = problem.getParameters().stream()
				.map(parameter -> parameter.getType() + " " + parameter.getName())
				.collect(Collectors.joining(", "));
		return """
				class Solution {
				    public %s solution(%s) {
				        %s answer = %s;
				        return answer;
				    }
				}
				""".formatted(problem.getReturnType(), parameters, problem.getReturnType(), javaDefaultValue(problem.getReturnType()));
	}

	private static String javascript(Problem problem) {
		String parameters = problem.getParameters().stream()
				.map(ProblemParameter::getName)
				.collect(Collectors.joining(", "));
		return """
				function solution(%s) {
				    let answer = %s;
				    return answer;
				}
				""".formatted(parameters, javascriptDefaultValue(problem.getReturnType()));
	}

	private static String javaDefaultValue(String type) {
		return switch (type) {
			case "int", "long" -> "0";
			case "double" -> "0.0";
			case "boolean" -> "false";
			case "String" -> "\"\"";
			default -> "new " + type + "{}";
		};
	}

	private static String javascriptDefaultValue(String type) {
		return switch (type) {
			case "int", "long", "double" -> "0";
			case "boolean" -> "false";
			case "String" -> "''";
			default -> "[]";
		};
	}

}
