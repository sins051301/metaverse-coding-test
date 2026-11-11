// 시크릿 창이나 저장소 차단 설정에서는 접근만 해도 예외가 나므로, 실패는 "값 없음"으로 다루고 화면은 그대로 동작하게 둔다.
export function readStorage(storage: Storage, key: string): string | null {
  try {
    return storage.getItem(key)
  } catch {
    return null
  }
}

export function writeStorage(storage: Storage, key: string, value: string) {
  try {
    storage.setItem(key, value)
  } catch {
    return
  }
}

export function removeStorage(storage: Storage, key: string) {
  try {
    storage.removeItem(key)
  } catch {
    return
  }
}
