package com.codingtest.problem;

import com.codingtest.judge.ExecutionLimits;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.List;

// @Entity: JPA(Hibernate)가 관리하는 엔티티라는 표시. 이 객체 하나가 DB 테이블의 행 하나에 대응한다.
//   엔티티는 기본 생성자와 @Id 필드가 반드시 있어야 한다.
// @Table(name = "problems"): 연결할 테이블 이름을 직접 지정한다. 생략하면 클래스 이름(problem)으로 매핑된다.
// @Getter (Lombok): 컴파일할 때 모든 필드의 getter(getId(), getTitle() ...)를 자동으로 만들어 준다.
//   setter는 만들지 않으므로 외부에서 값을 바꿀 수 없다.
// @NoArgsConstructor(access = PROTECTED) (Lombok): `protected Problem() {}`를 만들어 준다.
//   JPA는 리플렉션으로 객체를 만들고 지연 로딩용 프록시(하위 클래스)도 만들기 때문에 기본 생성자가 필요하다.
//   protected로 두면 JPA 요구사항은 만족하면서 다른 코드가 `new Problem()`으로 빈 객체를 만드는 것은 막을 수 있다.
@Entity
@Table(name = "problems")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Problem {

	// @Id: 기본 키(PK) 필드.
	//   @GeneratedValue가 없으므로 DB가 번호를 자동으로 매기지 않고, 이미 정해진 id 값(시드 데이터 등)을 그대로 쓴다.
	@Id
	private Long id;

	// 어노테이션이 없는 필드도 기본적으로 컬럼에 매핑된다.
	//   Spring Boot 기본 네이밍 규칙으로 camelCase → snake_case가 된다 (returnType → return_type, timeLimitMs → time_limit_ms).
	private String title;

	private int level;

	// @Column(columnDefinition = "text"): 컬럼의 DB 타입을 SQL 문자열 그대로 지정한다.
	//   String은 기본이 varchar(255)라서 긴 문제 설명을 담을 수 있도록 text로 지정한다.
	@Column(columnDefinition = "text")
	private String description;

	private String returnType;

	private int timeLimitMs;

	private int memoryLimitMb;

	// @OneToMany: 문제 1개 : 파라미터 N개 관계.
	//   mappedBy = "problem": 외래 키(problem_id)는 ProblemParameter의 `problem` 필드(@ManyToOne)가 관리한다는 뜻.
	//   이쪽은 읽기용 반대편이라 이 리스트에 추가/삭제해도 DB에는 반영되지 않는다.
	//   기본이 LAZY 로딩이라 getParameters()를 실제로 사용하는 순간 별도 쿼리가 나간다.
	// @OrderBy("id.position"): 리스트를 불러올 때 ORDER BY를 붙여 정렬한다. 방향을 안 쓰면 오름차순.
	//   컬럼명이 아니라 엔티티 필드 경로다. "id.position" = ProblemParameter 복합 키(Id) 안의 position 필드.
	//   그래서 파라미터가 함수 시그니처 순서(첫 번째 인자, 두 번째 인자 ...)대로 들어온다.
	@OneToMany(mappedBy = "problem")
	@OrderBy("id.position")
	private List<ProblemParameter> parameters;

	// 테스트 케이스는 컬렉션으로 두지 않는다. 문제와 조인해서 읽으면 MySQL이 인덱스 순서를 못 쓰고 수 MB짜리 행을 다시 정렬하다 실패하므로, TestCaseRepository 로 따로 읽는다.

	public ExecutionLimits executionLimits() {
		return new ExecutionLimits(Duration.ofMillis(timeLimitMs), memoryLimitMb);
	}

}
