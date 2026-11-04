package com.codingtest.problem;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Hibernate 기본 JSON 매퍼는 추상 타입인 JsonNode 를 만들지 못해서(readValue 실패), 트리로 직접 읽고 쓴다.
@Converter
public class JsonNodeConverter implements AttributeConverter<JsonNode, String> {

	private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

	@Override
	public String convertToDatabaseColumn(JsonNode value) {
		return value == null ? null : value.toString();
	}

	@Override
	public JsonNode convertToEntityAttribute(String column) {
		return column == null ? null : JSON_MAPPER.readTree(column);
	}

}
