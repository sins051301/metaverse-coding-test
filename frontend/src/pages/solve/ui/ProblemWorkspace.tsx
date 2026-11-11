import { useQuery } from '@tanstack/react-query'
import type { editor } from 'monaco-editor'
import { lazy, Suspense, useRef, useState } from 'react'
import { Group, Panel, Separator } from 'react-resizable-panels'
import { Link } from 'react-router'
import type { JudgeMode } from '@/entities/judge/api/judgeApi'
import { problemDetailQuery } from '@/entities/problem/api/problemQueries'
import { startLogin } from '@/features/auth/model/login'
import { activityTracker } from '@/entities/lounge/model/activityTracker'
import { loadDraft, saveDraft } from '@/features/edit-code/model/codeDraft'
import { ResetCodeButton } from '@/features/edit-code/ui/ResetCodeButton'
import { useJudge } from '@/features/judge-code/model/useJudge'
import { useCodeSharing } from '@/features/share-code/model/useCodeSharing'
import {
  LANGUAGE_ORDER,
  LANGUAGES,
  type Language,
} from '@/shared/config/languages'
import { Button } from '@/shared/ui/Button'
import { JudgeResultPanel } from '@/widgets/judge-result/ui/JudgeResultPanel'
import { ProblemStatement } from '@/widgets/problem-statement/ui/ProblemStatement'
import { SubmissionHistoryPanel } from '@/widgets/submission-history/ui/SubmissionHistoryPanel'
import * as styles from './ProblemWorkspace.css'

const CodeEditor = lazy(() =>
  import('@/features/edit-code/ui/CodeEditor').then((module) => ({
    default: module.CodeEditor,
  })),
)

type ProblemWorkspaceProps = {
  problemId: number
  language: Language
  onLanguageChange: (language: Language) => void
}

export function ProblemWorkspace({
  problemId,
  language,
  onLanguageChange,
}: ProblemWorkspaceProps) {
  const { data: problem, isError } = useQuery(problemDetailQuery(problemId))
  const editorRef = useRef<editor.IStandaloneCodeEditor | null>(null)
  const [codeEditor, setCodeEditor] =
    useState<editor.IStandaloneCodeEditor | null>(null)
  const spectatorCount = useCodeSharing(codeEditor, problemId, language)
  const { view, isJudging, judge } = useJudge(problemId)
  const [showHistory, setShowHistory] = useState(false)

  if (isError)
    return (
      <p className={styles.message}>
        문제를 불러오지 못했어요. 주소의 문제 번호를 확인해 주세요.
      </p>
    )
  if (!problem)
    return <p className={styles.message}>문제를 불러오는 중이에요.</p>

  const starterCode = problem.starterCode[language]
  const { fileName, monacoLanguage } = LANGUAGES[language]

  function judgeCurrentCode(mode: JudgeMode) {
    const sourceCode = editorRef.current?.getValue()
    if (sourceCode === undefined) return
    judge(mode, { language, sourceCode })
  }

  function resetCode() {
    const codeEditor = editorRef.current
    const model = codeEditor?.getModel()
    if (!codeEditor || !model) return
    codeEditor.executeEdits('reset', [
      { range: model.getFullModelRange(), text: starterCode },
    ])
    codeEditor.pushUndoStop()
  }

  return (
    <div className={styles.workspace}>
      <div className={styles.problemBar}>
        <Link to="/problems" className={styles.backLink}>
          ← 문제 목록
        </Link>
        <h2 className={styles.problemTitle}>{problem.title}</h2>
        <span className={styles.level}>Lv. {problem.level}</span>
        <Button
          size="small"
          aria-pressed={showHistory}
          onClick={() => setShowHistory(!showHistory)}
        >
          {showHistory ? '문제 보기' : '제출 내역'}
        </Button>
        <span className={styles.spacer} />
        <select
          className={styles.languageSelect}
          aria-label="풀이 언어"
          value={language}
          onChange={(event) => onLanguageChange(event.target.value as Language)}
        >
          {LANGUAGE_ORDER.map((option) => (
            <option key={option} value={option}>
              {LANGUAGES[option].label}
            </option>
          ))}
        </select>
      </div>

      <Group orientation="horizontal" className={styles.main}>
        <Panel
          id="statement"
          defaultSize={380}
          minSize={280}
          maxSize={520}
          groupResizeBehavior="preserve-pixel-size"
        >
          {showHistory ? (
            <SubmissionHistoryPanel problemId={problemId} />
          ) : (
            <ProblemStatement problem={problem} />
          )}
        </Panel>
        <Separator className={styles.columnSeparator} />
        <Panel id="code" minSize={360}>
          <Group orientation="vertical">
            <Panel id="editor" minSize={160}>
              <div className={styles.editorCard}>
                <div className={styles.editorHead}>
                  {fileName}
                  {spectatorCount > 0 ? (
                    <span className={styles.spectators}>
                      {spectatorCount}명이 구경하고 있어요
                    </span>
                  ) : null}
                </div>
                <div className={styles.editorBody}>
                  <Suspense
                    fallback={
                      <p className={styles.editorLoading}>
                        에디터를 불러오는 중이에요.
                      </p>
                    }
                  >
                    <CodeEditor
                      path={`${problemId}/${fileName}`}
                      language={monacoLanguage}
                      defaultValue={
                        loadDraft(problemId, language) ?? starterCode
                      }
                      onChange={(code) => {
                        saveDraft(problemId, language, code)
                        activityTracker.touch()
                      }}
                      onMount={(mounted) => {
                        editorRef.current = mounted
                        setCodeEditor(mounted)
                      }}
                    />
                  </Suspense>
                </div>
              </div>
            </Panel>
            <Separator className={styles.rowSeparator} />
            <Panel
              id="result"
              defaultSize={260}
              minSize={120}
              maxSize="60%"
              groupResizeBehavior="preserve-pixel-size"
            >
              <JudgeResultPanel
                view={view}
                parameters={problem.parameters}
                timeLimitMs={problem.timeLimitMs}
                onRetry={() =>
                  judgeCurrentCode(view.phase === 'idle' ? 'run' : view.mode)
                }
                onLogin={startLogin}
              />
            </Panel>
          </Group>
        </Panel>
      </Group>

      <div className={styles.actionBar}>
        <span className={styles.spacer} />
        <ResetCodeButton onConfirm={resetCode} disabled={isJudging} />
        <Button onClick={() => judgeCurrentCode('run')} disabled={isJudging}>
          코드 실행
        </Button>
        <Button
          variant="primary"
          onClick={() => judgeCurrentCode('submit')}
          disabled={isJudging}
        >
          제출 후 채점하기
        </Button>
      </div>
    </div>
  )
}
