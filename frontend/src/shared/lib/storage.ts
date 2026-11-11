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
