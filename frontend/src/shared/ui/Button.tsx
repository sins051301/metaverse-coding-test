import type { ButtonHTMLAttributes } from 'react'
import styles from './Button.module.css'

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'default' | 'primary' | 'quiet'
  size?: 'medium' | 'small'
}

const VARIANT_CLASS = {
  default: '',
  primary: styles.primary,
  quiet: styles.quiet,
}

export function Button({
  variant = 'default',
  size = 'medium',
  className = '',
  type = 'button',
  ...rest
}: ButtonProps) {
  const classNames = [
    styles.button,
    VARIANT_CLASS[variant],
    size === 'small' ? styles.small : '',
    className,
  ]
  return <button type={type} className={classNames.join(' ')} {...rest} />
}
