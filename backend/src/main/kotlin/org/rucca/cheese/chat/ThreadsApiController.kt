package org.rucca.cheese.chat

import org.rucca.cheese.api.ThreadsApi
import org.rucca.cheese.auth.AuthenticationService
import org.rucca.cheese.auth.annotation.Guard
import org.rucca.cheese.auth.annotation.ResourceId
import org.rucca.cheese.model.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
class ThreadsApiController(
    private val threadService: ThreadService,
    private val authenticationService: AuthenticationService,
) : ThreadsApi {

    @Guard("enumerate", "chat_thread")
    override fun listThreads(
        pageStart: Long?,
        pageSize: Int,
    ): ResponseEntity<ListThreadsResponseDTO> {
        val userId = authenticationService.getCurrentUserId()
        val (threads, page) = threadService.listThreads(userId, pageStart, pageSize)
        return ResponseEntity.ok(ListThreadsResponseDTO(threads = threads, page = page))
    }

    @Guard("create", "chat_thread")
    override fun createThread(
        createThreadRequestDTO: CreateThreadRequestDTO?
    ): ResponseEntity<ThreadDTO> {
        val body = createThreadRequestDTO ?: return ResponseEntity.badRequest().build()
        val userId = authenticationService.getCurrentUserId()
        return ResponseEntity(threadService.createThread(userId, body), HttpStatus.CREATED)
    }

    @Guard("read", "chat_thread")
    override fun getThread(
        @PathVariable("id") @ResourceId id: Long
    ): ResponseEntity<ThreadDetailDTO> = ResponseEntity.ok(threadService.getThread(id))

    @Guard("update", "chat_thread")
    override fun renameThread(
        @PathVariable("id") @ResourceId id: Long,
        dto: RenameThreadRequestDTO?,
    ): ResponseEntity<ThreadDTO> = ResponseEntity.ok(threadService.renameThread(id, dto!!))

    @Guard("delete", "chat_thread")
    override fun dissolveThread(@PathVariable("id") @ResourceId id: Long): ResponseEntity<Unit> {
        threadService.dissolveThread(id)
        return ResponseEntity(HttpStatus.NO_CONTENT)
    }

    @Guard("read", "chat_message")
    override fun listMessages(
        @PathVariable("id") @ResourceId id: Long,
        pageStart: Long?,
        pageSize: Int,
    ): ResponseEntity<ListMessagesResponseDTO> {
        val (messages, page) = threadService.listMessages(id, pageStart, pageSize)
        return ResponseEntity.ok(ListMessagesResponseDTO(messages = messages, page = page))
    }

    @Guard("create", "chat_message")
    override fun postMessage(
        @PathVariable("id") @ResourceId id: Long,
        dto: PostMessageRequestDTO?,
    ): ResponseEntity<MessageDTO> {
        val userId = authenticationService.getCurrentUserId()
        return ResponseEntity(threadService.postMessage(id, userId, dto!!), HttpStatus.CREATED)
    }

    @Guard("read", "chat_thread")
    override fun listThreadMembers(
        @PathVariable("id") @ResourceId id: Long
    ): ResponseEntity<List<ThreadMemberDTO>> = ResponseEntity.ok(threadService.listMembers(id))

    @Guard("update", "chat_thread")
    override fun addThreadMember(
        @PathVariable("id") @ResourceId id: Long,
        dto: AddMemberRequestDTO?,
    ): ResponseEntity<Unit> {
        val body = dto ?: return ResponseEntity.badRequest().build()
        threadService.addMember(id, body.userId, body.role?.toDomain() ?: ThreadMemberRole.MEMBER)
        return ResponseEntity(HttpStatus.NO_CONTENT)
    }

    @Guard("update", "chat_thread")
    override fun removeThreadMember(
        @PathVariable("id") @ResourceId id: Long,
        @PathVariable("userId") userId: Long,
    ): ResponseEntity<Unit> {
        threadService.removeMember(id, userId)
        return ResponseEntity(HttpStatus.NO_CONTENT)
    }

    @Guard("update", "chat_thread")
    override fun changeThreadMemberRole(
        @PathVariable("id") @ResourceId id: Long,
        @PathVariable("userId") userId: Long,
        dto: ChangeRoleRequestDTO?,
    ): ResponseEntity<Unit> {
        threadService.changeMemberRole(id, userId, dto!!.role.toDomain())
        return ResponseEntity(HttpStatus.NO_CONTENT)
    }
}
