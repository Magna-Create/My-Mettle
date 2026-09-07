package dev.kian.mymettle.inference

import dev.kian.mymettle.domain.inference.CapabilityEquipmentContext
import dev.kian.mymettle.domain.inference.CapabilityTransferSource
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierPosteriorNode
import dev.kian.mymettle.engine.inference.DirectedDynamicTransferRelationshipDescriptor
import dev.kian.mymettle.engine.inference.DynamicTransferM0DirectedEdgeKey
import dev.kian.mymettle.engine.inference.DynamicTransferM0HistoricalPairing
import dev.kian.mymettle.engine.inference.DynamicTransferM0PosteriorFit
import dev.kian.mymettle.engine.inference.DynamicTransferM0Prediction
import dev.kian.mymettle.engine.inference.DynamicTransferM0SourceCoreset
import dev.kian.mymettle.engine.inference.DynamicTransferM0SourceSelectionMode
import dev.kian.mymettle.engine.inference.DynamicTransferM0SourceSelectionPolicyDefinition
import dev.kian.mymettle.engine.inference.NBio7FM0V1
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64

enum class DynamicTransferM0DerivedStateKind { POSTERIOR_FIT, PREDICTION }

data class DynamicTransferM0DerivedSnapshot(
    val id: String,
    val stateKind: DynamicTransferM0DerivedStateKind,
    val destinationExecutionProfileVersionId: String,
    val sourceExecutionProfileVersionId: String,
    val side: String,
    val relationshipId: String,
    val relationshipVersion: Int,
    val relationshipPolicyIdentity: String,
    val relationshipFingerprint: String,
    val modelConfigId: String,
    val mathematicalModelIdentity: String,
    val solverIdentity: String,
    val sourceSelectionPolicyId: String,
    val sourceSelectionPolicyVersion: Int,
    val sourceSelectionPolicyMode: DynamicTransferM0SourceSelectionMode,
    val sourceSelectionPolicyIdentity: String,
    val sourceSelectionPolicyFrozenAt: Instant,
    val evidenceThrough: Instant,
    val predictionCutoff: Instant?,
    val codecId: String,
    val codecVersion: Int,
    val payloadSchemaVersion: Int,
    val payload: String,
    val payloadSha256: String,
    val dependencyIds: Set<String>,
    val createdAt: Instant,
)

/**
 * Deterministic derived-only cache codec for frozen M0.
 *
 * The cache is never canonical evidence. Unknown codec/schema/model identity, payload mutation or
 * envelope/dependency mutation requires delete + recompute.
 */
object DynamicTransferM0DerivedSnapshotCodec {
    const val CODEC_ID = "n-bio-7f-m0-derived-canonical-text-v1"
    const val CODEC_VERSION = 1
    const val PAYLOAD_SCHEMA_VERSION = 1
    const val MAX_PAYLOAD_BYTES = 16 * 1024 * 1024
    private const val ID_PREFIX = "m0derived_sha256_"

    fun snapshotFit(
        fit: DynamicTransferM0PosteriorFit,
        policy: DynamicTransferM0SourceSelectionPolicyDefinition,
        createdAt: Instant,
    ): DynamicTransferM0DerivedSnapshot {
        val evidenceThrough = fit.replayInput.destination.n0.destinationFit.support.lastEvidenceAt
            ?: error("M0 derived fit requires an evidence-through timestamp.")
        return build(
            kind = DynamicTransferM0DerivedStateKind.POSTERIOR_FIT,
            relationship = fit.relationship,
            policy = policy,
            evidenceThrough = evidenceThrough,
            predictionCutoff = null,
            dependencies = fit.replayDependencyScope.sourceDependencyIds,
            payload = fitPayload(fit, policy),
            createdAt = createdAt,
        )
    }

    fun snapshotPrediction(
        prediction: DynamicTransferM0Prediction,
        policy: DynamicTransferM0SourceSelectionPolicyDefinition,
        createdAt: Instant,
    ): DynamicTransferM0DerivedSnapshot {
        require(prediction.replayInput.relationship == prediction.fit.relationship)
        require(policy.frozenAt.isBefore(prediction.replayInput.predictionCutoff)) {
            "M0 source-selection policy must be frozen before prediction."
        }
        val evidenceThrough = prediction.fit.replayInput.destination.n0.destinationFit.support.lastEvidenceAt
            ?: error("M0 derived prediction requires an evidence-through timestamp.")
        val fitId = snapshotFit(prediction.fit, policy, createdAt).id
        return build(
            kind = DynamicTransferM0DerivedStateKind.PREDICTION,
            relationship = prediction.fit.relationship,
            policy = policy,
            evidenceThrough = evidenceThrough,
            predictionCutoff = prediction.replayInput.predictionCutoff,
            dependencies = prediction.replayDependencyScope.sourceDependencyIds,
            payload = predictionPayload(prediction, policy, fitId),
            createdAt = createdAt,
        )
    }

    fun verifyReplay(
        persisted: DynamicTransferM0DerivedSnapshot,
        fit: DynamicTransferM0PosteriorFit,
        policy: DynamicTransferM0SourceSelectionPolicyDefinition,
    ) = requireReplayMatch(persisted, snapshotFit(fit, policy, persisted.createdAt))

    fun verifyReplay(
        persisted: DynamicTransferM0DerivedSnapshot,
        prediction: DynamicTransferM0Prediction,
        policy: DynamicTransferM0SourceSelectionPolicyDefinition,
    ) = requireReplayMatch(persisted, snapshotPrediction(prediction, policy, persisted.createdAt))

    fun validateStored(snapshot: DynamicTransferM0DerivedSnapshot) {
        require(snapshot.codecId == CODEC_ID) { "Unknown M0 derived codec; recomputation is required." }
        require(snapshot.codecVersion == CODEC_VERSION) { "Unsupported M0 derived codec version; recomputation is required." }
        require(snapshot.payloadSchemaVersion == PAYLOAD_SCHEMA_VERSION) {
            "Unsupported M0 derived payload schema; recomputation is required."
        }
        require(snapshot.modelConfigId == NBio7FM0V1.EXPECTED_MODEL_CONFIG_ID) {
            "Persisted M0 config is unsupported; recomputation is required."
        }
        require(snapshot.mathematicalModelIdentity == NBio7FM0V1.mathematicalModelIdentity.identity) {
            "Persisted M0 mathematical identity is unsupported; recomputation is required."
        }
        require(snapshot.solverIdentity == NBio7FM0V1.solverIdentity.identity) {
            "Persisted M0 solver identity is unsupported; recomputation is required."
        }
        require(snapshot.relationshipVersion > 0 && snapshot.sourceSelectionPolicyVersion > 0)
        require(snapshot.relationshipFingerprint.startsWith("sha256_"))
        require(snapshot.sourceSelectionPolicyIdentity.startsWith("m0_source_selection_sha256_"))
        require(snapshot.dependencyIds.isNotEmpty() && snapshot.dependencyIds.all(String::isNotBlank))
        require(snapshot.payload.isNotBlank())
        require(snapshot.payload.toByteArray(StandardCharsets.UTF_8).size <= MAX_PAYLOAD_BYTES)
        require(snapshot.payloadSha256 == sha256Hex(snapshot.payload)) {
            "M0 derived payload hash mismatch; recomputation is required."
        }
        when (snapshot.stateKind) {
            DynamicTransferM0DerivedStateKind.POSTERIOR_FIT -> require(snapshot.predictionCutoff == null)
            DynamicTransferM0DerivedStateKind.PREDICTION -> require(snapshot.predictionCutoff != null)
        }
        require(snapshot.id == derivedId(snapshot)) {
            "M0 derived envelope/dependency identity mismatch; recomputation is required."
        }
    }

    fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun build(
        kind: DynamicTransferM0DerivedStateKind,
        relationship: DirectedDynamicTransferRelationshipDescriptor,
        policy: DynamicTransferM0SourceSelectionPolicyDefinition,
        evidenceThrough: Instant,
        predictionCutoff: Instant?,
        dependencies: Set<String>,
        payload: String,
        createdAt: Instant,
    ): DynamicTransferM0DerivedSnapshot {
        require(dependencies.isNotEmpty())
        val edge = DynamicTransferM0DirectedEdgeKey.from(relationship)
        val draft = DynamicTransferM0DerivedSnapshot(
            id = "",
            stateKind = kind,
            destinationExecutionProfileVersionId = relationship.destinationExecutionProfileVersionId.value,
            sourceExecutionProfileVersionId = relationship.sourceExecutionProfileVersionId.value,
            side = relationship.side.name,
            relationshipId = relationship.relationshipId,
            relationshipVersion = relationship.version,
            relationshipPolicyIdentity = relationship.policyIdentity,
            relationshipFingerprint = edge.relationshipFingerprint,
            modelConfigId = NBio7FM0V1.EXPECTED_MODEL_CONFIG_ID,
            mathematicalModelIdentity = NBio7FM0V1.mathematicalModelIdentity.identity,
            solverIdentity = NBio7FM0V1.solverIdentity.identity,
            sourceSelectionPolicyId = policy.policyId,
            sourceSelectionPolicyVersion = policy.version,
            sourceSelectionPolicyMode = policy.mode,
            sourceSelectionPolicyIdentity = policy.policyIdentity,
            sourceSelectionPolicyFrozenAt = policy.frozenAt,
            evidenceThrough = evidenceThrough,
            predictionCutoff = predictionCutoff,
            codecId = CODEC_ID,
            codecVersion = CODEC_VERSION,
            payloadSchemaVersion = PAYLOAD_SCHEMA_VERSION,
            payload = payload,
            payloadSha256 = sha256Hex(payload),
            dependencyIds = dependencies.toSortedSet(),
            createdAt = createdAt,
        )
        return draft.copy(id = derivedId(draft)).also(::validateStored)
    }

    private fun derivedId(s: DynamicTransferM0DerivedSnapshot): String {
        val canonical = buildString {
            append(s.stateKind.name).append('|')
            append(e(s.destinationExecutionProfileVersionId)).append('|')
            append(e(s.sourceExecutionProfileVersionId)).append('|')
            append(s.side).append('|')
            append(e(s.relationshipId)).append('|').append(s.relationshipVersion).append('|')
            append(e(s.relationshipPolicyIdentity)).append('|').append(s.relationshipFingerprint).append('|')
            append(s.modelConfigId).append('|').append(e(s.mathematicalModelIdentity)).append('|')
            append(e(s.solverIdentity)).append('|')
            append(e(s.sourceSelectionPolicyId)).append('|').append(s.sourceSelectionPolicyVersion).append('|')
            append(s.sourceSelectionPolicyMode.name).append('|').append(s.sourceSelectionPolicyIdentity).append('|')
            append(s.sourceSelectionPolicyFrozenAt).append('|').append(s.evidenceThrough).append('|')
            append(s.predictionCutoff?.toString() ?: "null").append('|')
            append(s.codecId).append('|').append(s.codecVersion).append('|').append(s.payloadSchemaVersion).append('|')
            append(s.payloadSha256).append('|')
            append(s.dependencyIds.sorted().joinToString(",") { e(it) })
        }
        return ID_PREFIX + sha256Hex(canonical)
    }

    private fun fitPayload(
        fit: DynamicTransferM0PosteriorFit,
        policy: DynamicTransferM0SourceSelectionPolicyDefinition,
    ): String = Writer().apply {
        text("kind", "fit-v1")
        policy("policy", policy)
        relationship("relationship", fit.relationship)
        val d = fit.replayInput.destination.n0.destinationFit
        text("destination.config", d.modelConfigId.value)
        text("destination.math", d.mathematicalModelIdentity.identity)
        text("destination.solver", d.solverDiagnostics.solverIdentity.identity)
        text("destination.horizon", d.inferenceHorizon.toString())
        double("destination.referenceReps", d.referenceRepetitions)
        textList("destination.observations", d.selectedObservationIds)
        textList("destination.sessions", d.selectedSessionIds)
        double("sourceCentre.mean", fit.sourceCentre.sourceCentre)
        double("sourceCentre.variance", fit.sourceCentre.betweenSessionExpectedAnchorVariance)
        textList("dependencies", fit.replayDependencyScope.sourceDependencyIds.sorted())
        fit.replayInput.trainingSessions.forEachIndexed { i, session ->
            val p = "training.$i"
            text("$p.id", session.sessionId)
            text("$p.time", session.firstObservationTime.toString())
            int("$p.offset", session.destinationSessionOffset)
            session.observations.forEachIndexed { j, o ->
                text("$p.obs.$j.id", o.observationId)
                double("$p.obs.$j.reps", o.repetitions)
                double("$p.obs.$j.kg", o.resistanceKg)
            }
            when (val pairing = session.pairing) {
                is DynamicTransferM0HistoricalPairing.Unpaired -> {
                    text("$p.pairing", "unpaired")
                    text("$p.reason", pairing.reason)
                }
                is DynamicTransferM0HistoricalPairing.Paired -> {
                    text("$p.pairing", "paired")
                    source("$p.source", pairing.source)
                    relationship("$p.relationship", pairing.relationship)
                    textList("$p.dependencies", pairing.replayDependencyScope.sourceDependencyIds.sorted())
                }
            }
        }
        fit.pairedTraining.forEachIndexed { i, pair ->
            text("prepared.$i.session", pair.sessionId)
            coreset("prepared.$i.coreset", pair.preparedEdge.sourceCoreset)
        }
        fit.posteriorNodes.forEachIndexed { i, n ->
            int("posterior.$i.destinationIndex", n.destinationNodeIndex)
            int("posterior.$i.betaIndex", n.betaNodeIndex)
            node("posterior.$i.destinationNode", n.destinationNode)
            double("posterior.$i.beta", n.betaNode.beta)
            double("posterior.$i.betaPrior", n.betaNode.priorWeight)
            double("posterior.$i.delta", n.logLikelihoodRatio)
            double("posterior.$i.weight", n.posteriorWeight)
        }
    }.finish()

    private fun predictionPayload(
        prediction: DynamicTransferM0Prediction,
        policy: DynamicTransferM0SourceSelectionPolicyDefinition,
        fitId: String,
    ): String = Writer().apply {
        text("kind", "prediction-v1")
        text("fitId", fitId)
        policy("policy", policy)
        relationship("relationship", prediction.fit.relationship)
        text("cutoff", prediction.replayInput.predictionCutoff.toString())
        double("queryReps", prediction.replayInput.queryRepetitions)
        source("source", prediction.replayInput.source)
        coreset("sourceCoreset", prediction.sourceCoreset)
        textList("dependencies", prediction.replayDependencyScope.sourceDependencyIds.sorted())
        prediction.components.forEachIndexed { i, c ->
            int("component.$i.destinationIndex", c.destinationNodeIndex)
            int("component.$i.betaIndex", c.betaNodeIndex)
            int("component.$i.sourceIndex", c.sourceOriginalNodeIndex)
            double("component.$i.beta", c.beta)
            double("component.$i.anchor", c.sourceAnchor)
            double("component.$i.logFrontier", c.logFrontier)
            double("component.$i.slack", c.destinationSlackScale)
            double("component.$i.noise", c.destinationNoiseScale)
            double("component.$i.weight", c.mixtureWeight)
        }
    }.finish()

    private fun requireReplayMatch(a: DynamicTransferM0DerivedSnapshot, b: DynamicTransferM0DerivedSnapshot) {
        validateStored(a)
        validateStored(b)
        require(a.id == b.id && a.payload == b.payload) {
            "M0 deterministic replay differs from persisted derived state; discard the cache."
        }
    }

    private class Writer {
        private val lines = mutableListOf<String>()
        fun text(key: String, value: String) { lines += "$key=${e(value)}" }
        fun int(key: String, value: Int) { lines += "$key=$value" }
        fun double(key: String, value: Double) {
            require(value.isFinite())
            lines += "$key=${java.lang.Double.toHexString(value)}"
        }
        fun textList(key: String, values: List<String>) {
            lines += "$key=${values.joinToString(",") { e(it) }}"
        }
        fun policy(key: String, p: DynamicTransferM0SourceSelectionPolicyDefinition) {
            text("$key.semantic", p.semanticVersion)
            text("$key.id", p.policyId)
            int("$key.version", p.version)
            text("$key.frozenAt", p.frozenAt.toString())
            text("$key.mode", p.mode.name)
            text("$key.identity", p.policyIdentity)
            textList("$key.priority", p.explicitPriority.map { it.canonicalIdentity })
        }
        fun relationship(key: String, r: DirectedDynamicTransferRelationshipDescriptor) {
            val edge = DynamicTransferM0DirectedEdgeKey.from(r)
            text("$key.id", r.relationshipId)
            int("$key.version", r.version)
            text("$key.policy", r.policyIdentity)
            text("$key.fingerprint", edge.relationshipFingerprint)
            text("$key.sourceProfile", r.sourceExecutionProfileId.value)
            text("$key.sourceVersion", r.sourceExecutionProfileVersionId.value)
            text("$key.destinationProfile", r.destinationExecutionProfileId.value)
            text("$key.destinationVersion", r.destinationExecutionProfileVersionId.value)
            text("$key.side", r.side.name)
            text("$key.sourceEquipment", r.sourceEquipmentId.value)
            text("$key.sourceInterpretation", r.sourceEquipmentInterpretationVersion)
            textList("$key.sourceFacts", r.sourceEquipmentFactVersionIds.sorted())
            text("$key.destinationEquipment", r.destinationEquipmentId.value)
            text("$key.destinationInterpretation", r.destinationEquipmentInterpretationVersion)
            textList("$key.destinationFacts", r.destinationEquipmentFactVersionIds.sorted())
            text("$key.sourceLoad", r.sourceLoadAccounting.storageValue)
            text("$key.destinationLoad", r.destinationLoadAccounting.storageValue)
        }
        fun source(key: String, s: CapabilityTransferSource) {
            text("$key.profile", s.profile.executionProfileId.value)
            text("$key.version", s.profile.executionProfileVersionId.value)
            text("$key.side", s.side.name)
            text("$key.asOf", s.causalCutoff.asOf.toString())
            text("$key.evidenceThrough", s.causalCutoff.evidenceThrough.toString())
            textList("$key.observations", s.selectedObservationIds)
            textList("$key.sessions", s.selectedSessionIds)
            text("$key.upstreamConfig", s.upstream.modelConfigId.value)
            text("$key.upstreamMath", s.upstream.mathematicalModelIdentity.identity)
            text("$key.upstreamSolver", s.upstream.solverIdentity.identity)
            val equipment = s.equipmentContext as? CapabilityEquipmentContext.ResolvedSingleContext
            if (equipment != null) {
                text("$key.equipment", equipment.equipmentId.value)
                text("$key.interpretation", equipment.interpretationVersion)
                textList("$key.facts", equipment.equipmentFactVersionIds.sorted())
            } else {
                text("$key.equipment", "unresolved")
            }
        }
        fun coreset(key: String, c: DynamicTransferM0SourceCoreset) {
            text("$key.algorithm", c.algorithmIdentity)
            double("$key.sourceRef", c.sourceReferenceRepetitions)
            double("$key.destinationRef", c.destinationReferenceRepetitions)
            int("$key.originalCount", c.originalNodeCount)
            c.nodes.forEachIndexed { i, n ->
                int("$key.$i.originalIndex", n.stableOriginalIndex)
                double("$key.$i.anchor", n.sourceAnchor)
                double("$key.$i.originalWeight", n.originalPosteriorWeight)
                node("$key.$i.node", n.node)
            }
        }
        fun node(key: String, n: DynamicTrendFrontierPosteriorNode) {
            double("$key.c", n.logFrontierAtLatestSession)
            double("$key.b", n.slope)
            double("$key.g", n.frontierTrend)
            double("$key.u", n.slackScale)
            double("$key.e", n.noiseScale)
            double("$key.w", n.posteriorWeight)
        }
        fun finish(): String = lines.joinToString("\n")
    }

    private fun e(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
}
