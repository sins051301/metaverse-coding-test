import { type FormEvent, useEffect, useRef, useState } from 'react'
import type { PairChatMessage } from '@/entities/desk/api/deskMessages'
import * as styles from './PairChat.css'

const PAIR_CHAT_MAX_LENGTH = 500

type PairChatProps = {
  messages: PairChatMessage[]
  myAvatarId: string | null
  onSend: (text: string) => void
}

export function PairChat({ messages, myAvatarId, onSend }: PairChatProps) {
  const [draft, setDraft] = useState('')
  const logRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    logRef.current?.scrollTo({ top: logRef.current.scrollHeight })
  }, [messages])

  function submit(event: FormEvent) {
    event.preventDefault()
    const text = draft.trim()
    if (text === '') return
    onSend(text)
    setDraft('')
  }

  return (
    <aside className={styles.chat} aria-label="페어 채팅">
      <h3 className={styles.title}>둘만의 채팅</h3>
      <div ref={logRef} className={styles.log}>
        {messages.length === 0 ? (
          <p className={styles.empty}>
            줄 번호로 짚어 말하면 편해요. 채팅은 저장되지 않아요.
          </p>
        ) : null}
        {messages.map((message) => (
          <div
            key={`${message.sentAt}-${message.memberId}`}
            className={
              message.memberId === myAvatarId ? styles.mine : styles.message
            }
          >
            <div className={styles.who}>
              {message.memberId === myAvatarId ? '나' : message.nickname}
            </div>
            <p className={styles.text}>{message.text}</p>
          </div>
        ))}
      </div>
      <form className={styles.form} onSubmit={submit}>
        <input
          className={styles.input}
          value={draft}
          maxLength={PAIR_CHAT_MAX_LENGTH}
          placeholder="메시지를 입력하고 Enter"
          onChange={(event) => setDraft(event.target.value)}
        />
      </form>
    </aside>
  )
}
