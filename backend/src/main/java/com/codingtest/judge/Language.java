package com.codingtest.judge;

public enum Language {

	JAVA("coding-test/judge-java", "Solution.java"),
	JAVASCRIPT("coding-test/judge-javascript", "solution.js");

	private final String image;
	private final String sourceFileName;

	Language(String image, String sourceFileName) {
		this.image = image;
		this.sourceFileName = sourceFileName;
	}

	public String image() {
		return image;
	}

	public String sourceFileName() {
		return sourceFileName;
	}

}
