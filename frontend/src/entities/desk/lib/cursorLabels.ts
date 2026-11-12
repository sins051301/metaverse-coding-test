import type { Awareness } from 'y-protocols/awareness'

export type CursorUser = { name: string }

export function watchCursorLabels(awareness: Awareness) {
  const styleElement = document.createElement('style')
  document.head.append(styleElement)

  function render() {
    const rules: string[] = []
    awareness.getStates().forEach((state, clientId) => {
      const user = (state as { user?: CursorUser }).user
      if (clientId === awareness.clientID || !user?.name) return
      rules.push(
        `.yRemoteSelectionHead-${clientId}::after { content: ${JSON.stringify(user.name)}; }`,
      )
    })
    styleElement.textContent = rules.join('\n')
  }

  awareness.on('change', render)
  render()
  return () => {
    awareness.off('change', render)
    styleElement.remove()
  }
}
