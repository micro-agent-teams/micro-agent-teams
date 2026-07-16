// Team API (nt backend). Mirrors NT-API.yml /teams* exactly.
import { ntGet, ntPost, ntPatch, ntDelete, qs, type Page } from "@/lib/ntApi";

export type Role = "OWNER" | "ADMIN" | "MEMBER";

export interface Team {
  id: number;
  name: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface TeamMember {
  userId: number;
  nickname?: string;
  role: Role;
}

export interface TeamDetail {
  team: Team;
  members: TeamMember[];
}

export interface ListTeamsResponse {
  teams: Team[];
  page: Page;
}

export function listTeams(
  params: {
    role?: Role;
    page_start?: number;
    page_size?: number;
  } = {},
): Promise<ListTeamsResponse> {
  return ntGet<ListTeamsResponse>(`/teams${qs(params)}`);
}

export function createTeam(name: string): Promise<Team> {
  return ntPost<Team>("/teams", { name });
}

export function getTeam(id: number): Promise<TeamDetail> {
  return ntGet<TeamDetail>(`/teams/${id}`);
}

export function renameTeam(id: number, name: string): Promise<Team> {
  return ntPatch<Team>(`/teams/${id}`, { name });
}

export function deleteTeam(id: number): Promise<void> {
  return ntDelete<void>(`/teams/${id}`);
}

export function listMembers(id: number): Promise<TeamMember[]> {
  return ntGet<TeamMember[]>(`/teams/${id}/members`);
}

export function addMember(
  id: number,
  userId: number,
  role: Role,
): Promise<void> {
  return ntPost<void>(`/teams/${id}/members`, { userId, role });
}

export function changeMemberRole(
  id: number,
  userId: number,
  role: Role,
): Promise<void> {
  return ntPatch<void>(`/teams/${id}/members/${userId}`, { role });
}

export function removeMember(id: number, userId: number): Promise<void> {
  return ntDelete<void>(`/teams/${id}/members/${userId}`);
}
