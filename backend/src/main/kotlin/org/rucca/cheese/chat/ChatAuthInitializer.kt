/*
 *  Description: Registers custom auth logic handlers for the chat module.
 *               Called once at startup; no per-request overhead.
 *
 *  Author(s):
 *      Nictheboy Li    <nictheboy@outlook.com>
 *
 */

package org.rucca.cheese.chat

import javax.annotation.PostConstruct
import org.rucca.cheese.auth.AuthorizationService
import org.rucca.cheese.auth.AuthorizedAction
import org.rucca.cheese.common.persistent.IdGetter
import org.rucca.cheese.common.persistent.IdType
import org.springframework.stereotype.Component

@Component
class ChatAuthInitializer(
    private val authorizationService: AuthorizationService,
    private val threadMemberRepository: ThreadMemberRepository,
    private val messageRepository: MessageRepository,
) {
    @PostConstruct
    fun register() {
        // Register the thread-ownership resolver so the built-in "owned"
        // custom logic (AuthorizationService.kt:45) can answer "does user X
        // own resource thread Y?" without an is_thread_owner handler.
        // Must match the resourceType used by @Guard / RolePermissionService
        // ("chat_thread"); otherwise the "owned" predicate can't resolve an owner.
        authorizationService.ownerIds.register("chat_thread") { threadId ->
            threadMemberRepository.findByThreadIdAndRole(threadId, ThreadMemberRole.OWNER)?.userId
                ?: 0
        }

        // is_thread_member: user is any member (member/admin/owner) of a thread.
        // resourceId may be a thread ID or message ID (resolved via resolveThreadId).
        authorizationService.customAuthLogics.register("is_thread_member") {
            userId: IdType,
            _: AuthorizedAction,
            _: String,
            resourceId: IdType?,
            _: Map<String, Any>,
            _: IdGetter?,
            _: Any? ->
            resourceId != null &&
                threadMemberRepository.findByThreadIdAndUserId(
                    resolveThreadId(resourceId) ?: return@register false,
                    userId,
                ) != null
        }

        // is_thread_admin: user is admin or owner of a thread (role >= 1).
        authorizationService.customAuthLogics.register("is_thread_admin") {
            userId: IdType,
            _: AuthorizedAction,
            _: String,
            resourceId: IdType?,
            _: Map<String, Any>,
            _: IdGetter?,
            _: Any? ->
            if (resourceId == null) false
            else {
                val tid = resolveThreadId(resourceId) ?: return@register false
                val m = threadMemberRepository.findByThreadIdAndUserId(tid, userId)
                m != null && (m.role == ThreadMemberRole.ADMIN || m.role == ThreadMemberRole.OWNER)
            }
        }

        // is_message_author: user wrote the message (resourceId = message id).
        authorizationService.customAuthLogics.register("is_message_author") {
            userId: IdType,
            _: AuthorizedAction,
            _: String,
            resourceId: IdType?,
            _: Map<String, Any>,
            _: IdGetter?,
            _: Any? ->
            resourceId != null &&
                messageRepository.findById(resourceId).orElse(null)?.senderId == userId
        }
    }

    // resolveThreadId: if resourceId is a thread ID, return it directly;
    // if it's a message ID, look up the message and return its threadId.
    private fun resolveThreadId(resourceId: Long): Long? {
        val msg = messageRepository.findById(resourceId).orElse(null)
        return if (msg != null) msg.threadId else resourceId
    }
}
