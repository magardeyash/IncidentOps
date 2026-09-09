interface Props {
  type: string
}

const TYPE_STYLES: Record<string, string> = {
  BUILD_ERROR: 'bg-orange-700 text-orange-100',
  TEST_FAILURE: 'bg-red-700 text-red-100',
  DEPENDENCY_ERROR: 'bg-purple-700 text-purple-100',
  TIMEOUT: 'bg-amber-700 text-amber-100',
  INFRA_FLAKE: 'bg-cyan-700 text-cyan-100',
  UNKNOWN: 'bg-slate-600 text-slate-200',
}

export default function ClassificationBadge({ type }: Props) {
  const cls = TYPE_STYLES[type] ?? 'bg-slate-600 text-slate-200'
  return (
    <span className={`inline-block px-2 py-0.5 rounded text-xs font-semibold uppercase tracking-wide ${cls}`}>
      {type.replace(/_/g, ' ')}
    </span>
  )
}
