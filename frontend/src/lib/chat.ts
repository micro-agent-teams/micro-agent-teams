// Chat API (nt backend). Mirrors NT-API.yml /threads* exactly.
import { ntGet, ntPost, ntPatch, ntDelete, qs, type Page } from "@/lib/ntApi";
import type { Role } from "@/lib/teams";

export interface Thread {
  id: number;
  title?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface ThreadMember {
  id: number;
  threadId: number;
  userId: number;
  role: Role;
  joinedAt: string;
}

export interface ThreadDetail {
  thread: Thread;
  members: ThreadMember[];
}

export interface Message {
  id: number;
  threadId: number;
  senderId: number;
  content: string;
  createdAt: string;
  editedAt?: string;
}

export interface ListThreadsResponse {
  threads: Thread[];
  page: Page;
}

export interface ListMessagesResponse {
  messages: Message[];
  page: Page;
}

export function listThreads(
  params: {
    page_start?: number;
    page_size?: number;
  } = {},
): Promise<ListThreadsResponse> {
  return ntGet<ListThreadsResponse>(`/threads${qs(params)}`);
}

export function createThread(
  title: string,
  memberIds?: number[],
): Promise<Thread> {
  return ntPost<Thread>("/threads", { title, memberIds });
}

export function getThread(id: number): Promise<ThreadDetail> {
  return ntGet<ThreadDetail>(`/threads/${id}`);
}

export function renameThread(id: number, title: string): Promise<Thread> {
  return ntPatch<Thread>(`/threads/${id}`, { title });
}

export function dissolveThread(id: number): Promise<void> {
  return ntDelete<void>(`/threads/${id}`);
}

export function listMessages(
  id: number,
  params: { page_start?: number; page_size?: number } = {},
): Promise<ListMessagesResponse> {
  return ntGet<ListMessagesResponse>(`/threads/${id}/messages${qs(params)}`);
}

export function postMessage(id: number, content: string): Promise<Message> {
  return ntPost<Message>(`/threads/${id}/messages`, { content });
}

export function listThreadMembers(id: number): Promise<ThreadMember[]> {
  return ntGet<ThreadMember[]>(`/threads/${id}/members`);
}

export function addThreadMember(
  id: number,
  userId: number,
  role?: Role,
): Promise<void> {
  return ntPost<void>(`/threads/${id}/members`, { userId, role });
}

export function changeThreadMemberRole(
  id: number,
  userId: number,
  role: Role,
): Promise<void> {
  return ntPatch<void>(`/threads/${id}/members/${userId}`, { role });
}

export function removeThreadMember(id: number, userId: number): Promise<void> {
  return ntDelete<void>(`/threads/${id}/members/${userId}`);
}
