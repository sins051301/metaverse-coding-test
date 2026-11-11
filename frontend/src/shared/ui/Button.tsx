import type { ButtonHTMLAttributes } from 'react'
import { button, type ButtonVariants } from './Button.css'

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & ButtonVariants

export function Button({
  variant,
  size,
  className = '',
  type = 'button',
  ...rest
}: ButtonProps) {
  return (
    <button
      type={type}
      className={`${button({ variant, size })} ${className}`.trim()}
      {...rest}
    />
  )
}
