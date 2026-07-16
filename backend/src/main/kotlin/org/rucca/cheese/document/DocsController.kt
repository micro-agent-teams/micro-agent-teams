/*
 *  Description: This file defines the DocsController class. It exposes a compact,
 *               query-parameterised API over each team's git document repository
 *               under /teams/{id}/docs: one GET serves the tree, a single file,
 *               its git history and a diff (selected by query flags), plus
 *               PUT/PATCH/DELETE for write/move/delete. The git tree is the source
 *               of truth — there is no document table.
 *
 *  Author(s):
 *      Nictheboy Li    <nictheboy@outlook.com>
 *
 */

package org.rucca.cheese.document

import org.rucca.cheese.auth.AuthenticationService
import org.rucca.cheese.auth.annotation.Guard
import org.rucca.cheese.auth.annotation.ResourceId
import org.rucca.cheese.common.error.BadRequestError
import org.rucca.cheese.common.persistent.IdType
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
class DocsController(
    private val documentService: DocumentService,
    private val authenticationService: AuthenticationService,
) {
    private fun currentUserId(): IdType = authenticationService.getCurrentUserId()

    /** Rejects paths that could escape the repo. Empty path is allowed (= repo root). */
    private fun checkPath(path: String) {
        val unsafe =
            path.startsWith("/") || path.startsWith("\\") || path.split('/').any { it == ".." }
        if (unsafe) throw BadRequestError("unsafe path: $path")
    }

    private fun requirePath(path: String) {
        if (path.isBlank()) throw BadRequestError("path is required")
    }

    @Guard("read", "team_document")
    @GetMapping("/teams/{id}/docs")
    fun getDocuments(
        @PathVariable("id") @ResourceId id: IdType,
        @RequestParam(value = "path", required = false, defaultValue = "") path: String,
        @RequestParam(value = "recursive", required = false, defaultValue = "false")
        recursive: Boolean,
        @RequestParam(value = "content", required = false, defaultValue = "false") content: Boolean,
        @RequestParam(value = "history", required = false, defaultValue = "false") history: Boolean,
        @RequestParam(value = "diff", required = false) diff: String?,
    ): ResponseEntity<DocNode> {
        checkPath(path)
        return ResponseEntity.ok(
            documentService.getDocument(id, path, recursive, content, history, diff)
        )
    }

    @Guard("create", "team_document")
    @PutMapping("/teams/{id}/docs")
    fun writeDocument(
        @PathVariable("id") @ResourceId id: IdType,
        @RequestParam("path") path: String,
        @RequestBody(required = false) content: String?,
    ): ResponseEntity<DocNode> {
        checkPath(path)
        requirePath(path)
        return ResponseEntity.ok(
            documentService.writeDocument(id, path, content ?: "", currentUserId())
        )
    }

    @Guard("update", "team_document")
    @PatchMapping("/teams/{id}/docs")
    fun moveDocument(
        @PathVariable("id") @ResourceId id: IdType,
        @RequestParam("path") path: String,
        @RequestBody body: MoveDocumentBody,
    ): ResponseEntity<DocNode> {
        checkPath(path)
        requirePath(path)
        checkPath(body.newPath)
        requirePath(body.newPath)
        return ResponseEntity.ok(
            documentService.moveDocument(id, path, body.newPath, currentUserId())
        )
    }

    @Guard("delete", "team_document")
    @DeleteMapping("/teams/{id}/docs")
    fun deleteDocument(
        @PathVariable("id") @ResourceId id: IdType,
        @RequestParam("path") path: String,
    ): ResponseEntity<Unit> {
        checkPath(path)
        requirePath(path)
        documentService.deleteDocument(id, path, currentUserId())
        return ResponseEntity(HttpStatus.NO_CONTENT)
    }

    data class MoveDocumentBody(val newPath: String)
}
