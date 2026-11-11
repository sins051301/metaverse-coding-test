export class HttpError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

export function isHttpError(error: unknown, status: number) {
  return error instanceof HttpError && error.status === status
}

// 토큰은 백엔드가 심은 HttpOnly 쿠키에 있다. /api 는 dev 서버 프록시를 거치는 같은 출처 요청이라 브라우저가 쿠키를 자동으로 붙인다.
export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers)
  if (init?.body) headers.set('Content-Type', 'application/json')

  const response = await fetch(path, { ...init, headers })
  if (!response.ok) {
    throw new HttpError(
      response.status,
      `${init?.method ?? 'GET'} ${path} → ${response.status}`,
    )
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}
