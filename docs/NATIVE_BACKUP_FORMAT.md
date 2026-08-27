# My Mettle Native full-backup format

## Purpose

The Native full backup is the restoration contract for My Mettle Native.

It is **not** a My Mettle Lite import format. Runtime Native code does not parse Lite backups. If historical Lite data needs to be brought forward during development/cutover, translate it offline into the then-current Native full-backup format first, then restore that Native backup normally.

## Compatibility policy

The current format is deliberately exact-schema only.

A backup records:

- `kind = "my-mettle-native-full-backup"`;
- `formatVersion`;
- `databaseSchemaVersion`;
- export timestamp;
- every application Room table, including empty tables;
- every row using explicit SQLite storage classes.

Restore requires the backup `databaseSchemaVersion` to equal the Room schema currently opened by the app. A mismatch is rejected before database mutation. During pre-cutover development, an older backup should be translated to the latest Native format rather than silently guessed/migrated by the restore UI.

The current N-BIO-7A.5 database is Room 14. Because the backup discovers application tables from the live schema rather than maintaining a second table list, Room 14 snapshots include `note_interpretation_run` and `context_annotation` alongside their canonical raw owners such as `session_review` and `exercise_reflection`. Raw note text and derived interpretation/provenance therefore round-trip as distinct state.

A Room 13 Native backup is intentionally rejected by Room 14 under this exact-schema policy; it must be translated to the current Native format before restore.

## Version 1 envelope

```json
{
  "kind": "my-mettle-native-full-backup",
  "formatVersion": 1,
  "databaseSchemaVersion": 14,
  "exportedAt": "2026-08-27T10:30:00Z",
  "tables": [
    {
      "name": "health_integration_state",
      "columns": ["id", "provider", "permissionState", "lastSyncedAt", "lastError"],
      "rows": [
        [
          {"type": "text", "value": "primary"},
          {"type": "text", "value": "fixture"},
          {"type": "text", "value": "allowed"},
          {"type": "null"},
          {"type": "text", "value": "example"}
        ]
      ]
    }
  ]
}
```

The real full backup must contain every current application table, not only the table shown in this example.

## Cell encoding

Every cell carries its SQLite storage class explicitly:

| `type` | `value` encoding |
|---|---|
| `null` | no value |
| `integer` | signed 64-bit integer encoded as a decimal string |
| `real` | finite IEEE-754 double encoded as a decimal string |
| `text` | JSON string |
| `blob` | Base64 string |

Integer/real values are strings deliberately so translation tooling cannot silently lose 64-bit integer precision through a generic JSON-number implementation.

Temporal evidence chunk payloads therefore remain lossless BLOBs rather than being interpreted by the backup layer.

## Restore validation

Before mutation, Native verifies:

1. backup kind;
2. backup format version;
3. exact current Room schema version;
4. exact current application-table set;
5. exact column order/name for every table;
6. row width and cell encoding.

Restore then replaces all application-table contents inside one Room transaction and runs `PRAGMA foreign_key_check` before committing. If parsing, insertion or referential validation fails, the transaction is rolled back rather than leaving a partial restore.

Room internal tables such as `room_master_table`, `sqlite_sequence`, `android_metadata` and `sqlite_*` implementation tables are not canonical backup payload.

## Recomputable state is still part of a full snapshot

The full-backup contract is a current-state snapshot, not merely a minimum replay package. Derived context interpretation rows are recomputable, but Room 14 still includes them because they preserve historical interpreter/model/prompt/schema provenance and the exact state the user backed up.

Deleting annotations in normal app lifecycle remains independent from deleting raw notes. Backup/restore preserves whichever raw and derived rows exist at export time without collapsing one into the other.

## Offline Lite translation

A future/manual Lite→Native translation should target this contract, not runtime Lite parser classes.

The translator is responsible for producing rows that already obey the current Native schema and N-BIO semantic rules. Facts absent in Lite must remain absent/unknown. Historical Lite notes may legitimately remain unannotated until explicitly interpreted later; translation must not fabricate 7A.5 annotations.

Derived N-BIO state may be omitted/empty and recomputed from canonical raw evidence after restore only when the translator intentionally targets such a state. The shipped Native restore flow itself does not understand Lite formats or invent derived annotations.

When Room or the backup envelope changes, update the offline translation target accordingly.
