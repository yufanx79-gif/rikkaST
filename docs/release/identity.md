# 10-22-16 Release Identity & Package Boundaries (`docs/release/identity.md`)

| Attribute | Official RikkaHub 3.1.0 | Public Release (`2.5.6`) | Candidate Input (`fixed-1`) | Target Release (`10-22-16.zip`) | Rationale |
|---|---|---|---|---|---|
| `namespace` | `me.rerere.rikkahub` | `me.rerere.rikkahub` | `me.rerere.rikkahub` | `me.rerere.rikkahub` | Preserves resource & R class compatibility |
| `applicationId` | `me.rerere.rikkahub` | `me.rerere.rikkahub.st` | `me.rerere.rikkahub.st` | `me.rerere.rikkahub.st` | Coexists with official RikkaHub; preserves rikkaST in-place upgrade path |
| `debug applicationIdSuffix` | `.debug` | `.debug` | `.debug` | `.debug` | Debug builds install side-by-side as `me.rerere.rikkahub.st.debug` |
| `versionCode` | `193` | `238` | `240` | `241` | Strictly monotonic (`241 > 240 > 238`), preventing downgrade errors on upgrade |
| `versionName` | `3.1.0` | `2.5.6` | `2.5.8` | `2.5.9` | Distinguishes 10-22-16 recovery source release from fixed-1 (2.5.8) and public (2.5.6) |
| `AppDatabase.version` | `31` | `29` | `32` | `32` | Superset schema containing all 3.1.0 v31 tables + rikkaST tables with verified v28/v29/v30/v31 -> v32 migrations |
| `rikkahub.json` target | `3.1.0` (`eeef60b2...`) | N/A | `3.1.0` (`eeef60b2...`) | `3.1.0` (`eeef60b2...`) | Locked upstream RikkaHub tag & commit |
| `sillytavern.json` target | N/A | `1.19.0` | `1.19.0` (`7e8663cd...`) | `1.19.0` (`7e8663cd...`) | Locked upstream SillyTavern tag & commit |
