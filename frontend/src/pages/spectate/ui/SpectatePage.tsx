import { useQuery } from '@tanstack/react-query'
import { lazy, Suspense, useEffect } from 'react'
import { useNavigate, useParams } from 'react-router'
import { problemDetailQuery } from '@/entities/problem/api/problemQueries'
import { useLoungeState } from '@/entities/lounge/model/loungeStore'
import { useMyAvatarId } from '@/entities/lounge/model/rosterStore'
import { currentUserQuery } from '@/entities/user/api/userQueries'
import { startLogin } from '@/features/auth/model/login'
import { useSpectate } from '@/features/spectate-code/model/useSpectate'
import { LANGUAGES } from '@/shared/config/languages'
import { Button } from '@/shared/ui/Button'
import { PairChat } from '@/widgets/pair-chat/ui/PairChat'
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
  const { state, pair, chats, onEditorMount, requestPair, endPair, sendChat } =
    useSpectate(roomId)
  const { data: user } = useQuery(currentUserQuery())
  const myAvatarId = useMyAvatarId()
  const paired = pair === 'paired'
  const problemId = state.phase === 'watching' ? state.problemId : 0
  const { data: problem } = useQuery({
    ...problemDetailQuery(problemId),
    enabled: problemId > 0,
  })

  const leave = () => navigate(seatedDeskId === null ? '/' : '/problems')

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      const target = event.target
      const typing =
        target instanceof HTMLInputElement ||
        target instanceof HTMLTextAreaElement
      if (event.key === 'Escape' && !typing) leave()
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
          <strong className={styles.owner}>{state.nickname}</strong>
          {paired
            ? '님 책상에서 함께 풀고 있어요. 제출은 책상 주인만 할 수 있어요.'
            : '님의 코드를 구경하고 있어요. 드래그해서 복사할 수 있어요.'}
        </span>
        <span className={styles.spacer} />
        {pair === 'requested' ? (
          <span className={styles.notice}>페어를 요청했어요. 기다리는 중…</span>
        ) : null}
        {pair === 'declined' ? (
          <span className={styles.notice}>이번에는 거절했어요.</span>
        ) : null}
        {pair === 'none' || pair === 'declined' ? (
          <Button
            size="small"
            variant="primary"
            onClick={user ? requestPair : startLogin}
          >
            {user ? '페어 요청' : '로그인하고 페어 요청'}
          </Button>
        ) : null}
        {paired ? (
          <Button size="small" variant="quiet" onClick={endPair}>
            페어 끝내기
          </Button>
        ) : null}
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
                readOnly={!paired}
                onMount={onEditorMount}
              />
            </Suspense>
          </div>
        </div>
        {paired ? (
          <PairChat
            messages={chats}
            myAvatarId={myAvatarId}
            onSend={sendChat}
          />
        ) : null}
      </div>
    </section>
  )
}
