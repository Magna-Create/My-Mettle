### Evidence-quality table

| Question | Evidence quality | What is established | What remains uncertain |
|---|---|---|---|
| Health Connect series/interval structure | **Very high — official platform docs** | Exact record topology and metadata | Producer sampling density |
| HC granular-session association | **High** | Time-range querying; route-by-session-ID | True semantic association of arbitrary cross-origin records |
| HC DataOrigin provenance | **Very high** | Origin is record metadata and can filter reads | Whether cross-origin records belong to same physical workout |
| Samsung Watch→Samsung Health→HC | **High — Samsung docs** | Supported workflow | Exact latency per device/workout |
| Samsung speed/HR/power export | **High for type support** | Current mapping includes these record types | Per-workout availability/density |
| Samsung cadence through HC | **Unknown/not documented** | — | Device/workout/version behaviour |
| Running critical speed | **High** | Valid modality-specific performance framework | Reliability from sparse recreational observations |
| Cycling CP/W′ | **High** | Well-established power-duration framework | Individual implementation/update model |
| Rowing power/pace capability | **Moderate–high** | Strong relationship with erg performance | Cross-manufacturer equivalence |
| Machine-level comparability | **Low/unsupported cross-device** | Local levels can track same-device workload | SI meaning/cross-machine scaling |
| Cardio local-muscle adaptation | **Moderate, context-dependent** | Endurance and resistance adaptations overlap partly | Per-session local dose inference |
| Cardio→hypertrophy-set conversion | **Unsupported** | — | No defensible general conversion |
| Resistance exercise-specific HR response | **Moderate–high descriptive** | Exercise/protocol changes HR response | Stable individual signature for every profile |
| HR as local hypertrophic-stimulus proxy | **Low/unsupported** | — | Possible tiny incremental prediction in specialised contexts |
| HR as local-fatigue predictor | **Low direct evidence** | Plausible contextual relationship | Increment beyond performance/rest |
| HR as systemic-strain context | **Moderate** | Cardiorespiratory load tracks protocol/work rate | Best personal latent representation |
| HR recovery as set-readiness rule | **Low** | HR recovery contains autonomic information | Threshold that improves set prescription |
| Exercise HR as readiness marker | **Moderate broader sport; low resistance-specific** | Can vary with training status/fatigue | Direction and individual interpretation |
| Expected-HR residual model | **Reasonable modelling hypothesis** | Strong statistical rationale | Out-of-sample N-BIO utility |
| Wrist HR in resistance exercise | **Device-dependent** | Useful measurements are possible | Sample-by-sample reliability and lag |
| TRIMP/internal HR load | **Moderate–high for endurance/internal load** | Useful training-load context | Universal fatigue/local-muscle interpretation |

### Open platform and research questions

Several questions should be resolved empirically on the actual Galaxy Watch/Samsung Health stack before N-BIO-9 ingestion is finalised.

**Samsung export density is the largest platform unknown.** Official documentation establishes record-type support but not whether a Galaxy Watch treadmill session exported through a particular Samsung Health release yields HR/speed as dense samples, sparse chunks or different behaviour by workout type. The correct engineering response is a device-fixture test matrix, not an architectural assumption. citeturn22view6

Likewise, Samsung's current public Health Connect table does not establish cadence export, and route availability should be tested rather than assumed.

For Health Connect generally, there is no documented universal semantic link between an `ExerciseSessionRecord` and ancillary non-route records. My Mettle should therefore test real-world origin patterns involving Samsung Health and other common apps before deciding whether cross-origin association ever deserves automatic acceptance. Android's own example's time-only query means a hard same-origin rule would be stricter than the platform itself, while unrestricted cross-origin mixing would be too permissive. citeturn24view5

The major physiological unknown is equally clear: **does set-aligned HR improve personal prediction once external performance and rest are already known?** Existing resistance studies show cardiovascular structure but do not answer that machine-learning question directly. That is a research opportunity uniquely suited to My Mettle's longitudinal dataset rather than something to resolve through a population-derived heuristic.

### Sources

The most load-bearing **official Android / Health Connect sources** are the current Health Connect workout guide, raw-data/read documentation, data-type/API references, metadata specification and synchronisation/change-log guidance. Together they establish the session/time-series/interval model, route behaviour, origin metadata, IDs and update/deletion semantics. citeturn24view5turn6search6turn22view4turn22view7turn7view1

Key record-specific Android references used here include `HeartRateRecord`, `SpeedRecord`, `PowerRecord`, `CyclingPedalingCadenceRecord`, `StepsCadenceRecord`, `DistanceRecord`, `ElevationGainedRecord`, `FloorsClimbedRecord`, `StepsRecord`, `ActivityIntensityRecord` and `ExerciseRoute`. citeturn2view3turn2view2turn3view0turn5search2turn2view4turn24view2turn24view3turn24view4turn5search1turn24view0turn6search5

The principal **Samsung sources** are Samsung Developer's *Accessing Samsung Health Data through Health Connect* and Samsung's Health Connect FAQ, which document the Watch→Samsung Health→Health Connect flow, current supported record mappings and synchronisation conditions. citeturn22view6turn12view1

For **conditioning capability**, the main evidence base used includes reviews of the critical-power concept, contemporary critical-speed literature, rowing-ergometer performance evidence, treadmill-grade research and endurance-training physiology. citeturn14search0turn15search20turn14search4turn15search6turn19search13

For **resistance-training cardiovascular behaviour**, particularly informative primary studies include comparisons of dynamic/isometric resistance exercise, exercise-specific HR recovery kinetics, set configuration/rest effects, workload/internal-intensity relationships and repeatable individual cardiovascular responses. citeturn17search4turn17search5turn17search0turn16search19turn17search10

For **HR monitoring and readiness**, Achten and Jeukendrup's review of HR monitoring and Buchheit's review of resting, exercise and recovery HR measures are important because both emphasise the context dependence and ambiguity of fatigue/training-status interpretations rather than a simple monotonic HR rule. citeturn19search0turn19search1

For **wearable measurement quality**, resistance/cycling validation, broader wearable-validity reviews and PPG motion-artefact research show why raw watch HR is useful evidence but should carry measurement uncertainty rather than being treated as ground truth. citeturn18search2turn18search8turn18search15

For **cardio/resistance interaction and skeletal-muscle adaptation**, recent concurrent-training meta-analyses and physiological reviews show genuine overlap alongside training-mode specificity, supporting storage of cardio muscular context while arguing strongly against a simplistic cardio-to-hypertrophy conversion. citeturn23search9turn23search11turn23search15

**Final foundation judgement:** freeze N-BIO-6 only after temporal evidence, interval semantics, provenance chunks, resolution/acquisition metadata, precise observation boundaries and session-scoped physiological traces are represented. Cardio capability models, Health Connect ingestion, interval detection, HR residual modelling and personalised systemic-cost inference can then safely arrive later as versioned consumers of that substrate rather than forcing another ontology rewrite.