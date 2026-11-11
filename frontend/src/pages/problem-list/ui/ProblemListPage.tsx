import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { problemListQuery } from '@/entities/problem/api/problemQueries'
import styles from './ProblemListPage.module.css'

// 책상에 앉아 노트북을 켜면 처음 보이는 화면. 상단 바와 왼쪽 지도는 공통 레이아웃(LoungeLayout)이 그린다
export function ProblemListPage() {
  const { data: problems, isError } = useQuery(problemListQuery())
  const [selectedLevel, setSelectedLevel] = useState<number | null>(null)

  const levels = [
    ...new Set(problems?.map((problem) => problem.level)),
  ].toSorted((left, right) => left - right)
  const visibleProblems = problems?.filter(
    (problem) => selectedLevel === null || problem.level === selectedLevel,
  )

  return (
    <section className={styles.laptop} aria-labelledby="problem-list-title">
      <div className={styles.head}>
        <h1 id="problem-list-title" className={styles.title}>
          어떤 문제를 풀까요?
        </h1>
        <div className={styles.chips} role="group" aria-label="난이도">
          <button
            type="button"
            className={styles.chip}
            aria-pressed={selectedLevel === null}
            onClick={() => setSelectedLevel(null)}
          >
            전체
          </button>
          {levels.map((level) => (
            <button
              key={level}
              type="button"
              className={styles.chip}
              aria-pressed={selectedLevel === level}
              onClick={() => setSelectedLevel(level)}
            >
              Lv. {level}
            </button>
          ))}
        </div>
      </div>

      {isError ? (
        <p className={styles.message}>
          문제 목록을 불러오지 못했어요. 잠시 뒤에 새로고침해 주세요.
        </p>
      ) : null}
      {!isError && !problems ? (
        <p className={styles.message}>문제 목록을 불러오는 중이에요.</p>
      ) : null}

      {visibleProblems ? (
        <ol className={styles.list}>
          {visibleProblems.map((problem) => (
            <li key={problem.id}>
              <Link to={`/problems/${problem.id}`} className={styles.row}>
                <span className={styles.number}>{problem.id}</span>
                <span className={styles.problemTitle}>{problem.title}</span>
                <span className={styles.level}>Lv. {problem.level}</span>
              </Link>
            </li>
          ))}
        </ol>
      ) : null}
    </section>
  )
}
