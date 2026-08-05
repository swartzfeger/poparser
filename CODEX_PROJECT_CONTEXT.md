# PO Parser - Codex Project Context

Last updated: 2026-08-04

## How To Use This File

This document is a handoff between Codex sessions and computers. At the start of
a new task, tell Codex:

> Read `CODEX_PROJECT_CONTEXT.md`, inspect the current repository state, and use
> it as background context. Do not assume the document is newer than the code.

The repository and its tests remain the source of truth. Update this file after
meaningful architectural, workflow, or product changes.

## Project Identity

- Product: PO Parser
- Repository: https://github.com/swartzfeger/poparser
- Local path used on the Mac mini: `/Users/jay/dev/kotlin/poparser`
- Current branch: `main`
- Current version: `1.6.0`
- Snapshot commit when this file was created: `b176564`
- Technology: Kotlin Multiplatform, Compose Desktop, JVM 21
- Development machine: macOS
- Production client: Windows only

PO Parser imports customer purchase orders in PDF or XLSX format, extracts order
and item data, enriches it with Precision Laboratories master data, estimates
shipping boxes, and exports a Sage-ready CSV.

## Product Workflow

1. The user chooses or drags PDF/XLSX purchase orders into the application.
2. `OrderFileParser` extracts text or invokes OCR when required.
3. `PdfFieldParser` chooses the best customer-specific layout strategy.
4. The strategy returns `ParsedPdfFields` and `ParsedPdfItem` values.
5. `OrderEnricher` resolves customer data, SKU descriptions, prices, quantity
   discounts, UOM conversions, and GL accounts.
6. `PackagingPlanner` uses the enriched/export quantity for volume and weight.
7. `SageCsvExporter` writes the final Sage-compatible CSV.

Do not calculate pricing or packaging from the raw PO quantity when
`quantityForExport` has been normalized for UOM. Both must use the same resolved
quantity.

## Important Source Files

- Application shell and dialogs:
  `composeApp/src/jvmMain/kotlin/com/jay/parser/main.kt`
- Main parser UI:
  `composeApp/src/jvmMain/kotlin/com/jay/parser/ui/MainScreen.kt`
- PDF/XLSX routing and OCR selection:
  `composeApp/src/jvmMain/kotlin/com/jay/parser/pdf/OrderFileParser.kt`
- Native PDF extraction:
  `composeApp/src/jvmMain/kotlin/com/jay/parser/pdf/PdfTextExtractor.kt`
- OCR extraction:
  `composeApp/src/jvmMain/kotlin/com/jay/parser/pdf/OcrPdfTextExtractor.kt`
- Customer parser selection:
  `composeApp/src/jvmMain/kotlin/com/jay/parser/parser/StrategyRegistry.kt`
- Enrichment, UOM, pricing, and discounts:
  `composeApp/src/jvmMain/kotlin/com/jay/parser/parser/OrderEnricher.kt`
- Export models:
  `composeApp/src/jvmMain/kotlin/com/jay/parser/models/ExportModels.kt`
- Sage CSV output:
  `composeApp/src/jvmMain/kotlin/com/jay/parser/export/SageCsvExporter.kt`
- Master-list import and persistence:
  `composeApp/src/jvmMain/kotlin/com/jay/parser/masterdata/`
- Packaging import, persistence, and planning:
  `composeApp/src/jvmMain/kotlin/com/jay/parser/packaging/`
- Bundled data:
  `composeApp/src/jvmMain/resources/data/`
- Desktop packaging configuration:
  `composeApp/build.gradle.kts`

## Parsing Architecture

Customer PDF formats are handled by implementations of `LayoutStrategy`. A
strategy has matching/scoring behavior and parsing behavior. `StrategyRegistry`
selects the highest-scoring matching strategy.

When changing a customer parser:

- Inspect the actual PDF and extracted/debug text first.
- Keep the fix inside that customer's strategy when possible.
- Avoid broad OCR normalization changes for one noisy PO.
- Add a focused regression test using representative extracted lines.
- Run the full test suite because similar SKUs and PO labels occur across
  customers.

Fisher Scientific POs are unusually noisy and often require OCR. Fisher parsing
contains special segmentation and PO-number recovery logic. Treat broad OCR or
Fisher changes as high risk and test on Windows as well as macOS.

OCR currently invokes an external Tesseract executable. On Windows it searches
the app/working directory and standard Tesseract installation paths. On macOS it
searches common Homebrew/MacPorts paths. Do not assume macOS OCR output exactly
matches Windows output.

## Enrichment And UOM Rules

`OrderEnricher` is the central authority for resolved customer data, SKU repair,
master pricing, quantity discounts, UOM normalization, GL accounts, and the call
to packaging.

The raw PO price is retained as `unitPriceReference`, but normal exported pricing
comes from the active master data and customer price level. `PLBL` is a special
fallback that may use the parsed PO price when no master price exists.

UOM handling is customer-sensitive. The presence of `144V`, `500V`, `1V`, `1B`,
or similar text in a SKU does not mean every customer's visible quantity should
always be divided. Existing exceptions in `OrderEnricher` are intentional.

Regression examples:

- Intercon `145-144V-100`, raw quantity 288, resolves to quantity 2.
- School Specialty 12-vial quantities are divided into sellable packs.
- Jayhawk WI keeps order quantity and adjusts mapped price instead.
- Some Diversified, Dove, and Eisco rows already express pack quantity and must
  not be divided a second time.
- Auto-Chlor has special vial/strip quantity and price behavior.
- Butler Chemical orders `145-500V-100` as individual vials, so its visible
  quantity is divided by 500 into sellable packages.
- Beta Procesos `1V` quantities already represent the ordered sellable quantity
  and remain unchanged.

When fixing UOM behavior, verify all of these independently:

- `quantityRaw`
- `quantityForExport`
- `unitPriceResolved`
- extended CSV amount
- packaging volume, weight, and box count

## Master Data

Bundled defaults live in:

- `data/items.json`
- `data/customers.json`
- `data/glAccounts.json`
- built-in quantity-discount defaults from `QtyDiscountMapper`

The UI's **Update Master List** dialog imports a client XLSX file and writes JSON
overrides outside the installed application. Imported overrides take precedence
over bundled resources. Existing imports are backed up before replacement.

Prices support three decimal places. JSON numbers omit unnecessary trailing
zeroes, so `265.250` may be represented as `265.25` without losing value.

Application data locations:

- macOS: `~/Library/Application Support/PO Parser/`
- Windows: `%APPDATA%\PO Parser\`

Master overrides are under `master-data/`. Packaging overrides are under
`packaging-data/`.

## Packaging Calculator

Packaging support was introduced in version 1.6.0 and is preliminary.

- Product dimensions and weights come from `productPackaging.json`.
- The bundled August 5, 2026 dataset contains 180 products: all 180 have weights
  and 150 have complete dimensions.
- The user can replace the data through **Update Packaging Data** using a CSV.
- Missing measurements do not block order parsing; they produce a review status.
- Box capacity reserves 10 percent for packing material (`MAX_FILL_RATIO = 0.90`).
- Maximum weight is 50 pounds per box.
- Box IDs 1 through 15 and dimensions are defined in `ShippingBoxes.kt`.
- Packaging calculations use `quantityForExport`, not `quantityRaw`.
- The UI shows total boxes and box plan in the parsed-order summary.
- The CSV writes the shipping plan to `Invoice Note`; packaging measurements stay in the UI.
- Invoice notes identify grouped box count, per-box weight (or `?`), box ID and
  dimensions, and the one-based order line numbers contained in each box.
- SKU segments `12V`, `24V`, `400V`, and `500V` represent indivisible packages;
  packaging calculations round fractional resolved quantities up to a whole package.

The current planner is volume-based with per-item dimensional fit checks. It is
not a full three-dimensional spatial packing engine. The product measurement
database is incomplete by design and will be improved over time.

## Sage CSV Requirements

The exporter intentionally produces:

- Windows-1252 encoding
- CRLF line endings
- quoted CSV cells
- negative unit prices and amounts for Sage import
- up to three decimal places for unit prices, with trailing zeroes omitted
- an `Invoice Note` shipping summary repeated on each line of an order

The **No Ship Via**, **No Ship To**, and **No Invoice Note** settings can blank
those fields during export. The parsed-order UI retains detailed volume, weight,
box-plan, and packaging-status information that is no longer exported as separate
CSV columns. Preserve Sage column ordering unless the client explicitly approves
a schema change.

## Build And Test

Use JDK 21.

Run the app on macOS:

```bash
./gradlew :composeApp:run
```

Run all checks:

```bash
./gradlew :composeApp:check
```

Create the native distribution for the current host:

```bash
./gradlew :composeApp:createDistributable
```

Windows development run:

```powershell
.\gradlew.bat :composeApp:run
```

Windows MSI/EXE build:

```powershell
powershell -ExecutionPolicy Bypass -File .\build-windows.ps1
```

Compose native installers must be built on their target operating system. A
successful macOS package does not prove that Windows packaging succeeds.

Before finishing a change, normally run:

```bash
./gradlew :composeApp:check
git diff --check
git status --short
```

Check `git status` for untracked files before committing. Version 1.6.0 initially
failed to compile on Windows because tracked code referenced new packaging files
that had not been added to Git.

## Releases

GitHub Releases are used to distribute the application:

https://github.com/swartzfeger/poparser/releases

The Windows installer uses a stable `upgradeUuid` in `composeApp/build.gradle.kts`.
Do not change it for future releases of the same Windows application.

The application version currently appears in both:

- `APP_VERSION` in `main.kt`
- `appVersion` in `composeApp/build.gradle.kts`

Update both together. A future improvement should establish one version source.

## Discussed But Not Implemented

A lightweight update checker is desired in the About dialog. This is explicitly
not a silent/self-installing updater.

Proposed behavior:

1. **Check for Updates** queries the latest public GitHub Release.
2. It compares the release version with the installed application version.
3. If newer, it shows the new version and an **Open Download Page** action.
4. The action opens the exact GitHub Release page in the default browser.
5. **View Changelog** opens the repository's Releases page.

The repository is public, so the application should not embed a GitHub token.
Release tag formats have historically included names such as
`POParserV1.6.0`; version parsing should extract semantic version numbers rather
than require a tag to start with only `v`.

## Current Regression Tests

Focused tests currently cover:

- master-list import
- quantity discounts
- Sage CSV formatting and packaging columns
- packaging CSV import and box planning
- Auto-Chlor
- Bartovation
- Beta Procesos
- Butler Chemical Products
- Electronic Controls Design
- Eisco
- Fisher Scientific
- Intercon Chemical
- School Specialty
- Sanitech

When a real customer PO reveals a bug, prefer adding a small extracted-text test
to the corresponding strategy test rather than committing confidential source
documents to the repository.

## Collaboration Guardrails

- Read the code and current Git status before editing.
- Do not revert unrelated user changes in a dirty worktree.
- Keep customer-specific parsing fixes narrowly scoped.
- Preserve cross-platform behavior; the client runs Windows even though most
  development happens on macOS.
- Validate output CSV contents, not only whether parsing completed.
- Be conservative with Fisher/OCR changes because previous broad OCR experiments
  improved one field while damaging the rest of the order.
- Do not commit client purchase orders, exports, master lists, or debug logs
  unless the user explicitly requests it and confidentiality has been considered.
- Add every new source/resource/test file to Git before publishing a release.

## Maintaining This Handoff

Update this document when any of the following changes:

- application version or release process
- parsing/enrichment architecture
- persisted data format or location
- UOM rules with broad impact
- Sage CSV schema
- packaging algorithm or box catalog
- OCR installation/distribution strategy
- update-checking behavior

Do not turn this into a chronological chat log. Keep durable decisions here and
put detailed behavior in code and tests.
