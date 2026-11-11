import { useState } from 'react'
import { useParams } from 'react-router'
import { LANGUAGE_ORDER, type Language } from '@/shared/config/languages'
import { readStorage, writeStorage } from '@/shared/lib/storage'
import { MapPanel } from '@/widgets/map-panel/ui/MapPanel'
import { TopBar } from '@/widgets/top-bar/ui/TopBar'
import { ProblemWorkspace } from './ProblemWorkspace'
import styles from './SolvePage.module.css'

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
    <div className={styles.page}>
      <TopBar />
      <div className={styles.body}>
        <MapPanel />
        {/* 문제가 바뀌면 채점 결과·에디터 상태를 새로 시작한다 */}
        <ProblemWorkspace
          key={problemId}
          problemId={problemId}
          language={language}
          onLanguageChange={changeLanguage}
        />
      </div>
    </div>
  )
}
