// Document API (nt backend). Documents are git-backed — there is no document
// table. One GET on /teams/{id}/docs serves the tree, a single file, its history
// and a diff via query flags; PUT/PATCH/DELETE write/move/delete. See DocsController.
import { ntGet, ntPutRaw, ntPatch, ntDelete, qs } from "@/lib/ntApi";

export interface CommitInfo {
  sha: string;
  message: string;
  author: string;
  timestamp: number;
}

/** A node in a team's document tree — a file or a folder. Optional fields are
 *  only present when the request asked for them (content / history / diff /
 *  recursive children). */
export interface DocNode {
  path: string;
  isFolder: boolean;
  commitSha?: string;
  children?: DocNode[];
  content?: string;
  history?: CommitInfo[];
  diff?: string;
}

/** The leaf name of a doc path ("" → "/", "a/b/c.md" → "c.md"). */
export function baseName(path: string): string {
  if (!path) return "/";
  const i = path.lastIndexOf("/");
  return i < 0 ? path : path.slice(i + 1);
}

/** The parent folder path of a doc path ("a/b/c.md" → "a/b", "a.md" → ""). */
export function parentPath(path: string): string {
  const i = path.lastIndexOf("/");
  return i < 0 ? "" : path.slice(0, i);
}

/**
 * Fetch a node. path="" is the repo root. Flags:
 *  - recursive: expand the whole subtree (folders only)
 *  - content:   include file content
 *  - history:   include the git log for the path
 *  - diff:      include that commit's unified diff
 */
export function getDoc(
  teamId: number,
  path: string,
  opts: {
    recursive?: boolean;
    content?: boolean;
    history?: boolean;
    diff?: string;
  } = {},
): Promise<DocNode> {
  return ntGet<DocNode>(`/teams/${teamId}/docs${qs({ path, ...opts })}`);
}

/** Create or overwrite a file (idempotent for identical content). */
export function writeDoc(
  teamId: number,
  path: string,
  content: string,
): Promise<DocNode> {
  return ntPutRaw<DocNode>(`/teams/${teamId}/docs${qs({ path })}`, content);
}

export function moveDoc(
  teamId: number,
  path: string,
  newPath: string,
): Promise<DocNode> {
  return ntPatch<DocNode>(`/teams/${teamId}/docs${qs({ path })}`, { newPath });
}

export function deleteDoc(teamId: number, path: string): Promise<void> {
  return ntDelete<void>(`/teams/${teamId}/docs${qs({ path })}`);
}

// Git can't track an empty directory, so a "folder" only exists once it holds a
// file. To let users create an empty folder we drop a hidden placeholder inside
// it; the UI filters these out and renders the folder instead.
export const KEEP_FILE = ".gitkeep";

export function isKeepFile(path: string): boolean {
  return baseName(path) === KEEP_FILE;
}

/** Create an (otherwise empty) folder by committing its placeholder file. */
export function createFolder(
  teamId: number,
  folderPath: string,
): Promise<DocNode> {
  const clean = folderPath.replace(/\/+$/, "");
  return writeDoc(teamId, `${clean}/${KEEP_FILE}`, "");
}

/** Every file path under [node] (a file yields itself). Includes placeholders. */
export function descendantFiles(node: DocNode): string[] {
  if (!node.isFolder) return [node.path];
  const out: string[] = [];
  for (const child of node.children ?? []) out.push(...descendantFiles(child));
  return out;
}

/**
 * Delete a file or an entire folder. Folders aren't real git objects, so we
 * delete every file underneath (the tree node must be loaded recursively).
 */
export async function deletePath(teamId: number, node: DocNode): Promise<void> {
  for (const file of descendantFiles(node)) {
    await deleteDoc(teamId, file);
  }
}

/**
 * Rename/move a file or folder to [newPath]. For a folder, every descendant
 * file is re-based from the old prefix onto the new one.
 */
export async function movePath(
  teamId: number,
  node: DocNode,
  newPath: string,
): Promise<void> {
  const to = newPath.replace(/^\/+|\/+$/g, "");
  if (!node.isFolder) {
    await moveDoc(teamId, node.path, to);
    return;
  }
  for (const file of descendantFiles(node)) {
    const rel = file.slice(node.path.length); // begins with "/"
    await moveDoc(teamId, file, `${to}${rel}`);
  }
}
