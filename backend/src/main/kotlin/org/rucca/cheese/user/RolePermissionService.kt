/*
 *  Description: This file implements the RolePermissionService class.
 *               It provides the permissions for different roles.
 *
 *  Author(s):
 *      Nictheboy Li    <nictheboy@outlook.com>
 *      nameisyui
 *
 */

package org.rucca.cheese.user

import org.rucca.cheese.auth.Authorization
import org.rucca.cheese.auth.AuthorizedResource
import org.rucca.cheese.auth.Permission
import org.rucca.cheese.common.persistent.IdType
import org.springframework.stereotype.Service

@Service
class RolePermissionService {
    fun getAuthorizationForUserWithRole(userId: IdType, role: String): Authorization {
        return when (role) {
            "standard-user" -> getAuthorizationForStandardUser(userId)
            else -> throw IllegalArgumentException("Role '$role' is not supported")
        }
    }

    // The original space/team/task/ai:quota permission entries were removed along
    // with the business modules they gated (nothing left registers their
    // resourceType or the custom logics like "is-space-admin"/"is-team-admin" they
    // referenced — keeping them would just be dead, misleading rules). See
    // ~/work/ref-study/cheese-backend-nt for the full original set as a reference
    // when writing authorization for our own modules; the auth framework itself
    // (AuthorizationService, AuthorizationAspect, the Permission/AuthorizedResource
    // model, custom-logic expressions) is untouched and still the pattern to follow.
    fun getAuthorizationForStandardUser(userId: IdType): Authorization {
        return Authorization(
            userId = userId,
            permissions =
                listOf(
                    // Minimal auth-chain smoke-test endpoint.
                    Permission(
                        authorizedActions = listOf("query"),
                        authorizedResource = AuthorizedResource(types = listOf("ping")),
                    ),

                    // -- Chat: threads ---------------------------------------------------

                    // List my threads: any authenticated user (service query is
                    // self-scoping to the user's own memberships anyway).
                    Permission(
                        authorizedActions = listOf("enumerate"),
                        authorizedResource = AuthorizedResource(types = listOf("chat_thread")),
                    ),
                    // Create a thread: any authenticated user.
                    Permission(
                        authorizedActions = listOf("create"),
                        authorizedResource = AuthorizedResource(types = listOf("chat_thread")),
                    ),
                    // Get thread detail: members only.
                    Permission(
                        authorizedActions = listOf("read"),
                        authorizedResource = AuthorizedResource(types = listOf("chat_thread")),
                        customLogic = "is_thread_member",
                    ),
                    // Rename thread: admin+.
                    Permission(
                        authorizedActions = listOf("update"),
                        authorizedResource = AuthorizedResource(types = listOf("chat_thread")),
                        customLogic = "is_thread_admin",
                    ),
                    // Dissolve thread: owner only.
                    Permission(
                        authorizedActions = listOf("delete"),
                        authorizedResource = AuthorizedResource(types = listOf("chat_thread")),
                        customLogic = "owned",
                    ),
                    // Add/remove members, change role: guarded by admin/owner custom logics.
                    // (update action on chat_thread with is_thread_admin or is_thread_owner
                    // — the individual operations below share this guard now.)

                    // -- Chat: messages ---------------------------------------------------

                    // List messages: thread member.
                    Permission(
                        authorizedActions = listOf("read"),
                        authorizedResource = AuthorizedResource(types = listOf("chat_message")),
                        customLogic = "is_thread_member",
                    ),
                    // Post message: thread member.
                    Permission(
                        authorizedActions = listOf("create"),
                        authorizedResource = AuthorizedResource(types = listOf("chat_message")),
                        customLogic = "is_thread_member",
                    ),
                    // -- Teams --------------------------------------------------

                    // List my teams: any authenticated user (the service self-scopes
                    // to the caller's own memberships). A distinct "enumerate" action
                    // keeps this from also satisfying the member-gated "read" below.
                    Permission(
                        authorizedActions = listOf("enumerate"),
                        authorizedResource = AuthorizedResource(types = listOf("team")),
                    ),
                    // Create team: any authenticated user.
                    Permission(
                        authorizedActions = listOf("create"),
                        authorizedResource = AuthorizedResource(types = listOf("team")),
                    ),
                    // Read team detail: member only.
                    Permission(
                        authorizedActions = listOf("read"),
                        authorizedResource = AuthorizedResource(types = listOf("team")),
                        customLogic = "is_team_member",
                    ),
                    // Rename/update team: admin+.
                    Permission(
                        authorizedActions = listOf("update"),
                        authorizedResource = AuthorizedResource(types = listOf("team")),
                        customLogic = "is_team_admin",
                    ),
                    // Delete team: owner only. Uses the built-in "owned" predicate,
                    // backed by the "team" owner-id provider registered in TeamService
                    // (which resolves the OWNER member row). There is no separate
                    // is_team_owner custom logic.
                    Permission(
                        authorizedActions = listOf("delete"),
                        authorizedResource = AuthorizedResource(types = listOf("team")),
                        customLogic = "owned",
                    ),

                    // -- Documents (team-scoped) -------------------------------

                    // List documents: team member.
                    Permission(
                        authorizedActions = listOf("read"),
                        authorizedResource = AuthorizedResource(types = listOf("team_document")),
                        customLogic = "is_team_member",
                    ),
                    // Create document: team member.
                    Permission(
                        authorizedActions = listOf("create"),
                        authorizedResource = AuthorizedResource(types = listOf("team_document")),
                        customLogic = "is_team_member",
                    ),
                    // Update document: team member.
                    Permission(
                        authorizedActions = listOf("update"),
                        authorizedResource = AuthorizedResource(types = listOf("team_document")),
                        customLogic = "is_team_member",
                    ),
                    // Delete document: team member.
                    Permission(
                        authorizedActions = listOf("delete"),
                        authorizedResource = AuthorizedResource(types = listOf("team_document")),
                        customLogic = "is_team_member",
                    ),
                ),
        )
    }
}
