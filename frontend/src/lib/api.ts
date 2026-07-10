// Client for the auth backend's /users/... endpoints, proxied at /api (see
// vite.config.ts / nginx — same origin, so the httpOnly REFRESH_TOKEN cookie
// rides along automatically). Field names mirror the backend's DTOs exactly.
const BASE = "/api";

export interface User {
  id: number;
  username: string;
  nickname: string;
  avatarId: number;
  intro: string;
  follow_count: number;
  fans_count: number;
  is_follow: boolean;
}

interface Envelope<T> {
  code: number;
  message: string;
  data: T;
}

export class ApiError extends Error {
  status: number;

  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

async function request<T>(
  path: string,
  init: RequestInit & { accessToken?: string } = {},
): Promise<T> {
  const { accessToken, ...rest } = init;
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  };
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  const res = await fetch(`${BASE}${path}`, {
    credentials: "include", // send/receive the httpOnly REFRESH_TOKEN cookie
    ...rest,
    headers,
  });
  const body = (await res.json().catch(() => null)) as Envelope<T> | null;
  if (!res.ok || !body) {
    throw new ApiError(body?.message ?? `HTTP ${res.status}`, res.status);
  }
  return body.data;
}

export function sendEmailVerifyCode(email: string): Promise<void> {
  return request<void>("/users/verify/email", {
    method: "POST",
    body: JSON.stringify({ email }),
  });
}

export function register(input: {
  username: string;
  nickname: string;
  password: string;
  email: string;
  emailCode: string;
}): Promise<{ user: User; accessToken: string }> {
  return request("/users/", { method: "POST", body: JSON.stringify(input) });
}

export function login(
  username: string,
  password: string,
): Promise<{ user: User; accessToken: string }> {
  return request("/users/auth/login", {
    method: "POST",
    body: JSON.stringify({ username, password }),
  });
}

export function refreshToken(): Promise<{ user: User; accessToken: string }> {
  return request("/users/auth/refresh-token", { method: "POST" });
}

export function logout(): Promise<void> {
  return request<void>("/users/auth/logout", { method: "POST" });
}

export function getMe(accessToken: string): Promise<{ user: User }> {
  return request("/users/me", { accessToken });
}
