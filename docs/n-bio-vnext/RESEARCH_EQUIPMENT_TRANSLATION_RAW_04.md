## 3. Does exact OEM model identity help?

Sometimes materially.

It can unlock starting resistance, pulley ratio, mechanism, geometry, and permitted selections.

It is neither required nor sufficient.

## 4. What is worth representing now?

Schema/backend capability for:

- stable instance identity where useful;
- coarse equipment family/mechanism;
- user-facing local label;
- optional manufacturer/model;
- declared implement/base resistance;
- display/load semantics;
- documented mechanical ratio;
- documented rail angle;
- independent-arm structure;
- available load increments where useful;
- provenance/quality/version.

Fields may legitimately remain unknown.

## 5. What is not worth requiring now?

- guessed friction;
- complete machine digital twins;
- exhaustive lever geometry;
- mandatory cam curves;
- measured endpoint calibration;
- formal calibration workflow.

## 6. Should `EntryBasis` remain aggregation?

**Yes.**

## 7. Is another load-value semantic needed?

**Yes.**

At minimum, the system must be capable of representing inclusive/complete load versus added-only load.

Exact schema naming remains an implementation/source-audit decision.

## 8. Starting/base resistance?

Represent as a versioned equipment fact with explicit scope, meaning, and provenance.

Do not treat it automatically as a universal additive intercept.

## 9. When should physics be deterministic?

Only where the local transformation is exact and semantically understood.

## 10. When should physics remain a feature/prior?

Where it constrains but does not completely determine the personal-performance relationship.

## 11. What should transfer?

Primarily typed existing capability posterior/predictive information.

## 12. Should existing capability posteriors be consumed?

**Yes, initially.**

Keep raw-history one-stage modelling as an offline challenger.

## 13. Strongest simple challenger?

Directed robust pairwise capability relationship with explicit destination-only/no-transfer component.

## 14. Strongest richer successor?

Central feature-conditioned relationship hypermodel plus sparse directed local graph.

## 15. How should negative transfer be controlled?

Semantic gating, robust relationship modelling, explicit no-transfer availability, destination-only comparison, and chronological evaluation.

## 16. Where should canonical equipment data live?

Core/domain history.

## 17. Where should learned translation live?

Derived, versioned translation-engine state.

## 18. When does equipment change require a new `ExecutionProfileVersion`?

Only when capability-defining execution semantics materially change under the existing profile ontology.

Hardware identity alone is insufficient.

## 19. How should unknown equipment behave?

Retain stable anonymous context where possible and infer broadly from what is known.

Do not fabricate mechanics.

## 20. What is the legitimate role of cross-user evidence?

Optional future relationship/noise hyperpriors and meta-learning.

It is not required for cold start and does not define global user strength.

## 21. What remains no-transfer?

Unsupported semantic bridges, predictively useless sources, arbitrary cross-device ordinal conversions without evidence, relationships repeatedly worse than destination-only prediction, and unsupported transitive graph inference.

---

# Required / Optional / Learned / Future / Reject

| Concept | Classification | Clarification |
|---|---|---|
| Existing `EntryBasis` | **REQUIRED NOW** | Preserve current aggregation semantics |
| Existing `ResistanceSemantics` | **REQUIRED NOW** | Preserve heterogeneous resistance meanings |
| Existing heterogeneous metrics | **REQUIRED NOW** | Do not homogenise them |
| Inclusive vs added-load semantic capability | **REQUIRED NOW** | Legacy values may remain unknown |
| Equipment context/history support | **REQUIRED NOW** | Exact physical instance not mandatory for every implement |
| Historical equipment binding | **REQUIRED NOW** | Effective observation context must be resolvable |
| Equipment family/mechanism representation | **REQUIRED NOW** | Value may legitimately be unknown |
| Provenance/versioning | **REQUIRED NOW** | Particularly for behaviour-relevant mechanics |
| Manufacturer/model | **OPTIONAL NOW** | Useful when known |
| Starting resistance | **OPTIONAL NOW, HIGH VALUE WHEN KNOWN** | Preserve meaning and scope |
| Pulley/mechanical ratio | **OPTIONAL NOW** | Only when genuinely established |
| Independent arms | **OPTIONAL NOW** | Structural relationship feature |
| Sled/rail angle | **OPTIONAL NOW** | Partial physical information |
| Feasible increments/selections | **OPTIONAL NOW** | Useful later for prescriptions |
| User-specific edge relationship | **LEARNED / DERIVED** | Not canonical equipment truth |
| Transfer/no-transfer gate | **LEARNED / DERIVED** | Learned chronologically |
| Equipment-specific residual | **LEARNED / DERIVED** | User/context specific |
| Profile-by-equipment capability facet | **OPEN CHALLENGER** | M2 must earn adoption |
| Central sparse graph | **FUTURE CHALLENGER** | Only if simpler relationships earn need |
| Multi-output GP | **FUTURE CHALLENGER** | Nonlinear/covariance candidate |
| Factor/tensor model | **FUTURE EXTENSION** | Requires demonstrated low-rank structure |
| Cross-user meta-learning | **FUTURE EXTENSION** | Requires defensible population data |
| Complete cam curves | **FUTURE EXTENSION** | Preserve as functions if ever useful |
| Detailed lever geometry | **FUTURE EXTENSION** | Do not require in v1 |
| Actual endpoint calibration | **FUTURE EXTENSION** | Optional measured pathway |
| Generic friction default | **REJECT** | Unknown remains unknown |
| Universal `L_true` | **REJECT** | No evidential need |
| Global human-strength scalar | **REJECT** | Central relationships do not require it |
| Fixed modality conversion table | **REJECT** | Device/task/user dependence |
| Hard observation-count gate | **REJECT** | Count is not information |
| Fixed 1-5 stability/DOF scale | **REJECT AS PROPOSED** | No validated universal scale found |

---

# Open Questions

The evidence leaves the following questions legitimately open:

1. Does M1 or M2 better represent one execution profile used across multiple stable equipment contexts?
2. How much predictive value does exact OEM identity add after stable direct-instance history is mature?
3. Does starting resistance improve performance transfer materially, or mainly preserve load-entry interpretation?
4. Should an OEM-effective cable coordinate be explicitly derived, or retained only as local mechanical metadata?
5. How should multiple correlated source profiles be combined without double-counting common information?
6. Do relationship parameters themselves need temporal dynamics as familiarity changes?
7. How much incremental predictive value does equipment identity provide beyond N-BIO's already-rich execution semantics?
8. Can arbitrary ordinal-device relationships ever become reliable enough for useful cross-device transfer?
9. Are full resistance curves practically available and useful enough to justify structured storage?
10. At what relationship density does a central hypermodel materially outperform independent pairwise relationships?
11. Can cross-user priors improve cold start without materially increasing negative transfer?
12. How often do two physical instances of the same exact model produce meaningful personal-performance differences?

These should become empirical questions rather than hidden assumptions.

---

# Full Bibliography

## Resistance training, equipment, and biomechanics

**Biscarini, A.**  
“Measurement of Power in Selectorized Strength-Training Equipment.”  
*Journal of Applied Biomechanics*, 2012, 28(3), 229-241.  
DOI: **10.1123/jab.28.3.229**.

**Biscarini, A., & Bonafoni, S.**  
“Optimization of the biomechanical design of plate-loaded strength training machines: The free-weight lifting experience.”  
*Proceedings of the Institution of Mechanical Engineers, Part P: Journal of Sports Engineering and Technology*, 2017, 231(1), 14-20.  
DOI: **10.1177/1754337115624076**.

**Brodt, G. A., Melo, M. de O., Bonezi, A., Gertz, L. C., & Loss, J. F.**  
“Avaliação da Força de Atrito em máquina de musculação durante exercícios de extensão de joelho.”  
*Motriz: Revista de Educação Física*, 2013, 19(2), 523-531.  
DOI: **10.1590/S1980-65742013000200030**.  
Used only as limited direct evidence that friction can vary with apparatus/load/speed; not as a generic coefficient.

**Cacchio, A., Don, R., Ranavolo, A., Guerra, E., McCaw, S. T., Procaccianti, R., Camerota, F., Frascarelli, M., & Santilli, V.**  
“Effects of 8-week strength training with two models of chest press machines on muscular activity pattern and strength.”  
*Journal of Electromyography and Kinesiology*, 2008, 18(4), 618-627.  
DOI: **10.1016/j.jelekin.2006.12.007**.

**Cotterman, M. L., Darby, L. A., & Skelly, W. A.**  
“Comparison of muscle force production using the Smith machine and free weights for bench press and squat exercises.”  
*Journal of Strength and Conditioning Research*, 2005, 19(1), 169-176.  
DOI: **10.1519/14433.1**.

**Dalleau, G., Baron, B., Bonazzi, B., Leroyer, P., Verstraete, T., & Verkindt, C.**  
“The influence of variable resistance moment arm on knee extensor performance.”  
*Journal of Sports Sciences*, 2010, 28(6), 657-665.  
DOI: **10.1080/02640411003631976**.

**Farias, D. de A., Willardson, J. M., Paz, G. A., Bezerra, E. de S., & Miranda, H.**  
“Maximal Strength Performance and Muscle Activation for the Bench Press and Triceps Extension Exercises Adopting Dumbbell, Barbell, and Machine Modalities Over Multiple Sets.”  
*Journal of Strength and Conditioning Research*, 2017, 31(7), 1879-1887.  
DOI: **10.1519/JSC.0000000000001651**.

**Folland, J., & Morris, B.**  
“Variable-cam resistance training machines: do they match the angle-torque relationship in humans?”  
*Journal of Sports Sciences*, 2008, 26(2), 163-169.  
DOI: **10.1080/02640410701370663**.

**Grgic, J., Lazinica, B., Schoenfeld, B. J., & Pedisic, Z.**  
“Test-Retest Reliability of the One-Repetition Maximum (1RM) Strength Assessment: a Systematic Review.”  
*Sports Medicine - Open*, 2020, 6, 31.  
DOI: **10.1186/s40798-020-00260-z**.

**Haugen, M. E., Vårvik, F. T., Larsen, S., Haugen, A. S., van den Tillaar, R., & Bjørnsen, T.**  
“Effect of free-weight vs. machine-based strength training on maximal strength, hypertrophy and jump performance - a systematic review and meta-analysis.”  
*BMC Sports Science, Medicine and Rehabilitation*, 2023, 15, 103.  
DOI: **10.1186/s13102-023-00713-4**.

**Hernández-Belmonte, A., Buendía-Romero, Á., Pallarés, J. G., & Martínez-Cava, A.**  
“Velocity-Based Method in Free-Weight and Machine-Based Training Modalities: The Degree of Freedom Matters.”  
*Journal of Strength and Conditioning Research*, 2023, 37(9), e500-e509.  
DOI: **10.1519/JSC.0000000000004480**.

**Hernández-Belmonte, A., Buendía-Romero, Á., Franco-López, F., Martínez-Cava, A., & Pallarés, J. G.**  
“Adaptations in athletic performance and muscle architecture are not meaningfully conditioned by training free-weight versus machine-based exercises: Challenging a traditional assumption using the velocity-based method.”  
*Scandinavian Journal of Medicine & Science in Sports*, 2023, 33(10), 1948-1957.  
DOI: **10.1111/sms.14433**.

**Larsen, S., et al.**  
“Dumbbell versus cable lateral raises for lateral deltoid hypertrophy: an experimental study.”  
*Frontiers in Physiology*, 2025, 16, 1611468.  
DOI: **10.3389/fphys.2025.1611468**.

**Marcos-Frutos, D., Miras-Moreno, S., Márquez, G., & García-Ramos, A.**  
“Comparative Effects of the Free Weights and Smith Machine Squat and Bench Press: The Important Role of Specificity for Strength Adaptations.”  
*International Journal of Sports Physiology and Performance*, 2025, 20(2), 292-300.  
DOI: **10.1123/ijspp.2024-0274**.

**Mitter, B., Csapo, R., Bauer, P., & Tschan, H.**  
“Reproducibility of strength performance and strength-endurance profiles: A test-retest study.”  
*PLOS ONE*, 2022, 17(5), e0268074.  
DOI: **10.1371/journal.pone.0268074**.

**Pallarés, J. G., Hernández-Belmonte, A., Martínez-Cava, A., Vetrovsky, T., Steffl, M., & Courel-Ibáñez, J.**  
“Effects of range of motion on resistance training adaptations: A systematic review and meta-analysis.”  
*Scandinavian Journal of Medicine & Science in Sports*, 2021, 31(10), 1866-1881.  
DOI: **10.1111/sms.14006**.

**Ruiz-Alias, S. A., Baena-Raya, A., Hernández-Martínez, A., Díez-Fernández, D. M., Rodríguez-Pérez, M. A., & Pérez-Castilla, A.**  
“Estimating Repetitions in Reserve During the Bench Press Exercise: Should We Consider Sex and the Exercise Equipment?”  
*Sports Health*, 2025, 17(5), 1007-1012.  
DOI: **10.1177/19417381241285891**.

**Saeterbakken, A. H., van den Tillaar, R., & Fimland, M. S.**  
“A comparison of muscle activity and 1-RM strength of three chest-press exercises with different stability requirements.”  
*Journal of Sports Sciences*, 2011, 29(5), 533-538.  
DOI: **10.1080/02640414.2010.543916**.

**Santana, J. C., Vera-Garcia, F. J., & McGill, S. M.**  
“A kinetic and electromyographic comparison of the standing cable press and bench press.”  
*Journal of Strength and Conditioning Research*, 2007, 21(4), 1271-1277.  
DOI: **10.1519/R-20476.1**.

**Schwanbeck, S., Chilibeck, P. D., & Binsted, G.**  
“A comparison of free weight squat to Smith machine squat using electromyography.”  
*Journal of Strength and Conditioning Research*, 2009, 23(9), 2588-2591.  
DOI: **10.1519/JSC.0b013e3181b1b181**.

**Simpson, S. R., Rozenek, R., Garhammer, J., Lacourse, M. G., & Storer, T. W.**  
“Comparison of One Repetition Maximums Between Free Weight and Universal Machine Exercises.”  
*Journal of Strength and Conditioning Research*, 1997, 11(2), 103-106.  
DOI: **10.1519/00124278-199705000-00009**.

**Willardson, J. M., & Bressel, E.**  
“Predicting a 10 Repetition Maximum for the Free Weight Parallel Squat Using the 45 Degree Angled Leg Press.”  
*Journal of Strength and Conditioning Research*, 2004, 18(3), 567-571.  
DOI: **10.1519/1533-4287(2004)18<567:PARMFT>2.0.CO;2**.

## Statistical transfer, modular modelling, and negative transfer

**Aßmann, C., Boysen-Hogrefe, J., & Pape, M.**  
“Bayesian Analysis of Static and Dynamic Factor Models: An Ex-Post Approach Towards the Rotation Problem.”  
*Journal of Econometrics*, 2016, 192(1), 190-206.  
DOI: **10.1016/j.jeconom.2015.10.010**.

**Bakker, B., & Heskes, T.**  
“Task Clustering and Gating for Bayesian Multitask Learning.”  
*Journal of Machine Learning Research*, 2003, 4, 83-99.

**Brynjarsdóttir, J., & O'Hagan, A.**  
“Learning about physical parameters: the importance of model discrepancy.”  
*Inverse Problems*, 2014, 30(11), 114007.  
DOI: **10.1088/0266-5611/30/11/114007**.

**Goudie, R. J. B., Presanis, A. M., Lunn, D., De Angelis, D., & Wernisch, L.**  
“Joining and splitting models with Markov melding.”  
*Bayesian Analysis*, 2019, 14(1), 81-109.  
DOI: **10.1214/18-BA1104**.

**Kaizer, A. M., Koopmeiners, J. S., & Hobbs, B. P.**  
“Bayesian hierarchical modeling based on multisource exchangeability.”  
*Biostatistics*, 2018, 19(2), 169-184.  
DOI: **10.1093/biostatistics/kxx031**.

**Kennedy, M. C., & O'Hagan, A.**  
“Bayesian Calibration of Computer Models.”  
*Journal of the Royal Statistical Society: Series B*, 2001, 63(3), 425-464.  
DOI: **10.1111/1467-9868.00294**.

**Neuenschwander, B., Wandel, S., Roychoudhury, S., & Bailey, S.**  
“Robust exchangeability designs for early phase clinical trials with multiple strata.”  
*Pharmaceutical Statistics*, 2016, 15(2), 123-134.  
DOI: **10.1002/pst.1730**.

**Schmidli, H., Gsteiger, S., Roychoudhury, S., O'Hagan, A., Spiegelhalter, D., & Neuenschwander, B.**  
“Robust meta-analytic-predictive priors in clinical trials with historical control information.”  
*Biometrics*, 2014, 70(4), 1023-1032.  
DOI: **10.1111/biom.12242**.

## Forecast evaluation

**Bolin, D., & Wallin, J.**  
“Local Scale Invariance and Robustness of Proper Scoring Rules.”  
*Statistical Science*, 2023, 38(1), 140-159.  
DOI: **10.1214/22-STS864**.

**Bracher, J., Ray, E. L., Gneiting, T., & Reich, N. G.**  
“Evaluating Epidemic Forecasts in an Interval Format.”  
*PLOS Computational Biology*, 2021, 17(2), e1008618.  
DOI: **10.1371/journal.pcbi.1008618**.

**Dawid, A. P.**  
“Present Position and Potential Developments: Some Personal Views - Statistical Theory: The Prequential Approach.”  
*Journal of the Royal Statistical Society Series A*, 1984, 147(2), 278-290.  
DOI: **10.2307/2981683**.

**Epstein, E. S.**  
“A Scoring System for Probability Forecasts of Ranked Categories.”  
*Journal of Applied Meteorology*, 1969, 8(6), 985-987.  
DOI: **10.1175/1520-0450(1969)008<0985:ASSFPF>2.0.CO;2**.

**Gneiting, T., & Raftery, A. E.**  
“Strictly Proper Scoring Rules, Prediction, and Estimation.”  
*Journal of the American Statistical Association*, 2007, 102(477), 359-378.  
DOI: **10.1198/016214506000001437**.

## Measurement and semantics

**Joint Committee for Guides in Metrology.**  
*International Vocabulary of Metrology - Basic and General Concepts and Associated Terms (VIM), 3rd edition.*  
Definition 1.26: Ordinal quantity.

**Schadow, G., & McDonald, C. J.**  
*Unified Code for Units of Measure (UCUM).*  
Relevant point: annotations do not change the semantic identity of the underlying unit.

## Standards

**International Organization for Standardization.**  
*ISO 20957-2:2024 - Stationary training equipment - Part 2: Strength training equipment - Additional specific safety requirements and test methods.*  
Edition 3, 2024.

Relevant scope limitation: accuracy classes are not applicable to this category because they do not affect safety.

## OEM and manufacturer evidence

Manufacturer specifications were treated as product/model-specific declared information, not automatically as measured calibration of a particular gym instance.

**Life Fitness - Adjustable Cable Crossover, LCM-CC.**  
Documented 92.5 kg stack, 46.25 kg user-effective resistance, 2:1 cable ratio.

**Hammer Strength / Life Fitness - Plate Loaded Iso-Lateral Row.**  
Documented starting resistance 5.4 kg per workarm.

**Hammer Strength / Life Fitness - Plate Loaded Seated Dip.**  
Documented starting resistance 2 kg per arm.

**Hammer Strength / Life Fitness - Plate Loaded Seated Calf Raise.**  
Documented starting resistance 27.2 kg.

**Hammer Strength / Life Fitness - Plate Loaded Linear Leg Press.**  
Documented starting resistance 53 kg.

**Precor - Discovery DPL0601 Angled Leg Press.**  
Documented starting weight 62 kg.

**Precor - Discovery DPL0802 Smith Machine.**  
Current detailed UK specification lists an unloaded Smith-bar assembly of 16 kg and an 11-degree glide path. Precor's current material elsewhere rounds the start weight to approximately 15-16 kg. Earlier official DPL0802 documentation listed 11.3 kg. The report therefore treats this as evidence that model/product identity may span mechanically relevant revisions, not as evidence that one starting value is eternally correct.

**International Weightlifting Federation.**  
Equipment specifications identifying standard 20 kg and 15 kg competition bars and standard plate/collar denominations.

---

# Final Self-Check

**Universal `L_true` introduced?**  
No.

**Global strength scalar introduced?**  
No.

**`L_Tensor` made physically homogeneous?**  
No.

**Equipment reduced to machine-only ontology?**  
No.

**Free weights treated as trivial machines?**  
No.

**Physical non-equivalence confused with uselessness?**  
No.

**Exact local physics ignored?**  
No.

**Exact local physics converted into personal equivalence without evidence?**  
No.

**Current `EntryBasis` reinterpreted?**  
No.

**Selector-labelled kg confused with ordinal levels?**  
No.

**Historical loads rewritten?**  
No.

**Unknown mechanics replaced by arbitrary defaults?**  
No.

**Exact manufacturer/model made mandatory?**  
No.

**Equipment history assigned to ContextModule private memory?**  
No.

**Hardware change automatically creates a new execution profile?**  
No.

**Barbell-RDL-to-Smith-RDL treated as a proven profile split?**  
No.

**Hierarchical pooling assumed to prevent negative transfer automatically?**  
No.

**Generic population strength priors made necessary?**  
No.

**Hard one-observation or three-observation transfer gates retained?**  
No.

**Generic e1RM substituted for N-BIO capability?**  
No.

**Future information allowed into historical prediction?**  
No.

**OEM specification confused with measured instance calibration?**  
No.

**ISO safety compliance confused with cross-device force calibration?**  
No.

**Stray Mandarin/CJK text retained?**  
No.

---

# Final Conclusion

The smallest defensible N-BIO-7F is **not an equipment converter**.

It is a relationship-learning layer over heterogeneous, historically grounded local evidence and capability states.

Its governing responsibilities are:

```text
PHYSICS
What is genuinely known about this local apparatus and coordinate?

SAME-PROFILE N-BIO
What does the user's evidence imply about capability in this local context?

7F
Does another context improve prediction here, and by how much?

PROBABILITY
How uncertain is that relationship?

PREQUENTIAL VALIDATION
Did transferring that information actually improve the future prediction?
```

There is no scientific or architectural step that requires:

```text
derive universal equipment load
```

and no step that requires:

```text
derive the user's one true strength
```

The appropriate system instead preserves:

\[
\boxed{\text{local evidence}}
\]

\[
\boxed{\text{local semantics}}
\]

\[
\boxed{\text{local capability}}
\]

and learns:

\[
\boxed{\text{uncertain relationships}}
\]

with:

\[
\boxed{\text{no-transfer}}
\]

remaining a first-class result.

The one major unresolved architecture choice - shared same-profile capability with equipment-conditioned observation mappings versus separate profile-by-equipment local capability facets - is now sufficiently well specified to be tested rather than guessed.

That is the correct research endpoint for N-BIO-7F.
