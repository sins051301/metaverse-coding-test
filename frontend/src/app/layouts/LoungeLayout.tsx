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
import * as styles from './LoungeLayout.css'

export const LOUNGE_PATH = '/'
export const PROBLEM_LIST_PATH = '/problems'
export const SPECTATE_PATH = '/spectate'

export function LoungeLayout() {
  const location = useLocation()
  const navigate = useNavigate()
  const view = location.pathname === LOUNGE_PATH ? 'lounge' : 'panel'
  const spectating = location.pathname.startsWith(SPECTATE_PATH)
  const { seatedDeskId, spectateRequest } = useLoungeState()
  const previous = useRef<{ view: string; seatedDeskId: number | null } | null>(
    null,
  )

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
    } else if (
      viewChanged &&
      view === 'panel' &&
      seatedDeskId === null &&
      !spectating
    ) {
      const freeDesk = firstFreeDesk()
      if (freeDesk === null) {
        navigate(LOUNGE_PATH)
      } else {
        loungeStore.sit(freeDesk)
      }
    }
  }, [view, seatedDeskId, spectating, navigate])

  useEffect(() => {
    if (spectateRequest !== null) {
      loungeStore.requestSpectate(null)
      navigate(`${SPECTATE_PATH}/${spectateRequest}`)
    }
  }, [spectateRequest, navigate])

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
