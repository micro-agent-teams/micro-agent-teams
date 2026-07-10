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
                    // Minimal example endpoint (see design/API/NT-API.yml's /ping and
                    // org.rucca.cheese.ping.PingController) — kept from the stripped
                    // skeleton as a real auth-chain smoke test.
                    Permission(
                        authorizedActions = listOf("query"),
                        authorizedResource = AuthorizedResource(types = listOf("ping")),
                    )
                ),
        )
    }
}
