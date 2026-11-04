package com.codingtest.problem;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// @Entity / @Table / @Getter / @NoArgsConstructor(PROTECTED): Problem과 같은 의미.
//   problem_parameters 테이블의 행 하나 = 문제 함수의 파라미터 하나.
@Entity
@Table(name = "problem_parameters")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProblemParameter {

	// @EmbeddedId: 기본 키가 컬럼 하나가 아니라 여러 컬럼의 조합(복합 키)이고, 그 묶음을 아래 Id 타입 하나로 표현한다는 뜻.
	//   PK = (problem_id, position). "몇 번 문제의 몇 번째 파라미터"가 곧 식별자다.
	@EmbeddedId
	private Id id;

	// @ManyToOne: 파라미터 N개 : 문제 1개 관계. 외래 키(problem_id)를 실제로 관리하는 쪽(연관관계 주인)이 여기다.
	//   fetch = LAZY: @ManyToOne의 기본값은 EAGER(파라미터를 읽을 때 문제도 즉시 조인해서 읽음)라서,
	//   getProblem()을 실제로 쓸 때만 문제를 읽도록 지연 로딩으로 바꿨다.
	// @MapsId("problemId"): 이 연관관계의 외래 키 값을 복합 키 Id의 problemId 필드로도 쓴다는 뜻.
	//   problem_id 컬럼 하나가 PK의 일부이자 FK 역할을 동시에 하므로, 같은 값을 두 컬럼에 중복 매핑하지 않아도 된다.
    // 연쇄 작용 보호 차원에서 lazy로 사용
	@MapsId("problemId")
	@ManyToOne(fetch = FetchType.LAZY)
	private Problem problem;

	private String name;

	private String type;

	public int getPosition() {
		return id.position();
	}

	// @Embeddable: 독립된 테이블이 아니라 다른 엔티티 안에 "끼워 넣어지는" 값 타입이라는 표시. 필드들이 ProblemParameter 테이블의 컬럼이 된다.
	//   복합 키로 쓰려면 equals/hashCode가 필요한데, record는 이를 자동으로 만들어 주므로 별도 구현이 필요 없다.
	@Embeddable
	public record Id(Long problemId, Integer position) {
	}

}
