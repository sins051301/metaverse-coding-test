import { createBrowserRouter, redirect } from 'react-router'
import { takeLoginReturnPath } from '@/features/auth/model/login'
import { ProblemListPage } from '@/pages/problem-list/ui/ProblemListPage'
import { SolvePage } from '@/pages/solve/ui/SolvePage'

const PROBLEM_LIST_PATH = '/problems'

export const router = createBrowserRouter([
  // 라운지가 생기면 / 는 라운지가 되고, 책상에 앉을 때 문제 목록으로 간다
  { path: '/', loader: () => redirect(PROBLEM_LIST_PATH) },
  { path: PROBLEM_LIST_PATH, element: <ProblemListPage /> },
  { path: '/problems/:problemId', element: <SolvePage /> },
  // 백엔드가 토큰 쿠키를 심고 보내는 곳. 로그인 전에 보던 주소로 되돌려 보낸다
  {
    path: '/login/callback',
    loader: () => redirect(takeLoginReturnPath() ?? PROBLEM_LIST_PATH),
  },
])
