/*
 *  Description: This file implements the DocumentService class. Documents live in
 *               each team's git repository (no database table); this service shapes
 *               the flat git tree into a nested Document model and performs the
 *               write operations (create/overwrite/move/delete) as git commits.
 *
 *  Author(s):
 *      Nictheboy Li    <nictheboy@outlook.com>
 *
 */

package org.rucca.cheese.document

import com.fasterxml.jackson.annotation.JsonInclude
import org.rucca.cheese.common.persistent.IdType
import org.rucca.cheese.git.CommitInfo
import org.rucca.cheese.git.GitService
import org.rucca.cheese.git.NoSuchFileException
import org.rucca.cheese.git.TreeEntry
import org.springframework.stereotype.Service

/**
 * A node in a team's document tree — a file or a folder. Optional fields are only populated when
 * the request asks for them (content / history / diff / recursive children), so one endpoint can
 * serve the tree, a single file, its git log and a diff without a separate endpoint per view. Null
 * fields are omitted from JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class DocNode(
    val path: String,
    val isFolder: Boolean,
    val commitSha: String? = null,
    val children: List<DocNode>? = null,
    val content: String? = null,
    val history: List<CommitInfo>? = null,
    val diff: String? = null,
)

@Service
class DocumentService(private val gitService: GitService) {

    /**
     * Returns the document at [path] (or the repo root when [path] is empty).
     * - a folder/root carries [DocNode.children] — the whole subtree when [recursive], otherwise
     *   just its immediate entries (folders left unexpanded for lazy loading);
     * - a file carries [DocNode.content] when [withContent];
     * - [withHistory] adds the git log for the path, [diffSha] adds that commit's diff.
     */
    fun getDocument(
        teamId: IdType,
        path: String,
        recursive: Boolean,
        withContent: Boolean,
        withHistory: Boolean,
        diffSha: String?,
    ): DocNode {
        val entries = gitService.refreshIndex(teamId)
        val history =
            if (withHistory && path.isNotEmpty()) gitService.getHistory(teamId, path) else null
        val diff = diffSha?.let { gitService.getDiff(teamId, it) }

        val fileEntry = if (path.isNotEmpty()) entries.find { it.path == path } else null
        if (fileEntry != null) {
            return DocNode(
                path = path,
                isFolder = false,
                commitSha = fileEntry.commitSha,
                content = if (withContent) gitService.getContent(teamId, path) else null,
                history = history,
                diff = diff,
            )
        }

        val under = if (path.isEmpty()) entries else entries.filter { it.path.startsWith("$path/") }
        if (path.isNotEmpty() && under.isEmpty()) throw NoSuchFileException(path)
        return DocNode(
            path = path,
            isFolder = true,
            children = buildTree(strip(under, path), path, recursive),
            history = history,
            diff = diff,
        )
    }

    /** Create or overwrite a file (idempotent for identical content). */
    fun writeDocument(teamId: IdType, path: String, content: String, authorId: IdType): DocNode {
        val commit = gitService.updateDocument(teamId, path, content, "user-$authorId")
        return DocNode(path = path, isFolder = false, commitSha = commit.sha, content = content)
    }

    fun moveDocument(teamId: IdType, from: String, to: String, authorId: IdType): DocNode {
        val commit = gitService.moveDocument(teamId, from, to, "user-$authorId")
        return DocNode(
            path = to,
            isFolder = false,
            commitSha = commit.sha,
            content = gitService.getContent(teamId, to),
        )
    }

    fun deleteDocument(teamId: IdType, path: String, authorId: IdType) {
        gitService.deleteDocument(teamId, path, "user-$authorId")
    }

    // -- tree construction -------------------------------------------------

    private fun join(prefix: String, name: String) = if (prefix.isEmpty()) name else "$prefix/$name"

    /** Re-roots [entries] so their paths are relative to [prefix]. */
    private fun strip(entries: List<TreeEntry>, prefix: String): List<TreeEntry> =
        if (prefix.isEmpty()) entries
        else entries.map { it.copy(path = it.path.removePrefix("$prefix/")) }

    private fun buildTree(
        entries: List<TreeEntry>,
        prefix: String,
        recursive: Boolean,
    ): List<DocNode> {
        val files = mutableListOf<DocNode>()
        val dirs = linkedMapOf<String, MutableList<TreeEntry>>()
        for (e in entries) {
            val slash = e.path.indexOf('/')
            if (slash < 0) {
                files.add(DocNode(join(prefix, e.path), isFolder = false, commitSha = e.commitSha))
            } else {
                val dirName = e.path.substring(0, slash)
                dirs
                    .getOrPut(dirName) { mutableListOf() }
                    .add(e.copy(path = e.path.substring(slash + 1)))
            }
        }
        val folders =
            dirs.map { (dirName, children) ->
                val full = join(prefix, dirName)
                DocNode(
                    path = full,
                    isFolder = true,
                    children = if (recursive) buildTree(children, full, true) else null,
                )
            }
        return folders + files
    }
}
