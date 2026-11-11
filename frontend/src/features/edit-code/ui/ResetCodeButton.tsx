import { useRef } from 'react'
import { Button } from '@/shared/ui/Button'
import styles from './ResetCodeButton.module.css'

type ResetCodeButtonProps = {
  disabled: boolean
  onConfirm: () => void
}

// 브라우저 기본 <dialog>의 showModal을 쓴다. 뒤 화면 클릭 막기·Esc로 닫기·포커스 가두기가 기본으로 된다
export function ResetCodeButton({ disabled, onConfirm }: ResetCodeButtonProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)

  function confirm() {
    dialogRef.current?.close()
    onConfirm()
  }

  return (
    <>
      <Button
        onClick={() => dialogRef.current?.showModal()}
        disabled={disabled}
      >
        초기화
      </Button>
      <dialog
        ref={dialogRef}
        className={styles.dialog}
        aria-labelledby="reset-code-title"
      >
        <h2 id="reset-code-title" className={styles.title}>
          코드를 초기화할까요?
        </h2>
        <p className={styles.description}>
          지금 작성한 코드가 처음 시작 코드로 바뀌어요. 바로 뒤에 에디터에서
          Ctrl+Z를 누르면 되돌릴 수 있어요.
        </p>
        <div className={styles.actions}>
          <Button variant="quiet" onClick={() => dialogRef.current?.close()}>
            취소
          </Button>
          <Button variant="primary" onClick={confirm}>
            초기화
          </Button>
        </div>
      </dialog>
    </>
  )
}
