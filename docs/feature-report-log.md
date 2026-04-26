# Feature Report Log

Append-only. One row per `feature-reporter` run (including NOOPs).

Columns:
- `timestamp_utc` — `date -u +%Y-%m-%dT%H:%M:%SZ` at run start
- `mode` — `update | rebuild | since | area:<name> | bootstrap`
- `features_added` — count of new feature sections written
- `features_updated` — count of existing sections refreshed
- `deprecated_added` — count of new entries in Deprecated section
- `high_water_to` — new high-water timestamp the run advanced to
- `result` — `REPORT_OPENED | NOOP`
- `pr_link_or_NOOP` — PR URL or `NOOP`

| timestamp_utc | mode | features_added | features_updated | deprecated_added | high_water_to | result | pr_link_or_NOOP |
|---|---|---|---|---|---|---|---|
