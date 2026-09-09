import { useState, useEffect } from 'react'
import { getIncidentContext, ApiError } from '../api/client'
import type { IncidentContext } from '../types'

const AI_KEY_STORAGE = 'incidentops_ai_key'

interface Props {
  incidentId: string
}

function buildSystemPrompt(): string {
  return `You are an expert Site Reliability Engineer (SRE) and DevOps incident responder.
You will be given context about a CI/CD pipeline incident.
Your job is to:
1. Analyze the incident details provided
2. Identify likely root causes based on the classification, log excerpts, and timeline
3. Suggest concrete, actionable remediation steps
4. Recommend follow-up actions to prevent recurrence

Be specific, practical, and concise. Format your response with clear sections:
- **Root Cause Analysis**
- **Immediate Actions**
- **Remediation Steps**
- **Prevention Recommendations**`
}

function buildUserPrompt(ctx: IncidentContext): string {
  const lines: string[] = [
    `## Incident Context`,
    `- **Incident ID**: ${ctx.incidentId}`,
    `- **Status**: ${ctx.status}`,
    `- **Repository**: ${ctx.repositoryFullName}`,
    `- **Branch**: ${ctx.branch}`,
    `- **Commit SHA**: ${ctx.commitSha}`,
    `- **Classification**: ${ctx.classificationType ?? 'Unknown'}`,
    `- **Confidence**: ${ctx.confidence ?? 'N/A'}`,
    `- **Created At**: ${new Date(ctx.createdAt).toLocaleString()}`,
    `- **Updated At**: ${new Date(ctx.updatedAt).toLocaleString()}`,
  ]

  if (ctx.matchedLogExcerpt) {
    lines.push(`\n## Matched Log Excerpt\n\`\`\`\n${ctx.matchedLogExcerpt}\n\`\`\``)
  }

  if (ctx.recommendedAction) {
    lines.push(`\n## Playbook Recommended Action\n${ctx.recommendedAction}`)
  }

  if (ctx.playbookDescription) {
    lines.push(`\n## Playbook Description\n${ctx.playbookDescription}`)
  }

  if (ctx.changedFiles && ctx.changedFiles.length > 0) {
    lines.push(`\n## Changed Files\n${ctx.changedFiles.map(f => `- ${f}`).join('\n')}`)
  }

  if (ctx.timelineSummary && ctx.timelineSummary.length > 0) {
    lines.push(`\n## Event Timeline\n${ctx.timelineSummary.map(t => `- ${t}`).join('\n')}`)
  }

  if (ctx.assignedNotes) {
    lines.push(`\n## Assigned Notes\n${ctx.assignedNotes}`)
  }

  lines.push(`\n---\nPlease analyze this incident and provide your expert recommendations.`)
  return lines.join('\n')
}

export default function AiAdvisorPanel({ incidentId }: Props) {
  const [apiKey, setApiKey] = useState('')
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const stored = sessionStorage.getItem(AI_KEY_STORAGE)
    if (stored) setApiKey(stored)
  }, [])

  async function handleAnalyze() {
    if (!apiKey.trim()) {
      setError('Please enter an API key to enable AI analysis.')
      return
    }

    setLoading(true)
    setError(null)
    setResult(null)

    try {
      // Save key to sessionStorage
      sessionStorage.setItem(AI_KEY_STORAGE, apiKey.trim())

      // Fetch incident context from our backend
      let ctx: IncidentContext
      try {
        ctx = await getIncidentContext(incidentId)
      } catch (e) {
        const msg = e instanceof ApiError ? e.message : 'Failed to fetch incident context'
        throw new Error(msg)
      }

      const systemPrompt = buildSystemPrompt()
      const userPrompt = buildUserPrompt(ctx)

      // Call OpenAI directly from the browser
      const aiResponse = await fetch('https://api.openai.com/v1/chat/completions', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${apiKey.trim()}`,
        },
        body: JSON.stringify({
          model: 'gpt-4o-mini',
          messages: [
            { role: 'system', content: systemPrompt },
            { role: 'user', content: userPrompt },
          ],
          max_tokens: 1500,
          temperature: 0.3,
        }),
      })

      if (!aiResponse.ok) {
        let errMsg = `AI API error: ${aiResponse.status}`
        try {
          const errData = await aiResponse.json()
          errMsg = errData?.error?.message ?? errMsg
        } catch {
          // ignore
        }
        throw new Error(errMsg)
      }

      const aiData = await aiResponse.json()
      const text: string = aiData?.choices?.[0]?.message?.content ?? ''
      if (!text) throw new Error('Empty response from AI')
      setResult(text)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Unknown error occurred')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="border border-purple-700 rounded-lg bg-slate-900 p-5">
      <h3 className="text-purple-300 font-semibold text-lg mb-4">🤖 AI Advisor</h3>

      <div className="space-y-3 mb-4">
        <div>
          <label className="block text-sm text-slate-300 mb-1" htmlFor="ai-key-input">
            OpenAI API Key
          </label>
          <input
            id="ai-key-input"
            type="password"
            value={apiKey}
            onChange={e => setApiKey(e.target.value)}
            placeholder="sk-..."
            className="w-full bg-slate-800 border border-slate-600 rounded px-3 py-2 text-slate-100 text-sm placeholder-slate-500 focus:outline-none focus:border-purple-500"
          />
        </div>

        <p className="text-xs text-slate-500 leading-relaxed">
          🔒 Your API key is stored only in sessionStorage and sent directly to the AI provider. It
          never touches the IncidentOps backend.
        </p>

        <button
          onClick={handleAnalyze}
          disabled={loading || !apiKey.trim()}
          className="w-full bg-purple-700 hover:bg-purple-600 disabled:opacity-50 disabled:cursor-not-allowed text-white font-medium py-2 px-4 rounded text-sm"
        >
          {loading ? 'Analyzing…' : 'Analyze with AI'}
        </button>
      </div>

      {!apiKey.trim() && !result && !error && (
        <p className="text-sm text-slate-500 italic">
          Enter an API key to enable AI analysis.
        </p>
      )}

      {error && (
        <div className="mt-3 bg-red-900/50 border border-red-700 rounded p-3 text-red-200 text-sm">
          {error}
        </div>
      )}

      {loading && (
        <div className="mt-3 text-purple-400 text-sm">Fetching context and consulting AI…</div>
      )}

      {result && (
        <div className="mt-4">
          <div className="flex items-center gap-2 mb-2">
            <span className="text-xs font-bold uppercase tracking-widest text-purple-400 border border-purple-600 rounded px-2 py-0.5">
              AI-GENERATED SUGGESTION
            </span>
          </div>
          <div className="bg-slate-800 border border-purple-700/50 rounded p-4 text-slate-200 text-sm whitespace-pre-wrap leading-relaxed">
            {result}
          </div>
        </div>
      )}
    </div>
  )
}
