/*
 *  Description: Cleaning up after a machine we had agents on. The machine module announces that a
 *               machine was forgotten and stops there — it must not reach into agent tables, since
 *               keeping that dependency one-way is the whole point of separating the two. So the
 *               agent module listens and forgets its own.
 *
 *  Author(s):
 *      Nictheboy Li    <nictheboy@outlook.com>
 *
 */

package org.rucca.cheese.agent

import org.rucca.cheese.agent.screen.AgentScreenRepository
import org.rucca.cheese.machine.MachineForgottenEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class AgentCleanup(
    private val agentScreenRepository: AgentScreenRepository,
    private val agentRegistry: AgentRegistry,
) {
    private val logger = LoggerFactory.getLogger(AgentCleanup::class.java)

    @EventListener
    @Transactional
    fun onMachineForgotten(event: MachineForgottenEvent) {
        val rows = agentScreenRepository.findByMachineId(event.machineId)
        rows.forEach { agentRegistry.unregister(it.agentUserId) }
        agentScreenRepository.deleteByMachineId(event.machineId)
        if (rows.isNotEmpty()) {
            logger.info("forgot {} agent(s) that ran on machine {}", rows.size, event.machineId)
        }
    }
}
