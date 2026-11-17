import { createBrowserRouter, redirect } from 'react-router'
import {
  LOUNGE_PATH,
  LoungeLayout,
  PROBLEM_LIST_PATH,
  SPECTATE_PATH,
} from '@/app/layouts/LoungeLayout'
import { announceAuthChange } from '@/features/auth/model/authSync'
import { takeLoginReturnPath } from '@/features/auth/model/login'
import { ProblemListPage } from '@/pages/problem-list/ui/ProblemListPage'
import { SolvePage } from '@/pages/solve/ui/SolvePage'
import { SpectatePage } from '@/pages/spectate/ui/SpectatePage'

export const router = createBrowserRouter([
  {
    path: LOUNGE_PATH,
    element: <LoungeLayout />,
    children: [
      { index: true, element: null },
      { path: PROBLEM_LIST_PATH, element: <ProblemListPage /> },
      { path: `${PROBLEM_LIST_PATH}/:problemId`, element: <SolvePage /> },
      { path: `${SPECTATE_PATH}/:roomId`, element: <SpectatePage /> },
    ],
  },
  {
    path: '/login/callback',
    loader: () => {
      announceAuthChange()
      return redirect(takeLoginReturnPath() ?? LOUNGE_PATH)
    },
  },
])
