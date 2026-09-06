package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.equipment.EquipmentInvalidationImpact
import dev.kian.mymettle.domain.inference.CapabilityTransferSource
import java.time.Instant

/** Fail-closed reasons for the preregistered destination-session atomic M0 freeze. */
enum class DynamicTransferM0FreezeFailure {
    DESTINATION_N0_NOT_STRICTLY_PRIOR,
    DESTINATION_SESSION_ALREADY_IN_N0,
    SOURCE_NOT_STRICTLY_PRIOR,
    SOURCE_CONTAINS_DESTINATION_SESSION,
}

class DynamicTransferM0FreezeException(
    val reason: DynamicTransferM0FreezeFailure,
    message: String,
) : IllegalArgumentException(message)

/** One held-out destination independent session at its immutable pre-score cutoff. */
data class DynamicTransferM0DestinationSessionDescriptor(
    val sessionId: String,
    val firstObservationTime: Instant,
    val destination: DynamicTransferM0DestinationContext,
) {
    init { require(sessionId.isNotBlank()) }
}

/**
 * Frozen source pairing for one destination session.
 *
 * Unpaired is a first-class state: the destination session remains represented by N0 and contributes
 * exactly zero M0 likelihood-ratio increment. Paired always contains one already-admissible direct
 * edge; there is no multi-source container on the candidate boundary.
 */
sealed interface DynamicTransferM0FrozenSourcePairing {
    data class Paired(val preparedEdge: DynamicTransferM0PreparedEdge) : DynamicTransferM0FrozenSourcePairing

    data class Unpaired(val reason: String) : DynamicTransferM0FrozenSourcePairing {
        init { require(reason.isNotBlank()) }
    }
}

data class DynamicTransferM0FrozenDestinationSession(
    val destinationSession: DynamicTransferM0DestinationSessionDescriptor,
    val sourcePairing: DynamicTransferM0FrozenSourcePairing,
)

/** Pure chronology/pairing policy around the frozen M0 kernel. No history is updated here. */
object DynamicTransferM0SessionPolicy {
    fun freezePaired(
        destinationSession: DynamicTransferM0DestinationSessionDescriptor,
        source: CapabilityTransferSource,
        sourceLoadAccounting: DynamicTransferM0LoadAccountingContext,
        relationship: DirectedDynamicTransferRelationshipDescriptor,
    ): DynamicTransferM0FrozenDestinationSession {
        requireDestinationN0Prior(destinationSession)
        if (!source.causalCutoff.evidenceThrough.isBefore(destinationSession.firstObservationTime)) {
            freezeFailure(
                DynamicTransferM0FreezeFailure.SOURCE_NOT_STRICTLY_PRIOR,
                "M0 source evidence must end strictly before the held-out destination session begins.",
            )
        }
        if (destinationSession.sessionId in source.selectedSessionIds) {
            freezeFailure(
                DynamicTransferM0FreezeFailure.SOURCE_CONTAINS_DESTINATION_SESSION,
                "M0 source snapshots cannot contain the held-out destination workout/session identity.",
            )
        }

        return DynamicTransferM0FrozenDestinationSession(
            destinationSession = destinationSession,
            sourcePairing = DynamicTransferM0FrozenSourcePairing.Paired(
                DynamicTransferM0Kernel.prepareDirectedEdge(
                    source = source,
                    sourceLoadAccounting = sourceLoadAccounting,
                    destination = destinationSession.destination,
                    relationship = relationship,
                ),
            ),
        )
    }

    fun freezeUnpaired(
        destinationSession: DynamicTransferM0DestinationSessionDescriptor,
        reason: String,
    ): DynamicTransferM0FrozenDestinationSession {
        requireDestinationN0Prior(destinationSession)
        return DynamicTransferM0FrozenDestinationSession(
            destinationSession = destinationSession,
            sourcePairing = DynamicTransferM0FrozenSourcePairing.Unpaired(reason),
        )
    }

    /** Applies the frozen section-11 rule: unpaired destination sessions have Delta_h = 0. */
    fun likelihoodRatioIncrement(
        frozenSession: DynamicTransferM0FrozenDestinationSession,
        pairedIncrement: Double,
    ): Double {
        require(pairedIncrement.isFinite())
        return when (frozenSession.sourcePairing) {
            is DynamicTransferM0FrozenSourcePairing.Paired -> pairedIncrement
            is DynamicTransferM0FrozenSourcePairing.Unpaired -> 0.0
        }
    }

    private fun requireDestinationN0Prior(destinationSession: DynamicTransferM0DestinationSessionDescriptor) {
        val fit = destinationSession.destination.n0.destinationFit
        val lastEvidenceAt = fit.support.lastEvidenceAt
            ?: freezeFailure(
                DynamicTransferM0FreezeFailure.DESTINATION_N0_NOT_STRICTLY_PRIOR,
                "Destination N0 has no evidence timestamp to establish a strictly prior frozen state.",
            )
        if (!lastEvidenceAt.isBefore(destinationSession.firstObservationTime)) {
            freezeFailure(
                DynamicTransferM0FreezeFailure.DESTINATION_N0_NOT_STRICTLY_PRIOR,
                "Destination N0 must be frozen strictly before the held-out session begins.",
            )
        }
        if (destinationSession.sessionId in fit.selectedSessionIds) {
            freezeFailure(
                DynamicTransferM0FreezeFailure.DESTINATION_SESSION_ALREADY_IN_N0,
                "The held-out destination session cannot already be present in its frozen N0 state.",
            )
        }
    }

    private fun freezeFailure(reason: DynamicTransferM0FreezeFailure, message: String): Nothing =
        throw DynamicTransferM0FreezeException(reason, message)
}

/**
 * One M0 candidate consumes one explicit direct relationship only.
 * Callers with several source edges must score them independently rather than building a path or
 * precision-combined candidate here.
 */
object DynamicTransferM0CandidateTopology {
    fun requireSingleDirectEdge(
        relationships: List<DirectedDynamicTransferRelationshipDescriptor>,
    ): DirectedDynamicTransferRelationshipDescriptor {
        require(relationships.size == 1) {
            "M0 v1 consumes exactly one direct source edge; multi-source and transitive paths are not candidates."
        }
        return relationships.single()
    }
}

/** Immutable dependency roots retained by derived M0 state for correction-scoped invalidation. */
data class DynamicTransferM0ReplayDependencyScope(
    val sourceDependencyIds: Set<String>,
) {
    init {
        require(sourceDependencyIds.isNotEmpty())
        require(sourceDependencyIds.all { it.isNotBlank() })
    }

    fun invalidatedBy(impact: EquipmentInvalidationImpact): Boolean =
        sourceDependencyIds.any { it in impact.sourceDependencyIds }
}
