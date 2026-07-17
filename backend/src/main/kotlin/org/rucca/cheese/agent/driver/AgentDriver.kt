/*
 *  Description: The seam that swapping Claude Code for Codex (or anything else) moves through.
 *               Everything that knows what the driven program *is* lives behind this interface,
 *               and it is exactly two things: how to launch it (the argv) and the cheeselet that
 *               drives its terminal. Nothing else in the backend mentions Claude.
 *
 *               The cheeselet is the interesting half: it is JavaScript that runs in the CLI's
 *               embedded VM on the machine, reads the program's terminal, and mirrors variables
 *               back up. All of the per-program knowledge — what "working" looks like, where the
 *               token count is painted — is knowledge about pixels, so it belongs there rather
 *               than here. A driver's cheeselet reports its own name on screenReady, so the wire
 *               already carries which driver a screen is running.
 *
 *  Author(s):
 *      Nictheboy Li    <nictheboy@outlook.com>
 *
 */

package org.rucca.cheese.agent.driver

/** How to launch and drive one kind of agent program. */
interface AgentDriver {
    /** Matches the `driver` the cheeselet reports on screenReady (e.g. "claude"). */
    val name: String

    /** The cheeselet source shipped into the screen to drive this program's terminal. */
    val cheeseletSource: String

    /**
     * The argv that launches (or resumes) one session of this program. We mint [sessionId]
     * ourselves rather than letting the program pick, so a screen can be resumed after a restart.
     */
    fun command(sessionId: String, cwd: String?, resume: Boolean): List<String>
}
