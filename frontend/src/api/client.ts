import type { Incident, IncidentContext, Page, Postmortem } from '../types'

const TOKEN_KEY = 'incidentops_token'

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
    this.name = 'ApiError'
  }
}

function getBaseUrl(): string {
  return import.meta.env.VITE_API_BASE_URL || ''
}

function getToken(): string | null {
  return sessionStorage.getItem(TOKEN_KEY)
}

export async function apiFetch(path: string, options: RequestInit = {}): Promise<Response> {
  const token = getToken()
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> | undefined),
  }
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  const url = `${getBaseUrl()}${path}`
  const response = await fetch(url, { ...options, headers })

  if (response.status === 401) {
    sessionStorage.removeItem(TOKEN_KEY)
    window.location.href = '/login'
    // Return the response anyway to avoid type errors; redirect will happen
    return response
  }

  return response
}

// ---------------------------------------------------------------------------
// Auth
// ---------------------------------------------------------------------------

export async function login(username: string, password: string): Promise<string> {
  const response = await fetch(`${getBaseUrl()}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })

  if (!response.ok) {
    let message = 'Login failed'
    try {
      const data = await response.json()
      message = data.message ?? data.error ?? message
    } catch {
      // ignore parse errors
    }
    throw new ApiError(response.status, message)
  }

  const data = await response.json()
  // Accept token from various response shapes
  const token: string = data.token ?? data.accessToken ?? data.jwt ?? ''
  if (!token) {
    throw new ApiError(200, 'No token in response')
  }
  return token
}

// ---------------------------------------------------------------------------
// Incidents
// ---------------------------------------------------------------------------

export interface ListIncidentsParams {
  page?: number
  size?: number
  status?: string
  classificationType?: string
}

export async function listIncidents(params: ListIncidentsParams = {}): Promise<Page<Incident>> {
  const qs = new URLSearchParams()
  if (params.page !== undefined) qs.set('page', String(params.page))
  if (params.size !== undefined) qs.set('size', String(params.size))
  if (params.status) qs.set('status', params.status)
  if (params.classificationType) qs.set('classificationType', params.classificationType)

  const path = `/incidents${qs.toString() ? `?${qs}` : ''}`
  const response = await apiFetch(path)

  if (!response.ok) {
    let message = 'Failed to load incidents'
    try {
      const data = await response.json()
      message = data.message ?? data.error ?? message
    } catch {
      // ignore
    }
    throw new ApiError(response.status, message)
  }

  return response.json() as Promise<Page<Incident>>
}

export async function getIncident(id: string): Promise<Incident> {
  const response = await apiFetch(`/incidents/${id}`)

  if (!response.ok) {
    let message = 'Failed to load incident'
    try {
      const data = await response.json()
      message = data.message ?? data.error ?? message
    } catch {
      // ignore
    }
    throw new ApiError(response.status, message)
  }

  return response.json() as Promise<Incident>
}

export async function getIncidentContext(id: string): Promise<IncidentContext> {
  const response = await apiFetch(`/incidents/${id}/context`)

  if (!response.ok) {
    let message = 'Failed to load incident context'
    try {
      const data = await response.json()
      message = data.message ?? data.error ?? message
    } catch {
      // ignore
    }
    throw new ApiError(response.status, message)
  }

  return response.json() as Promise<IncidentContext>
}

export async function updateIncidentStatus(
  id: string,
  newStatus: string
): Promise<Incident> {
  const response = await apiFetch(`/incidents/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ newStatus }),
  })

  if (!response.ok) {
    let message = 'Failed to update status'
    try {
      const data = await response.json()
      message = data.message ?? data.error ?? message
    } catch {
      // ignore
    }
    throw new ApiError(response.status, message)
  }

  return response.json() as Promise<Incident>
}

export async function addNote(id: string, note: string): Promise<Incident> {
  const response = await apiFetch(`/incidents/${id}/notes`, {
    method: 'POST',
    body: JSON.stringify({ note }),
  })

  if (!response.ok) {
    let message = 'Failed to add note'
    try {
      const data = await response.json()
      message = data.message ?? data.error ?? message
    } catch {
      // ignore
    }
    throw new ApiError(response.status, message)
  }

  return response.json() as Promise<Incident>
}

// ---------------------------------------------------------------------------
// Postmortem
// ---------------------------------------------------------------------------

export async function generatePostmortem(id: string): Promise<Postmortem> {
  const response = await apiFetch(`/incidents/${id}/postmortem`, {
    method: 'POST',
  })

  if (!response.ok) {
    let message = 'Failed to generate postmortem'
    try {
      const data = await response.json()
      message = data.message ?? data.error ?? message
    } catch {
      // ignore
    }
    throw new ApiError(response.status, message)
  }

  return response.json() as Promise<Postmortem>
}

export async function getPostmortem(id: string): Promise<Postmortem | null> {
  const response = await apiFetch(`/incidents/${id}/postmortem`)

  if (response.status === 404) {
    return null
  }

  if (!response.ok) {
    let message = 'Failed to load postmortem'
    try {
      const data = await response.json()
      message = data.message ?? data.error ?? message
    } catch {
      // ignore
    }
    throw new ApiError(response.status, message)
  }

  return response.json() as Promise<Postmortem>
}

export interface PostmortemUpdatePayload {
  summary?: string
  timeline?: string
  rootCause?: string
  recommendedFollowUp?: string
}

export async function updatePostmortem(
  id: string,
  data: PostmortemUpdatePayload
): Promise<Postmortem> {
  const response = await apiFetch(`/incidents/${id}/postmortem`, {
    method: 'PATCH',
    body: JSON.stringify(data),
  })

  if (!response.ok) {
    let message = 'Failed to update postmortem'
    try {
      const errData = await response.json()
      message = errData.message ?? errData.error ?? message
    } catch {
      // ignore
    }
    throw new ApiError(response.status, message)
  }

  return response.json() as Promise<Postmortem>
}
