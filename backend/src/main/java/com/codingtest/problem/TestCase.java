package com.codingtest.problem;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;

/**
 * input 은 solution 에 넘길 인자 배열, expected 는 기대 반환값이다. 둘 다 통째로 래퍼에 넘기거나 비교하는 값이라 JSON 그대로 둔다.
 */
@Entity
@Table(name = "test_cases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TestCase {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	private Problem problem;

	private int sequence;

	private boolean hidden;

	@Convert(converter = JsonNodeConverter.class)
	@Column(columnDefinition = "json")
	private JsonNode input;

	@Convert(converter = JsonNodeConverter.class)
	@Column(columnDefinition = "json")
	private JsonNode expected;

}
