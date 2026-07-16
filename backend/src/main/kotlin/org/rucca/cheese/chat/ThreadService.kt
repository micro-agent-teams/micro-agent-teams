package org.rucca.cheese.chat

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.rucca.cheese.common.error.NotFoundError
import org.rucca.cheese.common.helper.PageHelper
import org.rucca.cheese.model.*
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class ThreadService(
    private val threadRepository: ThreadRepository,
    private val threadMemberRepository: ThreadMemberRepository,
    private val messageRepository: MessageRepository,
    private val messagingTemplate: SimpMessagingTemplate?,
) {
    fun listThreads(userId: Long, pageStart: Long?, pageSize: Int): Pair<List<ThreadDTO>, PageDTO> {
        val threads = threadRepository.threadsForUser(userId).sortedBy { it.id }
        val (page, pageInfo) =
            PageHelper.pageFromAll(threads, pageStart, pageSize, { it.id!! }, null)
        return page.map { it.toDTO() } to pageInfo
    }

    fun createThread(userId: Long, body: CreateThreadRequestDTO): ThreadDTO {
        val t = ThreadEntity().apply { title = body.title }
        threadRepository.save(t)
        threadMemberRepository.save(
            ThreadMemberEntity().apply {
                threadId = t.id
                this.userId = userId
                role = ThreadMemberRole.OWNER
            }
        )
        body.memberIds?.forEach { mid ->
            if (mid != userId)
                threadMemberRepository.save(
                    ThreadMemberEntity().apply {
                        threadId = t.id
                        this.userId = mid
                        role = ThreadMemberRole.MEMBER
                    }
                )
        }
        return t.toDTO()
    }

    fun getThread(threadId: Long): ThreadDetailDTO {
        val t =
            threadRepository.findById(threadId).orElseThrow { NotFoundError("thread", threadId) }
        return ThreadDetailDTO(
            thread = t.toDTO(),
            members = threadMemberRepository.findByThreadId(threadId).map { it.toDTO() },
        )
    }

    fun renameThread(threadId: Long, body: RenameThreadRequestDTO): ThreadDTO {
        val t =
            threadRepository.findById(threadId).orElseThrow { NotFoundError("thread", threadId) }
        t.title = body.title
        threadRepository.save(t)
        return t.toDTO()
    }

    fun dissolveThread(threadId: Long) {
        val t =
            threadRepository.findById(threadId).orElseThrow { NotFoundError("thread", threadId) }
        t.deletedAt = LocalDateTime.now()
        threadRepository.save(t)
    }

    fun listMessages(
        threadId: Long,
        pageStart: Long?,
        pageSize: Int,
    ): Pair<List<MessageDTO>, PageDTO> {
        val messages = messageRepository.findByThreadIdAndDeletedAtIsNullOrderById(threadId)
        val (page, pageInfo) =
            PageHelper.pageFromAll(messages, pageStart, pageSize, { it.id!! }, null)
        return page.map { it.toDTO() } to pageInfo
    }

    fun postMessage(threadId: Long, userId: Long, body: PostMessageRequestDTO): MessageDTO {
        val m =
            MessageEntity().apply {
                this.threadId = threadId
                senderId = userId
                content = body.content
            }
        messageRepository.save(m)
        val dto = m.toDTO()
        messagingTemplate?.convertAndSend("/topic/thread/$threadId", dto)
        return dto
    }

    fun listMembers(threadId: Long): List<ThreadMemberDTO> =
        threadMemberRepository.findByThreadId(threadId).map { it.toDTO() }

    fun addMember(threadId: Long, userId: Long, role: ThreadMemberRole) {
        val existing = threadMemberRepository.findByThreadIdAndUserId(threadId, userId)
        if (existing != null) {
            existing.role = role
            threadMemberRepository.save(existing)
        } else {
            threadMemberRepository.save(
                ThreadMemberEntity().apply {
                    this.threadId = threadId
                    this.userId = userId
                    this.role = role
                }
            )
        }
    }

    fun removeMember(threadId: Long, userId: Long) {
        threadMemberRepository.deleteByThreadIdAndUserId(threadId, userId)
    }

    fun changeMemberRole(threadId: Long, userId: Long, role: ThreadMemberRole) {
        val m =
            threadMemberRepository.findByThreadIdAndUserId(threadId, userId)
                ?: throw NotFoundError("member", userId)
        m.role = role
        threadMemberRepository.save(m)
    }
}

fun ThreadEntity.toDTO() =
    ThreadDTO(
        id = id!!,
        title = title,
        createdAt = createdAt?.atOffset(ZoneOffset.UTC) ?: OffsetDateTime.now(),
        updatedAt = updatedAt?.atOffset(ZoneOffset.UTC),
    )

fun ThreadMemberEntity.toDTO() =
    ThreadMemberDTO(
        id = id!!,
        threadId = threadId!!,
        userId = userId!!,
        role = role.toDTO(),
        joinedAt = createdAt?.atOffset(ZoneOffset.UTC) ?: OffsetDateTime.now(),
    )

fun MessageEntity.toDTO() =
    MessageDTO(
        id = id!!,
        threadId = threadId!!,
        senderId = senderId!!,
        content = content ?: "",
        createdAt = createdAt?.atOffset(ZoneOffset.UTC) ?: OffsetDateTime.now(),
        editedAt = editedAt?.atOffset(ZoneOffset.UTC),
    )
