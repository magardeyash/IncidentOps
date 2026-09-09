import { useEffect } from 'react'

interface Props {
  message: string
  type: 'error' | 'success' | 'info'
  onClose: () => void
}

const TYPE_STYLES = {
  error: 'bg-red-700 border-red-500 text-red-100',
  success: 'bg-green-700 border-green-500 text-green-100',
  info: 'bg-blue-700 border-blue-500 text-blue-100',
}

export default function Toast({ message, type, onClose }: Props) {
  useEffect(() => {
    const timer = setTimeout(onClose, 5000)
    return () => clearTimeout(timer)
  }, [onClose])

  return (
    <div
      className={`fixed top-4 right-4 z-50 flex items-start gap-3 max-w-sm w-full border rounded-lg px-4 py-3 shadow-lg ${TYPE_STYLES[type]}`}
      role="alert"
    >
      <p className="flex-1 text-sm font-medium">{message}</p>
      <button
        onClick={onClose}
        className="shrink-0 text-current opacity-70 hover:opacity-100 transition-opacity text-lg leading-none"
        aria-label="Dismiss"
      >
        ×
      </button>
    </div>
  )
}
