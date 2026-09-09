import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { listIncidents, ApiError } from '../api/client'
import type { Incident, Page } from '../types'
import StatusBadge from '../components/StatusBadge'
import ClassificationBadge from '../components/ClassificationBadge'

const STATUS_OPTIONS = ['', 'OPEN', 'INVESTIGATING', 'RESOLVED', 'IGNORED'] as const
const CLASSIFICATION_OPTIONS = [
  '',
  'BUILD_ERROR',
  'TEST_FAILURE',
  'DEPENDENCY_ERROR',
  'TIMEOUT',
  'INFRA_FLAKE',
  'UNKNOWN',
] as const

const PAGE_SIZE = 20

export default function IncidentListPage() {
  const navigate = useNavigate()

  const [data, setData] = useState<Page<Incident> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [statusFilter, setStatusFilter] = useState('')
  const [classificationFilter, setClassificationFilter] = useState('')
  const [page, setPage] = useState(0)

  const fetchIncidents = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const result = await listIncidents({
        page,
        size: PAGE_SIZE,
        status: statusFilter || undefined,
        classificationType: classificationFilter || undefined,
      })
      setData(result)
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message)
      } else {
        setError('Failed to load incidents')
      }
    } finally {
      setLoading(false)
    }
  }, [page, statusFilter, classificationFilter])

  useEffect(() => {
    fetchIncidents()
  }, [fetchIncidents])

  // Reset to page 0 when filters change
  function handleStatusChange(val: string) {
    setStatusFilter(val)
    setPage(0)
  }

  function handleClassificationChange(val: string) {
    setClassificationFilter(val)
    setPage(0)
  }

  function handleLogout() {
    sessionStorage.clear()
    navigate('/login', { replace: true })
  }

  const totalPages = data?.totalPages ?? 0
  const totalElements = data?.totalElements ?? 0

  return (
    <div className="min-h-screen bg-slate-900 text-slate-100">
      {/* Header */}
      <header className="bg-slate-800 border-b border-slate-700 px-6 py-4 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <span className="text-2xl">🚨</span>
          <h1 className="text-xl font-bold text-slate-100 tracking-tight">IncidentOps</h1>
        </div>
        <button
          onClick={handleLogout}
          className="text-sm text-slate-400 hover:text-slate-100 border border-slate-600 hover:border-slate-400 px-3 py-1.5 rounded"
        >
          Logout
        </button>
      </header>

      <main className="px-6 py-6 max-w-7xl mx-auto">
        {/* Filters */}
        <div className="flex flex-wrap items-center gap-4 mb-6">
          <div className="flex items-center gap-2">
            <label className="text-sm text-slate-400">Status</label>
            <select
              value={statusFilter}
              onChange={e => handleStatusChange(e.target.value)}
              className="bg-slate-800 border border-slate-600 text-slate-200 text-sm rounded px-3 py-1.5 focus:outline-none focus:border-blue-500"
            >
              {STATUS_OPTIONS.map(s => (
                <option key={s} value={s}>
                  {s === '' ? 'All Statuses' : s}
                </option>
              ))}
            </select>
          </div>

          <div className="flex items-center gap-2">
            <label className="text-sm text-slate-400">Classification</label>
            <select
              value={classificationFilter}
              onChange={e => handleClassificationChange(e.target.value)}
              className="bg-slate-800 border border-slate-600 text-slate-200 text-sm rounded px-3 py-1.5 focus:outline-none focus:border-blue-500"
            >
              {CLASSIFICATION_OPTIONS.map(c => (
                <option key={c} value={c}>
                  {c === '' ? 'All Classifications' : c.replace(/_/g, ' ')}
                </option>
              ))}
            </select>
          </div>

          <button
            onClick={fetchIncidents}
            disabled={loading}
            className="text-sm bg-slate-700 hover:bg-slate-600 disabled:opacity-50 text-slate-200 px-3 py-1.5 rounded"
          >
            {loading ? 'Loading…' : 'Refresh'}
          </button>
        </div>

        {/* Error */}
        {error && (
          <div className="mb-4 bg-red-900/60 border border-red-600 text-red-200 rounded-lg px-4 py-3 text-sm">
            {error}
          </div>
        )}

        {/* Table */}
        <div className="bg-slate-800 border border-slate-700 rounded-lg overflow-hidden">
          {loading ? (
            <div className="px-6 py-12 text-center text-slate-400">Loading incidents…</div>
          ) : !data || data.content.length === 0 ? (
            <div className="px-6 py-12 text-center text-slate-400">
              No incidents found.{' '}
              {(statusFilter || classificationFilter) && (
                <span>
                  Try clearing your filters.
                </span>
              )}
            </div>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-slate-700/60 text-slate-300 text-left">
                  <th className="px-4 py-3 font-medium">Repository</th>
                  <th className="px-4 py-3 font-medium">Branch</th>
                  <th className="px-4 py-3 font-medium">Status</th>
                  <th className="px-4 py-3 font-medium">Classification</th>
                  <th className="px-4 py-3 font-medium">Created At</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((incident, idx) => (
                  <tr
                    key={incident.id}
                    onClick={() => navigate(`/incidents/${incident.id}`)}
                    className={`cursor-pointer hover:bg-slate-700/50 border-t border-slate-700/50 ${
                      idx % 2 === 0 ? '' : 'bg-slate-800/30'
                    }`}
                  >
                    <td className="px-4 py-3 text-slate-200 font-medium">
                      {incident.repository?.fullName ?? '—'}
                    </td>
                    <td className="px-4 py-3 text-slate-300 font-mono text-xs">
                      {incident.pipelineRun?.branch ?? '—'}
                    </td>
                    <td className="px-4 py-3">
                      <StatusBadge status={incident.status} />
                    </td>
                    <td className="px-4 py-3">
                      {incident.classification ? (
                        <ClassificationBadge type={incident.classification.type} />
                      ) : (
                        <span className="text-slate-500 text-xs">Unclassified</span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-slate-400 text-xs">
                      {new Date(incident.createdAt).toLocaleString()}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        {/* Pagination */}
        {!loading && data && totalPages > 0 && (
          <div className="flex items-center justify-between mt-4">
            <p className="text-sm text-slate-400">
              Page {page + 1} of {totalPages} ({totalElements} total)
            </p>
            <div className="flex gap-2">
              <button
                onClick={() => setPage(p => Math.max(0, p - 1))}
                disabled={page === 0}
                className="text-sm bg-slate-700 hover:bg-slate-600 disabled:opacity-40 disabled:cursor-not-allowed text-slate-200 px-4 py-1.5 rounded"
              >
                ← Prev
              </button>
              <button
                onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
                className="text-sm bg-slate-700 hover:bg-slate-600 disabled:opacity-40 disabled:cursor-not-allowed text-slate-200 px-4 py-1.5 rounded"
              >
                Next →
              </button>
            </div>
          </div>
        )}
      </main>
    </div>
  )
}
