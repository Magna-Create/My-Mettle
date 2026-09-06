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

Validation: source/binary isolation and diff whitespace pass locally. Android unit tests, lint, APK and native audits run in dedicated GitHub Actions; build result recorded after completion. Physical acceptance pending.
