# Weight OCR acceptance and placard research

## Scope and acceptance

Kian accepted the general weight-stack selection/review behaviour on 6 September 2026. Freeze that behaviour after the final >=1,000 kg attention check; do not expand OCR/filter/column inference in this mission. This is workflow acceptance, not proof of complete or error-free recognition. Placard physical testing continues separately; machine masking and LAB-2C remain deferred.

Version 0.6.1 keeps positive high readings visible, unchecked, with a CHECK WEIGHT explanation in row evidence and exported warnings. The inclusive threshold applies after lb conversion, to both main and add-on captures, explicit kg and column-selected extraction, and human edits. A user may deliberately select a high row after inspecting it. No decimal is inserted automatically. Legacy persisted drafts with high readings lose their inclusion/review confirmation once; policy-marked drafts preserve subsequent explicit review. Prior silent upper cut-offs (2,000/5,000) are removed so suspicious joined labels remain reviewable.

Evidence: export lab2b-ocr-stack-1788735384105.json reads the photographed bottom 115.5 label as 1155. The white-background export lab2b-ocr-stack-1788735597432.json has joined raw labels including 59130 and omits some physical settings from its reviewed list. The guard does not claim to repair those omissions.

## Logo recognition: research recommendation, not implemented

Keep the agreed two crops: placard and optional logo. Compare OCR brand matching with a reference-logo gallery using local feature matching, then a compact image embedding model if needed. OpenCV demonstrates feature matching plus homography for known planar objects: https://docs.opencv.org/4.x/d1/de0/tutorial_py_feature_homography.html . Simple low-detail logos may have too few stable features; measure this rather than assume success.

MediaPipe provides an Android Image Embedder and similarity comparison: https://developers.google.com/edge/mediapipe/solutions/vision/image_embedder/android . This establishes deployment feasibility, not gym-logo accuracy. Reference variants should cover old/new wordmarks, reversed colours, and photographed examples. Evaluate on held-out real photos, with unknown brands, blur, glare and difficult fonts. Report top candidate accuracy, false acceptance of unknowns, latency, memory and asset size. Similarity scores are not calibrated probabilities. Require an acceptance threshold and separation from the next candidate, tuned on validation data. If retrieval is inadequate, compare a small trained classifier. A YOLO detector becomes useful if we later need to locate logos without the user's crop; it is not necessary merely to identify the already-isolated logo.

## Infographic extraction: feasibility and limits

Proposed first target: automatically find the illustration inside the placard crop, remove the background, retain the pictured machine and offer a preview. Simple thresholding/connected components are a baseline for clean line art; a person, arrows and overlapping machine strokes may require segmentation. Do not invent hidden machine parts. A thumbnail retaining the user figure can be an acceptable reviewed intermediate; a clean machine-only icon is a distinct quality target. No extra mandatory user crop.

Plausible descriptive candidates include machine family, seated/standing/lying posture, visible plate horns or stack, linear rails versus pivoting lever, and depicted movement direction. These are illustration-derived suggestions, separately reviewed from OCR text and manufacturer specifications. Muscle shading can capture the manufacturer's depicted targets but does not establish quantitative muscle contribution.

Exact rail angle is not generally recoverable from a stylised perspective drawing. A planar homography can rectify the photographed placard, not undo the illustration's own 3D projection or artistic simplification: https://docs.opencv.org/4.x/d9/dab/tutorial_homography.html . Measurement is only defensible with suitable metric calibration and a verified side elevation, or explicit text/manufacturer model specifications. A generic visual estimate must not become a verified physics input. As an example, Hammer Strength lists a 45-degree pressing angle for its current Plate Loaded Hack Squat: https://www.lifefitness.com/en-us/catalog/strength-training/plate-loaded/hack-squat . This does not identify Kian's photographed machine or establish the angle of every hack squat.

Even a known angle does not establish effective resistance: a simple ideal uncounterbalanced sled uses gravitational force m*g*sin(theta) along its rails; moving mass, counterbalance, friction and transmission must be established separately. Do not infer pulley ratios, starting resistance, lever arms or resistance curves from instructional artwork alone.

## Verification

Source isolation and diff whitespace checks passed locally. Android test/build/lint and native compatibility checks must be read from the new commit's CI result; previous 0.6.0 results do not validate this change. Added focused tests cover threshold boundary, 1155, joined labels above prior caps, add-ons, edits and lb conversion.
