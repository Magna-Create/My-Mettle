# LAB-2B 0.6: number columns and unit confirmation

Starting remote HEAD: 86899d62ce1b3eee7745b98dfc85c6df98e8bc0e, agent/lab2b-ocr-stack.

Physical evidence: an unlabelled stack has lb on the left and decimal kg on the right; another has small KGS above the left numbers and LBS above the right. Neither side nor decimals establishes units. Both examples change increment part-way down. Do not complete a stack using a single assumed progression.

Implementation:

- Preserve unlabelled numeric readings with source boxes in diagnostic JSON. They do not enter kg output until the user confirms a column and unit.
- Associate explicit kg/lb text immediately above or beside a number. Explicit KGS above a number can supply the default kg parser.
- Propose two columns from paired rows with a fitted sloping centre line. This is a heuristic, with manual fallback. Never silently select a proposal.
- Choose number column + kg / lb opens a cyan strip. Drag the top half to move its upper end, bottom half for its lower end; adjust width. Buttons select proposed columns. User explicitly confirms the unit. Approximate paired lb/kg ratios can suggest a unit, but never establish it automatically.
- Unit conflicts and digit repairs remain unchecked. Changing column/unit clears previous edits, confirmation and filter comparison. Crop replacement clears column selection. Filter change preserves crop-relative column selection but clears current OCR/review.
- Selected lb values convert using 0.45359237 kg/lb with printed value and conversion recorded. Selected kg stays as printed. No missing weights inserted.
- Four-filter comparison matches rows by vertical geometry within the selected strip, reports missing/additional/conflicting readings and preserves every pass. No majority-vote replacement or automatic merging.
- OCR-only stack processing splits multi-number OCR lines into element boxes to preserve separate columns. Placard and VLM OCR behaviour is unchanged.
- OCR still uses the normalised source-resolution crop, not the smaller VLM preview. Existing bounded source normalisation remains (16 MP / 4096 edge). Additional tiling, deblurring and source-resolution expansion are deferred; no accuracy claim for those is made.
- Same app identity, native libraries and model registry. No production import, N-BIO changes, LAB-2C or machine masking.

Physical test: import each new photo, crop whole stack including top plate, Run OCR, Choose number column + kg / lb, confirm correct column, inspect row values, Compare four filters, Copy diagnostic JSON. Test left/right reversed, lb conversion, unit reassignment clearing review, and restart restoring selection. Add-ons remain optional.

Validation: source/binary isolation and diff whitespace pass. Final source 618dd6bfa08f558453bd8f833b2e81e1e9a0f614 passes Actions [34058885353](https://github.com/Magna-Create/My-Mettle/actions/runs/34058885353): 76 tests, zero failures/errors/skips, lint, APK, ELF/ZIP 16 KB checks and Termux native reuse/prebuilt build. APK 36,686,877 bytes; SHA-256 0422956eb8e6f9b168a86edf8e9fe8957653f0ba9c77e3a64366c855386016a6. Artifact lab2b-vlm-harness-debug, ID 9996883064. Physical acceptance pending.

Termux update (copy included explicitly):

```sh
(
set -e
cd "$HOME/My-Mettle"
git fetch origin
git switch agent/lab2b-ocr-stack
git pull --ff-only origin agent/lab2b-ocr-stack
bash experiments/lab2b-vlm-harness/tools/build_termux.sh
cp "$HOME/My-Mettle/experiments/lab2b-vlm-harness/app/build/outputs/apk/debug/app-debug.apk" "$HOME/storage/downloads/LAB-2B-OCR-debug.apk"
)
```

Install using Samsung My Files → Downloads → LAB-2B-OCR-debug.apk. Native inputs are unchanged; no new native bundle or model weights required.
