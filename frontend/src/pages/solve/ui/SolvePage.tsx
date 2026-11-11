import { useState } from 'react'
import { useParams } from 'react-router'
import { LANGUAGE_ORDER, type Language } from '@/shared/config/languages'
import { readStorage, writeStorage } from '@/shared/lib/storage'
import { ProblemWorkspace } from './ProblemWorkspace'

const LANGUAGE_KEY = 'codingtest:v1:language'

function readSavedLanguage(): Language {
  const saved = readStorage(localStorage, LANGUAGE_KEY)
  return LANGUAGE_ORDER.find((language) => language === saved) ?? 'JAVA'
}

export function SolvePage() {
  const problemId = Number(useParams().problemId)
  const [language, setLanguage] = useState(readSavedLanguage)

  function changeLanguage(next: Language) {
    setLanguage(next)
    writeStorage(localStorage, LANGUAGE_KEY, next)
  }

  return (
    // 문제가 바뀌면 채점 결과·에디터 상태를 새로 시작한다. 상단 바와 왼쪽 지도는 공통 레이아웃(LoungeLayout)이 그린다
    <ProblemWorkspace
      key={problemId}
      problemId={problemId}
      language={language}
      onLanguageChange={changeLanguage}
    />
  )
}
