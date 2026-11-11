import { useQuery } from '@tanstack/react-query'
import { lazy, Suspense, useEffect } from 'react'
import { useNavigate, useParams } from 'react-router'
import { problemDetailQuery } from '@/entities/problem/api/problemQueries'
import { useLoungeState } from '@/entities/lounge/model/loungeStore'
import { useSpectate } from '@/features/spectate-code/model/useSpectate'
import { LANGUAGES } from '@/shared/config/languages'
import { Button } from '@/shared/ui/Button'
import { ProblemStatement } from '@/widgets/problem-statement/ui/ProblemStatement'
import * as styles from './SpectatePage.css'

const CodeEditor = lazy(() =>
  import('@/features/edit-code/ui/CodeEditor').then((module) => ({
    default: module.CodeEditor,
  })),
)

export function SpectatePage() {
  const roomId = useParams().roomId ?? ''
  const navigate = useNavigate()
  const { seatedDeskId } = useLoungeState()
  const { state, onEditorMount } = useSpectate(roomId)
  const problemId = state.phase === 'watching' ? state.problemId : 0
  const { data: problem } = useQuery({
    ...problemDetailQuery(problemId),
    enabled: problemId > 0,
  })

  const leave = () => navigate(seatedDeskId === null ? '/' : '/problems')

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') leave()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  })

  if (state.phase !== 'watching') {
    return (
      <section className={styles.page}>
        <div className={styles.banner}>
          <span>
            {state.phase === 'connecting'
              ? '책상에 다가가는 중이에요.'
              : state.phase === 'closed'
                ? '자리를 떠났어요. 코드 구경이 끝났어요.'
                : '아직 풀이 화면을 열지 않은 책상이에요.'}
          </span>
          <span className={styles.spacer} />
          <Button size="small" onClick={leave}>
            나가기 <kbd>Esc</kbd>
          </Button>
        </div>
      </section>
    )
  }

  const { fileName, monacoLanguage } = LANGUAGES[state.language]
  return (
    <section className={styles.page}>
      <div className={styles.banner}>
        <span>
          <strong className={styles.owner}>{state.nickname}</strong>님의 코드를
          구경하고 있어요. 고칠 수는 없지만 드래그해서 복사할 수 있어요.
        </span>
        <span className={styles.spacer} />
        <Button size="small" onClick={leave}>
          나가기 <kbd>Esc</kbd>
        </Button>
      </div>
      <div className={styles.body}>
        <div className={styles.statement}>
          {problem ? <ProblemStatement problem={problem} /> : null}
        </div>
        <div className={styles.editorCard}>
          <div className={styles.editorHead}>
            {problem ? `${problem.title} · ` : ''}
            {fileName}
          </div>
          <div className={styles.editorBody}>
            <Suspense fallback={null}>
              <CodeEditor
                key={state.documentVersion}
                path={`spectate/${roomId}/${state.documentVersion}/${fileName}`}
                language={monacoLanguage}
                defaultValue=""
                readOnly
                onMount={onEditorMount}
              />
            </Suspense>
          </div>
        </div>
      </div>
    </section>
  )
}
