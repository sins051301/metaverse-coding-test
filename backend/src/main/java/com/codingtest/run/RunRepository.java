package com.codingtest.run;

import com.codingtest.judge.JudgeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RunRepository extends JpaRepository<Run, Long> {

	Optional<Run> findByIdAndUserId(long id, long userId);

	@Query("select run.id from Run run where run.status in :statuses order by run.id")
	List<Long> findIdsByStatusIn(Collection<JudgeStatus> statuses);

	long countByStatusIn(Collection<JudgeStatus> statuses);

	long countByUserIdAndStatusIn(long userId, Collection<JudgeStatus> statuses);

}
