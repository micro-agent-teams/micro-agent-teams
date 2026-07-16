/*
 *  Description: This file defines the TeamController class.
 *               It provides the endpoints of /teams and registers the team
 *               authorization logics (owner resolver, member/admin predicates).
 *
 *  Author(s):
 *      Nictheboy Li    <nictheboy@outlook.com>
 *
 */

package org.rucca.cheese.team

import javax.annotation.PostConstruct
import javax.validation.Valid
import org.rucca.cheese.api.TeamsApi
import org.rucca.cheese.auth.AuthenticationService
import org.rucca.cheese.auth.AuthorizationService
import org.rucca.cheese.auth.AuthorizedAction
import org.rucca.cheese.auth.annotation.Guard
import org.rucca.cheese.auth.annotation.ResourceId
import org.rucca.cheese.common.error.BadRequestError
import org.rucca.cheese.common.persistent.IdGetter
import org.rucca.cheese.common.persistent.IdType
import org.rucca.cheese.model.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
class TeamController(
    private val teamService: TeamService,
    private val authorizationService: AuthorizationService,
    private val authenticationService: AuthenticationService,
) : TeamsApi {
    @PostConstruct
    fun initialize() {
        authorizationService.ownerIds.register("team", teamService::getTeamOwner)
        authorizationService.customAuthLogics.register("is_team_member") {
            userId: IdType,
            _: AuthorizedAction,
            _: String,
            resourceId: IdType?,
            _: Map<String, Any>,
            _: IdGetter?,
            _: Any? ->
            resourceId != null && teamService.isTeamMember(resourceId, userId)
        }
        authorizationService.customAuthLogics.register("is_team_admin") {
            userId: IdType,
            _: AuthorizedAction,
            _: String,
            resourceId: IdType?,
            _: Map<String, Any>,
            _: IdGetter?,
            _: Any? ->
            resourceId != null && teamService.isTeamAdmin(resourceId, userId)
        }
    }

    private fun currentUserId(): IdType = authenticationService.getCurrentUserId()

    @Guard("create", "team")
    override fun createTeam(
        @Valid @RequestBody(required = false) dto: CreateTeamRequestDTO?
    ): ResponseEntity<TeamDTO> {
        val name = dto?.name ?: return ResponseEntity.badRequest().build()
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(teamService.createTeam(name, currentUserId()))
    }

    @Guard("enumerate", "team")
    override fun listTeams(
        role: String?,
        pageStart: Long?,
        pageSize: Int,
    ): ResponseEntity<ListTeamsResponseDTO> {
        val roleFilter =
            role?.let {
                runCatching { TeamMemberRole.valueOf(it) }
                    .getOrElse { throw BadRequestError("invalid role: $role") }
            }
        val (teams, page) =
            teamService.listMyTeams(currentUserId(), roleFilter, pageStart, pageSize)
        return ResponseEntity.ok(ListTeamsResponseDTO(teams = teams, page = page))
    }

    @Guard("read", "team")
    override fun getTeam(
        @PathVariable("id") @ResourceId id: IdType
    ): ResponseEntity<TeamDetailDTO> = ResponseEntity.ok(teamService.getTeamDetail(id))

    @Guard("update", "team")
    override fun renameTeam(
        @PathVariable("id") @ResourceId id: IdType,
        @Valid @RequestBody(required = false) dto: RenameTeamRequestDTO?,
    ): ResponseEntity<TeamDTO> {
        val name = dto?.name ?: return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(teamService.renameTeam(id, name))
    }

    @Guard("delete", "team")
    override fun deleteTeam(@PathVariable("id") @ResourceId id: IdType): ResponseEntity<Unit> {
        teamService.deleteTeam(id)
        return ResponseEntity(HttpStatus.NO_CONTENT)
    }

    @Guard("read", "team")
    override fun listTeamMembers(
        @PathVariable("id") @ResourceId id: IdType
    ): ResponseEntity<List<TeamMemberDTO>> = ResponseEntity.ok(teamService.listMembers(id))

    @Guard("update", "team")
    override fun addTeamMember(
        @PathVariable("id") @ResourceId id: IdType,
        @Valid @RequestBody(required = false) dto: AddTeamMemberRequestDTO?,
    ): ResponseEntity<Unit> {
        if (dto == null) return ResponseEntity.badRequest().build()
        teamService.addMember(id, dto.userId, dto.role.convert())
        return ResponseEntity(HttpStatus.NO_CONTENT)
    }

    @Guard("update", "team")
    override fun changeMemberRole(
        @PathVariable("id") @ResourceId id: IdType,
        @PathVariable("userId") userId: IdType,
        @Valid @RequestBody(required = false) dto: ChangeRoleRequestDTO?,
    ): ResponseEntity<Unit> {
        val role = dto?.role?.convert() ?: return ResponseEntity.badRequest().build()
        teamService.changeRole(id, userId, role)
        return ResponseEntity(HttpStatus.NO_CONTENT)
    }

    @Guard("update", "team")
    override fun removeTeamMember(
        @PathVariable("id") @ResourceId id: IdType,
        @PathVariable("userId") userId: IdType,
    ): ResponseEntity<Unit> {
        teamService.removeMember(id, userId)
        return ResponseEntity(HttpStatus.NO_CONTENT)
    }
}
