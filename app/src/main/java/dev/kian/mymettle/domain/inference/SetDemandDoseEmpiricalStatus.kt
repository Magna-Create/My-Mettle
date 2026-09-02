package dev.kian.mymettle.domain.inference

/**
 * PD-002 applies to every 7D demand/dose candidate. The three 7C capability families additionally
 * retain PD-001 empirical-accuracy quarantine. Keeping this as a set prevents one unresolved
 * empirical claim from overwriting the other.
 */
val SetDemandPosterior.empiricalStatuses: Set<SetDemandEmpiricalStatus>
    get() = buildSet {
        add(SetDemandEmpiricalStatus.EMPIRICAL_CALIBRATION_PENDING)
        add(empiricalStatus)
    }

val EffectiveDosePosterior.empiricalStatuses: Set<SetDemandEmpiricalStatus>
    get() = buildSet {
        add(SetDemandEmpiricalStatus.EMPIRICAL_CALIBRATION_PENDING)
        add(empiricalStatus)
    }
