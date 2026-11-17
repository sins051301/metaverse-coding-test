import {
  MutationCache,
  QueryCache,
  QueryClient,
  QueryClientProvider,
} from '@tanstack/react-query'
import { useEffect } from 'react'
import { RouterProvider } from 'react-router'
import { router } from '@/app/router'
import {
  handleUnauthorized,
  listenAuthChangeElsewhere,
} from '@/features/auth/model/authSync'
import { HttpError } from '@/shared/api/httpClient'
import * as styles from './App.css'

const MAX_RETRY = 2

const queryClient: QueryClient = new QueryClient({
  queryCache: new QueryCache({
    onError: (error) => handleUnauthorized(queryClient, error),
  }),
  mutationCache: new MutationCache({
    onError: (error) => handleUnauthorized(queryClient, error),
  }),
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: (failureCount, error) =>
        !(error instanceof HttpError && error.status < 500) &&
        failureCount < MAX_RETRY,
    },
  },
})

export function App() {
  useEffect(() => listenAuthChangeElsewhere(queryClient), [])

  return (
    <QueryClientProvider client={queryClient}>
      <div className={styles.app}>
        <RouterProvider router={router} />
      </div>
      <p className={styles.narrowNotice}>
        넓은 화면에서 이용해 주세요. 가로 1280px 이상에서 열 수 있어요.
      </p>
    </QueryClientProvider>
  )
}
