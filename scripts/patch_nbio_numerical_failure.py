from pathlib import Path

p = Path('app/src/main/java/dev/kian/mymettle/engine/inference/DynamicTrendSolverHistoricalBakeoff.kt')
s = p.read_text()

old = '''            val baseFit = requireNotNull(frozenV1)
            var v1Scored: List<DynamicHeldOutEvaluation> = emptyList()
            v1ScoreMillis += measureTimeMillis {
                v1Scored = score(baseFit, heldOut, sessionId, training, horizon, benchmark)
            }
            v1Results += v1Scored
'''
new = '''            val baseFit = requireNotNull(frozenV1)
            var v1Scored: List<DynamicHeldOutEvaluation> = emptyList()
            var v1ScoringFailureReason: String? = null
            v1ScoreMillis += measureTimeMillis {
                try {
                    v1Scored = score(baseFit, heldOut, sessionId, training, horizon, benchmark)
                } catch (failure: DynamicCapabilityFitException) {
                    v1ScoringFailureReason = failure.reason.storageValue
                } catch (failure: IllegalArgumentException) {
                    v1ScoringFailureReason = "numerical_invariant:${failure.message ?: "illegal_argument"}"
                }
            }
            v1Results += if (v1ScoringFailureReason == null) {
                v1Scored
            } else {
                heldOut.map {
                    modelFailure(
                        it,
                        sessionId,
                        training,
                        reference,
                        benchmark,
                        "v1_scoring:${requireNotNull(v1ScoringFailureReason)}",
                    )
                }
            }
'''
if old not in s:
    raise SystemExit('v1 scoring anchor not found')
s = s.replace(old, new, 1)

old = '''                } else {
                    val fitted = requireNotNull(fit)
                    val projected = solver.projectToNextSession(fitted)
                    var scored: List<DynamicHeldOutEvaluation> = emptyList()
                    val scoreMillis = measureTimeMillis {
                        scored = score(projected, heldOut, sessionId, training, horizon, benchmark)
                    }
                    scoringMillis.getValue(solver)[0] += scoreMillis
                    candidateResults.getValue(solver) += scored
                    diagnostics.getValue(solver) += DynamicTrendSolverSessionDiagnostic(
                        sessionId = sessionId,
                        priorIndependentSessionCount = fitted.support.effectiveIndependentSessionCount,
                        solverIdentity = solver.solverIdentity,
                        trendP05 = fitted.frontierTrend.summary.p05,
                        trendP50 = fitted.frontierTrend.summary.p50,
                        trendP95 = fitted.frontierTrend.summary.p95,
                        effectivePosteriorNodeCount = fitted.solverDiagnostics.effectiveNodeCount
                            ?: fitted.posteriorEffectiveNodeCount,
                        evaluatedNodeCount = fitted.solverDiagnostics.evaluatedNodeCount,
                        solverRuntimeNanos = fitted.solverDiagnostics.updateRuntimeNanos,
                        wallElapsedMillis = fitMillis,
                        approximationFailure = fitted.solverDiagnostics.approximationFailure,
                        fitFailureReason = null,
                    )
                }
'''
new = '''                } else {
                    val fitted = requireNotNull(fit)
                    var scored: List<DynamicHeldOutEvaluation> = emptyList()
                    var evaluationFailureReason: String? = null
                    val scoreMillis = measureTimeMillis {
                        try {
                            val projected = solver.projectToNextSession(fitted)
                            scored = score(projected, heldOut, sessionId, training, horizon, benchmark)
                        } catch (failure: DynamicCapabilityFitException) {
                            evaluationFailureReason = failure.reason.storageValue
                        } catch (failure: IllegalArgumentException) {
                            evaluationFailureReason = "numerical_invariant:${failure.message ?: "illegal_argument"}"
                        }
                    }
                    scoringMillis.getValue(solver)[0] += scoreMillis
                    if (evaluationFailureReason == null) {
                        candidateResults.getValue(solver) += scored
                    } else {
                        candidateResults.getValue(solver) += heldOut.map {
                            modelFailure(
                                it,
                                sessionId,
                                training,
                                reference,
                                benchmark,
                                "candidate_projection_or_scoring:${requireNotNull(evaluationFailureReason)}",
                            )
                        }
                    }
                    diagnostics.getValue(solver) += DynamicTrendSolverSessionDiagnostic(
                        sessionId = sessionId,
                        priorIndependentSessionCount = fitted.support.effectiveIndependentSessionCount,
                        solverIdentity = solver.solverIdentity,
                        trendP05 = fitted.frontierTrend.summary.p05,
                        trendP50 = fitted.frontierTrend.summary.p50,
                        trendP95 = fitted.frontierTrend.summary.p95,
                        effectivePosteriorNodeCount = fitted.solverDiagnostics.effectiveNodeCount
                            ?: fitted.posteriorEffectiveNodeCount,
                        evaluatedNodeCount = fitted.solverDiagnostics.evaluatedNodeCount,
                        solverRuntimeNanos = fitted.solverDiagnostics.updateRuntimeNanos,
                        wallElapsedMillis = fitMillis,
                        approximationFailure = evaluationFailureReason ?: fitted.solverDiagnostics.approximationFailure,
                        fitFailureReason = null,
                    )
                }
'''
if old not in s:
    raise SystemExit('candidate scoring anchor not found')
s = s.replace(old, new, 1)
p.write_text(s)
