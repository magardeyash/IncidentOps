import { useState, useEffect, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  getIncident,
  getPostmortem,
  updateIncidentStatus,
  addNote,
  generatePostmortem,
  updatePostmortem,
  ApiError,
} from '../api/client'
import type { Incident, Postmortem, IncidentEvent } from '../types'
import StatusBadge from '../components/StatusBadge'
import ClassificationBadge from '../components/ClassificationBadge'
import Toast from '../components/Toast'
import AiAdvisorPanel from '../components/AiAdvisorPanel'

// ─── Confidence Badge ──────────────────────────────────────────────────────
function ConfidenceBadge({ confidence }: { confidence: string }) {
  const styles: Record<string, string> = {
    HIGH: 'bg-green-700 text-green-100',
    MEDIUM: 'bg-amber-600 text-amber-100',
    LOW: 'bg-red-700 text-red-100',
  }
  const cls = styles[confidence] ?? 'bg-slate-600 text-slate-200'
  return (
    <span className={`inline-block px-2 py-0.5 rounded text-xs font-semibold uppercase ${cls}`}>
      {confidence}
    </span>
  )
}

// ─── Event Type Badge ──────────────────────────────────────────────────────
function EventTypeBadge({ eventType }: { eventType: string }) {
  const styles: Record<string, string> = {
    CREATED: 'bg-blue-800 text-blue-200',
    STATE_CHANGED: 'bg-amber-800 text-amber-200',
    NOTE_ADDED: 'bg-slate-700 text-slate-200',
    CLASSIFIED: 'bg-teal-800 text-teal-200',
  }
  const cls = styles[eventType] ?? 'bg-slate-700 text-slate-300'
  return (
    <span className={`inline-block px-2 py-0.5 rounded text-xs font-semibold ${cls}`}>
      {eventType.replace(/_/g, ' ')}
    </span>
  )
}

// ─── Postmortem Section ────────────────────────────────────────────────────
interface PostmortemSectionProps {
  incidentId: string
  postmortem: Postmortem
  onUpdate: (updated: Postmortem) => void
  onError: (msg: string) => void
}

function PostmortemSection({ incidentId, postmortem, onUpdate, onError }: PostmortemSectionProps) {
  const [editing, setEditing] = useState(false)
  const [saving, setSaving] = useState(false)
  const [draft, setDraft] = useState({
    summary: postmortem.summary ?? '',
    timeline: postmortem.timeline ?? '',
    rootCause: postmortem.rootCause ?? '',
    recommendedFollowUp: postmortem.recommendedFollowUp ?? '',
  })

  function handleEdit() {
    setDraft({
      summary: postmortem.summary ?? '',
      timeline: postmortem.timeline ?? '',
      rootCause: postmortem.rootCause ?? '',
      recommendedFollowUp: postmortem.recommendedFollowUp ?? '',
    })
    setEditing(true)
  }

  async function handleSave() {
    setSaving(true)
    try {
      const updated = await updatePostmortem(incidentId, draft)
      onUpdate(updated)
      setEditing(false)
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : 'Failed to save postmortem'
      onError(msg)
    } finally {
      setSaving(false)
    }
  }

  const labelClass = 'text-sm font-semibold text-purple-300 mb-1 block'
  const valueClass = 'text-slate-300 text-sm whitespace-pre-wrap leading-relaxed'
  const textareaClass =
    'w-full bg-slate-700 border border-slate-600 rounded px-3 py-2 text-slate-100 text-sm focus:outline-none focus:border-purple-500 min-h-[80px] resize-y'

  return (
    <div className="bg-slate-800 border border-purple-700/50 rounded-lg p-5">
      <div className="flex items-center justify-between mb-4">
        <h3 className="font-semibold text-purple-300 text-lg">📋 Postmortem</h3>
        <div className="flex gap-2">
          {editing ? (
            <>
              <button
                onClick={() => setEditing(false)}
                disabled={saving}
                className="text-xs bg-slate-700 hover:bg-slate-600 disabled:opacity-50 text-slate-300 px-3 py-1.5 rounded"
              >
                Cancel
              </button>
              <button
                onClick={handleSave}
                disabled={saving}
                className="text-xs bg-purple-700 hover:bg-purple-600 disabled:opacity-50 text-white px-3 py-1.5 rounded font-medium"
              >
                {saving ? 'Saving…' : 'Save'}
              </button>
            </>
          ) : (
            <button
              onClick={handleEdit}
              className="text-xs bg-slate-700 hover:bg-slate-600 text-slate-300 px-3 py-1.5 rounded"
            >
              Edit
            </button>
          )}
        </div>
      </div>

      <div className="space-y-4">
        {/* Summary */}
        <div>
          <label className={labelClass}>Summary</label>
          {editing ? (
            <textarea
              className={textareaClass}
              value={draft.summary}
              onChange={e => setDraft(d => ({ ...d, summary: e.target.value }))}
            />
          ) : (
            <p className={valueClass}>{postmortem.summary ?? <em className="text-slate-500">—</em>}</p>
          )}
        </div>

        {/* Timeline */}
        <div>
          <label className={labelClass}>Timeline</label>
          {editing ? (
            <textarea
              className={textareaClass}
              value={draft.timeline}
              onChange={e => setDraft(d => ({ ...d, timeline: e.target.value }))}
            />
          ) : (
            <p className={valueClass}>{postmortem.timeline ?? <em className="text-slate-500">—</em>}</p>
          )}
        </div>

        {/* Root Cause */}
        <div>
          <label className={labelClass}>Root Cause</label>
          {editing ? (
            <textarea
              className={textareaClass}
              value={draft.rootCause}
              onChange={e => setDraft(d => ({ ...d, rootCause: e.target.value }))}
            />
          ) : (
            <p className={valueClass}>{postmortem.rootCause ?? <em className="text-slate-500">—</em>}</p>
          )}
        </div>

        {/* Recommended Follow-Up */}
        <div>
          <label className={labelClass}>Recommended Follow-Up</label>
          {editing ? (
            <textarea
              className={textareaClass}
              value={draft.recommendedFollowUp}
              onChange={e => setDraft(d => ({ ...d, recommendedFollowUp: e.target.value }))}
            />
          ) : (
            <p className={valueClass}>
              {postmortem.recommendedFollowUp ?? <em className="text-slate-500">—</em>}
            </p>
          )}
        </div>

        <div className="pt-2 border-t border-slate-700 text-xs text-slate-500 flex gap-4">
          <span>Generated: {new Date(postmortem.generatedAt).toLocaleString()}</span>
          {postmortem.editedAt && (
            <span>Last edited: {new Date(postmortem.editedAt).toLocaleString()}</span>
          )}
        </div>
      </div>
    </div>
  )
}

// ─── Main Page ─────────────────────────────────────────────────────────────
export default function IncidentDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()

  const [incident, setIncident] = useState<Incident | null>(null)
  const [postmortem, setPostmortem] = useState<Postmortem | null>(null)
  const [loading, setLoading] = useState(true)
  const [pageError, setPageError] = useState<string | null>(null)

  // Toast state
  const [toast, setToast] = useState<{ message: string; type: 'error' | 'success' | 'info' } | null>(null)

  // Conflict banner (409)
  const [conflictMsg, setConflictMsg] = useState<string | null>(null)

  // Note form
  const [note, setNote] = useState('')
  const [noteLoading, setNoteLoading] = useState(false)

  // Status transition loading
  const [statusLoading, setStatusLoading] = useState(false)

  // Postmortem generation
  const [postmortemLoading, setPostmortemLoading] = useState(false)

  const showToast = (message: string, type: 'error' | 'success' | 'info') => {
    setToast({ message, type })
  }

  // ── Fetch Incident ──────────────────────────────────────────────────────
  const fetchAll = useCallback(async () => {
    if (!id) return
    setLoading(true)
    setPageError(null)
    try {
      const [inc] = await Promise.all([getIncident(id)])
      setIncident(inc)

      // Postmortem – may 404
      try {
        const pm = await getPostmortem(id)
        setPostmortem(pm)
      } catch {
        setPostmortem(null)
      }
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : 'Failed to load incident'
      setPageError(msg)
    } finally {
      setLoading(false)
    }
  }, [id])

  useEffect(() => {
    fetchAll()
  }, [fetchAll])

  // ── Status Transitions ──────────────────────────────────────────────────
  async function handleStatusChange(newStatus: string) {
    if (!incident || !id) return
    setStatusLoading(true)
    setConflictMsg(null)
    try {
      const updated = await updateIncidentStatus(id, newStatus)
      setIncident(updated)
      showToast(`Status updated to ${newStatus}`, 'success')
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 409) {
          setConflictMsg(`Conflict: ${err.message}`)
          // Re-fetch to get fresh version
          fetchAll()
        } else {
          showToast(err.message, 'error')
        }
      } else {
        showToast('Failed to update status', 'error')
      }
    } finally {
      setStatusLoading(false)
    }
  }

  // ── Add Note ────────────────────────────────────────────────────────────
  async function handleAddNote(e: React.FormEvent) {
    e.preventDefault()
    if (!note.trim() || !id) return
    setNoteLoading(true)
    try {
      const updated = await addNote(id, note.trim())
      setIncident(updated)
      setNote('')
      showToast('Note added', 'success')
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : 'Failed to add note'
      showToast(msg, 'error')
    } finally {
      setNoteLoading(false)
    }
  }

  // ── Generate Postmortem ─────────────────────────────────────────────────
  async function handleGeneratePostmortem() {
    if (!id) return
    setPostmortemLoading(true)
    try {
      const pm = await generatePostmortem(id)
      setPostmortem(pm)
      showToast('Postmortem generated', 'success')
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : 'Failed to generate postmortem'
      showToast(msg, 'error')
    } finally {
      setPostmortemLoading(false)
    }
  }

  // ── Render helpers ──────────────────────────────────────────────────────
  if (loading) {
    return (
      <div className="min-h-screen bg-slate-900 text-slate-100 flex items-center justify-center">
        <p className="text-slate-400">Loading incident…</p>
      </div>
    )
  }

  if (pageError || !incident) {
    return (
      <div className="min-h-screen bg-slate-900 text-slate-100 flex items-center justify-center">
        <div className="text-center">
          <p className="text-red-400 mb-4">{pageError ?? 'Incident not found'}</p>
          <button
            onClick={() => navigate('/incidents')}
            className="text-sm text-blue-400 hover:text-blue-300 underline"
          >
            ← Back to incidents
          </button>
        </div>
      </div>
    )
  }

  const isFinal = incident.status === 'RESOLVED' || incident.status === 'IGNORED'
  const canGeneratePostmortem = isFinal

  const sortedEvents = [...(incident.events ?? [])].sort(
    (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
  )

  const labelCls = 'text-xs font-semibold text-slate-400 uppercase tracking-wide mb-1 block'
  const valueCls = 'text-slate-200 text-sm'

  return (
    <div className="min-h-screen bg-slate-900 text-slate-100">
      {/* Toast */}
      {toast && (
        <Toast
          message={toast.message}
          type={toast.type}
          onClose={() => setToast(null)}
        />
      )}

      {/* Header */}
      <header className="bg-slate-800 border-b border-slate-700 px-6 py-4 flex items-center justify-between">
        <div className="flex items-center gap-4">
          <button
            onClick={() => navigate('/incidents')}
            className="text-slate-400 hover:text-slate-100 text-sm flex items-center gap-1"
          >
            ← Back
          </button>
          <div className="h-4 w-px bg-slate-600" />
          <span className="text-lg">🚨</span>
          <h1 className="text-lg font-bold text-slate-100">IncidentOps</h1>
        </div>
      </header>

      <main className="px-6 py-6 max-w-7xl mx-auto space-y-6">
        {/* Conflict Banner */}
        {conflictMsg && (
          <div className="bg-red-900/60 border border-red-600 text-red-200 rounded-lg px-4 py-3 text-sm flex items-center justify-between">
            <span>{conflictMsg}</span>
            <button
              onClick={() => setConflictMsg(null)}
              className="text-red-300 hover:text-red-100 ml-4 text-lg leading-none"
            >
              ×
            </button>
          </div>
        )}

        {/* Incident Header */}
        <div className="bg-slate-800 border border-slate-700 rounded-lg p-5">
          <div className="flex flex-wrap items-center gap-3">
            <h2 className="text-slate-100 font-mono text-sm text-slate-400">ID</h2>
            <span className="font-mono text-sm text-slate-200 bg-slate-700 px-2 py-0.5 rounded">
              {incident.id}
            </span>
            <StatusBadge status={incident.status} />
            <span className="text-xs text-slate-500">v{incident.version}</span>
          </div>
        </div>

        {/* Two-column layout */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Left: Incident Details */}
          <div className="lg:col-span-2 space-y-5">
            {/* Repository & Pipeline */}
            <div className="bg-slate-800 border border-slate-700 rounded-lg p-5">
              <h3 className="font-semibold text-slate-200 mb-4">📁 Repository & Pipeline</h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className={labelCls}>Repository</label>
                  <p className={valueCls}>{incident.repository?.fullName ?? '—'}</p>
                </div>
                <div>
                  <label className={labelCls}>Branch</label>
                  <p className="font-mono text-sm text-slate-200">
                    {incident.pipelineRun?.branch ?? '—'}
                  </p>
                </div>
                <div>
                  <label className={labelCls}>Commit SHA</label>
                  <p className="font-mono text-xs text-slate-300 break-all">
                    {incident.pipelineRun?.commitSha ?? '—'}
                  </p>
                </div>
                <div>
                  <label className={labelCls}>Pipeline Status</label>
                  <p className={valueCls}>{incident.pipelineRun?.status ?? '—'}</p>
                </div>
                <div>
                  <label className={labelCls}>Started At</label>
                  <p className="text-slate-300 text-xs">
                    {incident.pipelineRun?.startedAt
                      ? new Date(incident.pipelineRun.startedAt).toLocaleString()
                      : '—'}
                  </p>
                </div>
                <div>
                  <label className={labelCls}>Finished At</label>
                  <p className="text-slate-300 text-xs">
                    {incident.pipelineRun?.finishedAt
                      ? new Date(incident.pipelineRun.finishedAt).toLocaleString()
                      : '—'}
                  </p>
                </div>
              </div>
            </div>

            {/* Classification */}
            <div className="bg-slate-800 border border-slate-700 rounded-lg p-5">
              <h3 className="font-semibold text-slate-200 mb-4">🔍 Classification</h3>
              {incident.classification ? (
                <div className="space-y-3">
                  <div className="flex flex-wrap items-center gap-3">
                    <ClassificationBadge type={incident.classification.type} />
                    <ConfidenceBadge confidence={incident.classification.confidence} />
                    <span className="text-xs text-slate-500">
                      {new Date(incident.classification.classifiedAt).toLocaleString()}
                    </span>
                  </div>
                  {incident.classification.matchedLogExcerpt && (
                    <div>
                      <label className={labelCls}>Matched Log Excerpt</label>
                      <pre className="bg-slate-900 border border-slate-700 rounded p-3 text-xs text-slate-300 overflow-x-auto whitespace-pre-wrap font-mono">
                        {incident.classification.matchedLogExcerpt}
                      </pre>
                    </div>
                  )}
                </div>
              ) : (
                <p className="text-slate-500 text-sm italic">Not yet classified</p>
              )}
            </div>

            {/* Playbook */}
            {incident.playbook && (
              <div className="bg-slate-800 border border-slate-700 rounded-lg p-5">
                <h3 className="font-semibold text-slate-200 mb-4">📖 Playbook</h3>
                <div className="space-y-2">
                  <div>
                    <label className={labelCls}>Recommended Action</label>
                    <p className={valueCls}>{incident.playbook.recommendedAction}</p>
                  </div>
                  <div>
                    <label className={labelCls}>Description</label>
                    <p className="text-slate-400 text-sm leading-relaxed">
                      {incident.playbook.description}
                    </p>
                  </div>
                </div>
              </div>
            )}

            {/* Assigned Notes */}
            <div className="bg-slate-800 border border-slate-700 rounded-lg p-5">
              <h3 className="font-semibold text-slate-200 mb-3">📝 Assigned Notes</h3>
              {incident.assignedNotes ? (
                <p className="text-slate-300 text-sm whitespace-pre-wrap leading-relaxed">
                  {incident.assignedNotes}
                </p>
              ) : (
                <p className="text-slate-500 text-sm italic">No notes assigned</p>
              )}
            </div>
          </div>

          {/* Right: Actions */}
          <div className="space-y-5">
            {/* Status Transitions */}
            <div className="bg-slate-800 border border-slate-700 rounded-lg p-5">
              <h3 className="font-semibold text-slate-200 mb-4">⚡ Actions</h3>

              {incident.status === 'OPEN' && (
                <button
                  onClick={() => handleStatusChange('INVESTIGATING')}
                  disabled={statusLoading}
                  className="w-full bg-blue-700 hover:bg-blue-600 disabled:opacity-50 text-white font-medium py-2 px-4 rounded text-sm mb-2"
                >
                  {statusLoading ? 'Updating…' : '🔎 Move to Investigating'}
                </button>
              )}

              {incident.status === 'INVESTIGATING' && (
                <div className="space-y-2">
                  <button
                    onClick={() => handleStatusChange('RESOLVED')}
                    disabled={statusLoading}
                    className="w-full bg-green-700 hover:bg-green-600 disabled:opacity-50 text-white font-medium py-2 px-4 rounded text-sm"
                  >
                    {statusLoading ? 'Updating…' : '✅ Mark Resolved'}
                  </button>
                  <button
                    onClick={() => handleStatusChange('IGNORED')}
                    disabled={statusLoading}
                    className="w-full bg-slate-600 hover:bg-slate-500 disabled:opacity-50 text-white font-medium py-2 px-4 rounded text-sm"
                  >
                    {statusLoading ? 'Updating…' : '🚫 Mark Ignored'}
                  </button>
                </div>
              )}

              {isFinal && (
                <p className="text-slate-500 text-sm italic">
                  This incident is in a final state ({incident.status}).
                </p>
              )}
            </div>

            {/* Add Note */}
            <div className="bg-slate-800 border border-slate-700 rounded-lg p-5">
              <h3 className="font-semibold text-slate-200 mb-3">➕ Add Note</h3>
              <form onSubmit={handleAddNote} className="space-y-3">
                <textarea
                  value={note}
                  onChange={e => setNote(e.target.value)}
                  disabled={noteLoading}
                  placeholder="Add a note to this incident…"
                  rows={4}
                  className="w-full bg-slate-700 border border-slate-600 rounded px-3 py-2 text-slate-100 text-sm placeholder-slate-500 focus:outline-none focus:border-blue-500 resize-y disabled:opacity-50"
                />
                <button
                  type="submit"
                  disabled={noteLoading || !note.trim()}
                  className="w-full bg-slate-600 hover:bg-slate-500 disabled:opacity-50 disabled:cursor-not-allowed text-white font-medium py-2 px-4 rounded text-sm"
                >
                  {noteLoading ? 'Saving…' : 'Add Note'}
                </button>
              </form>
            </div>

            {/* Generate Postmortem */}
            {canGeneratePostmortem && !postmortem && (
              <div className="bg-slate-800 border border-slate-700 rounded-lg p-5">
                <h3 className="font-semibold text-slate-200 mb-3">📋 Postmortem</h3>
                <p className="text-slate-400 text-sm mb-3">
                  Generate an AI-assisted postmortem report for this incident.
                </p>
                <button
                  onClick={handleGeneratePostmortem}
                  disabled={postmortemLoading}
                  className="w-full bg-purple-700 hover:bg-purple-600 disabled:opacity-50 disabled:cursor-not-allowed text-white font-medium py-2 px-4 rounded text-sm"
                >
                  {postmortemLoading ? 'Generating…' : '✨ Generate Postmortem'}
                </button>
              </div>
            )}

            {/* Incident metadata */}
            <div className="bg-slate-800 border border-slate-700 rounded-lg p-5">
              <h3 className="font-semibold text-slate-200 mb-3">ℹ️ Metadata</h3>
              <div className="space-y-2 text-xs text-slate-400">
                <div>
                  <span className="font-medium text-slate-500">Created:</span>{' '}
                  {new Date(incident.createdAt).toLocaleString()}
                </div>
                <div>
                  <span className="font-medium text-slate-500">Updated:</span>{' '}
                  {new Date(incident.updatedAt).toLocaleString()}
                </div>
                <div>
                  <span className="font-medium text-slate-500">Version:</span> {incident.version}
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* AI Advisor Panel */}
        <AiAdvisorPanel incidentId={incident.id} />

        {/* Event Timeline */}
        <div className="bg-slate-800 border border-slate-700 rounded-lg p-5">
          <h3 className="font-semibold text-slate-200 mb-4">🕐 Event Timeline</h3>
          {sortedEvents.length === 0 ? (
            <p className="text-slate-500 text-sm italic">No events yet</p>
          ) : (
            <div className="space-y-3">
              {sortedEvents.map((event: IncidentEvent) => (
                <div
                  key={event.id}
                  className="flex gap-4 border-l-2 border-slate-600 pl-4 py-1"
                >
                  <div className="shrink-0 text-xs text-slate-500 pt-0.5 w-36">
                    {new Date(event.createdAt).toLocaleString()}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex flex-wrap items-center gap-2 mb-1">
                      <EventTypeBadge eventType={event.eventType} />
                      {event.fromStatus && event.toStatus && (
                        <span className="text-xs text-slate-400">
                          {event.fromStatus} → {event.toStatus}
                        </span>
                      )}
                    </div>
                    {event.detail && (
                      <p className="text-slate-400 text-xs whitespace-pre-wrap leading-relaxed">
                        {event.detail}
                      </p>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Postmortem Section */}
        {postmortem && (
          <PostmortemSection
            incidentId={incident.id}
            postmortem={postmortem}
            onUpdate={setPostmortem}
            onError={msg => showToast(msg, 'error')}
          />
        )}
      </main>
    </div>
  )
}
