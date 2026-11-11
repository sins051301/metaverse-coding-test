import { createBrowserRouter, redirect } from 'react-router'
import {
  LOUNGE_PATH,
  LoungeLayout,
  PROBLEM_LIST_PATH,
} from '@/app/layouts/LoungeLayout'
import { takeLoginReturnPath } from '@/features/auth/model/login'
import { ProblemListPage } from '@/pages/problem-list/ui/ProblemListPage'
import { SolvePage } from '@/pages/solve/ui/SolvePage'

export const router = createBrowserRouter([
  {
    // 세 화면이 지도를 함께 쓴다. 라운지(/)는 자식 화면이 없어 지도가 가운데를 다 차지한다
    path: LOUNGE_PATH,
    element: <LoungeLayout />,
    children: [
      { index: true, element: null },
      { path: PROBLEM_LIST_PATH, element: <ProblemListPage /> },
      { path: `${PROBLEM_LIST_PATH}/:problemId`, element: <SolvePage /> },
    ],
  },
  // 백엔드가 토큰 쿠키를 심고 보내는 곳. 로그인 전에 보던 주소로 되돌려 보낸다
  {
    path: '/login/callback',
    loader: () => redirect(takeLoginReturnPath() ?? LOUNGE_PATH),
  },
])
