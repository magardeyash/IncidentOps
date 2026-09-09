interface Props {
  status: string
}

const STATUS_STYLES: Record<string, string> = {
  OPEN: 'bg-yellow-600 text-yellow-100',
  INVESTIGATING: 'bg-blue-600 text-blue-100',
  RESOLVED: 'bg-green-700 text-green-100',
  IGNORED: 'bg-slate-600 text-slate-200',
}

export default function StatusBadge({ status }: Props) {
  const cls = STATUS_STYLES[status] ?? 'bg-slate-700 text-slate-200'
  return (
    <span className={`inline-block px-2 py-0.5 rounded text-xs font-semibold uppercase tracking-wide ${cls}`}>
      {status}
    </span>
  )
}
