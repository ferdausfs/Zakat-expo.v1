# AGENT_LOG

Append-only log of AI agent work sessions on this repository.
Newest entry first — never delete or reorder existing entries.

---

## Task ID: Step 0 (Batch 1 re-delivery) — Z-09, Z-17, Z-02

- **Date:** 2026-10-24
- **Agent:** Z.ai GLM (autonomous coding session, user-directed)
- **Branch:** `batch1-step0-fixes` (work pushed here; PR to `main`)
- **Commit:** this entry ships with the batch commits — see `git log --oneline -5` on the branch
- **Feature:** Zakat correctness fixes — metal-rate timestamp gate, strict decimal parsing, honest "price not set" state

### What changed

1. **Z-09 — metal last-fetch timestamp no longer freezes failed conversions**
   - `app/src/main/java/com/ritesh/cashiro/data/metals/MetalRateService.kt`
     - BEFORE: `setZakatMetalLastFetchAt()` was written immediately after the
       provider fetch (old lines ~91-93), BEFORE USD→base conversion. A failed
       conversion therefore still stamped the fetch and `maybeAutoRefresh`
       stayed silent for ~24h with missing/stale prices.
     - AFTER: the stamp is written only when the fetch succeeded AND no
       attempted conversion failed (`!goldConversionFailed && !silverConversionFailed`).
       Manual skips are NOT attempts — they count as OK, so a refresh where
       both metals are skipped still records the stamp and the dashboard does
       not re-hit the network on every open (regression guard). When an
       attempted conversion fails, the stamp is left untouched so the next
       dashboard open retries.
   - Tests (`app/src/test/java/com/ritesh/cashiro/data/metals/MetalRateServiceTest.kt`):
     - `both metals manual skipped still records the fetch timestamp` (new)
     - `one metal skipped and the other conversion failed does not record timestamp` (new)
     - `conversion failure keeps the cached price for that metal` — extended with
       the stamp-untouched assertion (both-failed scenario)
     - `auto refresh respects manual override until explicit refresh` — extended:
       one skipped + one stored ⇒ stamp recorded
     - `refresh converts usd spot to base currency and stores timestamps` —
       covers both-stored ⇒ stamp recorded
     - All "not written" assertions seed an explicit stale stamp first so they
       are order-independent under the shared Robolectric DataStore.

2. **Z-17 — strict decimal-comma rules; no more silent reinterpretation**
   - NEW `app/src/main/java/com/ritesh/cashiro/utils/DecimalParsing.kt`
     - `parseUserDecimal()`: a comma is a decimal separator ONLY when it appears
       exactly once, is followed by exactly 1-2 digits at the end of the input
       ("2,5" → 2.5, "85,50" → 85.50). "1,500", "1,50,000", "1,5,0", mixed
       separators, signs, stray characters → **null (field invalid)** — never a
       silently different value. Bengali digits ০-৯ supported. Negatives rejected.
     - `isValidAmountInput()`: matching live-typing filter (allows "12," while
       typing; blocks a second separator or a third decimal digit).
   - Wired into every zakat numeric path:
     - `ZakatDashboardViewModel.parseAmount` (now on the companion; invalid stored
       price ⇒ ZERO ⇒ Z-02 "price not set" state)
     - `ZakatAssetsViewModel.parseAmount`
     - `ZakatScreen.kt` PriceField filter (gold/silver price inputs)
     - `ZakatAssetsScreen.kt` quantity/estimatedValue filters; save path no longer
       falls back to `BigDecimal.ONE` for an unparseable quantity (silently
       fabricating an asset); save button requires the relevant field to parse
     - `ZakatModulesScreens.kt` `amountFilter` + fitr save path (`pricePerKg`,
       `kgPerPerson`, save-button gate)
   - Tests: NEW `app/src/test/java/com/ritesh/cashiro/utils/NumericInputTest.kt`
     (46 vectors: comma rules, grouping rejection, Bengali digits, signs, blanks,
     typing filter, dashboard price-path mapping).

3. **Z-02 — explicit "price not set" state instead of a fake nisab**
   - `ZakatDashboardViewModel.kt`
     - BEFORE (lines ~319-324): `goldPrice.coerceAtLeast(BigDecimal.ONE)` fed a
       price of 1 into `nisabValue`, producing a fake nisab of ≈87 currency
       units — every balance looked above-nisab and a hawl started silently.
     - AFTER: `goldPriceMissing` / `silverPriceMissing` detected explicitly
       (`signum() <= 0`); when the applied nisab method's metal is missing:
       nisab values render as ZERO ("not set"), crossing detection is
       short-circuited to "no crossing", eligibility is false ⇒ no due lines and
       `zakatDue = 0`. `UiState.metalPriceMissing` carries the state to the UI.
   - `ZakatDashboardScreen.kt`: when `metalPriceMissing`, the Nisab status card
     shows "Nisab threshold: not set — metal price missing" plus an error-colored
     prompt instead of the misleading gap line; the Above/Below badge is hidden.
   - `app/src/main/res/values/strings.xml`: added
     `zakat_dash_nisab_price_missing_line`, `zakat_dash_price_missing_prompt`.

### Touched files

- `app/src/main/java/com/ritesh/cashiro/data/metals/MetalRateService.kt` (Z-09)
- `app/src/main/java/com/ritesh/cashiro/utils/DecimalParsing.kt` (NEW, Z-17)
- `app/src/main/java/com/ritesh/cashiro/presentation/ui/features/zakat/dashboard/ZakatDashboardViewModel.kt` (Z-02 + Z-17)
- `app/src/main/java/com/ritesh/cashiro/presentation/ui/features/zakat/dashboard/ZakatDashboardScreen.kt` (Z-02 UI)
- `app/src/main/java/com/ritesh/cashiro/presentation/ui/features/zakat/ZakatScreen.kt` (Z-17 filter)
- `app/src/main/java/com/ritesh/cashiro/presentation/ui/features/zakat/assets/ZakatAssetsViewModel.kt` (Z-17)
- `app/src/main/java/com/ritesh/cashiro/presentation/ui/features/zakat/assets/ZakatAssetsScreen.kt` (Z-17 filters + save path)
- `app/src/main/java/com/ritesh/cashiro/presentation/ui/features/zakat/modules/ZakatModulesScreens.kt` (Z-17 filter + fitr save)
- `app/src/main/res/values/strings.xml` (Z-02 strings)
- `app/src/test/java/com/ritesh/cashiro/data/metals/MetalRateServiceTest.kt` (Z-09 tests)
- `app/src/test/java/com/ritesh/cashiro/utils/NumericInputTest.kt` (NEW, Z-17 tests)
- `.github/workflows/build-apk.yml` (NEW, CI — manual debug-signed APK artifact build)

### Pending items

- **BLOCKED (fiqh, need scholar/owner decision — do NOT fix silently):**
  Z-05 ratti conversion (0.243 vs 0.1215 g), Z-12 hawl reset on dipping below
  nisab, Z-13 fiqh constants (SOLAR rate, nisab grams, ushr threshold, fitr kg
  per person, madhhab behaviour), Z-06(b) per-asset SOLAR/LUNAR choice.
- **Next batch (2):** approved list from the Phase 1 report §6 — backup coverage
  round-trips (Z-01/D-01), multi-currency (Z-03), nisab check + cash hawl start
  (Z-06 a,c), livestock hawl date (Z-07 additive Room migration + exported
  schema + migration test), Z-08, Z-10, Z-11, Z-14..Z-16, Z-18..Z-21.
- Known open question (documented, intentionally unchanged): "1.500" (dot as
  thousands separator, European style) still parses as 1.5 — the approved spec
  covered decimal commas only.

### Notes for next agent

- Batch gating: wait for the user's "batch 1 green" before Batch 2.
- Verification runs on GitHub Actions (`test.yml` on PR to main; unit tests via
  `:app:testStandardDebugUnitTest`). Termux-side: unzip + grep-verify only.
- The metal timestamps test class relies on distinct per-test timelines (T1..T7)
  because Robolectric shares one DataStore across methods — keep that pattern.
- `DecimalParsing` is the single source of truth for user-entered amounts; route
  any new numeric field through it.
