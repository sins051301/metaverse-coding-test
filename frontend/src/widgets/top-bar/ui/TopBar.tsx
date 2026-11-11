import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  CURRENT_USER_QUERY_KEY,
  currentUserQuery,
} from '@/entities/user/api/userQueries'
import { loungeConnection } from '@/entities/lounge/api/loungeConnection'
import { useOnlineCount } from '@/entities/lounge/model/rosterStore'
import { logout, startLogin } from '@/features/auth/model/login'
import { Button } from '@/shared/ui/Button'
import styles from './TopBar.module.css'

export function TopBar() {
  const queryClient = useQueryClient()
  const { data: user } = useQuery(currentUserQuery())
  const onlineCount = useOnlineCount()
  const logoutMutation = useMutation({
    mutationFn: logout,
    onSuccess: () => {
      queryClient.setQueryData(CURRENT_USER_QUERY_KEY, null)
      // 라운지 신원은 연결할 때 정해지므로, 로그아웃했으면 다시 연결해 게스트로 들어간다
      loungeConnection.restart()
    },
  })

  return (
    <header className={styles.topBar}>
      <span className={styles.wordmark}>픽셀 모각코</span>
      {onlineCount !== null ? (
        <span className={styles.muted}>
          공용 라운지에 <span className={styles.figure}>{onlineCount}</span>마리
          접속
        </span>
      ) : null}
      <span className={styles.spacer} />
      {user ? (
        <div className={styles.me}>
          <span className={styles.nickname}>{user.nickname}</span>
          <span className={styles.muted}>
            <span className={styles.figure}>{user.totalSolved}</span>문제 해결
          </span>
          <Button
            size="small"
            variant="quiet"
            onClick={() => logoutMutation.mutate()}
          >
            로그아웃
          </Button>
        </div>
      ) : null}
      {user === null ? (
        <div className={styles.me}>
          <span className={styles.muted}>게스트</span>
          <Button size="small" onClick={startLogin}>
            로그인
          </Button>
        </div>
      ) : null}
    </header>
  )
}
