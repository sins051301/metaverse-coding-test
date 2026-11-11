import { useCallback, useEffect, useLayoutEffect, useRef } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router'
import {
  firstFreeDesk,
  loungeConnection,
} from '@/entities/lounge/api/loungeConnection'
import {
  loungeStore,
  useLoungeState,
} from '@/entities/lounge/model/loungeStore'
import { LoungeMapArea } from '@/widgets/lounge-map/ui/LoungeMapArea'
import { TopBar } from '@/widgets/top-bar/ui/TopBar'
import styles from './LoungeLayout.module.css'

export const LOUNGE_PATH = '/'
export const PROBLEM_LIST_PATH = '/problems'

/**
 * 라운지·문제 선택·풀이를 감싸는 공통 레이아웃. 지도(LoungeMapArea)가 여기 있어서 화면을 옮겨도 사라지지 않는다.
 *
 * 주소(route)와 "앉아 있는가"(loungeStore.seatedDeskId)를 맞춘다:
 *  - 라운지에서 [F]로 앉음 → 문제 선택(/problems)으로 이동
 *  - 앉은 화면에서 [일어나기]·ESC → 일어나고 라운지(/)로 이동
 *  - 브라우저 뒤로 가기로 라운지에 돌아옴 → 일어남
 *  - 앉지 않았는데 문제 주소로 바로 들어옴(새 탭, 링크) → 비어 있는 책상에 앉힘 (가운데 책상부터)
 *
 * 라운지 서버 연결(loungeConnection)도 여기서 연다. 레이아웃이 사라지지 않으니 화면을 옮겨도 연결이 유지된다.
 */
export function LoungeLayout() {
  const location = useLocation()
  const navigate = useNavigate()
  const view = location.pathname === LOUNGE_PATH ? 'lounge' : 'panel'
  const { seatedDeskId } = useLoungeState()
  const previous = useRef<{ view: string; seatedDeskId: number | null } | null>(
    null,
  )

  // 화면 모드는 그리기 전에 알려야 Phaser 카메라가 첫 프레임부터 맞는 배율로 그린다
  useLayoutEffect(() => {
    loungeStore.setView(view)
  }, [view])

  useEffect(() => {
    const before = previous.current
    previous.current = { view, seatedDeskId }
    const viewChanged = before?.view !== view
    const seatChanged = before !== null && before.seatedDeskId !== seatedDeskId

    if (seatChanged && seatedDeskId !== null && view === 'lounge') {
      navigate(PROBLEM_LIST_PATH)
    } else if (seatChanged && seatedDeskId === null && view === 'panel') {
      navigate(LOUNGE_PATH)
    } else if (viewChanged && view === 'lounge' && seatedDeskId !== null) {
      loungeStore.stand()
    } else if (viewChanged && view === 'panel' && seatedDeskId === null) {
      const freeDesk = firstFreeDesk()
      if (freeDesk === null) {
        navigate(LOUNGE_PATH)
      } else {
        loungeStore.sit(freeDesk)
      }
    }
  }, [view, seatedDeskId, navigate])

  useEffect(() => {
    loungeConnection.start()
    return () => loungeConnection.stop()
  }, [])

  const stand = useCallback(() => loungeStore.stand(), [])

  return (
    <div className={styles.page}>
      <TopBar />
      <div className={styles.body}>
        <LoungeMapArea onStand={stand} />
        <Outlet />
      </div>
    </div>
  )
}
