package ai.openclaw.node.capabilities

import ai.openclaw.node.gateway.NodeCommand
import ai.openclaw.node.gateway.NodeResponse

/**
 * Base interface for all device capabilities.
 */
interface Capability {
    val supportedActions: List<String>
    fun execute(cmd: NodeCommand): NodeResponse
}
