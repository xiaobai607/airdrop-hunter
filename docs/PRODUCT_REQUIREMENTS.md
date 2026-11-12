# Airdrop Hunter — Product Requirements & Progress Ledger

> This is the project-wide source of truth. Before each implementation step, compare the current code and plan against this document. User-confirmed requirements override implementation assumptions. Cancelled and deferred items must not be reintroduced without explicit approval.

## 0.0 Current master checklist — authoritative status and remaining work (2026-11-12)

> This is the first place to check for project status and remaining work. It consolidates the still-valid user requirements and current evidence. Detailed specifications remain in their original sections below; historical progress notes are evidence, not a new source of requirements. When a historical note conflicts with this checklist or the latest user correction, this checklist and the latest correction win.

### Status definitions

- **Complete in code + CI:** implementation exists and the exact relevant commit has passing automated checks. This does not imply production deployment or physical-device acceptance.
- **Partial:** a foundation or part of the flow exists, but the complete user workflow is not proven.
- **Pending verification:** implementation exists, but required real-device, live-data, production, or recovery verification has not passed.
- **Pending implementation:** confirmed requirement has not yet been implemented end to end.
- **Deferred / cancelled:** explicitly not part of the current release; do not reintroduce without user approval.

### Current repository checkpoint

- Repository: `xiaobai607/airdrop-hunter`; active delivery branch: `feature/mobile-browser-home-dock-batch`.
- PR #135 remains **Draft / Open / not merged**. Latest code head at this checkpoint: `baba1209e780c9bb4c9ce1b54a27ba41bcd500a1`. This is the latest code revision; requirements-ledger commits are recorded in the continuation checkpoint below.
- Historical exact-head backend push workflow #43022038023 passed on `c51a5e1256c7b29071cbb0b4f10062b4a81a04bd`: **244 passed, 3 warnings, 0 failed**. Android was correctly skipped because that batch changed backend files only. Current branch head and latest CI are recorded in the 2026-11-12 checkpoint below.
- The previous backend fix in workflow #43004421135 also passed: 232 passed, 3 warnings, 0 failed. It introduced a guard so a Binance item rejected by the central filter cannot be fetched directly by guessing its detail ID.
- Android source changes at `9b081f9ff7553c7843260b120fefc6a8c37283aa` passed Android unit tests, Debug APK assembly, and artifact upload in workflow #43002913809. Later commits changed only backend API/tests and documentation; Android source is unchanged.
- Airdrop Hub has a Room-backed public cache: show cached rows first, refresh the four-source pipeline in the background after 8 hours, retain last-known-good rows after failures, and apply a 5-minute retry cooldown. Manual refresh remains available; physical-device behavior still needs verification.
- The public Binance detail API now applies the same central-filter visibility rule as the list API. Filtered Binance records remain stored as provenance but cannot be opened through the public detail route.
- The pending review queue excludes records already rejected by the central filter. The explicit `status=all` view still includes them for audit/provenance; they are not deleted.
- Review-queue title search (`q`) and stable `limit`/`offset` pagination are implemented. This is backend support only; the separate review APP remains deferred.
- The four approved source adapters and central filtering/identity pipeline exist in code. Real provider configuration, live-data verification, campaign publication and device rendering remain incomplete; scheduled production sync stays disabled.
- The branch includes the current Airdrop Library, browser dock, community/accounting, campaign discovery and Binance announcement changes. None of these branch changes are part of `main` until PR #135 is reviewed and explicitly merged.
- CI success does not imply physical-device acceptance, production live-sync, or backup/recovery verification.

### Current product decisions — do not regress

- Public discovery destination and page heading: **“空投库”**. Do not display “微社区广场” or the Steam-style discovery subtitle. The personal project destination remains **“库管理”**.
- The Airdrop Library's primary purpose is to discover and display actual airdrop campaigns, not to act as a token screener, source-monitoring console, or moderation dashboard.
- The only currently approved discovery sources are the four zero-cash-cost sources in Section 0.2: CoinGecko Demo API, GitHub Raw Content, DexScreener public API, and Binance public announcements. CoinMarketCal, DropsTab, Airdrops.io, paid APIs, paid scraping, paid proxies, and metered services are excluded.
- CoinGecko/GitHub/DexScreener results are project/market leads only. They must not be displayed as confirmed campaigns without explicit campaign evidence. The primary visible campaign feed currently accepts explicit Binance announcement evidence.
- The in-app Binance detail page must show the official CMS body without AI rewriting or invented terms. JavaScript stays disabled; do not weaken WebView security or guess image lazy-loading behavior.
- Current-release personal wallets, personal ledger, personal project/task records and personal P&L remain local-only. Only eligible anonymous/de-identified aggregate contribution data may reach the public community service. Registration/login and private cloud restore are not current-release features.
- Demo data must remain isolated from real data and public statistics. No mock record may enter real mode, publication APIs, or community aggregates.

### Remaining work — authoritative delivery checklist

#### A. Automatic airdrop discovery and publication — **PARTIAL; highest product priority**

- [ ] Implement and validate the four approved adapters against their current documented free endpoints and terms:
  - [ ] CoinGecko Demo API: discover the Airdrop category and cache token/project leads.
  - [ ] GitHub Raw Content: fetch only manually reviewed source records with repository identity, a commit-pinned Raw URL, license identifier/reference, explicit attribution, approved status and review date; URL-only configuration must fail closed.
  - [ ] DexScreener: implement the user-approved latest/search discovery flow with bounded queries and thresholds; keep all results as market leads. Existing contract-address market enrichment is not a substitute for this adapter.
  - [ ] Binance CMS: validate the official Crypto Airdrop and Latest Activities feeds with the approved keyword filter, stable article IDs and last-known-good behavior.
- [ ] Verify the free-use path, rate limits, reuse rights, hosting/database limits and zero-cost guardrails for each adapter before enabling it. No paid fallback, card requirement, automatic upgrade or hidden metered dependency.
- [ ] Complete campaign-level normalization, project/campaign identity linking, evidence snapshots, source attribution, deduplication, unknown/null semantics and status history.
- [ ] Complete deterministic, versioned, explainable scoring and publication gates. Official HTTPS link verification and attributable evidence are mandatory; DEX liquidity/volume or project-list presence never proves an airdrop.
- [ ] Connect the real campaign publication API to the Android Airdrop Library and Room cache. Real mode must show only approved real campaign records; demo mode remains separate.
- [ ] Validate real source data end to end, including outages, malformed/empty responses, retries, idempotency, last-known-good cache and no false publication.
- [ ] Keep scheduled production sync disabled until source rights, zero-cost hosting, database migrations, exact-head CI and real-data APP rendering are verified.

Current evidence: campaign models, migrations, scoring/persistence foundations, publication API, Android campaign client/list and Binance announcement feed/detail code exist on PR #135. The campaign UI has shown an honest empty state because there is not yet a configured approved free campaign provider publishing real campaigns. The trusted-provider refactor explicitly leaves sync fail-closed when no eligible provider is configured. Therefore this feature is **not end-to-end complete**.

#### B. Existing core app features — **CODE FOUNDATION LARGELY COMPLETE; DEVICE ACCEPTANCE PENDING**

- [ ] Install the exact-head APK on the user's Android phone and exercise every main destination: Home, 空投库, 库管理, 我的.
- [ ] Run the reported cross-screen crash regression across browser, chain/coin search, wallet management, project management, Airdrop Library and profile destinations.
- [ ] Verify wallet address/network handling and external-wallet DApp handoff with MetaMask, OKX Wallet and Trust Wallet: connect, sign, send, reject, cancel, timeout and return callback. Airdrop Hunter never holds private keys or signs for the user.
- [ ] Verify accounting end to end: manual bill entry, pending/confirmation flow, actual-vs-estimated separation, wallet/chain association, local persistence and totals.
- [ ] Verify project/task lifecycle and “库管理”: private project isolation, official catalog binding, delisted-project marker, local accounting/history retention and eligible anonymous contribution rules.
- [ ] Verify offline encrypted backup/export/import on a real device, including wrong password, corrupt file, duplicate import, version compatibility and post-restore data integrity.
- [ ] Verify current-release privacy at runtime: no personal wallet addresses, personal ledger rows, transaction hashes, account identity or private project/task details are uploaded.
- [ ] Verify cache-first behavior, stale-while-revalidate, refresh throttling, background responsiveness, last-known-good retention and source failure states on device/network interruption.

#### C. Browser dock and UI acceptance — **IMPLEMENTED IN PR #135; DEVICE VERIFICATION PENDING**

- [x] Refactor the dock so a single outer Box owns the capsule background and LazyRow; its offset and long-press drag move the complete component together.
- [x] Clip the inner translucent capsule container with RoundedCornerShape(50), including the LazyRow, so icons cannot render outside the pill boundary.
- [x] Use detectDragGesturesAfterLongPress on the outer Box so ordinary horizontal swipes remain available to LazyRow; use Animatable for animated left/right edge snapping after drag end/cancel.
- [ ] Confirm the long horizontal glass dock proportions on the actual phone.
- [ ] Confirm inner horizontal scrolling and long-press whole-dock dragging do not conflict on the actual phone.
- [ ] Confirm edge snapping, safe-area bounds, add/remove/reorder/restore actions and all six tool actions on device.
- [ ] Confirm dock position is transient and resets to the bottom after leaving/re-entering the browser; only selected tool IDs/order persist.
- [ ] Confirm Home dashboard card layout/drag behavior and developer-controlled card dimensions.
- [ ] Confirm the Airdrop Library heading is “空投库”, the obsolete Steam subtitle is absent, and demo/real datasets never mix.

#### D. Binance announcement detail — **CODE + UNIT TESTS COMPLETE; REAL HTML/DEVICE VERIFICATION PENDING**

- [ ] Inspect an actual Binance CMS detail response and confirm whether announcement images use direct `src`, `data-src`, or another loading pattern.
- [ ] Verify HTTPS images load with JavaScript disabled. Do not add speculative HTML rewriting or enable JavaScript without a real fixture and security review.
- [ ] Verify wide tables scroll horizontally, images scale to viewport, and long content remains readable on the user's phone.
- [ ] Verify explicit user-clicked HTTPS links open the in-app browser and Back returns to the announcement detail; automatic redirects and non-HTTPS navigation remain blocked.

#### E. Production deployment, operations and recovery — **PARTIAL**

- [ ] Complete production environment variables/secrets, database, object/static storage and CDN configuration using verified $0 tiers.
- [ ] Run production readiness smoke checks against the actual deployment and verify migrations at the real database head.
- [ ] Perform an isolated PostgreSQL backup and restore drill, verify restored data and record evidence.
- [ ] Verify deployment rollback and recovery procedures.
- [ ] Activate scheduled community snapshots and airdrop sync only after configuration, cost, permissions and live-data checks pass. Skipped cron runs do not count as successful publishing.
- [ ] Confirm monitoring/error visibility and safe behavior when providers, database, CDN or network fail.

#### F. Final release gate — **PENDING**

- [ ] Reconcile every still-valid requirement in Sections 1–10 against implementation and tests.
- [ ] Close all device acceptance items in B–D and production/recovery items in E.
- [ ] Run exact-head Android unit tests/build and backend tests after the final coherent change set.
- [ ] Generate and install the final release candidate APK; complete a full regression pass.
- [ ] Verify privacy, zero-cost operation, source permissions, data integrity, backup/restore and rollback.
- [ ] Keep PR #135 Draft/Open until the user explicitly authorizes merge. Do not declare the project finished before all required gates pass.

### 2026-11-12 — cache-engine force-throttle regression and wallet-data scope audit

- Exact-head CI #43144096009 for `d82ac14579cb3538fb990508448f0cf3ce8ddbbb` failed: Android compilation and Debug APK assembly succeeded, but 1 of 169 unit tests failed. The failing test was `manualRefreshThrottleBlocksManualButForceRefreshBypassesIt`.
- Root cause confirmed in `CacheRefreshCoordinator.load()`: both the pre-lock and in-lock manual-throttle guards omitted the `!force` condition. Corrected both guards in commit `f9cdb10dbd0503806ba58e99661afc0bad4ac572`; exact-head CI #43144624372 passed Android build, all unit tests and Debug APK artifact upload.
- A source comparison against the parent of the wallet-cache integration confirms the generic coordinator was added specifically in commit `bdd69b17`. The current wallet activity payload still uses session-scoped local read/write, but its metadata and refresh lifecycle were incorrectly routed through the public/shared TTL engine. Per the public-only cache boundary above, the next code correction is to remove that coordinator integration while preserving the pre-existing session-scoped wallet transaction storage and worker call compatibility. No wallet data, account behavior or pending-sync state may be modified.
- Added a coordinator-level regression test proving a fresh cached payload suppresses an ordinary refresh without calling the provider. Removed the now-unused wallet-asset generic cache-key helper and its isolated policy test after the private-wallet scope correction. Commit `cfa162e85126118e9120a8586f6cf66146f13bd4`; exact-head CI #43145433344 passed Android compilation, all unit tests, Debug APK assembly and artifact upload. Backend tests were skipped because this commit changed Android code only.
- Follow-up integration audit: the shared coordinator is currently used by Airdrop Hub (`FeatureRepositories.kt`), public campaign directory (`AirdropCampaignRepository.kt`), official community catalog (`CommunityCatalogRepository.kt`), public community snapshots (`CommunitySnapshotRepository.kt`), market search/hot coins (`MarketSearchRepository.kt`) and OKX market detail (`OkxMarketRepository.kt`). These are public/shared datasets. Wallet activity and wallet balances are now excluded. `CoinGeckoPriceRepository` has no current call site and remains intentionally unintegrated.
- Cache-engine source review confirms: TTL is checked only when a cached payload exists; same source/key work is serialized through bounded mutex stripes; retryable errors are limited to IO and HTTP 408/425/429/5xx with three total attempts; last-known-good data is retained on failure; cancellation is rethrown and metadata reset to idle. The new fresh-cache regression test is included in the passing exact-head CI above.
- Superseded by the latest 2026-11-12 continuation checkpoint below. PR #135 remains Draft/Open/unmerged.

### Latest continuation checkpoint — 2026-11-12

- PR #135 remains **Draft / Open / unmerged**. Latest code head: `baba1209e780c9bb4c9ce1b54a27ba41bcd500a1`.
- Exact-head Android workflow **#43152173285** passed on `a7dc2647596f6a2f2c41cec900dfb0a49578560f`: Android build and unit tests passed; backend tests were skipped by change filtering.
- Code commit `66b5e0b89b59cb7922824c7d60057361edb039b8` adds wallet-balance scheduled refresh and campaign-directory background warming/cache-first display; exact-head Android workflow #43151808498 passed.
- Code commit `a7dc2647596f6a2f2c41cec900dfb0a49578560f` removes remaining manual-refresh cooldown bypasses and surfaces throttled results; exact-head Android workflow #43152173285 passed.
- Requirements-first audit found that wallet workers could treat a stale last-known-good fallback as a successful refresh and advance the 8-hour cadence after a provider failure. The defect and acceptance criteria were added to this document before code changes.
- Code commit `baba1209e780c9bb4c9ce1b54a27ba41bcd500a1` preserves `stale` and `errorMessage` in wallet activity/balance snapshots and prevents either worker from marking cadence success for stale/error/throttled results. Added wallet-activity stale-fallback regression coverage and pure cadence-success policy tests.
- Exact-head Android workflow **#43152987166** completed successfully on `baba1209e780c9bb4c9ce1b54a27ba41bcd500a1`: Android build, unit tests, Debug APK assembly and artifact upload passed. Backend tests were skipped because this batch changes Android files only.
- Live provider verification remains pending. No production database writes, campaign publications, production deployment or physical-device acceptance are claimed. Scheduled discovery sync remains disabled.
- Updated estimate after the passing exact-head batch: **code/CI implementation ~73%**; **overall release readiness ~45%**. These are rounded project-level estimates, not test coverage.
- Next actions after CI: if green, update the completion ledger and continue the all-caller/background-scope audit; if red, inspect the failing job and repair before another code batch.

### Delivery batch summary

| Area | Current status |
|---|---|
| Core product batches 01–13 | Code-level foundation recorded as complete; cross-screen/device regression still required |
| Batch 14 — production operations/recovery | Partial; live configuration, restore drill and rollback evidence remain |
| Batch 15 — navigation, account-scope UI and backup hardening | Code/CI complete; physical-device acceptance remains |
| Airdrop discovery pipeline | Partial; adapters/provider configuration and real publication flow remain |
| Binance announcement detail | Code/CI and wrapper unit tests complete; real HTML and device checks remain |
| Final release | Pending |

**Progress reporting rule:** do not use one unqualified percentage as a release-completion claim. Report code/CI progress separately from real-data, physical-device, production and final-release acceptance. A completed PR or successful CI is not a finished product.

---


### 2026-11-12 — requirements-first workflow and unified cache engine checkpoint

**Requirements-first rule (user reaffirmed 2026-11-12):**
- This file is the source of truth. Every newly requested feature or changed behavior must be recorded here first, with scope, priority, dependencies, acceptance criteria and status, before implementation begins.
- After the requirements entry is updated, compare the actual branch code against it, implement only the next approved slice, run the relevant tests, and update this ledger with the exact commit and CI result.
- Never silently turn an implementation assumption into a product requirement. If a request conflicts with an existing decision, record the conflict and follow the user's latest explicit correction.
- Current release registration is disabled. Do not add registration/login, account switching, guest-to-login migration, or private cloud restore flows. Do not reopen account architecture as part of cache work.
- Do not alter, clear, re-key, or migrate offline-created personal records or pending-sync queues as a side effect of cache changes. Personal ledger and personal project/task records are durable user data, not ordinary expiring cache entries.

**Unified cache engine — scope and requirements:**
- Use one shared cache refresh coordinator for approved cache-backed data sources; keep per-source TTL, cache key, validation, and persistence semantics explicit.
- Required behavior: cache-first/stale-while-revalidate where the existing screen contract requires it; per-source TTL; single-flight refresh for the same source/key; force refresh for an explicit manual refresh; bounded retry/error cooldown; preserve last-known-good payload on failure; expose stale/error/refresh metadata; propagate coroutine cancellation rather than treating it as a provider error.
- Cache-engine acceptance tests must explicitly prove: two overlapping same-source/key callers perform one fetch; a fresh cache suppresses an ordinary refresh; a manual-refresh throttle blocks only non-forced manual calls; force refresh bypasses TTL, cooldown and manual throttle; retryable failures retry only within the configured attempt bound; non-retryable failures do not retry; Retry-After is honored within a documented cap; failure cooldown suppresses background retries but does not block an explicit force refresh.
- Cache metadata is not the payload store. Existing Room/cache payload persistence and source-specific validators must remain authoritative.
- The shared coordinator governs refresh scheduling/concurrency/status for approved public data and wallet balance/transaction refreshes. Wallet payloads remain private and account/wallet/chain-scoped in their existing local Room stores; coordinator metadata keys must preserve that isolation. Do not place wallet payloads in a shared public cache. Personal ledger, private project/task records, offline additions and pending-sync queues remain durable local records, never ordinary TTL cache entries. Preserve user-data semantics.
- Do not add an unused repository to the active path solely to claim cache coverage. CoinGeckoPriceRepository remains out of scope until a real call site exists.

**Implementation recorded at branch head `a647f7b3af517fe90b004ad774e8053be2513e88`:**
- Shared `CacheRefreshCoordinator` supports forced refresh; Airdrop Hub and Community Snapshot repositories use the shared coordinator while retaining their source-specific persistence/validation behavior.
- Scope-audit finding (2026-11-12): `WalletActivityRepository.refresh()` had routed per-wallet transaction snapshots through the generic `CacheRefreshCoordinator` (`source = wallet-activity`, 8-hour TTL). Although payload reads/writes remained session-scoped, wallet transaction history is private wallet data and outside the approved public/shared reference-data scope. Corrected in commit `0d4f5b6ab049db3007ff843d17015f73c35810c6`: removed the generic coordinator dependency and restored direct refresh with the existing `readForSession`/`writeForSession` payload persistence. The `force` parameter remains accepted for worker call compatibility; the existing separate 8-hour WorkManager cadence is unchanged. Exact-head CI #43144871849 passed Android build, unit tests and Debug APK artifact upload. A second scope-audit finding: `WalletAssetRepository` also routed per-wallet balances through the generic coordinator and read its global refresh metadata. Wallet balances are private user-specific wallet data even though the underlying chain records are publicly observable. Corrected in commit `719b739eb7a36f1e947f8d31db91564eced0650e`: removed the generic coordinator, kept existing account-scoped Room payload persistence, and retained cached-first display followed by the screen's existing direct fetch path. Exact-head CI #43145084727 passed Android build, unit tests and Debug APK artifact upload. No private wallet payloads or keys were cleared, re-keyed, migrated or deleted. Neither correction changes account switching, guest-to-login migration or pending-sync queues.
- Airdrop campaign refresh validates records and verifies manual-refresh cache write/readback before accepting the replacement payload; invalid or incomplete refreshes must not overwrite last-known-good data.
- Community snapshot refresh preserves HTTPS CDN host validation, ETag/Last-Modified behavior and snapshot-version checks; cancellation is propagated.
- Added coordinator lifecycle tests for same-key reuse, last-known-good retention after failure, cancellation metadata recovery/propagation, and source+key metadata isolation.
- Exact-head Actions run #43142917658 for `a647f7b3af517fe90b004ad774e8053be2513e88` passed: Android build, unit tests and Debug APK artifact upload succeeded. Backend tests were skipped because this was an Android-only change. This is automated validation, not device acceptance.
- Previous exact-head Android validations: #43142399397 passed for `e39c2c22a4174d1f04b765dff44a3811257cd026`; #43140663969 passed for `d5732d1d299d8ca0ce58829ce8695a2994893abc`.

**Current cache-engine status:**
- Shared engine foundation and selected integrations: implemented in code and CI.
- Full concurrency semantics, force/manual-refresh throttling, retry/backoff edge cases and complete source-by-source audit: partial; additional tests/review required.
- Physical-device checks for cache-first rendering, background responsiveness, refresh interaction, offline/network interruption and last-known-good behavior: pending.
- Public data provider live verification remains a separate gate; CI does not prove Binance/CoinGecko/GitHub/DexScreener live availability or campaign publication.

**Progress reporting — 2026-11-12 (estimate, not a release claim):**
- Code/CI implementation coverage: approximately **68%** across the currently approved scope; this reflects existing core modules plus partial discovery, UI, cache and detail-page implementation.
- End-to-end release readiness: approximately **41%** using the six delivery areas A–F in the master checklist, with the weakest areas (live discovery, device acceptance, production/recovery and final release gate) weighted explicitly rather than counting source files or commits.
- Live-data, physical-device, production/recovery acceptance: still substantially behind code/CI. Do not collapse these into one “finished” percentage. Recalculate these figures whenever a delivery area changes, and state the evidence and outstanding gates.
- PR #135 must remain Draft/Open/unmerged unless the user explicitly authorizes otherwise.

## 0.2 Latest user-confirmed data-discovery strategy — four-source, zero-budget standard (2026-11-11)

The user approved four free public data sources for backend discovery: CoinGecko Demo API, manually approved GitHub Raw Content, DexScreener public API, and Binance public announcements. SolvoHQ was explicitly retired after its snapshot was found stale. CoinMarketCal, DropsTab, Airdrops.io, paid APIs, paid trials, paid proxies, and paid scraping services are excluded.

### Product experience — simple airdrop discovery (latest correction)
- The primary Airdrop Discovery page exists to find and display airdrop activities. It is not a source-monitoring dashboard, token screener, project directory, or moderation console.
- The user-facing list shows only explicit airdrop campaign/announcement evidence. Binance Crypto Airdrop catalog entries and Latest Activities headlines explicitly matching airdrop-related terms (Airdrop, Launchpool, HODLer, Megadrop) may appear as official-announcement cards linking to the original Binance announcement.
- CoinGecko Airdrop category membership, GitHub open-source list entries, and DexScreener token/pool observations are discovery leads only. They must stay out of the primary airdrop activity list unless a separate explicit campaign announcement/evidence is obtained. Trading volume, liquidity, category membership, or appearing in a repository list does not prove an airdrop exists.
- Keep the main page focused: activity title, concise official-source attribution, and a clear action to open the original announcement. Do not show source health, trust codes, publication-state codes, internal scoring, market-pool metrics, or technical error details to ordinary users. Do not label a project lead as an active airdrop.
- Do not build additional review queues, moderation dashboards, multi-layer scoring, or source-status UI for this discovery page. Retain only the backend provenance, deduplication, caching, URL validation, and per-source failure isolation needed for reliable operation.
- Never invent campaign dates, reward amounts, eligibility, KYC, chain, or claim links. If the official announcement does not state a field, leave it unknown rather than guessing.
- The main user flow is: open Airdrop Discovery → browse official airdrop announcements → open an in-app detail page showing the complete original Binance announcement content → optionally open the original Binance page to participate.
- The in-app detail page must display Binance CMS article body as supplied, preserving its headings, paragraphs, lists, tables and embedded images where possible. Do not replace the body with an AI summary or selectively omit terms. Show title and publication timestamp only when supplied by Binance. Keep the original Binance URL available as a separate action.
- The detail API fetches the article by the exact Binance article code. If Binance does not return a body or the request fails, show a retry/original-link fallback; never fabricate a replacement body or campaign terms.

### 1. CoinGecko Demo API — Airdrop category
- Use the CoinGecko Demo API key only from the backend environment variable `COINGECKO_DEMO_API_KEY`; never hardcode, commit, or package credentials in the Android APK.
- Query `GET /coins/categories/list` to identify the Airdrop category, then `GET /coins/markets?vs_currency=usd&category={category_id}` for at most 20 market-ranked leads. Resolve each candidate's actual official homepage through `GET /coins/{id}`; the markets response itself does not provide the website.
- **License gate:** CoinGecko's free Demo plan is for testing/exploration and does not itself grant commercial public-display or redistribution rights. Attribution alone is not sufficient permission. Keep Demo-derived records internal for research/filtering only; do not publish CoinGecko market data or reproduce it in the public campaign feed unless the operator first obtains a plan or written license that explicitly permits the intended display/use. Never resell or redistribute raw API data.
- Normalize token name, CoinGecko URL, category ID, source ID, and fetch timestamp. These rows are internal discovery leads, not active claim campaigns, and are not shown in the primary activity list.
- If the Airdrop category is absent or the API key/quota is unavailable, record the source failure and continue the other adapters.

### 2. GitHub Raw Content — open-source lists
- Support only `AIRDROP_GITHUB_RAW_SOURCE_APPROVALS`, a JSON array of complete manually reviewed source records. Require a unique source ID, matching `owner/repo` identity, HTTPS `raw.githubusercontent.com` URL pinned to a full 40-character commit SHA, license identifier, HTTPS license reference, explicit attribution text, `review_status: approved`, and an ISO review date. Reject the legacy URL-only `AIRDROP_GITHUB_RAW_SOURCES` setting; do not scrape GitHub search or private repositories.
- Parse JSON arrays/objects and Markdown project links/bullets. Extract only an explicit project name and HTTPS website URL; do not infer an official website from a repository name or arbitrary text.
- Keep repository/file URL as provenance, use stable source keys and deduplicate across configured files. Bound URL count, response size, request timeout, and polling frequency.
- The operator must inspect the repository license, README attribution and redistribution terms before setting the record to approved. The importer validates required metadata but does not make a legal determination or automatically trust/fetch the license URL. Reject incomplete, unapproved, duplicate, unpinned or unsafe records as a whole; cap the configuration at 20 sources. Pinning a commit makes content changes require a new explicit review. Preserve the reviewed license and attribution details with each imported lead.
- Imported entries are internal project leads and are not shown as confirmed airdrop activities without separate campaign evidence. A malformed or unavailable file must not block other sources; retain last-known-good rows.

### 3. DexScreener — recent active pool observations
- Use the documented public `GET /latest/dex/search?q={query}` endpoint. Search terms are operator-configurable via `DEXSCREENER_SEARCH_QUERIES`; use a small bounded query list and cache responses.
- The centralized filter excludes pools older than 7 days, requires liquidity strictly greater than USD 10,000, sorts eligible rows by liquidity then 24h volume then combined 24h buys/sells, and keeps at most 10. Volume and trades are ranking signals only; they are not separate eligibility thresholds or proof of an airdrop.
- Deduplicate by chain ID + token address. These rows are market/pool leads only and never appear in the primary airdrop activity list solely because they are active or liquid. Do not claim an airdrop, contract safety, or token legitimacy from trading activity.

### 4. Binance public announcements — official activity feed
- Use the existing public CMS article-list endpoint for catalog IDs 128 (Crypto Airdrop) and 93 (Latest Activities). Catalog 93 must be keyword-filtered for Airdrop, Launchpool, HODLer, and Megadrop.
- Parse article title and announcement code; construct the canonical Binance announcement URL from the code. Keep original title, source, and stable announcement code as backend provenance.
- These explicit official announcement entries are the primary visible discovery feed. The original Binance article is the source of truth for all campaign terms. Do not infer rewards, deadlines, eligibility, claim links, or user qualification from a headline.
- Treat CMS failures/format changes as a per-source failure and retain last-known-good records.

### Shared backend rules and exclusions
- The five adapters are independent: one failure must not stop the others. Persist source provenance and status internally; retain last-known-good records after empty/error responses.
- Keep stable source keys, HTTPS URL validation, bounded fields, deduplication, conservative polling, and caching.
- Do not send personal wallet addresses, private ledger rows, transaction hashes, or identity data to these providers.
- The primary public Airdrop Discovery API must return only explicit Binance airdrop announcement entries. CoinGecko, GitHub, and DexScreener observations remain excluded from that feed.


## 0.1 Latest user-confirmed corrections — 2026-11-11 (authoritative overrides)

The following decisions are newer than the older baseline below. Where older sections conflict, this section and the explicit replacements noted in those sections win.

### Current-release personal data boundary — local-only
- All detailed personal data for every user, including guests and any existing authenticated session, is stored on the phone in the local Room database for this release.
- Do not upload personal ledgers, personal wallet lists, personal project records, task details, transaction details, or personal P&L to a private cloud service. Do not build or expose private-ledger cloud sync, account-based personal-data persistence, or cross-device cloud restore in this phase.
- The server receives only the minimum anonymous/de-identified aggregate contribution data required for public community statistics. Never include wallet addresses, transaction hashes, individual ledger rows, account identity, or personal details in public payloads.
- Project ratings in this release are also local-only: eligibility is checked against the user's locally stored confirmed participation record, and the 1–5 star choice is saved in Room. Do not sync a private ledger or submit a rating through the authenticated/global-rating endpoint. Public rating aggregates remain read-only until a separate anonymous, privacy-preserving eligibility-proof flow is designed and validated; do not weaken real-participant or anti-abuse checks to work around this boundary.
- Do not expose registration or login in the current release. Existing authentication/account/private-sync code may remain in the repository for a later phase, but it is not an active current-release personal-data path and must not be expanded or presented as available. The app starts in local mode; a legacy token may remain only as a local Room namespace key and must never be sent to the server. The approved device-migration path is the encrypted local backup/import file in Section 1.

### Community public dashboard — confirmed presentation and aggregation
- Gas: show one community-wide total in USD plus a per-chain Gas breakdown. Also show a participant Gas-spend range distribution (bucket counts/percentages), without exposing individual records.
- Rewards/income: show one community-wide total in USD and a participant reward-range distribution. Do not split reward totals by chain.
- Community contributor counts: show cumulative distinct contributors and distinct contributors active in the last 30 days. Count a contributor once per period; do not expose identity or wallet address.
- Do not show net profit, net Gas-minus-reward, reward-versus-Gas comparison, correlation between individual Gas and reward ranges, personal profit rankings/leaderboards, individual financial details, project-participation counts, project-participation frequency, or independent-wallet counts.
- Public income is an approximate community overview and may include both theoretical/estimated rewards and user-confirmed actual rewards. It is not a precise financial statement and does not require every event to have a manually confirmed final settlement before an estimate can appear. Personal records must still keep theoretical and actual values in separate fields. Mock/demo records never enter real public aggregates.
- Distribution buckets are aggregate-only; choose sensible ranges from the observed data and do not create a personal ranking.

### Gas / reward comparison bar — single continuous two-color bar
- The visual is one horizontal proportional bar per metric row: red represents Gas cost and green represents reward/income. It is a single continuous bar with adjacent red and green segments, not two separate bars, not two independent numeric KPI tiles, and not a replacement by percentage numbers.
- Keep the accompanying textual amounts/currency breakdown as supporting information, but the comparison visualization itself must remain one bar. Use exact proportional widths; a zero-valued side has zero width; missing/zero-denominator data shows an honest empty state, never a fabricated 50:50 bar or minimum-width segment.
- The public project list and Micro Community Square overview do not show a Gas/reward comparison bar. Keep those surfaces concise and show only their approved aggregate metrics. The Gas/reward comparison belongs only in the individual community project detail page, inside one unified card container. Replace the four separately stacked period cards with a segmented tab selector containing exactly three options: “总览”, “近3天”, and “近1小时”; default to “总览”. Do not show a “近7天” tab or a separate “总体” card. Animate tab content changes with AnimatedContent or Crossfade. The existing backend 7d snapshot may remain for compatibility, but it is not a visible detail-page option.
- The “Today” window is cancelled and must not return. Do not add a manual refresh button to these community snapshot rows; use the existing Room-first cache and silent CDN revalidation schedule.
- List cards remain concise. Do not reintroduce the cancelled standalone 24-hour community heat metric, project participation counts, or independent-wallet counts merely because an older visual reference contains them.


### Community project detail — unified animated Gas/reward card (latest 2026-11-11 override)
- Delete the four individually stacked period cards. Render one single card container with a full-width segmented tab row at the top and exactly three options: “总览”, “近3天”, “近1小时”. “总览” is selected by default. There is no visible “近7天” option.
- Selected tab: full-pill solid brand purple #7A58FF with white text. Unselected tabs: transparent background with dark/neutral text. All tabs use fully rounded pill ends.
- Below the tabs, use a two-column metric layout. Left: red/orange flame icon and “Gas 支出”, bold large compact USD amount (e.g. $12.8K), then tiny native-token hint (e.g. ≈ 3.2 ETH). Right: green gift icon and “收益”, bold large compact USD amount (e.g. $18.5K), then tiny token hint (e.g. ≈ 4.6 ETH).
- Under both columns, render exactly one continuous proportional red/green comparison bar, 16.dp high with fully rounded ends. Red is Gas share; green is reward share. Put white percentage text inside each segment when that segment is at least 15%; hide the label for smaller segments rather than forcing a minimum segment width. Zero and missing data must remain honest and must not create a fabricated 50:50 bar.
- Collapse the data-updated timestamp to tiny muted text at the lower-right edge/bottom of this same card.
- Animate switching between tabs with Compose AnimatedContent or Crossfade.
- Support both lightColorScheme and darkColorScheme. Use the same semantic roles in both schemes: Gas red, reward green, purple selected tab, readable primary text, muted secondary text, and suitable card/track surfaces. Selected-tab brand purple remains #7A58FF in light mode; use an accessible lighter purple variant in dark mode if needed.
- This unified card is in the individual project detail page only. Do not add it to project list cards, personal ledger, or Micro Community Square overview.

### Airdrop Library UI and isolated test mode
- The public discovery page is named “空投库”. Do not use “微社区广场” as its page heading and do not show the Steam-style discovery subtitle. Keep the “提交新空投项目” entry, search by project name/chain/tag, the “测试演示 / 全网真实” switch, ranking chips (吸金榜 / 避坑榜 / 新星榜), project filters, cached-snapshot warning, and honest empty state.
- Test mode must include clearly labeled fictional airdrop projects as well as fictional metrics so the user can test list rendering, search, chain/tag filters, rankings, project detail navigation, and the Gas/reward bar. All demo projects and metrics are local UI fixtures only: no API submission, no local personal-ledger mutation, no public contribution, no real community rating, and no public statistics contamination. The “全网真实” mode must show only real catalog/snapshot data.
- Demo/real mode must be clearly labeled and must never silently mix the two datasets.

### Project-detail visual reference
- The Airdrop Library project list/detail layout follows the user's supplied two-screen reference, but all previously cancelled metrics remain excluded. The detail page includes project identity/description, task list, safety guidance, community rating, and the Gas/reward comparison module described above. The comparison module belongs in the detail screen, separate from the star-rating section; do not confuse the red/green Gas/reward bar with positive/negative reviews.
- The single-bar requirement is authoritative even if a generated mockup or earlier assistant draft shows two large amount tiles or percentage numerals in place of the bar.


### Reference-image-matched browser dock — detailed UI and behavior (2026-11-10)

The user's latest reference image is the visual source of truth. This is a **mobile app-launcher dock**, not a row of tiny floating action buttons:
- Outer frame: one long horizontal glass-effect capsule with a semi-transparent white fill (about 35% opacity), a 1 dp white highlight border (about 60% opacity), a 12 dp black-tinted ambient/spot shadow, and a 50%-rounded capsule shape. Keep the target height about 100 dp and width about 94% of the available phone width (capped at 420 dp); leave about 24 dp below it.
- Inner tools: each action keeps an approximately 68 dp touch target and contains a 48 dp rounded-square app icon with a 16 dp corner radius, a saturated solid-color background unique to the action, and a centered 24 dp solid white Material icon. Do not use thin line glyphs, emoji, pale icon backgrounds, or circular-only icon buttons. Keep the action label below the icon.
- The tray scrolls horizontally internally. A final plus tile opens an add/manage dialog. Available actions include mock bill, copy wallet address, pending bills, refresh page, copy page URL and open current page externally. The selected tool set is saved independently of the dock's position.
- Long-press and drag the tray itself to reposition it; release snaps its horizontal position to the nearest screen side and keeps it within the safe vertical area. Normal horizontal swipes should scroll the inner tool row.
- The tray defaults to the bottom of the browser viewport. Its coordinates are transient Compose state only and must not be written to preferences/database; leaving and re-entering the browser resets it to the bottom.
- Acceptance still requires Android build plus physical-device checks: visual proportions on the user's phone, internal scroll vs long-press drag distinction, edge snap, plus/add/remove, all six tool actions, and bottom reset after exit/re-entry.

### Implementation progress — browser dock source audit and implementation (feature branch; PR #135)

- The earlier progress notes above this section overstated implementation: GitHub source audit found that the toolbar was still the original three-action accounting capsule, and PR #135 did not yet include the toolbar source. Treat those earlier claims as superseded by this audit.
- Implemented the horizontal browser dock in `BrowserAccountingToolbar.kt`: semi-transparent white glass capsule with white highlight border and 12 dp shadow, 100 dp height, 94% width (maximum 420 dp), 48 dp saturated-color rounded-square app icons with 16 dp corners and solid white pictograms, 68 dp touch targets, horizontal internal scrolling, and a white/light plus tile with a dark-gray icon.
- The plus tile opens tool management. Users can add/remove the six supported tools, reorder them with up/down controls, and restore the default set. Only selected tool IDs/order are saved in app preferences.
- The six actions are connected: create mock bill (retains the existing accounting confirmation workflow), copy current wallet address, open pending bills, refresh the current WebView, copy current page URL, and open the current page externally.
- The entire dock is hosted as an overlay within the browser viewport. Long-press drag moves it vertically; release constrains it to the safe browser viewport and snaps horizontally to the nearest side. Ordinary horizontal swipes remain available to scroll the inner tool row.
- Drag coordinates exist only in Compose `remember` state and are not saved. A fresh browser entry recreates the composable and returns the dock to its bottom default. Selected tools persist independently.
- Source cleanup audit found no obsolete vertical rail, orientation selector, persisted dock-position keys, duplicate toolbar capsule, search entry, or unused imports in the current toolbar source. Existing bill/accounting state and dialogs remain referenced and are intentionally retained.
- Edge-snap correction: every completed/cancelled long-press drag now snaps horizontally to the nearest side, including short drags; the prior 15% movement threshold that could leave the dock floating off-center has been removed.
- The old accounting dialogs and data operations remain in the same composable; this change replaces only the visible action capsule and adds the browser utility actions.
- **Not yet validated:** exact-head Android build/unit tests and physical-device checks for internal scroll vs long-press drag, edge snap, vertical bounds, add/remove/reorder, all six actions, and bottom reset after exit/re-entry. Do not claim acceptance until those checks pass.
- Scope deliberately excludes the website loading failure and browser-page WalletConnect no-response issue, which the user asked to defer. Do not weaken HTTPS/TLS/origin checks or spend this batch reopening those investigations.

**Current implementation checkpoint:** browser dock code commit `c51395144f66216f914c2411560017b7998f1ca2` includes the saturated app-icon/glass-capsule styling and unconditional edge snap. The prior visual-only code commit passed Android debug build and unit tests in Actions run `42773766449` (run #783). The exact edge-snap commit's Actions run `42779422216` (run #786) was cancelled when the following documentation-only commit updated the same PR; GitHub rejected retrying that cancelled run, so the edge-snap correction is not yet covered by an exact-commit build. Physical-device acceptance remains pending. PR #135 remains draft/unmerged.

---

## Implementation progress after the user's 11:40–11:45 device test (feature branch; not yet merged)

- The new test corrections are recorded above in the authoritative requirements document before implementation.
- Home search correction: remove only the duplicate “全局搜索” quick card from 快捷入口. Keep exactly one global coin/DApp search field below the quick-entry cards; tapping it opens the existing GlobalSearchScreen. Preserve its coin/DApp search, local/remote results, search history, and result navigation. Do not remove the search field or its underlying destination.
- Source audit confirmed the public project-discovery screen still rendered “微社区广场” and the Steam analogy. The visible heading is now “空投库” and the analogy sentence is removed. Project discovery, search, tags, rankings, and submission remain intact.
- The 我的 page previously exposed only toast-only “消息通知” and “安全设置” rows. It now has separate “设置”, “安全中心”, and “消息通知” destinations with truthful initial information/empty states; wallet management remains available. These are initial destination shells, not a claim that all settings, security-permission controls, or push notifications are implemented.
- The external-wallet top-bar action now gives immediate launch feedback and catches/logs launch exceptions; the DApp-origin approval path also returns an error to the requesting page if AppKit launch fails. This is a diagnostic/feedback improvement, **not proof that the user's no-response issue is fixed**. Device verification is still required.
- The configurable horizontal dock is now implemented in the feature branch with the six agreed actions, internal scrolling, tool add/remove/reorder/reset, and transient drag/edge-snap positioning. Address QR/explorer utilities, address picker, and broader browser page utilities remain outside this six-action dock pass.
- The Home desktop-style card grid was implemented in the feature branch: add/remove/reorder/persist/restore. Card dimensions are developer-defined and fixed per card type; end users cannot resize cards.
- Website loading and browser-page WalletConnect remain deferred at the user's request. Preserve HTTPS/TLS/origin protections; do not investigate or modify these flows until the user explicitly resumes them.
- The browser dock and host integration are now in this feature branch. Exact-head CI and physical-device acceptance are tracked separately; do not mark either passed without evidence.

**Current branch:** `feature/mobile-browser-home-dock-batch`; the browser dock implementation is now in the branch after the earlier `9ffd05c2ee7c64e2c3ff3260e62fcdc504ca3321` checkpoint. Requirements/documentation and Android edits are not yet merged to main. Keep release completion pending.

---

### Home dashboard drag feedback completion (2026-11-10)

- During edit mode, the long-pressed card now follows the user's finger visually with elevated shadow and foreground z-order. The existing nearest-card target calculation still commits the new order on release.
- Card dimensions remain developer-controlled and fixed by card type; no user resize control or size preference is present.
- Exact-head Android CI and physical-device gesture validation are pending. Confirm long-press activation, finger-following translation, reorder target, tap behavior outside edit mode, and persistence on a real device.

### User correction — Home card dimensions are developer-controlled (2026-11-10)

- End users must not see or use any card resize control. Remove small/medium/large cycling from the UI and from persisted card state.
- Card dimensions and spans are chosen and adjusted by the developer in code, using fixed per-card design rules. End users may add/remove supported cards, reorder them, and restore defaults only.

### Home dashboard implementation — configurable local card layout (2026-11-10)

- Added a responsive quick-entry card layout while retaining the BTC/ETH market dashboard and its existing cache/freshness/error rendering unchanged.
- The three default cards remain 空投导航、钱包管理、TRON 能量租赁. Users can add the existing 空投库 and 库管理 destinations, remove cards, and long-press/drag cards to reorder them. Card sizes and spans are fixed by the app's design, not user-configurable. A restore-default-layout action is available in edit mode.
- Card order is stored in app-private SharedPreferences and remains local to this installation. Card dimensions are fixed in code by card type. Empty layouts are preserved, so removing every card is not silently undone on relaunch. No account, cloud, or public contribution data is involved.
- The single global search field remains below the quick-entry cards and continues to call the existing global-search destination. No duplicate search card is offered.
- Implementation submitted; exact-head Android build/unit tests and physical-device layout/drag acceptance are pending. Verify portrait/landscape, narrow widths, drag target selection, add/remove/reorder persistence, and restore defaults before marking accepted.

### Home global-search entry correction (2026-11-10)

- The user clarified that “remove duplicate global search from quick entries” means remove only the duplicate quick-entry card, **not** remove the Home search field.
- Home must show exactly one global search field below 快捷入口. It opens the existing GlobalSearchScreen and preserves coin/DApp search, history, local/remote lookup, and result navigation.
- No separate global-search card belongs inside the quick-entry grid. The dedicated search screen and all search functionality remain required.

### Floating browser toolbar — latest explicit layout correction (2026-11-10)

This correction supersedes the earlier vertical-dock inspiration where it conflicts:
- The floating toolbar **must be horizontal**, not vertical: a long, single-row rounded bar with icons arranged left-to-right.
- Make it visibly longer and more balanced than the current tiny three-button capsule. Give it a substantial phone-friendly width, comfortable icon targets and spacing, and a polished translucent rounded-rectangle surface. When the number of tools exceeds available width, scroll the icons horizontally inside the bar.
- The **whole bar** can be dragged to different screen positions independently of the internal icon scrolling. On release it snaps neatly to the nearest suitable screen edge/side, avoiding system bars and important browser controls.
- The toolbar's default position on every fresh entry to Airdrop Navigation is at the **bottom of the screen**.
- **Never persist or remember the dragged position.** Leaving/exiting Airdrop Navigation discards that position; the next time the user enters a website, the toolbar starts at the bottom again.
- Tool selection/configuration (if implemented) is separate from position: it may retain the user's selected/reordered tools, but must not persist the last dragged coordinates.
- Acceptance: drag from bottom to left/right/top-area and release; verify edge snapping; horizontally scroll icons without moving the bar; exit and reopen the page and verify the bar returns to bottom.

## Latest user-confirmed mobile test corrections (2026-11-10; supersedes conflicting UI assumptions)

These requirements were added from the user's physical-device test screenshots and observations on 2026-11-10. They are additive: preserve every unrelated existing feature, data rule, and security boundary.

### A. Browser loading and in-page WalletConnect — explicitly deferred by the user

- The website-loading failure and the browser-page “连接钱包” action that appears unresponsive are **deferred**. Do not make them the next task, reopen their investigation, or treat them as a release-blocking priority unless the user explicitly asks to resume them.
- Preserve the existing HTTPS/TLS validation, allowed-origin rules, DApp bridge allowlist, and external-wallet signing security. Never bypass certificate errors, loosen origin checks, fake a successful connection, or move signing/private keys into the app.
- Existing wallet-management address connection/selection and browser-page DApp WalletConnect are separate flows. Do not claim one proves the other works.

### B. Airdrop Navigation horizontal floating dock — current approved scope

- The dock is one horizontal glass-effect capsule with a 35%-opacity white fill, 60%-opacity white highlight border, 12 dp black-tinted shadow, 50%-rounded capsule shape, approximately 100 dp high, 94% of available width (maximum 420 dp), and about 24 dp bottom spacing. Each approximately 68 dp tool target contains a 48 dp, 16 dp-rounded saturated solid-color icon tile with a centered 24 dp solid white Material icon; labels sit below.
- Tool tiles scroll horizontally inside the tray. The add tile uses a white/light translucent background and a dark-gray plus icon, and opens tool management. Users can add/remove the six currently approved actions, reorder them, and restore defaults.
- The six approved actions are: mock bill, copy current wallet address, pending bills, refresh current page, copy current page URL, and open current page externally.
- The whole tray moves only after a long press and drag; normal horizontal swipes scroll its inner row. On release, it snaps horizontally to the nearest side and stays within the browser viewport's safe vertical bounds.
- The dock starts at the bottom on every fresh browser entry. Only selected tool IDs/order may persist; drag coordinates and position/orientation must never be persisted.
- Do not implement the superseded vertical rail, user-selectable horizontal/vertical presentation, persisted dock position/orientation, or the earlier expanded list of QR/explorer/bookmark/tab/cookie tools without new user approval.
- Keep the existing mock-bill, pending-bill, and manual accounting-confirmation workflows. Replacing the old three-button visual capsule does not authorize deleting those accounting operations.
- Acceptance requires exact-head Android build/tests and physical-device checks for proportions, internal scrolling vs drag, edge snapping, safe bounds, tool management, all six actions, and bottom reset after leaving/re-entering.

### C. Home becomes a phone-desktop card dashboard
- Keep exactly one global coin/DApp search field **below the quick-entry cards**, opening the existing GlobalSearchScreen. Remove only any duplicate global-search shortcut card from the quick-entry grid. Preserve search history, local/remote search, result navigation, and the dedicated search destination; do not delete the search field or search capability.
- Keep the three default quick functions readily accessible: **空投导航、钱包管理、TRON 能量租赁**.
- Rebuild the Home quick-entry area as a responsive phone-desktop card grid: users can add/remove supported cards and reorder by drag. **Card size and grid span are developer-defined fixed design values; users cannot resize cards.** Layout adapts to screen width/orientation and does not overlap or clip. Persist card selection/order locally and provide a restore-default-layout action.
- Retain the existing BTC/ETH market dashboard and its cache/freshness/error behavior unless the user later changes it. The card dashboard must coexist with it without duplicate search UI. The three default quick-function cards must remain readily accessible; do not remove the underlying wallet management or TRON energy-rental capabilities.

### D. Public discovery navigation and Micro Community Square heading — latest screenshot wins
- The bottom navigation destination and the page heading are both named **“空投库”**. Do not display the heading “微社区广场” or any Steam-style discovery subtitle. This is the current naming rule and overrides all older screenshot-matching wording that used “微社区广场”.
- Keep project discovery, submission, search, ranking chips, tags/favorites, cached-snapshot warning, and honest empty state.

### E. “我的” page entry points
- Add clearly visible navigation entries for **“设置”**, **“安全中心”**, and **“消息通知”** to the 我的 page.
- Each entry must open a real destination, not be a dead button. If a destination's full feature scope is not yet implemented, provide a coherent placeholder/settings shell with its scope clearly labeled as in development; do not claim unavailable controls are active.
- Settings should own app preferences (theme/appearance, language if supported, browser/dock preferences, cache and notification preferences, data/diagnostics where supported).
- Security Center should explain the app's non-custodial boundary, saved-wallet-address permissions, connected external-wallet sessions, site/origin permissions, and provide safe session/site permission management. It must never ask for or store seed phrases/private keys.
- Notifications should provide an inbox/list with read/unread state and empty/loading/error states. Do not fabricate notifications or enable unsolicited push delivery without an implemented and consented channel.

### F. Coherent implementation and acceptance order
1. Keep website loading and browser-page WalletConnect deferred unless the user explicitly resumes them.
2. Complete exact-head CI and physical-device acceptance for the six-action horizontal dock; retain existing accounting flows.
3. Finish/verify the responsive Home card dashboard, exactly one global search field, Airdrop Library wording, and the three 我的 destinations.
4. Continue the remaining user-confirmed product batches in dependency order, updating this ledger and minimizing Actions runs.

**Initial status for all newly added items:** requirements confirmed; implementation status pending code audit. Do not describe any as implemented until source inspection, tests, and device evidence support that status.

---

## 0. Highest-priority continuation rule — mandatory whenever the user says “继续”, including a new chat

This rule has the highest execution priority for this project. It must survive context limits and chat handoffs. The repository copy of this document is the durable source of truth; memory, summaries, and previous assistant messages are never substitutes for re-reading it.

Before any implementation work after “继续”:
1. **Read this complete requirements document first**, including the progress ledger, code-coupling map, and latest user-confirmed corrections. Do this again in every new chat, even when prior context claims it was already read.
2. Compare the latest user instructions with the document. The latest explicit user correction wins. If there is a conflict, correct this document before implementation.
3. Inspect the current GitHub main branch, open PRs, branches, latest commits, diffs, and CI results. Never treat an older commit's CI as validation for a newer commit.
4. Assess **code fit and feature fit together**: inspect the existing architecture, shared models, data ownership, APIs, persistence, background work, UI, tests, and dependencies alongside the complete user-facing feature flow. Plan implementation around the connected end-to-end capability, not isolated code edits.
5. **Do not develop in fragmented, piecemeal slices when related requirements share a code path or user workflow.** Group coupled requirements into a coherent implementation batch, preserve cross-module consistency, and validate the complete connected flow. Split work only when a real dependency, risk boundary, or verification constraint requires it; document that reason.
6. Classify every requirement as **complete in code**, **partial**, **in progress**, **pending verification**, **deferred**, or **cancelled**. A plan, commit, PR, passing CI, deployment, and real-device acceptance are different evidence levels; do not conflate them.
7. Select the next coherent batch by requirement coverage, code dependencies, and user priority. Do not repeat completed work without a demonstrated defect or reintroduce cancelled/deferred scope without explicit approval.
8. Update this document's requirements and progress ledger as part of every implementation change. Keep GitHub Actions usage low by validating coherent batches and avoiding unnecessary duplicate runs.
9. Report actual changes, verification evidence, remaining work, and the next dependency-driven batch.
10. **Completion declaration:** only after every confirmed requirement in this document has been implemented and the required verification/acceptance is complete, tell the user exactly: **“全部开发完毕，可以进行下一步工作了。”** Do not use this declaration earlier.

## Document map and current implementation baseline (2026-11-10)

This document remains the complete source of truth. The optimization below adds navigation and status guidance; it does **not** delete, weaken, or silently reinterpret any previously confirmed feature. The latest account-scope correction explicitly defers new registration and user-facing cross-device private-ledger restore; see Section 1.

- **Product boundaries and privacy:** Sections 1–4 and 10–11.
- **Personal accounting and project lifecycle:** Sections 5–6, including the new quick-entry and project-library requirements.
- **Community catalog, metrics, discovery, and search:** Sections 7–8.
- **Refresh/cache/reliability:** Section 9.
- **Implementation evidence and 15-batch roadmap:** Sections 12–14.
- **Naming rule:** the user-facing personal-project destination is named **“库管理”**. “空投库” is the public project-discovery/catalog destination. This is a display/navigation rename only; it does not remove or alter project management, tasks, wallet associations, ledger history, or any other existing capability.
- **Code status at this baseline:** Batches 01–13 have merged code-level implementations with their recorded CI evidence. Batch 14 is partial in code and awaits production configuration, live smoke checks, backup/restore drill, and rollback evidence. Batch 15 implementation and exact-head CI are complete after PR #132; real-device acceptance remains pending. Do not conflate code/CI completion with production or device acceptance.

### Latest continuation checkpoint (2026-11-10)

This checkpoint is the current handoff state and must be refreshed whenever implementation or verification advances.

- Latest main-branch audit found **no open pull requests**.
- PR #132 (Batch 15 Android navigation, offline backup contract, contribution-status UI) is merged. Exact-head Actions run `42746641763` passed Android unit tests and Debug APK assembly; artifact `airdrop-hunter-debug-apk` uploaded successfully. Physical-device acceptance is still outstanding.
- PR #133 (Batch 14 PostgreSQL restore-target preflight) is merged. Exact-head Actions run `42747428235` passed backend pytest (**153 passed**); Android build was correctly skipped because the PR did not change Android code.
- PR #134 (external-wallet DApp signing and transaction handoff) is merged as `4e7facc794b6b49618732c02558129a837a064d6`. Exact-head Actions run `42749265878` passed Android unit tests and Debug APK assembly; artifact `airdrop-hunter-debug-apk` uploaded successfully (44,315,860 bytes; artifact ID `13731634200`). Real-device MetaMask/OKX/Trust Wallet signing, sending, rejection, timeout, and callback acceptance remains outstanding.
- The production-configuration checklist and safe activation order are recorded in Batch 14 below. The latest schedule audit found seven community-snapshot cron runs, all `skipped`; production publishing and live readiness have not been proven.
- **Current repository state:** main is at `6a6221deaa425b4a4132854962f55b87382e4538`; no open PRs were found in the latest audit.
- **Next work order:** (1) complete Batch 15 physical-device acceptance using the exact merged APK, including the external-wallet handoff cases; record each pass/fail and fix confirmed defects in a coherent batch; (2) complete Batch 14 production secrets/storage configuration, live readiness smoke, isolated PostgreSQL backup/restore drill, and rollback evidence; (3) re-audit all confirmed requirements and their cross-module flows before declaring release readiness.
- **Release status:** not complete. Do not announce “全部开发完毕，可以进行下一步工作了。” until both implementation and all required device/production acceptance gates in this document are satisfied.


## 1. Product and engineering boundaries

### Current-release account and recovery scope (user-confirmed, 2026-11-10)

- Do not allow new users to register in the current release. Hide or disable registration entry points; if a user reaches one, show **“注册功能开发中，请等待后续更新”** and do not start an account-creation request.
- The current release has no registration or login entry point. All users use local mode and the complete non-cloud product experience, including Home, Micro Community, Airdrop Library, personal library/projects, tasks, wallet-address management, accounting, and eligible anonymous public contribution.
- Every user's private ledger, wallets, projects, tasks, favorites, and personal records remain in the phone's local Room database. Clearing app data or uninstalling loses those details unless the user has made an encrypted local backup.
- Defer all private cloud storage, private-ledger upload/sync, account-based personal-data persistence, and cross-device cloud restore. Do not advertise cloud recovery or make it a release acceptance condition.
- Do not delete already-implemented authentication, account-isolation, or sync code merely because these features are deferred. Preserve it for a later phase, but guard it so current-release user flows cannot invoke private cloud sync.
- This deferral applies to cloud-based private user data recovery only. Offline encrypted file backup/import is separately approved below. It does not cancel the separate PostgreSQL backup/restore drill for production operations, nor anonymous public aggregate contribution.


- Product: Airdrop Hunter, an Android application for airdrop projects, task tracking, wallet-address management, Gas/reward accounting, and community project statistics.
- Stack: Android Kotlin + Jetpack Compose, FastAPI, PostgreSQL/Neon, Render, and GitHub Actions APK builds.
- Keep the project affordable for a solo developer. Prefer free tiers, cache responsibly, batch coherent changes, and avoid unnecessary CI runs.
- Local mode is the only current-release experience. Do not expose registration or login UI or submit authentication requests. Keep the existing authentication/backend code dormant for a future phase; all current personal records remain in local Room storage.
- Never build an in-app custodial wallet or collect/store seed phrases or private keys. DApp connection and user-requested signing must be routed to an external wallet app (for example MetaMask, OKX Wallet, or Trust Wallet); the user reviews and approves there, and signing/broadcast happens outside Airdrop Hunter. Airdrop Hunter must never sign with an app-held key. Do not add a standalone in-app custody, transfer, or swap service.
- Public wallet addresses and read-only public chain data only.
- Test data must never contaminate real private ledgers or public statistics.

### Offline encrypted backup and device migration (user-confirmed, 2026-11-10)

- Add two prominent actions to the “我的” page: **“备份我的数据”** and **“导入数据”**. This is a local-file workflow and does not require registration, login, a cloud vault, or network access.
- Backup exports the active local session's personal data into one portable .ahbackup file using Android's system file picker. Include wallet address records and metadata, user-created/adopted projects, tasks and project-wallet associations, pending bills, confirmed ledger rows, P&L/history, favorites/ratings, pending anonymous contribution outbox entries, and all account-scoped/local cached payloads required to reconstruct those records.
- Do not export access tokens, passwords, Android Keystore keys, Reown sessions, signing material, private keys, seed phrases, unrelated users' rows, or shared public/CDN caches.
- Encrypt the portable file with AES-256-GCM using a password-derived key (PBKDF2-HMAC-SHA256 with a random per-file salt and fresh nonce). Require a user-chosen password of at least 8 characters (do not offer a four-digit PIN because an offline file is vulnerable to repeated guesses); the password is entered for backup and again on import, and is never stored or embedded in the file. Include a version/magic header, integrity protection, bounded file size/record counts, and validate the decrypted JSON's app_name: "AirdropHunter", format version, and required schema before any restore. The canonical current suffix is .ahbackup; the picker must also allow users to select .hunter files and rely on content validation rather than trusting the filename.
- Import validates the complete file, schema version, source scope, record limits, and authentication tag **before changing any local data**. Restore all included tables and cache payloads atomically into the currently active guest/account scope, remapping scope keys without importing credentials. A failed import leaves existing data untouched.
- Before import, explain that the current active session's local data will be replaced by the selected backup. Require explicit confirmation. Do not silently merge aggregate totals or duplicate ledger rows. Re-importing the same backup is deterministic and must not double-count P&L or create duplicate bills/interactions/outbox entries.
- Preserve stable record IDs, project IDs, wallet IDs, timestamps, native-asset breakdowns, historical quote snapshots, and pending/confirmed state. Never regenerate IDs or recalculate historical amounts during restore.
- Show completion feedback with backup creation time and restored record summary. Cancelling the picker/password/confirmation makes no changes.
- This is **offline file backup/restore**, not cloud recovery. Cloud-based cross-device restore and new-user registration remain deferred. The backup file and password remain under the user's control.
- The workflow must work offline. Backup/import never triggers public contribution or private cloud sync as a side effect; restored pending outbox rows retain their idempotency IDs and retry rules.


## 2. Mandatory data ownership and privacy policy

This policy is authoritative across Android storage, API requests, database models, sync workers, tests, and UI explanations.

| Data layer | All current-release users |
|---|---|---|
| Detailed personal ledger (wallet addresses, Gas details, rewards, project participation, transaction references, timestamps) | Stored on the phone in the local Room database only |
| Private cloud restore | Not available; clearing app data or uninstalling loses local details |
| Portable local backup | User can export an encrypted backup file and import it on another device; file/password remain under the user's control |
| Public community contribution | Eligible anonymous statistics from official community projects may contribute by default |
| Public contribution storage | Anonymous aggregate only; no wallet address, tx hash, personal ledger row, or account identity |
| Clearing/uninstalling app data | Local private data is lost; no server copy exists |

Additional rules:
- Every user's personal details are saved in the app-private local database only. They are not uploaded to a private cloud vault. Clearing app data or uninstalling the app loses those details unless a local backup exists.
- Public aggregate data is separate from the private ledger. Never expose individual bills, addresses, hashes, or account identifiers in public project statistics.
- Public Gas totals use eligible anonymous contribution records and retain their provenance tiers. Public reward/income overviews may include both theoretical estimates and user-confirmed actual rewards as an approximate community picture. Mock/demo records are always excluded.
- Contributions must be idempotent and retry-safe.
- Existing authenticated private-ledger endpoints/code may be retained for a later phase, but current-release personal records remain local-only and must not be uploaded or synced. If those dormant endpoints are reactivated in a later release, authentication and account scoping remain mandatory.
- The current release has no account switching; use one local data scope. Historical account-scoped code remains dormant.
- Community-directory projects are opted into anonymous aggregate contribution by default; users do not manually authorize each bill. A project created privately by the user (communityOfficialId = null) remains private and never contributes. A user-created project only becomes eligible after it is explicitly linked to a stable official community project ID.
- For an eligible community-directory project, public contribution is the default. A transaction hash and selected chain/network are used when available for verification; if no hash is supplied, keep the data in the user-reported tier rather than pretending it is verified. A supplied hash that is invalid, replayed, or fails payer/finality checks must not be shown as verified; keep the private local row intact.
- The backend verifies on the correct chain that the transaction exists and is sufficiently confirmed/finalized, and that its chain-specific fee-payer/sender identity matches the selected address. Use deterministic chain adapters (for example EVM transaction from-address, TRON owner address, and Solana fee payer). For chains/transactions where the fee payer cannot be attributed unambiguously (including ambiguous multi-input Bitcoin cases), reject public contribution and keep the private accounting entry.
- Enforce global replay protection using a unique key for the normalized chain/network + transaction hash across all users and projects. A transaction hash already used by any contribution cannot be submitted again. Retries of the same idempotent contribution are accepted without double counting; a different payload reusing the same hash is rejected.
- Keep the raw transaction hash and selected wallet address out of public aggregate tables, public snapshots, public APIs, analytics, and ordinary logs. The verifier may use them transiently to query the chain; persist only a server-keyed HMAC/fingerprint of the normalized chain+hash for global replay protection, plus minimal verification metadata. Do not attach the contribution to a user account.
- **Three-level data provenance/trust display is mandatory and deterministic (no AI scoring):**
  1. `📝 用户自报`: manually entered amount without successful chain-link verification. Display as user-reported data.
  2. `🔗 链上已确认`: transaction exists, has reached the required confirmation/finality threshold, and the chain-specific fee payer/sender matches the selected wallet address, but the fee amount has not been completely parsed/verified. This tier verifies the transaction link only; it must not be described as amount-verified.
  3. `✅ 链上精确核验`: the backend has successfully parsed all applicable fee components for that chain/network, calculated the transaction's actual fee, obtained a reliable historical native-asset/USD quote for the transaction time, and passed the amount comparison rule below. Only this tier may be labeled as precisely amount-verified.
- **Half-automated receipt verification (approved; replace the earlier hash-link-only amount boundary):** use an existing reliable chain explorer/provider API (EVM first, e.g. Etherscan-compatible receipt endpoint) to fetch transaction/receipt data; do not operate self-hosted nodes or build a full general-purpose chain parser. For EVM transactions, calculate the execution fee from `gasUsed × effectiveGasPrice`, with correct wei/native-unit conversion. The backend, not the client, is authoritative for the calculated amount.
- For USD comparison, convert the calculated native fee using a reliable historical price corresponding to the transaction timestamp. Do not use the current spot price to revalue historical transactions. Persist enough source/timestamp/asset metadata to explain the calculation and reproduce it.
- **Chain-specific completeness matters:** Etherscan-compatible receipt parsing is EVM-only. TRON, Solana, Bitcoin, and other ecosystems require their own deterministic adapters/providers before being eligible for Tier 3. Some L2 networks (including networks where an additional L1 data fee applies) may have fee components beyond `gasUsed × effectiveGasPrice`; if the provider/API does not expose and validate all applicable components, do not call the result “100% precise”. The transaction may remain Tier 2 if its linkage is verified.
- **Amount comparison tolerance:** compare the user's entered Gas amount with the backend-calculated transaction fee using a ±20% tolerance after converting both to the same asset/currency basis and applying the historical quote. Within tolerance, accept Tier 3 and use the backend-calculated chain amount as the public aggregate amount (not the user-entered amount). Outside tolerance, reject high-trust amount contribution and show **“金额与链上不符”**; retain the private ledger entry and its user-entered amount. Do not silently overwrite the private ledger.
- A verified transaction fee verifies Gas only. It does not verify reward/token income, reward valuation, project participation, or the user's realized profit. Public community income may include theoretical estimates and user-confirmed actual rewards as an approximate overview; keep their provenance separate in personal records and do not describe estimates as realized profit.
- Provider/API timeout, rate limit, outage, or incomplete response means **pending verification with retry**, not a false rejection and not an automatic downgrade to a falsely verified amount. Invalid hash, wrong payer, insufficient finality, unsupported attribution, or replay conflict is a terminal public-contribution rejection; the private ledger remains intact.
- Dashboard data may show approximate aggregate income that includes estimates and actual rewards; retain provenance labels where available, especially for Gas verification tiers. Do not imply the whole dashboard is a precise financial statement. This provenance display must not replace the single continuous red/green Gas/reward comparison bar.
- Keep the existing anonymous outbox/idempotency path; do not create a second contribution pipeline. Preserve global replay protection and privacy: raw Tx Hash/address must not appear in public tables, snapshots, public APIs, analytics, or ordinary logs; persist only server-keyed fingerprints plus minimal verification metadata.
- This verification flow never auto-completes an event or settlement. Actual final results remain pending until the user manually enters and confirms the realized amount under Section 5.
- Eligible official-project aggregate data enters the public contribution pipeline by default. If a transaction hash is supplied, only successful verification may be labeled as chain-linked/precisely verified; a failed or replayed hash must not be shown as verified. A missing hash may remain user-reported. The private local ledger entry remains intact and can be corrected/retried.
- Contribution eligibility is evaluated per accounting entry/batch row. For multi-wallet batch entry, each wallet row must have its own matching transaction hash; never reuse one hash across several selected addresses. Preserve atomic private-ledger batch behavior, but public verification failures must not erase or roll back private accounting data.

## 3. Wallet and address management

- Airdrop-specific multi-wallet and multi-chain public address management.
- Multiple addresses per chain; user labels, tags, notes, and project associations.
- Associate addresses with projects, Gas records, rewards, and transactions.
- Read-only chain activity and transaction history.
- Supported activity ecosystems currently include EVM networks (Ethereum, BSC, Polygon, Arbitrum, Optimism, Base, Avalanche), TRON, Bitcoin, and Solana.
- Manual chain selection and basic format validation are acceptable. Automatic address recognition is deferred.
- No seed phrase/private-key entry or wallet custody. External wallet connection and DApp signing are allowed only by waking/invoking the user's chosen external wallet; the external wallet performs the signature and obtains the user's approval. No in-app key-based signing or custodial transaction execution.

## 4. Browser and DApp interaction

- Built-in WebView for project sites and DApps.
- External wallet connection for user-approved DApp interaction, with origin/session protections and an approval-guarded bridge.
- User can choose/switch the address used by a DApp and enter project claim flows.
- Associate browser interaction with a project and later with its on-chain transaction/accounting record.
- HTTPS-only navigation and strict TLS failure handling; never bypass certificate errors.
- DApp signing/transaction requests are supported only through the external-wallet handoff: Airdrop Hunter may relay the user's explicit DApp request, but the external wallet displays the request and performs the final approval, signature, and broadcast. Never inject or use an app-owned private key.
- Real-device validation of WalletConnect/Reown external-wallet handoff, actual external signing approval, WebView lifecycle, origin boundaries, session expiry, and DApp compatibility remains required. A connection-only test does not prove the external signing flow.

### External-wallet DApp signing and transaction requests (user-confirmed 2026-11-07; implementation gap being closed)

- The previous blanket read-only restriction on `personal_sign`, `eth_signTypedData`, and `eth_sendTransaction` contradicted the user's explicit requirement to invoke MetaMask, OKX Wallet, Trust Wallet, and other external wallets for signing.
- A DApp may request only the explicit EVM method allowlist implemented in the browser bridge: `personal_sign`, `eth_sign`, `eth_signTypedData` (including v3/v4), `eth_signTransaction`, and `eth_sendTransaction`.
- Before relaying, require that the current HTTPS top-level origin has an active user-approved WalletConnect session, that the request's signing/sending address exactly matches an account approved in that session, and that the request uses the session's active CAIP-2 chain ID.
- Relay the method and parameters through Reown AppKit. The connected external wallet must open and present its own confirmation UI; only that wallet can approve/sign/broadcast. Airdrop Hunter never holds keys or signs on the user's behalf.
- Keep raw signed-transaction submission (`eth_sendRawTransaction`), smart-account batch methods, and unsupported RPC methods blocked. Network switching remains a manual action in the external wallet; the user reconnects after switching.
- Serialize pending DApp requests, return the external wallet's actual JSON-RPC result/error to the requesting page, and fail closed on session changes, origin navigation, unsupported connector paths, timeouts, ambiguous account parameters, or mismatched addresses.
- CI and real-device validation are separate: tests must cover the allowlist and address ownership; on-device acceptance must confirm MetaMask/OKX/Trust Wallet handoff, approval, rejection, and returned result. Connection-only success is not signing-flow completion.

## 5. Personal accounting, Gas, rewards, and project history

- Record project, chain/network, wallet address, transaction hash, interaction method, timestamp, theoretical Gas, actual Gas, theoretical reward, and actual reward.
- Keep theoretical/estimated and actual/realized values separate.
- An event ending or an estimated settlement must never automatically close an accounting item.
- After an event ends, it remains pending until the user manually enters the actual final amount and confirms it.
- Only explicit user confirmation transitions the item to confirmed/completed.
- Support pending-item deletion, duplicate transaction prevention, and idempotent confirmation.
- Link imported chain transactions to a project only with user selection; never infer a project without evidence.
- Prefill actual Gas only when payer attribution is reliable. Ambiguous/shared fees require manual review.
- Project-level accounting, cost, actual rewards, net P&L, interaction history, and day/week/month statistics.
- All private accounting remains local-only for this release. Private cloud ledger sync is deferred.

## 5.1 Quick manual accounting and batch wallet entry (new user-confirmed requirements, 2026-11-10)

- From the personal project detail or “库管理” project card, a **“记一笔”** action opens a compact accounting dialog. The selected project name and stable project ID are prefilled and locked; the user cannot accidentally record the entry against another project from that dialog.
- The dialog loads the user's saved public wallet addresses and presents them as a multi-select checkbox list. The user may select several addresses (for example, ten). Each selected address produces its own independent ledger entry, with its own wallet address, stable record ID, timestamp, and native/network metadata.
- Gas input is a per-wallet average amount applied to each selected wallet, not a single combined amount. Show quick amount chips such as $0.5, $1, $5, and $10, plus the last successfully used Gas amount. A chip fills the field; it does not submit the form.
- Reward allocation must be explicit to prevent multiplying a campaign total by the number of wallets: support “每个钱包的收益” and “本次总收益，按所选钱包平均分摊”. In total mode, divide token quantity or USD value across selected wallets before creating rows. Never duplicate a campaign-wide total into every wallet row.
- Reward entry modes:
  1. **USD direct entry:** enter the USD valuation directly.
  2. **Token entry:** enter token symbol and quantity; query a free price source (CoinGecko where available) and show the calculated USD estimate. Persist the quote used at entry time.
  3. **Manual valuation:** if no reliable quote is available, allow an explicitly user-entered USD valuation.
  4. **Points / not yet issued:** allow recording the reward as points or an unissued token without inventing a USD price. These rows remain visible in the private ledger but do not become realized USD income or inflate USD P&L/public USD totals.
- Price lookup is best-effort, bounded, cancellable, and failure-tolerant. Unknown/ambiguous symbols or unavailable quotes return an unknown price; never substitute zero or a similarly named token. Manual valuation and points/unissued modes remain available offline.
- **Historical quote immutability:** for a priced token entry, persist token symbol, token quantity, unit USD price, total USD valuation, quote source, quote observation timestamp, and record timestamp. Later market-price updates must never rewrite historical entries or previously computed contribution payloads.
- Batch submission is atomic: either every selected-wallet ledger row and its dependent aggregates/outbox rows are committed, or none are. A partial batch is forbidden. Repeated taps must be idempotent and must not duplicate rows.
- Keep the existing manual-confirmation boundary: a record is considered realized only when the user explicitly submits the actual result. Estimates, points, and unissued rewards remain separately typed and cannot automatically close a pending event.
- Preserve local private-ledger behavior for all users. No private ledger row or personal detail is uploaded. Only eligible anonymous aggregate contributions for official community projects may enter the public outbox; no address, transaction hash, account identity, or private ledger row may be stored in public aggregate records.
- For USD totals and net P&L, unknown/unissued/points rewards must be represented as incomplete rather than treated as "$0" realized income. Preserve the native token/points detail even when USD valuation is unknown.

## 5.2 Community-project default contribution and transaction-linked verification

- Community-directory projects are opted into anonymous aggregate contribution by default; users do not manually authorize each bill. A project created privately by the user (communityOfficialId = null) remains private and never contributes. A user-created project only becomes eligible after it is explicitly linked to a stable official community project ID.
- For an eligible community-directory project, each participating wallet row must provide its own transaction hash (Tx Hash) and selected chain/network. Validate hash syntax locally, but treat backend chain verification as authoritative. A missing/invalid hash or unsupported chain keeps the personal ledger row but prevents public contribution.
- The backend verifies on the correct chain that the transaction exists and is sufficiently confirmed/finalized, and that its chain-specific fee-payer/sender identity matches the selected address. Use deterministic chain adapters (for example EVM transaction from-address, TRON owner address, and Solana fee payer). For chains/transactions where the fee payer cannot be attributed unambiguously (including ambiguous multi-input Bitcoin cases), reject public contribution and keep the private accounting entry.
- Enforce global replay protection using a unique key for the normalized chain/network + transaction hash across all users and projects. A transaction hash already used by any contribution cannot be submitted again. Retries of the same idempotent contribution are accepted without double counting; a different payload reusing the same hash is rejected.
- Keep the raw transaction hash and selected wallet address out of public aggregate tables, public snapshots, public APIs, analytics, and ordinary logs. The verifier may use them transiently to query the chain; persist only a server-keyed HMAC/fingerprint of the normalized chain+hash for global replay protection, plus minimal verification metadata. Do not attach the contribution to a user account.
- Verification authority: use the three-tier provenance, half-automated receipt parsing, historical-price valuation, chain-specific fee completeness, ±20% tolerance, pending/retry behavior, mismatch handling, and privacy rules defined in Section 2. This section defines when the accounting UI may enqueue an eligible community-project contribution; there is no per-bill opt-in checkbox. Do not retain the superseded hash-link-only/no-receipt-parsing rule.
- Only after successful chain verification and replay checks may the eligible official-project contribution enter the public contribution pipeline. A missing/invalid hash, unsupported chain attribution, failed/unconfirmed transaction, or replay conflict must not create a verified public contribution; the local private ledger entry remains intact and can be corrected/retried.
- Contribution eligibility is evaluated per accounting entry/batch row. For multi-wallet batch entry, each wallet row must have its own matching transaction hash; never reuse one hash across several selected addresses. Preserve atomic private-ledger batch behavior, but public verification failures must not erase or roll back private accounting data.

- Keep theoretical estimates, user-entered actual amounts, and transaction-verification status as separate fields. A valid transaction link must not automatically confirm an event or replace the user's existing manual final-result confirmation.
- The public dashboard may aggregate only eligible values from a canonical public/official project that the accounting row is explicitly linked to. A user-created custom project has `projectOfficialId = null`: its bills and ledger remain private/local and must never be uploaded to or counted in Micro Community public statistics. Official-project community income may include separately typed theoretical estimates as well as manually confirmed actual rewards for a coarse overview; estimated values never mark an event complete or become personal realized profit. Gas provenance must remain explicit; do not infer a Gas amount from transaction existence alone.

## 6. Projects and tasks

- Project directory, project details, statuses, personal project tracking, and history. User-created custom projects with `projectOfficialId = null` are private/local-only: their bills are not uploaded and do not enter public community metrics. Public contribution is permitted only when the user explicitly links a confirmed bill to a canonical official project.
- Create/edit/complete/delete tasks with progress tracking and project ownership checks.
- Associate projects with wallets/addresses, chains, Gas, rewards, and interaction history.
- Project totals include Gas, actual rewards, and net P&L.
- APP 主动发现空投项目仍是正式需求，但必须优先采用零成本、现成且可稳定访问的数据源；不为付费 API、付费订阅或复杂授权投入成本。禁止自建爬虫抓取/解析千奇百怪的网站页面。优先使用免费的公开 API、免费 RSS/Atom、免费第三方空投平台公开页面/订阅源；能稳定取得哪些公开字段就收录哪些，无法取得的字段留空，不做强求。只有在免费且授权/访问条件简单时才接入 Telegram/Discord 等来源；X/Twitter 若需要付费 API 或复杂授权则不接入。若没有合适免费数据源，就不做该来源。内部社区项目目录/搜索持续保留。

### 6.1 Public catalog adoption into the personal project library (new user-confirmed requirements, 2026-11-10)

- The personal-project destination is displayed as **“库管理”** (formerly “项目管理”); the public discovery/catalog destination is displayed as **“空投库”**. These names must be consistent in bottom navigation, page headers, entry points, and user-facing success messages.
- From an “空投库” project card, provide a compact **“+”** action. The public project detail page also provides an **“添加”** action. Both actions add the canonical public project to the current guest/account-scoped personal project library.
- Reuse the existing canonical project ID and existing CacheStore.joinCommunityProject / user_projects path where appropriate. Do not create a duplicate private project row or a second membership system. Repeated add attempts are idempotent and show “已添加” state.
- The personal library displays the added project alongside user-created projects and retains the existing project tasks, wallet associations, accounting history, and day/week/month summaries.
- A gentle **“待记账”** label appears only when the project has been in the personal library for more than 3 days and has zero associated confirmed accounting entries. The label is a reminder only: it must not create a bill, change project status, or imply the user owes money. It disappears as soon as a qualifying ledger entry is associated with the project.
- Use the project's persisted createdAt and confirmed interaction count (or an equivalent account-scoped ledger count) as the source of truth. Do not infer from list ordering or public catalog creation time.
- The personal library and adoption state must respect guest/account isolation. Guest project membership stays local; any existing authenticated membership remains account-scoped. Cross-device restoration is deferred and must not be promised in the current release.

## 7. Micro Community

- Public project directory/cards, categories, tags, and project details. Project metadata may be populated from approved external APIs/RSS and user submissions; project skeleton (name, logo, official links, funding/tokenomics metadata) is distinct from community-generated participation metrics.
- **Project list card UI (user-confirmed):** keep cards concise for browsing, showing project avatar/logo, name, brief 1–5-star rating and reputation label (for example, “好评如潮” / “褒贬不一”), plus an **overall Gas cost vs actual reward** summary with separate USD valuations and a red/green comparison bar (red = Gas cost, green = actual reward). Tapping a card opens the full project detail page. The detail page exposes the underlying per-token amounts; native units are never added across different symbols.
- **Project detail UI (user-confirmed):** use a single vertically scrolling waterfall/stacked layout, not tabs. From top to bottom show project avatar, name, URL, and description; overall star rating and derived reputation wording; then one unified Gas/reward statistics card with three animated segmented tabs: total overview, last 3 days, and last 1 hour. Each row shows per-currency amounts and comparable USD valuation where available, plus a red/green proportional bar only when there are comparable non-zero amounts; never render a fabricated 50/50 bar for missing or zero-denominator data.
- **Metric integrity:** Gas and reward values are anonymous community aggregates. Public reward/income overviews may include theoretical estimates and user-confirmed actual rewards as approximate community context; they are not a precise realized-profit statement. Personal ledgers must still separate theoretical and actual fields. Do not add unlike tokens together without per-token breakdown; USD estimates must be labeled as valuations. Missing/zero-denominator data must be represented honestly; the single red/green comparison bar must use exact ratios and a zero-valued side must have zero width, never an artificial minimum-width segment. Reputation labels are derived from real rating aggregates and distribution, never hard-coded.
- Public aggregate Gas and approximate reward/income presentation only; public net P&L is not displayed.
- Rankings: 吸金榜、避坑榜、新星榜.
- Categories/tags may include L2, zero-cost, testnet-required, high-Gas, confirmed airdrop, and similar classifications.
- Favorites/following and personal to-do tracking.


- Ratings are 1–5 stars only; only actual participants with qualifying confirmed accounting may rate. No text reviews/comments.
- Abuse prevention, event idempotency, and validation of eligible participation.
- Public views must only use aggregate contribution data and must never expose personal addresses or transaction-level records.
- Home screen must provide a prominent “+ Submit New Airdrop Project” entry. Submission accepts project name and any usable project/listing URL (official website is optional); a third-party airdrop platform listing URL is acceptable. The project card's minimum required fields are avatar/logo (optional/fallback allowed), display name, and a URL. Backend normalizes and deduplicates by canonical URL/domain, slug, and other identifiers. Existing matches are reported as duplicates and linked to the existing project rather than creating another row. After submission, the backend automatically checks the project against the existing catalog and supported provider APIs. Duplicate matches return a clear “already exists” result and point to the existing project. A non-duplicate project that passes basic safety checks (HTTPS, valid domain, no deceptive URL/userinfo, normalized identity) is published to the dynamic catalog; suspicious or unverifiable submissions are held for review. Never auto-trust arbitrary user URLs.
- On user submission, run low-cost/free provider lookups to enrich whatever metadata is available; this is API/feed lookup, not crawling the submitted website. Discovery source priority: (a) free, stable public APIs such as supported DefiLlama endpoints where verified; (b) free public RSS/Atom feeds from established third-party airdrop platforms such as Airdrops.io where a usable feed is available; (c) public pages/listing URLs from third-party airdrop platforms when their content can be obtained through an allowed public feed/API or other permitted stable interface; (d) optional DropsTab or similar provider only when a genuinely free tier is sufficient; (e) Telegram/Discord only when public/authorized access is free and straightforward. X/Twitter is out of scope when it requires paid API access or complex authorization. Do not subscribe to paid APIs or paid RSS services. RSSHub may be used only as a free, trusted endpoint after checking route availability and terms; it is not a universal converter. Preserve source attribution and sync health. If a source cannot be accessed reliably and permissibly at no cost, skip it.
- Keep source attribution, source URL, first/last seen timestamps, and sync status for imported metadata. Failed/limited providers must not block user submissions or the rest of the catalog.
- Users supply community “blood”: Gas/reward/盈亏 data and 1–5 star ratings remain grounded in confirmed participant ledgers and user-entered records. External metadata must never fabricate Gas, rewards, P&L, ratings, or participation.

## 8. Search and market

- Existing global search and public market data for BTC, ETH, SOL, BNB, and XRP.
- Tapping a coin opens its market detail page, not a search-field autofill.
- Search failures must show a recoverable error/retry state and must not crash the app.
- Self-written crawlers/scrapers that parse arbitrary airdrop websites are prohibited. APP-side discovery prioritizes free, stable third-party APIs and allowlisted RSS/Atom feeds from airdrop platforms, plus user submissions. Use public third-party listing URLs as project links when official websites are unavailable or unnecessary. Paid data sources are not required; skip any source that cannot be accessed reliably and permissibly for free.
- Internal community project directory/search remains required.

### 8.1 Home navigation and market dashboard (new user-confirmed requirements, 2026-11-10)

- Bottom navigation order is exactly: **首页 → 空投库 → 库管理 → 我的**. Rename the existing airdrop discovery/library entry to “空投库”; rename the personal project-management destination to “库管理”; remove redundant subtitle copy. Navigation between destinations must preserve each tab's independent navigation/back-stack state using save/restore state behavior.
- Home starts with a compact BTC/ETH market dashboard. Its displayed refresh status must reflect the same underlying cache/network refresh state as the data it displays; do not show a success timestamp when refresh failed.
- Place the global search entry immediately below the market dashboard. Use a pill-shaped search field and adaptive category tabs: **币种 / DApp**. Tabs must adapt to available width rather than overflow.
- Search input uses a 300 ms debounce. Search locally against Room first with fuzzy matching, then request remote results as appropriate. Typing must not trigger one network request per keystroke.
- Search history is stored locally in Room, deduplicated by normalized query/category, ordered by most recent use, and capped at 10 entries. History remains local to the app/account scope and is not part of public community contributions.
- Hot-coin results include symbol/name, current price, price-change percentage, logo where available, and contract address/network where applicable. A 24-hour percentage here means market-price change only; it does **not** reintroduce the cancelled standalone community “24-hour heat” metric or project participation count.
- Preserve existing global search navigation behavior: selecting a coin opens its market/detail destination, not an automatic search-field fill. DApp results open their validated project/site destination under the existing WebView security rules. Stock search is explicitly out of scope; do not add stock categories, stock data providers, stock search UI, or stock-specific cache models.

### 8.2 Unified asset detail model and presentation (limited scope; not a portfolio dashboard)

- Normalize supported EVM multi-chain assets, Solana assets, and Bitcoin native balances into a shared **UnifiedAsset** domain model so UI and valuation logic do not need chain-specific presentation branches.
- Required Room/domain structures: **UnifiedAssetEntity** plus **UnifiedAsset** data class; **MarketCoinEntity/MarketCoin**; **HotCoinCacheEntity/HotCoin**; **SearchHistoryEntity/SearchHistory**; and cache metadata keyed by data source/cache key. Fields must preserve chain/network identity, asset/token contract (nullable for native assets), symbol/name, decimals, raw/formatted quantity, nullable price and USD value, verification/source metadata, and fetched/updated timestamps as applicable. Cache rows must be correctly scoped to the wallet/account context and must never be sent as public contribution payloads.
- In the wallet's relevant asset-detail view, hide or fold low-value dust whose verified USD value is below **$0.10**. Hide unpriced/fake or unverified spam tokens from the normal asset list; do not fabricate a zero valuation. Filtering is presentation-only: do not delete source balances or ledger history, and allow a deliberate way to inspect hidden items if needed.
- For supported non-Bitcoin asset detail, show a value-distribution pie chart using the five largest verified assets plus one aggregated “Others” slice. Do not include unpriced assets in the chart. Bitcoin detail must not show this pie chart.
- Reuse the existing TRON asset-card visual style for consistent chain asset rows. This limited detail view does not authorize restoring the cancelled full OKX-style portfolio dashboard, a separate asset tab, or wallet-wide portfolio overview.


## 9. Refresh, sync, and reliability

- Manual refresh and background refresh.
- Background work must not block normal app interaction.
- WorkManager-based sync, offline queue, retry/backoff, and recovery.
- Idempotent writes and duplicate prevention.
- Clear distinction between local cache, private cloud sync, and public aggregate contribution.
- Handle expired/invalid sessions, account switching, network failures, and partial sync without data loss.
- Do not claim app-wide refresh is complete based only on one subsystem's sync worker.

### 9.1 Room cache, 15-minute TTL, and stale-while-revalidate (new user-confirmed requirements, 2026-11-10)

- All remote data presented by the market/search/asset-query features must have a Room-backed local cache. Cache identity must include the data source and all relevant query dimensions (for example coin/category, chain, wallet/account scope, and query key) to prevent cross-source or cross-account contamination.
- For market dashboard, market coin detail, hot-coin lists, remote search results, and unified asset snapshots, use a **15-minute TTL**. A cache row is fresh for 15 minutes from its successful fetch timestamp. Do not apply this TTL to the authoritative personal ledger, pending bills, tasks, account credentials, or other user-authored records; those remain persistent data with their own synchronization rules.
- Apply stale-while-revalidate (SWR): render Room data immediately on screen entry; if stale or absent, start a background request while keeping existing content visible. On success, transactionally update Room and then let the UI observe the new rows. On timeout, provider error, rate limit, or partial outage, retain and continue displaying the last good cache; never clear valid stale data just because a refresh failed.
- Every cache-backed screen must show a human-readable last-successful-update label (for example “更新于 5 分钟前”). The label must come from the same cache metadata used by the screen, not from an unrelated global timestamp. Loading, stale, refreshing, and refresh-failed-with-cache states must be distinguishable without blanking existing content.
- User-triggered refresh for these market/search/asset cache surfaces is throttled to at most once per 30 seconds per relevant cache key. A throttled tap must not start duplicate requests and should provide restrained feedback. This 30-second throttle does not alter the community snapshot schedule and does not introduce pull-to-refresh for community pages.
- Background refresh must use bounded concurrency and cancellation/coalescing so multiple screens requesting the same cache key do not create duplicate network calls. Normal navigation and local cached interaction remain responsive while refresh runs.
- Use a shared cache repository/SWR policy or a generic **BaseCacheViewModel** abstraction so TTL, stale retention, refresh timestamps, request coalescing, and UI state are consistent rather than reimplemented independently in each screen. Suggested Room support includes **CacheMetadataEntity** keyed by cache key/source with fetchedAt, expiry/TTL policy, and refresh status/error metadata.
- Community project snapshots remain governed by their existing four-window schedule, static JSON/CDN publication, and Room cache-first revalidation requirements. The generic 15-minute market TTL must not overwrite the community overall/7d/3d/1h schedules or per-row update times.

### 9.2 Provider failover, node rotation, and retry policy

- For public RPC/market providers that publish multiple permitted endpoints, maintain a configurable ordered node pool and rotate to a healthy alternative after a retryable transport/provider failure. Track node health/cooldown so a persistently failing endpoint is not selected on every request.
- Use bounded exponential backoff with jitter, honoring **Retry-After** for HTTP 429 where supplied. Retry only safe/idempotent reads and retryable network failures or statuses such as 408, 429, 500, 502, 503, and 504. Do not retry validation errors, ordinary 4xx responses, authentication failures, or unsafe write/transaction operations.
- Set per-attempt and total time budgets, cap retry counts, propagate cancellation, and surface a useful recoverable state after all nodes fail. TLS certificate errors must never be bypassed and must not trigger insecure downgrade.
- A shared OkHttp interceptor may implement bounded GET retry/rotation where appropriate; repository/coroutine-level retry is preferred when it provides clearer provider-specific node selection, cancellation, and UI state. Do not block threads with Thread.sleep inside interceptors; use suspending delays at the repository layer when possible.
- Provider rotation and retry must not silently change chain, network, token contract, or query semantics. Preserve source attribution and the successful endpoint/source metadata for diagnostics.


## 10. Security, deployment, and release quality

- FastAPI authentication and account-level authorization for private data.
- PostgreSQL migrations must be reviewed, additive/data-preserving where possible, and never rewrite an applied production migration.
- Secrets remain in deployment environment, never source control.
- Verify database backup/restore and deployment rollback.
- Validate imported content, URLs, contract/address inputs, and WebView navigation boundaries.
- Meaningful Android and backend tests; run CI once per coherent ready-to-validate change set.
- Real-device acceptance is required for browser/wallet connection, data correctness, and end-to-end app flows. Cross-device cloud restore is explicitly deferred and is not a current-release acceptance gate.
- A merged PR or passing CI does not by itself prove real-device completion.
- Freeze broad UI redesign until core feature chains are complete; then perform one unified UI refinement and full regression.

## 11. Cancelled or deferred scope — do not reintroduce

- Full OKX-style portfolio/dashboard.
- In-app seed phrase/private-key wallet creation or custody, app-owned private-key signing, and standalone native transfer/swap execution are cancelled. External-wallet DApp signing and transaction requests remain required: the user approves and signs only inside their chosen external wallet.
- Self-written arbitrary-site crawling/scraping, RSS/Atom discovery feeds, unapproved third-party airdrop directories, paid X/API integrations and paid data services are out of scope. The only currently approved discovery sources are CoinGecko Demo API, operator-configured GitHub Raw Content lists, DexScreener public API, and Binance public announcements, subject to the source-specific evidence, license, attribution and zero-cost rules in Section 0.2. Do not add Telegram/Discord or another provider without the user's explicit approval.
- Automatic address recognition (deferred).
- Community chat, groups, DMs, mentoring, and teaching/social features (deferred).
- AI analysis is not a confirmed requirement.
- Old PR #31 page is not to be merged; obsolete/unsafe PR #36 is not to be merged directly.

### Later user corrections consolidated (source-of-truth overrides)

These later user statements supersede any earlier conflicting draft text or assistant assumptions:

- **External wallet signing (user-confirmed 2026-11-07):** absolutely no built-in wallet and no private-key custody. MetaMask, OKX Wallet, Trust Wallet, and other supported external wallets must be invoked for DApp signing. Airdrop Hunter does not hold keys or sign transactions itself; the external wallet presents the request and performs approval/signing/broadcast. The earlier blanket statement that the product has “no signing” is incorrect and is superseded.
- **Guest access (user-confirmed 2026-11-07):** no forced login. Guests can use the full non-cloud app experience, including browsing, Micro Community, Airdrop Library, project/task management, wallet-address management, accounting, and eligible anonymous public contribution. Registration remains disabled in the current release; login is not required for core use.
- **Custom project privacy (user-confirmed 2026-11-07):** a custom project has `projectOfficialId = null`; its bills stay private/local, are never uploaded, and never enter Micro Community public metrics. Only manually confirmed bills explicitly linked to an official public project can contribute anonymous aggregates. This rule is distinct from guest-vs-authenticated private-ledger ownership.
- **Guest data boundary (latest explicit correction, 2026-11-09 00:09):** “游客的数据我们不保存，我只是游客参与空投的gas，收益 等空投收益 数据要贡献到大盘里面 他的数据我们不保存”. Therefore guest detailed personal records are not uploaded or retained by a private cloud vault; only eligible anonymous aggregate contribution data is sent to the public dashboard. This later correction supersedes the earlier 2026-11-08 request that guest detailed bills also be uploaded to cloud.
- **Community time windows (user-confirmed 2026-11-09):** remove “今日” and the standalone “24小时热度” metric. Keep overall, last 7 days, last 3 days, and last 1 hour. A market coin's 24-hour price-change percentage is a separate market field and is not the cancelled community heat metric.
- **Community contribution default (latest correction, 2026-11-10):** every project in the official community catalog contributes eligible data by default; do not ask users to authorize each bill or wallet row. A private user-created project (`communityOfficialId = null`) remains excluded. If a private project is explicitly linked to an official catalog record, only qualifying records from that point onward may contribute; do not backfill earlier private bills. Contribution remains per wallet row and requires a supported network, that wallet's own valid Tx Hash, confirmed actual USD-denominated reward input, backend chain verification, replay protection, and the existing anonymous outbox. Public storage contains aggregates only, never the private bill, wallet address, Tx Hash, or account identity. This correction supersedes every earlier unchecked-by-default consent/opt-in statement in historical PR notes and implementation checkpoints. Actual realized rewards still require the user's manual confirmation; estimates never count as actual.
- **Crash regression (user-reported 2026-11-08):** the user reported that navigating to multiple areas (Airdrop navigation/site, coin/chain-browser search, wallet management, project management, and My) could close the entire app, while Micro Community worked. PR #103 fixed one confirmed WebView origin-rule crash at source level, but that does not prove every reported route is fixed. Keep a cross-screen physical-device crash regression gate open until every affected route is exercised on-device; do not mark the broad crash report resolved from the single WebView fix alone.

## 11.1 Current-release override for historical sync implementation notes (2026-11-11)

Older progress entries below describe code that was previously implemented for authenticated private cloud sync. They are historical implementation evidence only. The current release decision is local-only for all personal data: do not call those private-sync paths from current-release user flows, do not upload personal rows, and do not count the presence of those old endpoints/workers as an approved current feature. Preserve the source code for a future phase unless a separate safe removal is explicitly planned. The 2026-11-11 override in Section 0.1 is authoritative.

## 12. Verified implementation ledger (as of 2026-11-09)

### Merged foundation

- PR #87: corrected community Gas/reward comparison bars to exact proportional widths; zero-valued sides render no segment. Added Android unit coverage for zero, exact ratio, and invalid inputs; merged as `f400aa63d141210f21c210dbcbf9ee94ecc17c37` after Android and backend CI passed (run `42620976306`).

- PR #25: foundation/security.
- PR #27: empty API_BASE_URL workflow crash fix.
- PR #28: global search and public market BTC/ETH/SOL/BNB/XRP; coin tap opens detail.
- PR #29: Room accounting foundation, theoretical/actual separation, manual confirmation, P&L, pending bill deletion.
- PR #30 and #33: remove empty/obsolete files and empty login sync.
- PR #34: WebView TLS errors always cancel.
- PR #35: Reown/WalletConnect and guarded AndroidX WebKit DApp bridge.
- PR #37: project claim wallet selection.
- PR #38: project task ownership protection.
- PR #39: authenticated account-private cloud ledger API and Android sync/restore.
- PR #40: public community contribution API.
- PR #42: project ledger/community integration, local history, day/week/month summaries, outbox and background contribution sync.
- PR #43: read-only multi-chain wallet activity API.
- PR #44: wallet activity import into pending accounting.
- PR #45: Bitcoin wallet address/activity support; merged commit e259d9ec837b52ac2c75f8ea3f1e89884a8ab05d.
- PR #46: anonymous public community contributions; merged commit 85ac85e1729af303e41a16106cdf14aeb9dc95e1.
- PR #47: ledger-based personal achievement statistics and historical contribution anonymization migration; merged commit 8f96d9a7cbf1d24834494fcb5b6685094297d50c.
- PR #48: account/guest-scoped local community favorites; merged commit 9b0315b6753127a1a682b577641ac7daccb23754.
- PR #49: community ranking enhancement; merged commit 7dcf9fecf41bbb58bda0634a4d6e57599ad21506. The New ranking sorts by project catalog creation time.
- PR #50: verified global community star ratings; merged commit 3d8e9dff7db59471f63df4a6b2da9619e0c1efe6.
- PR #51: internal community project search by name, chain, and tags; merged commit 1902ecc74fc1307c3d6785842f55f6892aa6a3ff.
- PR #52: project task-name editing for guest-local and authenticated account tasks, with server ownership checks and name validation; merged commit 30b1c58849bbc4606b618cc5c74e0186d9e1892f.
- PR #53: project edit/status/delete controls for guest-local and authenticated account projects; merged commit 5e4b5ec97d602535bdfd0e20cee857deb45f17c5.
- PR #54: eight-hour WorkManager refresh for saved wallet activity, guest/account-scoped cache, and cached-first transaction display; merged commit f036e75a2df583cc56d0a5cecdca851adf041945.
- PR #55: wallet labels, tags, notes, Android edit controls, and owner-scoped private cloud updates; merged commit 5bb7b60083d0204c13b8b8d0a400f8c8aa5ecb1e.
- PR #56: authenticated private-ledger one-time retry and eight-hour WorkManager fallback; guest accounts are skipped; merged commit d23cca51377c241c05e3dc0fe866f3e9d408c111.
- PR #57: account identity changes trigger account-scoped screen reloads; stale requests are cancelled and account-specific editors/selections are cleared; merged commit c8545c70029f4ef82baf65b9ad231a4d432c1359.
- PR #58: persistent many-to-many project-to-wallet assignments; guests store assignments in account-scoped Room tables, logged-in users sync assignments to account-private API storage; project detail supports selecting multiple public addresses and prioritizes assigned addresses in the claim wallet picker; backend enforces project and wallet ownership; merged commit 3c8ffbf8731ca3c9c6f10095099840e45e477932.
- PR #59: dynamic community project catalog; backend PostgreSQL catalog seeded with the original seven entries, public active-project API, token-protected catalog management, Android dynamic catalog loading and tag derivation; merged commit 066703ad4e7bbf176435fd8f1aac814bea60cc12.
- PR #60: allowlisted RSS/Atom discovery candidate intake; separate candidate model/API, safe HTTPS feed allowlist, bounded parser, scheduled-script integration and source attribution; merged commit 1428e5af957d2d117cb10f151c6d0984e77981ac. No feed is enabled by default; Render Cron Job/environment configuration and source license/availability checks remain operational acceptance tasks.
- PR #61: public community project submission API and Android submission dialog; name + HTTPS URL, duplicate checks, rate limiting, known shorteners held for review; merged and CI passed.
- PR #62: community project cards display their stored URL and open-link action; merged commit e7879556a10d26ff94041d65eee90be708345982; Android and backend CI passed.
- PR #63: admin-token-protected discovery candidate queue, approve-to-catalog/reject actions, review timestamps/status, HTTPS/name revalidation, and duplicate linking; merged commit 1a88bef3f7baea3a4f79965c3840d6ee8a258a78; backend and Android CI passed.
- PR #64: end-to-end tests for candidate submission, admin review queue, approve/reject, persisted review metadata, duplicate-action conflicts, and admin-token protection; merged commit 0e31b0af2a0c9ab7958c09eba3d13a9f51e07bbb; backend and Android CI passed.
- PR #65: documented the user-confirmed project card/detail presentation.
- PR #66: compact community project cards with overall Gas/actual-reward comparison, honest empty state, and rating-derived reputation; merged commit 17171c81902dd44502ed3cac2eeba4500754f9e1.
- PR #67: documented the static snapshot architecture and user-confirmed four-window update behavior; merged commit b7d41176e56f2a412209f6d8c4db01c3f0edb55c.
- PR #68: initial backend snapshot-builder PR was closed as superseded; it was not merged separately. Its implementation was carried into PR #69.
- PR #69: backend four-window snapshot generation, anonymous aggregation, versioned object-storage publisher, Room-first cache, silent CDN revalidation, and waterfall detail integration; merged commit 25035c83e6f69f125d0b336170fdd0e0d5c93316 with Android and backend CI green. CDN/R2 and scheduled publisher remain unconfigured.
- PR #70: removed fabricated empty/50:50 comparison bars, made data presence depend on confirmed contribution count, corrected the stale five-window requirement to four windows; merged commit 0c57de441fcfb668dbb6e4d639ea68f9e6c443ee; Android and backend CI green.
- PR #71: consolidated the single-source product requirements, README boundaries, cross-module data-flow map, and dependency-driven roadmap; merged commit 1bc27b7a3ec78b45321891c3f68b82db5b91a1ec; Android and backend CI green.
- PR #72: explicit chain selection and checksum-aware address validation for EVM/TRON/Solana/Bitcoin; merged commit c4a2092d7a4a52edbd69525e408cade806846f99; Android and backend CI passed.
- PR #73: community data-contract cleanup across API, database, snapshots, and Android; merged commit bdca459794aced3c2ff39622b39222f26c0fb1ff; Android and backend CI passed.
- PR #74: transaction identity and account-scoped ledger storage hardening; merged commit 0574789e07176c3d7635e2445eccf53c686076ea; Android and backend CI passed.
- PR #75: currency-aware private ledger and public per-token snapshots; merged commit 33df51e0e03f9b25c905317ec7e63d135f7fe45f; Android debug build/unit tests and backend tests passed (Actions run 42582178910).

### Current audit findings

- Main-branch verification on 2026-11-09: PRs #83, #84, and #85 are merged; no open PRs were present. Latest main Android/backend CI run `42618469948` completed successfully. PR #85 merged at this main commit. Its privacy check is key-name based; follow-up hardening expands account/user identifier aliases. Production API/CDN configuration and an actual smoke run remain pending.

- Confirmed in main source: guest confirmed bills are written to a guest-scoped local personal-ledger cache and guest-scoped local P&L/project/interaction tables.
- Confirmed in main source: guest outbox batches use no Authorization header; logged-in batches use the user's bearer token.
- Confirmed in main source: guest wallet addresses are saved through the app-local cache; logged-in wallet addresses are loaded from the authenticated wallet API and cached under an account-specific key. Room project, P&L, interaction, pending-bill, and outbox records are scoped by token user ID or guest scope.
- Confirmed in main source: private personal-ledger endpoint requires authentication and scopes reads/deletes/upserts to the authenticated user. Android AccountingRepository.syncPersonalLedger() returns before any private cloud call when no token exists; PR #75 stores structured token breakdowns in the existing private-ledger extra JSON and preserves them during cloud restore.
- PR #77 hardens account-ledger sync against session changes during network requests: each run captures its bearer token, uses explicit authorization on private-ledger API calls, reads the captured token's local cache scope, and atomically merges cloud-canonical rows with latest local-only rows in a Room transaction shared with bill confirmation. It rejects restores beyond 10,000 rows before uploading or replacing local data. Android regression tests cover guest no-cloud behavior, account switching, concurrent local additions, cloud-canonical conflict handling, demo exclusion, and oversized-cloud protection.
- Wallet activity background refresh had a similar account-switch race: it captured only a guest/account boolean while the HTTP interceptor and cache keys consulted the currently active token. PR #80 binds wallet-list/activity requests and cache reads/writes to the exact token captured at worker start; guest requests explicitly suppress later account-token injection.
- PR #81 (merged) extends session binding to the foreground wallet-activity screen: observe token identity, cancel stale requests on account changes, clear old-account rows before rendering the new session, and test guest/account cache isolation. PR #81 merged as `8abdb407bd6eaf99603b0f8c7e14dee37d187a07`; Android and backend CI passed (Actions run 42595730174).
- PR #82 (merged) adds conditional static snapshot manifest requests using Room-persisted ETag/Last-Modified validators. Validators are stored only after every referenced snapshot is present and verified, so partial downloads are retried; malformed native amounts no longer create zero-valued phantom token rows. Android and backend CI passed.
- PR #83 (merged) adds a database/migration-aware API readiness endpoint and a manual deployment smoke workflow that verifies the production API plus all four HTTPS CDN snapshot windows without requiring or exposing credentials. PR #83 merged as `a2d59918bd75106fde7167dfe140734427121fdb`; Android build and backend tests passed (Actions run 42616401589). Production URL variables and a real deployed smoke run remain pending.
- PR #84 (merged) extends deployment smoke validation to enforce CDN cache headers (short-lived revalidating manifest, immutable one-year versioned snapshots), require ETag/Last-Modified on the manifest, require JSON responses, and reject cross-origin redirects. PR #84 merged as `b7c059cce9043759906785e726146d82f25af51d`; Android build and backend tests passed (Actions run 42617212905).
- PR #85 (merged) adds recursive privacy checks to the production snapshot smoke test, rejecting wallet addresses, transaction hashes, account/user identifiers, and private-ledger fields in public CDN payloads. PR #85 merged as `11c94078a50d0c08083c9cb2e18b8dc2868bf41b`; Android build and backend tests passed (Actions run 42618469948).
- PR #46 and PR #47 are merged: new public contribution rows store user_id as null; migration 0005 clears historical account links while preserving aggregate totals.
- Source audit confirms guest wallet addresses are stored in the guest-scoped app cache and authenticated wallet lists are fetched from the private wallet API and cached by account scope. Device-level app-data clearing and account-switch behavior still require real-device acceptance; production database backup/restore also remains unverified.
- Community implementation: PR #48 adds guest/account-scoped local favorites; PR #88 implements authenticated cross-device favorite state sync with timestamped deletion tombstones. Guest favorites remain local-only. PR #88 merged as 109b8bee8b629d0fd8d8d884eb954f232ae507a7; Android and backend CI passed (run 42623184111); real-device offline and multi-device acceptance remain pending. PR #49 adds a community ranking enhancement; its legacy metric output is scheduled for removal. PR #50 adds global 1–5 star aggregates for logged-in accounts after server verification against confirmed private-ledger participation; guest ratings remain local-only. PR #51 adds in-app search over the existing seven-project catalog; this does not search external websites. PR #59 has replaced the fixed seven-project list with a dynamic backend catalog and Android API loading. PR #60 adds RSS/Atom candidate intake and scheduled-script integration; candidates are not automatically promoted to verified projects. PR #61 adds public project submission and duplicate checks; PR #63 adds the protected review queue. Actual feed activation/source checks and moderation operations remain pending.
- Personal achievement: implemented in PR #47. Guest metrics read the guest-scoped local ledger; logged-in metrics read the authenticated private cloud ledger. Win rate uses only projects with confirmed ledger interactions.
- Task management now supports create, rename/edit, complete, and delete for guest-local and authenticated projects. PR #52 adds the edit flow and backend validation.
- Project management now exposes edit, status changes, and delete in the Android project list. PR #53 preserves confirmed ledger history when removing a project from the list. Ledger-derived project summaries remain read-only.
- PR #58 implements a separate persistent project-to-multiple-wallet assignment manager. Guest assignments are stored locally in Room under guest scope; logged-in assignments are stored in account-private backend records and cached locally under account scope. Project detail exposes multi-select controls, and the claim wallet picker prioritizes assigned addresses. Backend checks that private projects and all selected wallets belong to the authenticated user. This is complete in code; real-device verification of persistence, account switching, and claim-wallet ordering remains pending.
- PR #54 adds manual plus eight-hour background refresh of saved wallet transaction activity. Background snapshots are cached locally by guest/account scope and never auto-create ledger rows or public contributions.
- PR #89 introduces one app-level WorkManager dispatcher for community contributions, wallet activity, private-ledger sync, and authenticated favorites. The dispatcher runs every 15 minutes and fans work out into four independent unique queues, so a retry in one source cannot block another. Routine dispatch uses KEEP; explicit event/account changes append a forced follow-up. Per-source cadence remains 15 minutes for public contributions and 8 hours for wallet activity, private ledger, and favorites. Each task retains its own account/privacy boundary and retry result. Manual wallet refresh and foreground community cache revalidation remain separate user-facing flows. CI and real-device offline/account-switch acceptance are tracked independently.
- The Airdrop Hub has a dynamic backend catalog (PR #59), RSS/Atom candidate intake (PR #60), public project submission flow (PR #61), and project-card URL display/open action (PR #62). RSS records remain unverified candidates; no feeds are enabled by default and production Cron/environment setup is not yet confirmed. Public submissions accept a project name and HTTPS URL (official or third-party listing); unique non-shortener links currently enter the catalog after syntactic URL checks, while known shorteners are held as candidates. This is not proof that a domain is trustworthy; abuse monitoring and stronger moderation remain required. No submitted website is crawled. Discovery remains free-first: only stable, permitted free APIs/feeds/listing interfaces; user submissions are also supported. Telegram/Discord are optional only when free and authorized; paid X/API and paid data sources are out of scope. Check source access, terms, rate limits, and failure isolation before enabling any feed.
- Real-device acceptance for browser/wallet bridge, Android transaction import, guest data clearing, account switching, and cross-device restore remains pending. The static snapshot screens also need real-device cache/offline acceptance after CDN configuration.

### Status discipline

Use only these states: **complete in code**, **partial**, **in progress**, **pending verification**, **deferred**, **cancelled**. A PR merge/CI pass is code-level evidence only; device and production checks remain separate.


### Code-coupling map and next implementation order

The main data path is intentionally one-way at each boundary:

1. **Wallet/address layer:** user adds a public address -> account/guest-scoped wallet repository -> read-only chain activity refresh/cache.
2. **Accounting layer:** user selects a transaction or enters an interaction -> pending bill -> user manually reviews/enters actual Gas and actual reward -> one Room transaction updates the private ledger, project P&L, interaction history, and (only if eligible) public-contribution outbox.
3. **Private data layer:** every current-release user stores personal data locally only. Existing authenticated API/account-isolation code is dormant and retained for a future phase; no private ledger or personal record is uploaded.
4. **Public contribution layer:** local sessions may send only eligible official project IDs, anonymous aggregate Gas/reward values, provenance metadata, and an opaque event ID for idempotency. The server stores public aggregate rows with no account identity; idempotency prevents duplicate retries.
5. **Community presentation layer:** server-side scheduled jobs aggregate anonymous contributions into static JSON; Android Room cache renders first and CDN revalidation updates silently. Community UI must not bypass this path with live per-project aggregation.
6. **Project/task layer:** project and task ownership, project-to-wallet assignments, and ledger links are separate from public project metadata. A transaction is linked to a project only by explicit user selection.
7. **Discovery layer:** provider APIs/RSS candidates and user submissions populate project metadata only. They must never fabricate participation, Gas, rewards, ratings, or P&L.

Recommended next implementation order, based on shared-code dependencies:

1. **Public metric cleanup (PR #73 merged):** backend, snapshot, and Android public data contracts now expose confirmed monetary aggregates, star ratings, and catalog metadata. The New ranking sorts by catalog creation time.
2. **Transaction-to-ledger reliability (PR #74 merged):** duplicate transaction identities are isolated by account scope, public event IDs remain stable for server idempotency, demo rows are excluded from sync/contribution, fee-payer attribution is conservative, and manual confirmation is required.
3. **Currency-aware accounting (PR #75 merged):** native token amount/symbol/decimals and valuation timestamp/source now flow through chain activity, pending bills, private ledger, anonymous public contributions, and per-token static snapshots. USD valuations remain separate and unlike native units are never added together.
4. **Existing sync/restore hardening (PR #77 merged):** retain the token/account binding, explicit Authorization headers, token-scoped local cache access, atomic merge, and 10,000-row safety cap in the existing code. However, new-user registration and user-facing cross-device restore are deferred; do not advertise or rely on cross-device recovery in the current release.
5. **Discovery rights and operations (PR #78 merged):** require explicit source-level content-reuse approval for RSS and provider APIs, in addition to HTTPS host allowlisting. Do not enable feeds/APIs based only on public availability; keep sources disabled until their terms or written permission permit public display. PR #79 adds persistent source sync status, an admin-only status endpoint, and a daily scheduled workflow disabled by default.
   **PR #80 (merged):** bind wallet-list/activity requests and cache reads/writes to the captured guest/account session; guest requests suppress token injection after a session switch. Regression tests verify captured bearer and anonymous modes. PR #80 merged as `db24bcc8209acfa11305c91884b98551d6444131`; Android and backend CI passed (Actions runs 42594835182 and 42594866610).
   **PR #81 (merged):** apply captured-session handling to foreground wallet activity and test cache isolation across session identities.
   **PR #82 (merged):** implement conditional manifest revalidation and validate immutable snapshot/short-lived manifest cache policies.
   **PR #83 (merged):** add a manual production smoke test for API database/migration readiness and the complete four-window public CDN snapshot set.
   **PR #84 (merged):** validate CDN cache/conditional-request headers and prevent cross-origin redirects in deployment smoke checks.
   **PR #85 (merged):** recursively reject private wallet, transaction, account, and ledger fields in public snapshot payloads; production CDN smoke verification remains pending.

6. **Production and device acceptance:** configure database backups, CDN/R2, scheduled publishing, and end-to-end phone tests before claiming production readiness.
7. **Unified UI refinement:** defer broad visual redesign until the above core chains are complete.

### Wallet input validation implementation (PR #72)

- Wallet add UI asks the user to select EVM, TRON, Solana, or Bitcoin before entering an address; it no longer auto-detects the selected ecosystem.
- Android validates EVM hex shape, TRON Base58Check/version, Solana Base58 decoded length (32 bytes), and Bitcoin Base58Check or Bech32/Bech32m witness addresses. Guest local writes and authenticated remote writes share the same local validation.
- Backend create/update endpoints apply the same selected-chain validation. Legacy `/api/wallet/sync` retains strict address-only identification for backward compatibility; wallet serialization prefers stored chain metadata to avoid reclassifying Solana Base58 as Bitcoin.
- Validation checks encoding/checksum only, not ownership or live chain activity. WalletConnect-imported EVM addresses remain unchanged.

### Closed legacy pull requests

- PR #32 was closed as superseded after chain-specific wallet validation and Bitcoin address validation were implemented in merged PR #72.
- PR #36 was closed as superseded after its required ledger, community contribution, and accounting data-flow work was implemented in merged PRs #39, #40, #42, #74, and #75. Its remaining token-portfolio work is outside the confirmed product scope.
- Maintain one authoritative implementation on `main`; do not reopen or merge these legacy branches.



### User-confirmed zero-cost discovery refinement (2026-11-09)

- Use only genuinely free, stable, and permitted discovery sources. No paid APIs, paid RSS services, or paid platform plans. If no usable free source exists, skip that source rather than spending money or building brittle workarounds.
- Prefer free public APIs and RSS/Atom feeds only when their terms or explicit written permission allow the intended public reuse/display. A publicly accessible feed is not automatically licensed for republication. Use third-party airdrop listing pages as project URLs when useful; capture only fields that are actually available.
- The project directory's minimum card identity is display name + URL. Avatar/logo is optional and may use an available platform image or fallback. An official project website is NOT mandatory: a third-party airdrop platform listing URL may be the project's link.
- User submissions may provide a project name and any usable URL, including a third-party listing URL. Duplicate and URL-safety checks still apply.
- Telegram/Discord are optional, only if free and access is explicitly authorized/simple. X/Twitter is skipped if it requires paid API access or complex authorization.
- This supersedes any earlier assumption that every provider (Telegram, Discord, X, DropsTab) must be implemented. Source integration is conditional on free availability and practical reliability.


### Implemented discovery and submission slices

- PR #60 (merged): RSS/Atom candidate intake with HTTPS exact-host allowlisting, bounded parsing, source attribution, candidate API, and scheduled-script integration. No feeds are enabled by default; PR #78 adds explicit source-reuse approval gates for RSS and the existing provider API. PR #79 adds persistent last-attempt/last-success status and a disabled-by-default GitHub daily sync workflow.
- PR #61 (merged): public community project submission API and Android submission dialog. Required fields are project name + HTTPS URL; official websites are not mandatory, and third-party airdrop listing URLs are accepted. The backend normalizes URLs, checks name/URL duplicates against the catalog and RSS candidates, rejects unsafe URL forms, rate-limits submissions, and holds known URL shorteners as unverified candidates. It does not fetch or crawl submitted websites. CI passed; real-device verification and moderation/abuse monitoring remain pending.
- PR #62 (merged): show each catalog project's stored URL directly on its Android community card and provide an explicit open-link action. Missing URLs are labeled as awaiting supplementation; no URL is invented.
- PR #63 (merged): admin-token-protected candidate review queue, approve-to-catalog and reject actions, review timestamps/status, safe URL revalidation, and duplicate-to-existing-project linking. Android review UI remains deferred because the admin token must not be embedded in the public app.
- Project-list overall Gas/reward bar and project-detail waterfall layout above are confirmed requirements. PR #66 implements the concise project card. PRs #68–#70 implement the four-window static snapshot detail page and correct empty-data rendering
- Still pending: obtain/verify explicit reuse permission or a permissive license for any public feed before adding its source name to `AIRDROP_RSS_APPROVED_SOURCES`; configure `AIRDROP_DISCOVERY_SYNC_ENABLED` only after an approved source and database secret exist; real deployment workflow verification. Do not add paid or complex integrations.

### User-confirmed community static-newspaper snapshot mechanism (2026-11-09)

- **Public project-stat windows:** overall, rolling last 7 days, rolling last 3 days, and rolling last 1 hour.
- **Read-only UI:** community list and project detail have no pull-to-refresh, refresh button, swipe-triggered request, or other manual refresh affordance. Pull gestures only use normal elastic scroll/bounce behavior. The app reads cached snapshots and may perform a silent background network revalidation; users do not initiate it.
- **Server-side scheduled aggregation (Asia/Shanghai, UTC+8):**
  - Overall + last 7 days: once daily at 08:00.
  - Last 3 days: every 6 hours at 00:00, 06:00, 12:00, and 18:00.
  - Last 1 hour: every 5 minutes on the minute marks divisible by five (e.g. 10:05, 10:10, 10:15).
  - Persist UTC timestamps for ledger events; calculate exact rolling boundaries against a single UTC run timestamp, and expose the schedule/update timestamps in UTC+8. Each snapshot carries its own `update_time`.
- **Precomputed static distribution:** scheduled jobs aggregate all eligible confirmed public contribution/ledger facts server-side, then publish versioned JSON snapshots (overall, 7d, 3d, 1h) to static object storage/CDN (e.g. Cloudflare Pages/R2 or equivalent). App reads these static files; snapshot GET requests must not trigger SQL aggregation. Publish atomically: upload versioned payloads first, then replace a small manifest/index pointer only after all files are complete. Use ETag/Last-Modified and sensible CDN cache headers; never expose private ledger rows, wallet addresses, tx hashes, or user identifiers.
- **Android stale-while-revalidate cache:** Room stores the last successful JSON snapshot per scope/window (including project ID, payload/version, and update_time). On entering community list/detail, render Room immediately, then silently fetch the CDN manifest/snapshots in the background. If newer valid data arrives, upsert Room and update the UI without blocking or disruptive animation. If offline, CDN fails, or parsing/validation fails, keep displaying the last good cache without a blank screen or user-facing refresh error. First-ever offline use may show a clear no-cache placeholder.
- **Timestamp presentation:** under each non-overall comparison row show its own “更新于 …” timestamp. Overall may also show its generated time.
- **Data integrity:** snapshots aggregate only eligible anonymous public contributions and confirmed actual Gas/actual rewards; theoretical estimates, demo fixtures, private user details, and unconfirmed outcomes are excluded. Keep per-token amounts separate and label any USD conversion as an estimate/valuation. Missing data is shown as missing, not fabricated.
- **Operational boundary:** scheduled aggregation and CDN publication are backend/ops responsibilities. Do not add a public APK secret, do not calculate aggregate snapshots on app-open, and do not claim a production CDN/Cron is configured until the deployment is actually verified.
- **UI hierarchy (superseded by 2026-11-11 detail-card override):** concise project-list cards follow the latest library-list rules. Tapping opens the individual project detail page, where one unified animated card exposes exactly three visible tabs (总览、近3天、近1小时); no separately stacked period cards. Backend snapshot generation may retain overall/7d/3d/1h windows for compatibility, but 7d is not a visible detail-page tab.


### Static community snapshot implementation (PRs #67, #69, and #70 merged; #68 superseded)

- Backend precomputes overall, rolling 7d, rolling 3d, and rolling 1h snapshots from eligible anonymous public contribution rows.
- The scheduler computes due windows in Asia/Shanghai: overall + 7d daily at 08:00; 3d at 00:00/06:00/12:00/18:00; 1h every five minutes. Timestamps are stored/compared in UTC and presented in UTC+8.
- Snapshot payloads contain project catalog metadata, confirmed USD valuations, and token-separated native aggregates only. They do not contain wallet addresses, transaction hashes, user IDs, or private ledger rows. Gas and reward token amounts are grouped independently by token symbol and decimals; unlike native units are never added together.
- Versioned JSON objects are uploaded before the manifest is atomically switched. Android reads Room cache first, then silently revalidates the CDN using a dedicated unauthenticated HTTPS client restricted to the configured CDN host. Offline or invalid responses preserve the last good cache.
- Project list and detail screens use static snapshots rather than per-project live stats calls. The detail screen shows one unified three-tab card; the selected window's update time is collapsed to the card's lower-right edge. There is no manual refresh affordance in the micro-community. Empty/zero-denominator data is not represented by a fabricated balanced bar.
- PR #67 merged the architecture requirement; PR #68 was closed as superseded; PR #69 merged the actual backend and Android implementation as `25035c83e6f69f125d0b336170fdd0e0d5c93316`; PR #70 merged the empty-data correction as `0c57de441fcfb668dbb6e4d639ea68f9e6c443ee`. Android and backend CI passed for the merged implementation and correction.
- **Not production-complete:** the scheduled workflow is disabled until storage/CDN/database settings are configured. No production R2 bucket, public CDN endpoint, or scheduled publishing activation is claimed.

### Free scheduled snapshot publishing

- The snapshot dispatcher computes due windows in Asia/Shanghai: overall + 7d daily at 08:00, 3d at 00:00/06:00/12:00/18:00, and 1h every five minutes. The GitHub Actions cron trigger is currently paused; the schedule is not running until production configuration is verified and the cron is deliberately restored.
- Before restoring the GitHub Actions cron, configure repository variable `COMMUNITY_SNAPSHOT_PUBLISH_ENABLED=true` plus `DATABASE_URL`, `COMMUNITY_SNAPSHOT_S3_BUCKET`, `COMMUNITY_SNAPSHOT_S3_ENDPOINT_URL`, `COMMUNITY_SNAPSHOT_S3_ACCESS_KEY_ID`, `COMMUNITY_SNAPSHOT_S3_SECRET_ACCESS_KEY`, and `COMMUNITY_SNAPSHOT_PUBLIC_BASE_URL`. Until then only deliberate manual dispatch is available; missing configuration must never publish an empty manifest.
- Set `COMMUNITY_SNAPSHOT_BASE_URL` as a repository variable for Android builds after the public CDN endpoint is ready. No storage credential is included in the APK.
- Once restored, GitHub scheduled jobs are best-effort and can start late under platform load; the dispatcher computes due windows from Asia/Shanghai at actual execution time. This is not a real-time SLA.


### Transaction-to-ledger reliability (PR #74 merged)

- Pending bills, private interaction history, and public contribution outbox rows use account-scoped composite primary keys so the same chain transaction can exist independently in guest and authenticated scopes.
- Transaction identity remains stable across account scopes; the public event ID remains idempotent at the server boundary.
- Confirmed transaction imports are atomic: duplicate checks, private ledger updates, project totals, interaction history, outbox enqueue, and pending-bill deletion occur in one Room transaction.
- Only supported networks and non-demo transactions may enter the public contribution outbox. Demo/mock classification is shared by contribution eligibility and private cloud sync filtering.
- Chain fee attribution remains conservative: prefill only when chain data proves this wallet paid the complete fee; uncertain/shared fees stay blank for manual review.

### Currency-aware accounting (PR #75 merged)

- Each confirmed accounting event may retain a structured asset breakdown: kind (gas or reward), native amount, token symbol, token decimals, USD valuation (nullable), valuation timestamp, and valuation source.
- Imported Gas amounts use chain-native precision metadata (EVM 18, TRON 6, Bitcoin 8, Solana 9); a current USD quote is explicitly labelled with its provider and observation timestamp.
- Reward token amounts and symbols are entered or confirmed by the user. Unknown token precision or unavailable USD valuation remains null; never fabricate either.
- Private ledger asset detail remains local-only for all current-release users; only eligible anonymous aggregate contributions enter public contribution rows.
- Public snapshots group native amounts by kind, symbol, and decimals. ETH, BNB, BTC, TRX, SOL, and project tokens remain separate; USD valuations may be summed only as labelled USD estimates.
- Project detail displays native token breakdowns for each confirmed time window; project list cards retain concise USD-valuation summaries.
- PR #75 passed Android debug build/unit tests and backend tests before merge. Real-device verification of imported native precision, local persistence, account cloud restore, and token-separated public snapshot display remains pending.

### Private ledger sync/restore hardening (PR #77 merged)

- Capture the authenticated access token once per sync and use it for every private-ledger request; do not let a mid-flight account switch redirect requests.
- Read local ledger payloads under the captured account scope, not whichever account is active at completion.
- Merge canonical cloud rows and local-only rows atomically in Room, serialized against the local confirmed-bill transaction, so rows created during network work cannot be overwritten.
- Cloud records remain canonical for duplicate client record IDs; local-only and demo rows remain on the same account/device, and demo rows are never uploaded.
- Explicitly authenticate private ledger GET/PUT calls with the captured bearer token.
- Refuse cloud restore when more than 10,000 rows are present, before uploading or replacing local data.
- Unit tests cover guest no-cloud behavior, account switching during sync, concurrent local additions, canonical cloud conflicts, demo exclusion, and oversized cloud restore.
- PR #77 merged as `ebdd5a2c344b31522387e5c00234e246f27b3819`; Android debug build/unit tests and backend tests passed (Actions run 42587439108).
- Real-device account switching and offline retry acceptance remain pending. Cross-device restore is deferred from the current release, not an outstanding launch requirement.

### RSS content-reuse approval gate (PR #78 merged)

- A feed source must pass both exact HTTPS hostname allowlisting and explicit source-name approval via `AIRDROP_RSS_APPROVED_SOURCES`.
- The existing `web3-discover.vercel.app` API importer is also disabled by default and requires `AIRDROP_HUB_SOURCE_APPROVED=true` because its permission for third-party public-catalog republication has not been confirmed. The provider's separate GitHub mirror declares its dataset CC0, but its current snapshot is dated 2026-05-11; it is too stale to use as a live discovery source until freshness is restored.
- The approval variable must remain empty unless the source terms or written permission explicitly allow the intended public display/reuse of feed content.
- RSS discovery remains candidate intake only; feed records never become verified projects without admin review.
- Airdrops.io terms restrict the license to personal, non-commercial transitory viewing and prohibit copying, public display, and mirroring. AirdropAlert advertises an RSS endpoint, but its terms limit content use to personal purposes unless prior written permission is obtained. Neither source is approved for this app's public project catalog without further permission.
- Do not enable feeds merely because their RSS endpoint is public or because a feed-reader page advertises RSS. Keep user submissions and already permitted free APIs available.
- Production feed configuration and Cron activation remain pending; no new feed/API source is enabled by this change. User project submissions remain available.

### Discovery source sync health and daily operations (PR #79 merged)

- Persist one status row per configured discovery feed/API, keyed by exact source name + URL identity.
- Record last attempt, last successful sync, last successful item count, current status, and sanitized error class; never persist response bodies, credentials, or user data.
- A failed attempt updates failure status but preserves the last known successful timestamp and item count.
- Strip query strings/fragments from source URLs before storing or exposing source health metadata.
- Expose source health only through the admin-token-protected `GET /api/airdrops/discovery/sources/status` endpoint. Never embed the admin token in the Android APK.
- The intended daily sync time is 08:17 Asia/Shanghai. Its GitHub Actions cron trigger is currently paused; restore it only after repository variable `AIRDROP_DISCOVERY_SYNC_ENABLED=true`, a database secret, and at least one explicitly approved source are configured.
- Feed/API source approval gates from PR #78 remain mandatory; no source is enabled by this workflow automatically. User project submissions continue independently.
- PR #79 merged as `140166bf11be5215643291c102444dc43b278ee2`; backend tests and Android build passed (Actions run 42590876793 and main validation run 42591279205). Real production workflow activation and approved source setup remain pending.

### Legacy cached wallet-activity precision compatibility (PR #76 merged)

- Older cached wallet-activity JSON may not contain native token decimals. Treat missing precision as unknown in the DTO rather than defaulting every chain to 18 decimals.
- When precision is absent, infer only the documented native precision for known supported chains: EVM 18, TRON 6, Bitcoin 8, Solana 9. Unknown chains remain null; never fabricate precision.
- Preserve an explicitly supplied valid precision from the backend, and cover old-cache fallback and explicit-precision precedence with Android unit tests.
- PR #76 merged as `50e5dba160380637cf7a53acbd9e6a4e26615b98`; Android debug build/unit tests and backend tests passed (Actions run 42586518156).

### Public community metric scope (user-confirmed)

- Public project data shows only the four confirmed Gas-versus-actual-reward windows, per-currency amounts/valuations, and real star-rating aggregates.
- The New ranking uses project catalog creation time. Personal ledger history and internal eligibility checks remain intact; they are not public project statistics.
- Public contribution retries use an opaque event ID for idempotency only.

### Wallet chain selection and validation (PR #72 merged)

- Wallet creation requires explicit EVM / TRON / Solana / Bitcoin selection; no automatic chain guessing is added to the add-wallet flow.
- EVM hexadecimal address validation accepts either `0x` or `0X` prefix while retaining exactly 40 hexadecimal payload characters. Duplicate EVM addresses remain case-insensitive.
- Legacy address-format tests use a checksum-valid Bitcoin Base58Check fixture rather than a shape-only fake address, matching the stricter validator contract.
- PR #72's Android address fixture now uses a checksum-valid Bitcoin address; Android build/unit tests and backend tests passed before merge.

### Binding user correction — guest data ownership reaffirmed (2026-11-09)

This is the user's latest confirmed rule and overrides any contradictory text introduced in an earlier draft or implementation note:

- Guest private details (wallet addresses, Gas records, rewards, projects, chains, timestamps, and transaction records) are stored only in the app-private local database.
- Guest private details are NOT uploaded to a private cloud vault. Do not design or implement anonymous guest-vault endpoints, recovery credentials, or guest private cloud synchronization.
- Clearing app data or uninstalling the app removes the guest's private local records; there is no cloud copy to restore.
- Guest users may still contribute eligible, user-confirmed, anonymous aggregate Gas/reward/project participation data to the public community statistics. This contribution channel is separate from the private ledger and must never contain wallet addresses, transaction hashes, or detailed personal records.
- Existing authenticated ledger-sync code remains in the repository and account-scoped. Cross-device restore is deferred for the current release; do not promise that signing in on another device will recover private records.
- Never confuse public anonymous aggregate contribution with private personal cloud backup. The former is allowed for guests; the latter is not.


### Community favorites cloud synchronization (PR #88)

- Guest favorites remain in the guest-scoped Room database and are never sent to the API.
- Authenticated favorites are stored per user in a private API table. The API requires a bearer token and never exposes another account's rows.
- Favorite and unfavorite actions both persist timestamped state; unfavorite is a tombstone, so deletion can propagate to other devices rather than being resurrected by a stale local cache.
- Android merges local and cloud states per project using the latest update timestamp, sends the merged state with the bearer token captured at sync start, and applies the response only when it is not older than a concurrent local edit.
- Existing Room favorites migrate additively and are treated as pre-existing local state. Account switching uses account-scoped rows; guest state is never merged into an account.
- CI validates API authentication, account isolation, tombstone propagation, and stale-update protection. Real-device offline retry and multi-device acceptance remain pending.


### Global background sync coordination (PR #89)

- One app-level WorkManager dispatcher replaces independently registered periodic schedulers. It runs on a connected network every 15 minutes and schedules each source into its own unique WorkManager queue, preventing overlapping copies of the same source job.
- The coordinated sources are anonymous public contribution delivery, read-only wallet activity refresh, authenticated private-ledger sync, and authenticated community favorites reconciliation. They run independently so a failure in one source cannot block progress in another.
- Current-release background work retains the public contribution outbox and read-only wallet-activity refresh cadence. Private-ledger and favorite cloud-sync workers are disabled; their historical implementations remain dormant for a future phase.
- A task that is not yet due exits successfully without network calls. The dispatcher does not change manual refresh behavior.
- All chain requests require network connectivity. Existing event-driven ledger/contribution enqueue entry points now request the global chain, rather than creating parallel standalone worker schedules.
- Guest private ledger data is never uploaded; guest public contribution remains anonymous and idempotent; guest favorites remain local-only. Wallet activity remains bound to the session token captured when its worker starts. Private ledger and favorites remain authenticated and account-scoped.
- Each source queue uses unique-work KEEP for routine dispatch; explicit event/account changes append a forced follow-up behind any in-flight source job, so new ledger entries or account switches are not lost when a worker is already running. A failed source retries independently and does not block other sources.
- Unit tests cover first-run, interval boundary, not-yet-due, and clock rollback cadence behavior. Real-device background execution, offline retry, battery optimization, and account-switch acceptance remain pending.

### GitHub Actions quota and duplicate-build control (2026-11-09)

- The Android/backend verification workflow runs automatically for pull requests targeting `main`; it no longer runs a second full build on the merge commit push. A successful PR validation is the required code gate before merge.
- Documentation-only changes (`docs/**` and Markdown files) are excluded from automatic full Android/backend CI. Any PR that also changes application/backend code still receives the full verification.
- Workflow concurrency is grouped by GitHub ref and cancels superseded runs, so repeated pushes to the same PR branch do not leave obsolete full builds consuming runner minutes.
- Manual `workflow_dispatch` remains available for deliberate full verification.
- This reduces redundant runs; it does not delete historical workflow records or change the repository's GitHub Actions billing/retention settings.
 
### Dormant scheduled publishers while production configuration is absent (2026-11-09)

- The five-minute community snapshot cron and daily discovery cron are dormant until their required production storage/database and approved-source settings are configured. While dormant, keep manual `workflow_dispatch` available for deliberate operator runs, but do not create recurring no-op workflow history.
- Restore the community five-minute schedule only after `COMMUNITY_SNAPSHOT_PUBLISH_ENABLED=true` and all database/object-storage/public-CDN settings are configured and verified.
- Restore the discovery daily schedule only after an approved source with explicit content-reuse permission is configured and the database secret is present. Never enable sources based only on a publicly reachable feed.
- Workflow-only and documentation-only pull requests do not consume the Android/backend build workflow; application/backend changes continue to receive the full PR verification.


### Production readiness smoke-check hardening (PR #92 merged, 2026-11-10)

- The manual deployment-readiness smoke check bounds every JSON response to 10 MiB before decoding, preventing a malformed or unexpectedly large API/CDN response from exhausting the runner.
- The bound is enforced with a read limit of 10 MiB + 1 byte so oversized payloads are rejected deterministically; ordinary API health, manifest, and snapshot checks retain their existing HTTPS-origin, schema, cache-header, timestamp, and privacy validations.
- Corrected operational documentation: community snapshot and discovery cron triggers are paused, not merely job-gated; their UTC+8 schedules describe intended behavior after explicit configuration and deliberate restoration.


### Module-scoped CI to protect free Actions quota (PR #93 merged, 2026-11-10)

- Pull-request changes are classified by module before expensive jobs start.
- Android/Gradle changes run Android unit tests and debug APK assembly; backend/database/deployment changes run backend tests with PostgreSQL. A change touching both runs both suites.
- Workflow-only, documentation-only, and unrelated root-file changes do not start either expensive suite automatically.
- Manual workflow dispatch exposes `both`, `android`, and `backend` scopes for deliberate verification.
- Concurrency still cancels superseded runs on the same ref; merge pushes do not repeat successful PR builds.


### RSS discovery bounded streaming (PR #94 merged, 2026-11-10)

- Approved RSS/Atom feeds are fetched with redirects disabled and streamed in chunks. Enforce the 2 MB cap while receiving bytes, rather than buffering the full untrusted response and checking size afterward.
- Reject an oversized declared Content-Length before reading the body, enforce the same cap during streamed reads, and keep a parser-level cap for direct callers.
- A feed exceeding the limit is recorded as a failed source attempt; it cannot block other approved sources or create partial candidates.
- This does not enable any feed. Source allowlisting and explicit content-reuse approval remain mandatory; feeds stay disabled until production configuration and rights review are complete.


### PostgreSQL backup and restore drill (PR #95 merged, 2026-11-10)

- Add a manual-only GitHub Actions workflow to create a custom-format PostgreSQL backup, restore it into a separately provisioned empty database, and verify the Alembic revision and public table set.
- Require two independent repository secrets: BACKUP_SOURCE_DATABASE_URL and BACKUP_RESTORE_DATABASE_URL. The restore database name must end in _restore_check, differ from the source, and be created empty from template0 so the dump can recreate the public schema.
- The restore command intentionally does not drop databases or use pg_restore --clean; a non-empty target fails rather than being overwritten. Provision a dedicated empty target before running the drill.
- Database credentials and raw server output are never printed. The workflow is not scheduled and does not run against production unless an operator explicitly configures the secrets and dispatches it.
- Code and tests are complete only after CI; a real restore drill remains pending until the two secrets and isolated restore database are configured and the manual workflow succeeds.


### Readiness test follows the actual migration head (PR #96 merged, 2026-11-10)

- The readiness endpoint already compares the database Alembic revision with the current migration head. Its regression test now derives the expected revision from the repository's real migration directory instead of hard-coding an older migration ID.
- This keeps the deployment readiness test valid as future migrations are added and continues to assert that a database behind the code head returns HTTP 503.
- PR #95 added a manual backup/restore drill; actual execution remains pending until the isolated restore database and repository secrets are configured.


### Deployment readiness blocks unsafe redirects before following them (PR #99 merged, 2026-11-10)

- Validate every redirect target inside urllib's redirect handler before sending the redirected request.
- Reject redirects to another hostname, port, or non-HTTPS URL at the redirect boundary; do not wait until after contacting the redirected server.
- Same-origin HTTPS redirects remain supported. Regression tests cover cross-origin rejection, HTTPS downgrade rejection, same-origin acceptance, and oversized-response rejection through the opener-based request path.
- The checker remains unauthenticated and accepts only configured public HTTPS base URLs.
- PR #99 merged as `16026d8576b7e807451ee5cab68f698748a75494`; backend CI passed on final head `313db39fef2e664702a47bf78caf6b073a816186` (run `42666329837`), and Android build was correctly skipped.


### Public project submission URL safety hardening (PR #101 merged, 2026-11-10)

- Validate DNS hostname labels and total hostname length before accepting a submitted project URL; reject malformed labels, numeric-only top-level labels, control characters, backslashes, credentials, IP literals, and invalid ports.
- Keep ordinary HTTPS domains on the immediate-publication path. Internationalized/punycode domains and non-standard HTTPS ports are treated as suspicious and held in the existing admin review queue; known shorteners remain held as well.
- No submitted URL is fetched, crawled, or resolved through DNS. Manual review is a moderation safeguard, not a claim that the destination has been verified.
- Regression tests cover malformed domains, IDN review, custom-port review, and preservation of normal submission behavior.
- PR #101 merged as `e4c63e88b4f6c341d4fe708c83c5374789049b41`; backend CI passed on final head `d6cff05214f0c06755ab4fc56ad73b5e086f41bf` (run `42666994138`), and Android build was correctly skipped.


### Removal of the cancelled wallet asset overview (2026-11-10)

- The user had explicitly cancelled an OKX-style wallet asset/portfolio overview. The wallet detail screen no longer exposes an asset tab or requests token balances/asset valuations; it opens directly on read-only chain activity and the existing pending-accounting flow.
- Remove the unreachable legacy “check wallet” asset lookup screen, automatic address-detection helper, asset aggregation UI/repository, and TRONSCAN token-asset overview adapter. These were not part of the confirmed airdrop-specific wallet-address workflow.
- Preserve multi-chain address management, explicit chain selection and validation, read-only transaction history, conservative Gas attribution, manual pending-bill confirmation, and wallet-to-project associations. Do not remove or weaken those flows.
- Code cleanup is subject to Android CI; real-device wallet-detail/transaction-import acceptance remains pending.


### Wallet and personal project ledger alignment (PR #103, 2026-11-10)

- Remove the cancelled OKX-style wallet asset/portfolio view and its unreachable legacy asset-query/address-autodetection code. Saved wallet detail opens directly on read-only chain activity and pending accounting.
- Preserve explicit chain selection/validation, multiple addresses per chain, read-only transaction history, conservative Gas payer attribution, and manual bill confirmation.
- Project day/week/month summaries retain USD valuation totals and additionally aggregate confirmed native Gas/reward amounts by network, token symbol, and decimals. Native amounts from different networks or currencies are never combined; an incomplete USD valuation group is not displayed as a complete USD total.
- The first PR #103 Actions run (42671282759, head 8385865269f9c59f8d11f978a3b879ab71aefe69) ended with a workflow-level failure and no reported jobs/check-runs. It is not valid Android verification and must not be merged until a fresh, attributable Android test/build succeeds.
- Real-device wallet activity, transaction import, account switching, and native-currency display acceptance remain pending.

- Pending-bill confirmation now starts with the actual reward field empty; the user must explicitly enter a finite non-negative amount (including explicitly typing 0 when the realized reward is zero). Gas is prefilled only when a positive, attributable chain valuation exists; otherwise the user must enter the actual amount before confirmation. Input-policy unit tests cover blank, invalid, negative, non-finite, and explicit-zero values.


## 13. User-confirmed 15-batch delivery plan (2026-11-10)

The user explicitly requires development to proceed as **15 substantial functional batches**, not hundreds of tiny edits or PRs. Each batch must cover a connected end-to-end capability, compile/test, fix failures in the same batch until successful, then proceed. The user should not be asked to operate GitHub Actions or select workflow inputs. Reuse already completed and verified implementation; do not redo it merely to fill a batch.

1. **Wallet detail and accounting confirmation** — remove cancelled asset overview, preserve multi-chain address management and read-only activity, transaction-to-pending-bill flow, reliable Gas attribution, explicit actual Gas/reward confirmation, native-asset-aware project ledger summaries. Implemented and merged in PR #103 (`da5d7a862a067982669f87cf2377aba08b77ce48`); exact-head Android unit tests and debug APK build passed in run `42676514953`. Real-device acceptance remains pending.
2. **Multi-chain wallet and activity lifecycle** — address CRUD/tags/notes/project associations, supported chain queries, transaction normalization/deduplication, pending import and user-selected project association. Existing CRUD, chain routing, project binding and idempotent pending import are present; PR #104 closes a TRON provider duplicate/order edge case and corrects wallet-list copy.
3. **Built-in browser and DApp connection** — WebView navigation/TLS, Reown session lifecycle, origin validation, account/address selection, project association and read-only security boundary.
4. **Personal accounting end-to-end** — pending/confirmed lifecycle, actual versus theoretical Gas/reward, duplicate prevention, deletion, P&L, native-currency breakdown and day/week/month history.
5. **Project and task management** — project state, task CRUD/progress/completion, ownership and wallet/ledger associations.
6. **Guest local data and retained account infrastructure** — local-only guest private data and existing account isolation/sync code. New registration and cross-device restore are deferred from the current release; retain the existing code without presenting those capabilities as available.
7. **Anonymous public contribution** — eligibility, privacy minimization, idempotency, retries, exclusion of estimates/demo/unconfirmed rows and separation from private ledgers. Batch 07 plus transaction-verification extension are merged in PR #109 and PR #119; exact-head Android/backend CI passed for PR #119 (run 42716740435, head 5c62468). Live EVM provider/deployment validation and Render secret configuration remain pending.
8. **Community snapshot data pipeline** — four windows (overall/7d/3d/1h), scheduled aggregation, static JSON publication, version manifest and cache integrity. Code is implemented in PR #69 and tier-aware aggregate fields were extended in PR #119; production CDN/R2, database variables, and scheduled publishing remain pending.
9. **Community UI and social metrics** — waterfall project detail, rating eligibility/reputation, exact Gas/reward comparison, rankings, favorites and privacy. Existing base UI is implemented across PRs #48–#70 and #88; PR #120 adds three-tier Gas provenance bars to project cards and all four detail windows and is merged with passing Android CI.
10. **Project discovery and submissions** — safe user submission/deduplication/moderation, approved free data providers only, attribution and source-health reporting; no arbitrary-site crawler or paid source. Code is implemented across PRs #60–#63, #78–#79 and follow-up hardening; source-rights approval and scheduled production activation remain operational gates.
11. **Global search, market dashboard, and unified asset detail** — BTC/ETH dashboard, BTC/ETH/SOL/BNB/XRP market data, 300 ms local-first search across coin/DApp categories, hot-coin cache, search history, unified EVM/Solana/Bitcoin asset model and limited per-wallet asset detail. Core code merged in PRs #121 and #122; exact-head Android/backend CI passed. Real-device acceptance and free-provider coverage limitations remain.
12. **Background sync, Room cache, and reliability** — WorkManager coordination, 15-minute SWR for market/search/asset query data, 30-second per-key manual refresh throttle, cache metadata/updated labels, provider node rotation, bounded exponential backoff, offline queue and account-scope safety. Cross-device private-ledger recovery is deferred. Community snapshot schedules remain independent.
13. **Backend/API/data integrity** — authentication/authorization, schema/migrations, idempotency, validation, limits, tests and end-to-end contract consistency.
14. **Production operations and recovery** — CDN/storage/Cron configuration, readiness/smoke checks, backup/restore drill and rollback evidence.
15. **Unified navigation/UI and final regression** — bottom navigation order/state restoration, home dashboard/search integration, unified asset cards and chart behavior, one consolidated UI refinement after core flows, full Android/backend regression, APK artifact and acceptance checklist.

Batch numbering is a delivery grouping, not permission to reintroduce cancelled scope. Items already complete in code and validated are marked complete and carried forward; production configuration and real-device acceptance remain pending until actually verified. A passing CI run is not a substitute for deployment or device acceptance.

## 14. Batch execution and validation rules (user-confirmed)

- Work in one substantial batch at a time. Do not create a PR or commit for every small code adjustment. Related Android, backend, database, API, UI and tests should be developed together when they form one user workflow.
- At the end of a batch, run the relevant full compile and automated tests. If they fail, diagnose and repair within the same batch; do not start the next batch while a code failure remains.
- Minimize free GitHub Actions usage: do not rerun builds just to poll status, do not build Android for docs/backend-only changes, and do not run backend tests for Android-only changes.
- The user should not be asked to click Actions pages, select workflow scopes, copy logs, or manually manage development steps. Prefer automatic PR validation and report blockers plainly when GitHub itself prevents job creation.
- Merge only after the latest exact PR head has successful required validation. Never claim compilation or tests succeeded without the corresponding run evidence.
- Maintain the single source-of-truth progress ledger in this file. Each batch entry records implementation state, exact commit/PR, CI evidence, and any remaining deployment/device acceptance.
- The 15-batch plan does not imply 15 tiny PRs; use one coherent PR per substantial batch where practical. Existing completed work is reused rather than rebuilt.

### Batch 01 current state — wallet detail and accounting confirmation

- Implementation and validation fixes are on PR #103 branch `cleanup/cancelled-wallet-assets`; current head is tracked by the PR. Initial feature implementation commit: `193db78a92b37a5dd40238819260fe090b16f4f1`.
- The code removes the cancelled wallet asset overview and unreachable legacy asset lookup/address-autodetection code; it retains read-only wallet activity and pending accounting.
- Project period summaries now separate native Gas/reward amounts by network, symbol and precision; incomplete USD valuation groups remain explicitly incomplete.
- Pending bill confirmation requires an explicit finite non-negative actual reward, with zero accepted only when entered; Gas is prefilled only when a positive attributable valuation exists.
- Unit tests were added for currency/network separation, incomplete USD valuation and explicit actual-amount input.
- PR #103's earlier pull_request runs (42671282759, 42672851431, 42673160167) failed before jobs were created. A temporary branch-scoped `push` trigger was used to obtain an attributable Android run; it is removed in the next coherent change.
- Run `42675076534` created the Android job successfully. Main Kotlin compilation and `:app:assembleDebug` succeeded, but unit-test compilation initially failed because nullable `Double?` values were passed to a non-null `assertEquals` overload in `AccountingInputPolicyTest.kt` lines 20–21; the assertion was corrected.
- The test now asserts non-null explicitly and compares the unwrapped numeric values. Final exact-head run `42676514953` on PR #103 head `b1e6da022d2d626067f930dd7a097ea99954b18f` passed Android unit tests and debug APK assembly; the PR was squash-merged as `da5d7a862a067982669f87cf2377aba08b77ce48`.
- **Batch 01 code/CI complete:** PR #103 merged after exact-head Android tests and debug APK build passed. **Still pending:** real-device wallet activity/import/account switching/native-currency acceptance.


### WebView global-search crash and DApp bridge origin rules (fix in PR #103)

- Root cause confirmed in `DappProviderBridge.HTTPS_ORIGIN_RULES`: `https://*` is not a valid AndroidX WebView `allowedOriginRules` entry and can throw when registering the message listener. The global search route opens its result in the built-in browser, so the malformed bridge rule caused the navigation/search crash.
- Replace it with the WebView-supported single-token wildcard `*`. Do not broaden the bridge's actual authority: every incoming request is still required to originate from the trusted top-level HTTPS page, and the current page host/port must match the message source.
- Add a top-frame guard to the document-start provider script so it does not install the provider in iframes. The message listener also rejects non-main-frame messages.
- Keep the existing `WEB_MESSAGE_LISTENER` and `DOCUMENT_START_SCRIPT` feature checks before registering the bridge. On unsupported WebView providers, normal web navigation remains available but the DApp bridge is not installed; never fall back to an unrestricted legacy JavaScript interface.
- Add Android unit tests for the exact wildcard syntax and iframe exclusion. The existing runtime origin check continues to require the same HTTPS host and port.
- Exact-head Android unit tests and debug APK build passed in run `42676514953`; PR #103 merged as `da5d7a862a067982669f87cf2377aba08b77ce48`. The source-level crash fix is in main; real-device global-search/browser navigation acceptance remains pending.


### Batch 02 current state — multi-chain wallet and activity lifecycle

- Audit confirms wallet CRUD, metadata (name/tags/notes), explicit ecosystem validation, multiple saved addresses, EVM multi-network lookup, TRON/Bitcoin/Solana lookup, project-to-wallet assignments, pending-bill import, and transaction identity/idempotency are already implemented and covered by Android/backend tests. Reuse these features; do not recreate them.
- A concrete gap was found in the TRON activity adapter: raw provider rows were returned without validating, ordering, or deduplicating transaction IDs before receipt lookups. Duplicate rows could waste receipt requests and produce duplicate Compose list keys.
- PR #104 normalizes TRON provider rows before receipt lookup: validates 64-hex transaction IDs and positive timestamps, deduplicates case-insensitively, retains the newest duplicate, sorts newest-first, and enforces the requested limit. Tests cover duplicate casing, ordering, invalid rows, and limits.
- Wallet-list copy is corrected to describe public address management and read-only transaction history, not the cancelled token-asset overview.
- The temporary PR #103 branch-only push trigger is removed; the normal module-scoped pull-request workflow remains.
- PR #104 merged as `981076abd6b3a911524045a0a2d50eabb83db4d1`; exact-head Android unit tests, debug APK assembly, and backend tests all passed in run `42678672848`.
- **Batch 02 code/CI complete.** Real-device checks for all four ecosystems, provider partial outages, project binding, and duplicate imports remain pending.


### Batch 03 current state — built-in browser and DApp connection

- Main-branch audit confirmed the built-in WebView, HTTPS-only navigation, explicit cross-origin confirmation, TLS-error cancellation, JavaScript/file/permission restrictions, AndroidX WebMessageListener origin checks, Reown/AppKit external-wallet connection, account/session generation tracking, and read-only public-address exposure already exist.
- The audit found a product-boundary violation: the DApp bridge still routed `personal_sign`, typed-data signing, and `eth_sendTransaction` requests to the external wallet. The authoritative product requirement explicitly excludes signing and transaction submission in this app version.
- This Batch 03 change removes the wallet-action confirmation/relay path and its unused RPC coordinator, explicitly blocks signing and transaction-submission RPC families, and corrects the browser security notice. User-approved external-wallet connection and public account/address reads remain.
- Regression tests cover blocked signing/transaction methods and ensure account/chain read methods are not classified as prohibited.
- PR #105 merged as `8a2dbf5ed9c4b07d752d323189b6e6acf1b2dd4f`; exact latest-head Android unit tests and debug APK assembly passed in run `42680554041` on `cb0da93b005ff79583474c947d5676f1e9156fb1`; backend tests were correctly skipped because this was Android-only.
- **Batch 03 code/CI complete.** Real-device WebView navigation, cross-origin prompts, Reown connection/session expiry, and DApp compatibility remain separate acceptance checks.


### Batch 04 current state — personal accounting end-to-end

- Audit confirms the existing Room transaction atomically writes confirmed ledger JSON, project totals, user-project totals, interaction history, public-contribution outbox, and removes the pending bill. The flow requires explicit finite non-negative actual Gas/reward values, keeps expected reward unknown when no estimate exists, prevents duplicate transaction imports, and keeps guest/private-cloud data separated.
- Existing cloud ledger API is authenticated and account-scoped, uses idempotent `(user_id, client_record_id)` upserts, stable timestamp/id pagination, and retains expected/actual USD values plus native-asset details in `extra`.
- A confirmed-history display gap was found: expected Gas was retained in the private ledger payload, but `UserInteractionEntity` and the project history UI exposed only actual Gas/reward. Expected reward is legitimately unknown unless a source provides it; unknown must not be rendered as zero.
- Batch 04 adds nullable expected Gas/reward fields to Room interaction history with a data-preserving 14→15 migration; confirmed rows copy a positive known expected Gas estimate and leave expected reward null when unavailable. Project period summaries and each history row now show expected values separately from actual realized values. Period estimate totals are displayed only when every interaction in that period has a valid estimate.
- Added unit coverage for complete versus incomplete estimate aggregation. Existing local atomic confirmation, account-scoped cloud sync, public contribution eligibility, duplicate prevention, and native-asset breakdown are retained.
- PR #106 merged as `de2e4e8a2f3774a6d93338eb6ea7a126eefd2a84`; exact-head Android unit tests and Debug APK assembly passed for head `ba9f3ae7fc63c49b22877a88a28da5c7a875809c` in Actions run `42682557361`. Backend tests were correctly skipped for this Android-only batch.
- **Batch 04 code/CI complete.** Real-device accounting, account switching, and cross-device restore acceptance remain required.

### Batch 05 current state — project and task lifecycle

- Existing code already supports guest-local and authenticated project create/edit/status/delete, task create/rename/complete/delete, task progress display, account-owned backend tasks, and project-to-wallet associations. Reuse these flows rather than rebuilding them.
- Audit found guest project writes did not enforce the backend's status, URL, and amount contract consistently; guest project creation also dropped optional social URL, deadline, and amount fields. Guest task rename did not independently enforce the backend 240-character limit.
- Project deletion removed the project list row but left its local task cache and project-wallet assignments behind. Backend project deletion also left assignment rows because their project key is intentionally not a foreign key; confirmed P&L and interaction history must remain intact.
- Batch 05 hardens guest and authenticated project-write parity: trim and validate project name/description/status, require non-negative finite bounded amounts, and accept only valid HTTPS URLs without embedded credentials for official, claim, and social links. Guest creation now preserves every ProjectWriteRequest field.
- Project deletion clears the deleted project's task cache and local wallet assignments while preserving confirmed ledger/P&L history; backend deletion removes account-owned assignment rows in the same transaction before deleting the project.
- Task rename enforces the same 240-character limit locally as the authenticated API. Regression coverage exercises project write validation/parity, task length, task lifecycle/ownership, project deletion cleanup, and URL safety.
- Initial backend CI exposed a test-fixture issue: the new test helper generated a 23-character username, exceeding the API's 20-character limit. The helper is corrected to generate a valid 15-character username; this was a test setup defect, not an application failure. A follow-up code review also caught that the repository-level task rename guard had not been inserted even though the regression test was present; the 240-character check is now added. The corrected exact-head CI is pending. The first Android run then found a Kotlin string-template typo (`$label必须` parsed as an unresolved identifier); the message now uses explicit `${label}` interpolation.
- PR #107 merged as `32edbe4b0842d75bde668136ca63c72bbda2f9dd`; exact-head Android unit tests, Debug APK assembly, and backend PostgreSQL tests all passed for head `19a518d8e22d25cc80ee3f5bb8e3247fbd284ba9` in Actions run `42685857484`.
- **Batch 05 code/CI complete.** Real-device task progress, guest/account switching, and project-wallet association cleanup remain required.

### Batch 06 current state — guest local and authenticated private cloud data

- Existing implementation already keeps guest private ledgers local-only, stores authenticated ledgers under the account scope, uses encrypted Android Keystore-backed access-token storage, binds sync requests to the captured bearer token, merges cloud-canonical and concurrent local-only rows inside a Room transaction, retries offline failures, and refuses oversized cloud restores before replacing local data. Existing Android tests cover guest no-cloud behavior, account switching, concurrent local additions, cloud conflict resolution, demo exclusion, and the 10,000-row overflow guard; backend tests cover login requirements, per-account isolation, idempotent upserts, deletion ownership, and stable pagination.
- Audit found one cross-device data-integrity gap: Room's confirmed interaction history preserves unknown expected Gas as `null`, but the private-cloud DTO, API schema, and database model required a numeric value. `CacheDatabase.confirmPendingBill` therefore uploaded an unknown expected Gas estimate as `0`, and cloud restore could no longer distinguish unknown from a known zero.
- Batch 06 makes expected Gas nullable end-to-end while preserving actual Gas as required. New confirmations upload a positive finite expected Gas estimate or `null`; explicit known zero remains representable when supplied by a valid client. Unknown expected Gas and expected reward both remain `null` through authenticated upsert and restore.
- Additive migration `0016_nullable_expected_gas` drops the database NOT NULL/default-zero constraint without rewriting historical rows. Existing stored zero values are preserved because their original meaning cannot be reliably reconstructed; new records no longer fabricate zero for unknown estimates.
- Regression tests cover null-versus-explicit-zero API round trips, Android cloud upload/merge preserving null, and multi-page cloud restore with 501 records. Existing guest/account isolation and conflict-safety tests remain part of the same validation run.
- PR #108 merged as `f440919848aa26986bf9468dd80afbe296c34270`; exact-head Android unit tests, Debug APK assembly, backend PostgreSQL tests, and migration-chain validation passed in Actions run `42687551643` on head `d1be458a2f7267ae671feb8cf0650966a05a1e62`.
- **Batch 06 existing code/CI complete; product scope narrowed for current release.** Guest local data and existing account-isolation infrastructure remain. New-user registration and cross-device private-ledger restore are deferred; do not treat restore as a current-release acceptance requirement. Real-device guest data clearing and any retained account-switching flow still require acceptance.

### Batch 07 current state — anonymous public contribution

- Existing contribution flow inserts an outbox row in the same Room transaction as confirmed accounting, accepts guest and authenticated scopes, sends only project ID + opaque event ID + confirmed Gas/reward aggregates + token-separated asset breakdown, and never sends wallet address, transaction hash, private ledger rows, or account identity. Backend explicitly persists `user_id = NULL`, aggregates only the first insert, accepts identical retries idempotently, and returns 409 when the same event ID is reused with different project/amount data.
- Existing eligibility checks reject demo/simulated transactions, unknown networks, and placeholder wallets; only explicit bill confirmation creates an outbox row. Existing tests cover guest contribution, authenticated anonymity, aggregate idempotency, conflict detection, and demo exclusion.
- Audit found a retry starvation edge case: Android treated every HTTP error as transient. A backend 409 is a permanent event-ID/payload conflict, so the worker retried it indefinitely and returned before processing later entries in the same outbox batch.
- Batch 07 adds a Room `terminalFailure` marker with an additive 15→16 migration. On HTTP 409 the worker retains the failed row and diagnostic error, excludes it from future automatic batches, and continues processing subsequent contributions. Network errors, rate limits, and server failures retain retry behavior. The backend conflict response and no-double-counting semantics are unchanged.
- Regression tests distinguish permanent 409 conflicts from retryable 408/429/5xx responses; existing backend idempotency and anonymous-storage tests remain in the same validation suite.
- PR #109 merged as **0b131113113d6299e5a80724bec06c6e0cd4cf54**; exact-head Android unit tests and Debug APK assembly passed in Actions run **42688621980** on head **103e7638f9d171a12fa8559dbce239d27197744a**. Backend tests were correctly skipped because this batch changed Android only.
- **Batch 07 code/CI complete.** Real-device guest/account outbox retry, offline recovery, and public aggregate visibility remain required.

### Priority follow-up discovered during the 2026-11-10 code audit — requirements captured, implementation partial/pending

- **Community-to-personal adoption UI is present in the current main code and should not be rebuilt:** `CommunitySquareScreen` already shows a compact per-card add/check affordance; `MicroCommunityScreen` already observes the joined-project state and shows the add/already-added state on the detail page; `CacheStore.joinCommunityProject` reuses the stable `community:<official_id>` local project identity; `ProjectsScreen` already shows the “待记账” reminder from `createdAt` and `interactionCount`. Retain and regression-test these paths. The older statement that these affordances were missing is superseded by the current code audit.
- Do not add a duplicate project membership table. Reuse the existing Room `UserProjectEntity`, `CacheStore.joinCommunityProject`, and project-wallet assignment path.
- A **project-scoped direct “记一笔” dialog and atomic multi-wallet direct-entry path remain unimplemented**. Existing `confirmPendingBill` atomically updates the private ledger, P&L, user-project totals, interaction history, public-contribution outbox, and pending-bill deletion for one row. Extend/reuse this transaction boundary for a multi-wallet direct-entry batch; all wallet rows must commit together or none may commit.
- The direct-entry workflow must support per-wallet Gas, explicit reward allocation (per-wallet amount or campaign total split evenly), USD/token/manual-valuation/points-or-unissued reward modes, and native asset detail. Unknown/unissued/points rewards must not be represented as realized USD zero or make net P&L appear complete. Preserve historical rows with additive Room/backend migrations and backward-compatible cloud/backup serialization.
- Existing `CoinGeckoPriceRepository` currently supports only TRX/USDT spot quotes. Extend it conservatively for explicitly mapped, unambiguous token IDs; keep quote results nullable and persist quote source/time. Manual valuation remains available; never guess from ambiguous symbols.
- The latest transaction-linked contribution enhancement is a separate pending extension of Batch 07, but it shares the same confirmation/outbox path: implement the unchecked opt-in, per-wallet Tx Hash, chain verification, receipt fee calculation, historical valuation, provenance tiers, and retry/privacy behavior from Sections 2 and 5.2. Do not create a second contribution pipeline or send public contributions by default.
- **Next coherent implementation focus:** integrate the project-scoped direct accounting workflow with the existing ledger transaction boundary and the new opt-in contribution contract. Keep the community adoption affordances already present. After this integration is validated, resume the existing Batch 08–15 order.
- **Current implementation evidence (2026-11-10):** The multi-wallet accounting branch includes Room 16→17 reward-kind/quantity/unit/USD valuation and historical quote metadata, sticky USD-profit completeness, one Room transaction for all selected wallet rows plus local ledger/project aggregates, and deterministic per-wallet batch IDs. The project detail screen exposes “记一笔”, wallet multi-select, per-wallet Gas, per-wallet or total reward allocation, USD/token/manual valuation/points/unissued/unknown reward types, and quick Gas chips. Anonymous consent is independent per selected wallet and unchecked by default; only an explicitly authorized wallet must provide its own locally validated chain-specific Tx Hash. Unsupported networks cannot opt in. Opted-in rows remain private and pending chain verification; they are not yet enqueued to the public outbox. Exact PR head 35742a12b9784f2990adf5b41510939d87dd181a passed Android unit tests and Debug APK assembly in Actions run 42706233132. Backend Tx Hash verification, historical token quote lookup wiring, and post-verification outbox enqueue remain pending. Unknown/points/unissued rewards use a numeric compatibility sentinel only internally; explicit reward-kind/completeness fields govern presentation and USD totals.

### New cross-module requirements captured — 2026-11-10 (requirements only; not yet implemented)

- Added the home bottom-navigation order and state restoration; BTC/ETH dashboard; pill-shaped global search with coin/DApp categories, 300 ms debounce, local fuzzy search, and local ten-item deduplicated history.
- Added Room entities/domain models for unified multi-chain assets, market coins, hot coins, search history, and cache metadata. Added 15-minute Room TTL + stale-while-revalidate behavior, last-successful-update labels, stale-cache retention on failure, and a per-cache-key 30-second manual refresh throttle for market/search/asset surfaces.
- Added bounded exponential backoff, retryable-status policy, provider node rotation/health handling, and TLS-safe failover requirements.
- Added limited unified asset-detail presentation: EVM/Solana/Bitcoin normalization, $0.10 dust folding, spam/unpriced asset filtering without data deletion, top-five-plus-Others chart, no Bitcoin pie chart, and reuse of TRON card styling. This does not restore the cancelled full portfolio dashboard.
- **Documentation only:** these are now part of the project-wide source of truth and are not claimed as implemented. They must be integrated into the existing 15-batch plan by code fit and feature fit; no duplicate Room entities, cache layers, navigation destinations, or market APIs should be created if equivalent implementations already exist.


### New user-confirmed feature — offline encrypted backup/import (2026-11-10; implementation submitted, validation passed)

- PR #115 merged as 7c6ed1f2ad7a009fe403101e400ee222261067be. Exact PR head 368e813bdb4ae71c1250126a6ea625d041c6eed7 passed Android unit tests and Debug APK assembly in Actions run 42696896775; backend tests were correctly skipped for this Android-only change.
- The “我的” page now has “备份我的数据” and “导入数据”; Android system CreateDocument/OpenDocument pickers are used, with no manual path entry, registration, cloud recovery, or network requirement.
- The current implementation uses a versioned .ahbackup file, AES-256-GCM, PBKDF2-HMAC-SHA256 (210,000 iterations), random salt/nonce, bounded payloads, and a password of at least 8 characters. It exports active-scope Room rows and scoped cache payloads, excludes credentials/Keystore/Reown/signing material and other scopes, and validates/decrypts before an atomic active-scope restore with explicit replace confirmation.
- Existing codec tests cover encrypted round-trip/no plaintext leakage, wrong password, tampering, and invalid scope/short password.
- Remaining backup hardening requirements (not yet implemented/verified): include and validate JSON app_name: "AirdropHunter"; normalize password/JSON/marker/integrity failures to the user-facing message “文件损坏或密码错误，导入失败”; support selecting .hunter as well as the existing .ahbackup suffix; clear password state on every picker cancellation/failure path. Do not recreate the already implemented encryption, picker, or atomic restore workflow.
- Cloud-based private-data recovery and registration remain deferred. This file workflow is user-controlled offline portability, not cloud backup.

### New user-confirmed feature — transaction-linked public contribution with half-automated fee verification (2026-11-10; requirements captured, implementation pending)

- Existing anonymous contribution outbox/API and aggregate idempotency are implemented, but the current implementation does not yet require or verify a transaction hash. Existing payloads intentionally omit wallet addresses and hashes.
- Accounting opt-in remains unchecked by default. When enabled, require a Tx Hash for each wallet row and verify transaction existence, confirmation/finality, and exact chain-specific fee-payer/sender match to that row's selected wallet address.
- Adopt three deterministic provenance tiers: `📝 用户自报`, `🔗 链上已确认`, and `✅ 链上精确核验`. The middle tier confirms transaction linkage only; only the third tier confirms a calculated fee amount. Never imply that a verified Tx Hash alone proves the entered Gas amount or reward amount.
- Use half-automated provider/API receipt parsing rather than self-hosted nodes or a full automatic parser. EVM first: retrieve receipt data and calculate execution fee as `gasUsed × effectiveGasPrice`; normalize wei/native units and use a reliable historical native-asset/USD price at transaction time.
- Tier 3 requires complete fee-component coverage for that chain. EVM explorer APIs do not cover TRON/Solana/Bitcoin; add separate deterministic adapters before claiming Tier 3 for those networks. For L2s with additional L1 data fees, include those components or remain Tier 2; never label a partial fee as 100% precise.
- Compare the entered Gas amount with the backend-calculated fee on the same valuation basis using ±20% tolerance. Within tolerance, accept Tier 3 and publish the backend-calculated fee amount, not the user-entered value. Outside tolerance, reject high-trust contribution and show “金额与链上不符”; keep the private ledger entry unchanged.
- Provider outages, rate limits, timeouts, or incomplete receipts remain pending/retryable. Invalid hash, wrong sender/payer, insufficient finality, unsupported attribution, or replay conflict rejects only the public contribution; private accounting remains intact.
- A fee receipt verifies Gas only, never rewards or actual profit. Keep the existing anonymous outbox/idempotency pipeline and global chain+hash replay protection. Raw hashes and addresses remain excluded from public tables/snapshots/APIs/analytics/ordinary logs; use server-keyed fingerprints and minimal verification metadata.
- Public dashboard bars and totals distinguish user-reported, transaction-linked, and amount-verified data: lighter/semitransparent styling for user-reported values, an explicit separate transaction-linked state, and solid/high-confidence styling for receipt-verified amounts. Never silently combine tiers without provenance labels.
- No transaction verification result may automatically complete an event. The user must still manually enter and confirm actual realized results.
- **Status:** requirements only; Android opt-in/hash capture, durable pending/retry state, backend chain/provider adapters, historical-price valuation, fee-completeness checks, ±20% comparison, global fingerprint uniqueness, provenance-aware aggregates/UI, and regression tests are pending. Implement as an extension of Batch 07's existing contribution flow, not as a second pipeline.


### 2026-11-10 continuation verification note — PR #119

- The project-scoped direct-entry UI is now being validated against the exact PR head. CI run 42705514709 on head 213a402e2acf0475fa3beabaf0dfd9744cf0c251 failed at Kotlin compilation in ProjectDetailScreen.kt because the newly nullable USD reward/net values were passed to a metric composable that accepted only non-null Double.
- The fix changes the period metric to accept nullable values and display “估值未完整” instead of coercing an unknown USD result to zero. This preserves the product rule that incomplete reward valuation must not look like a real $0 income or net result.
- The accounting dialog now has separate per-wallet Gas and reward amount fields, plus the campaign-total even-split mode. Per-row public opt-in and local chain-specific Tx Hash validation remain pending; the current batch-wide opt-in must be replaced before public contribution is enabled.
- Do not merge PR #119 until the exact latest head passes Android unit tests and Debug APK assembly. Backend Tx verification, historical-price valuation, global chain+hash replay protection, old pending-bill/outbox gating, provenance-aware public aggregates, and full end-to-end regression remain pending.


### 2026-11-10 per-wallet consent and local hash-validation implementation

- PR #119 follow-up changes anonymous contribution authorization from one batch-wide switch to a separate unchecked-by-default switch on each selected wallet row. A wallet can remain private while another wallet in the same atomic batch explicitly opts in.
- Each opted-in row must provide its own chain-specific transaction hash: EVM hashes require 0x + 64 hex characters; TRON and Bitcoin transaction IDs require 64 hex characters; Solana signatures require 87–88 Base58 characters. Unsupported networks cannot opt in. This is local format validation only, not proof that a transaction exists or belongs to the selected wallet.
- The per-row consent bit is persisted with that interaction's existing metadata/entity; the batch persistence remains one Room transaction. No public outbox enqueue is performed by this direct-entry flow.
- Regression tests cover mixed consent within one batch, missing/malformed hashes, supported chain formats, and unsupported-network rejection. Backend receipt verification, historical pricing, replay protection, provenance tiers, and legacy pending-bill contribution gating remain pending.


### PR #119 validation follow-up — 2026-11-10

- Latest PR head before this correction: `d737a6b29bd247c80dd5dea87a4a83d0dcae474f`; Actions run `42708139131` compiled the Android app and assembled the Debug APK successfully, but the unit-test task failed (114 passed, one failed).
- Failure root cause: the new chain-hash-format test created otherwise valid EVM/TRON/Solana test rows without supplying the required reward input. `ManualProjectAccountingPolicy.prepare(..., PER_WALLET)` correctly rejects a row without a reward; the failure was in the test fixture, not the hash validators or application compile. The test now supplies an explicit UNKNOWN reward for each network row while testing only hash-format behavior.
- Exact-head CI must be rerun after this test-only correction. No PR merge is authorized until the new exact head passes.
- Code review confirms the direct-entry batch writes the per-wallet consent bit into each private interaction and does not enqueue an outbox row. However, the legacy `confirmPendingBill` path still auto-enqueues eligible contributions without the new explicit per-row opt-in and chain-verification contract. This is a separate connected entry point and remains a required follow-up: retain private bill confirmation, but default public contribution to off and only enqueue after explicit consent plus successful backend verification. Do not silently assume legacy confirmation is consent.
- Local hash validation is only syntax validation; it does not prove transaction existence, ownership, finality, fee amount, or historical USD value. TRON/Bitcoin/Solana must not be described as verified until deterministic server adapters exist.


### PR #119 legacy bill-confirmation integration — 2026-11-10 (implementation submitted; CI pending)

- The browser's existing “确认本次记账” dialog now includes a separate unchecked-by-default consent for that single pending bill. If selected, the current chain and Tx Hash must pass local format validation before confirmation; unsupported/malformed inputs can still be saved privately after consent is turned off.
- Consent is passed through the accounting repository and persisted in the private interaction row and ledger metadata. Opted-in rows are marked `pending_verification`; non-opted-in rows are marked `private`.
- Critical privacy correction: the legacy `confirmPendingBill` transaction no longer inserts an anonymous outbox row merely because the user confirmed private accounting. It preserves the local/private ledger confirmation and only stores the user's explicit intent. No public contribution is sent until the backend verifies transaction existence, fee payer, finality, full fee components, historical quote, tolerance, and global replay protection.
- The new direct multi-wallet dialog and the legacy single-bill confirmation dialog now share the same unchecked-by-default consent principle. The direct multi-wallet path still writes all selected rows atomically and keeps public verification separate from private-ledger success.
- Repository tests verify that opt-in intent is propagated. Hash-format tests now provide the required UNKNOWN reward fixture so they test only the intended network/hash validation behavior.
- Backend verifier/outbox integration and provenance-aware public aggregation remain pending. Do not label local syntax validation as on-chain verification.

- **Follow-up compile review:** the repository facade also needs to declare the new opt-in parameter before forwarding it to the local data source; this signature is included in the latest follow-up commit. Revalidate the exact latest head rather than relying on an earlier in-progress CI run.


### 2026-11-10 accounting aggregate correctness follow-up

- Exact latest PR #119 head `98818a62c666de9f7bd3ad17fce7f047e1582065` passed Android Debug APK assembly and unit tests in Actions run `42709360469`.
- Code-fit review then found that the profile's local achievement aggregator still summed compatibility sentinel values from projects whose USD reward valuation is incomplete. This could make a partial ledger appear to have a confirmed total reward, net profit, or win rate.
- The local profile aggregate now keeps Gas totals, but excludes incomplete-valuation projects from USD reward/profit and win-rate calculations. It carries an explicit `usd_profit_complete` flag; the profile shows “估值未完整” rather than presenting the partial sum as a final USD total. The number of participating projects remains the full ledger project count.
- Added regression coverage for a mixed complete/incomplete ledger. This is part of the same accounting-domain consistency pass; it does not change historical ledger rows or infer missing reward values.
- The new follow-up commit requires its own exact-head Android CI run before PR #119 can be considered validated. Backend receipt verification, historical-price quotes, global chain+hash replay protection, and the verified contribution pipeline remain pending.


### Cloud/local accounting valuation parity follow-up (2026-11-10; implementation submitted, CI pending)

- A cross-module audit found the guest/local profile now excludes incomplete-valuation projects, while the authenticated `/api/user/stats` endpoint still summed compatibility sentinel values from every private-ledger row.
- The backend now groups ledger rows by project and applies the same project-level completeness rule as Room: Gas totals remain visible, but reward totals, net P&L, profitable-project count, and win rate exclude a project if any row in that project has an incomplete USD reward valuation.
- The backend returns `usd_profit_complete`; Android's existing `UserStatsDto` and profile display consume this flag. New direct-accounting rows persist the flag in the private-ledger `extra` metadata so the same meaning survives authenticated sync.
- Legacy confirmed-bill rows without reward-kind metadata remain backward-compatible. New reward metadata infers POINTS/UNISSUED/UNKNOWN as incomplete and TOKEN as complete only when a persisted USD valuation exists. Explicit completeness metadata takes precedence.
- Added backend regression tests for unknown, points, unpriced-token, USD, manual USD valuation, and priced-token rows, including project-level aggregation.
- This is an end-to-end correctness extension of the current accounting integration, not a new public-contribution pipeline. The next dependency remains backend transaction receipt verification and provenance-aware anonymous outbox delivery.
- Current branch head before this follow-up: `b4a8149b459b464fcbffd9a2972809440a8cc0e5`; this change requires exact-head Android and backend CI before it is considered validated.


## 2026-11-10 链上核验与公共贡献闭环进展

本轮在原有多钱包记账基础上补齐公共贡献核验链路，以下内容已进入本次开发分支，仍需 CI 验证及部署配置确认：

1. **后端先验后收**：公共贡献接口现在要求网络、钱包地址、Tx Hash、Gas 金额和实际收益。没有通过服务端核验不会写入公共统计；仅有客户端金额或哈希格式不再足够。
2. **EVM 核验范围**：当前适配 Ethereum、BNB Smart Chain、Polygon、Arbitrum、Optimism、Base、Avalanche。服务端通过 Etherscan V2 读取交易、回执、发送方、执行状态、区块和确认数；发送钱包不匹配、金额超出 ±20% 容差时拒绝公共贡献。失败交易仍可核验其已经支付的 Gas，但不会被标记为成功活动；确认数不足属于待确认状态，保留队列并自动重试。
3. **历史价格与 Gas**：Tier 3 使用交易区块时点的 Chainlink 原生币/USD Feed 历史状态，按实际 gasUsed × effectiveGasPrice（并计入服务端可读取的 L1 fee）计算 Gas USD。若 L2 的 L1 数据费未能完整读取，只标记为 Tier 2“链上已确认”，不冒充精确核验；不完整或限流的服务响应返回可重试状态。
4. **三级来源标识**：历史旧数据保留为“用户自报”；交易发送方、成功状态和确认数核验通过但费用组件不完整的记录标记为“链上已确认”；费用组件和历史报价完整且金额通过容差检查的记录标记为“链上精确核验”。快照和项目详情接口输出各等级 Gas 汇总，详情页展示分级金额。
5. **防重与隐私**：服务端只保存由 `COMMUNITY_TX_FINGERPRINT_SECRET` 生成的链+Tx Hash HMAC 指纹，以及不含原始钱包地址/Tx Hash 的核验元数据；数据库唯一约束执行全局防重复。原始钱包地址和 Tx Hash 仅作为核验请求输入，不写入公共贡献表。用户私账仍按原有私有账本路径保存。
6. **Android 待处理队列**：Room 升级到 18，待核验队列在本机保存网络、钱包、Tx Hash 和发生时间；多钱包手工记账要求逐钱包明确选择实际 EVM 网络（不把通用“EVM”生态标签猜成 Ethereum），逐笔授权后才入队。浏览器待确认账单和项目多钱包记账均接入队列；仅开放当前 EVM 核验网络、公共项目及完整 USD 奖励估值。游客队列留在本地，登录用户的详细账本仍走私有云端账本。
7. **失败处理**：服务不可用、限流、回执未就绪等返回 503 并自动重试；金额不符、钱包不匹配、网络不支持和全局 Hash 重复等返回终态拒绝；成功或终态拒绝会更新私账中的贡献状态元数据。任何公共核验失败都不回滚或删除用户私账。
8. **奖励边界**：链上核验只核验交易与 Gas，不证明空投奖励或利润。实际奖励仍是用户明确确认的金额；不完整 USD 估值的记录不会进入公共贡献队列。

**发布前仍需完成**：
- Render 后端配置服务端专用 `ETHERSCAN_API_KEY` 和至少 32 字节随机 `COMMUNITY_TX_FINGERPRINT_SECRET`；不得把密钥放进 APK、仓库或日志。指纹密钥必须长期稳定保存，轮换前要先设计历史指纹迁移，否则全局防重复会失效。
- 完成当前分支 Android 单元测试/Debug APK 与后端 pytest 的最新 CI 验证；失败必须在同一 PR 修复。
- 进行真实 EVM 交易的部署后验证，确认各网络 Etherscan V2 与历史 Chainlink `eth_call` 的实际可用性；服务商不支持历史区块读取时保持待处理，不得降级伪造 Tier 3。
- 私账行级核验状态已写入本地账本及登录用户的私有账本元数据；“等待核验 / 已核验 / 核验拒绝”的可见状态文案和交互反馈并入 Batch 15 全局 UI 集成，避免在本批拆成零散 UI 改动。
- TRON、Bitcoin、Solana 暂不接入公共链上核验；这些网络仍可使用个人账本，不能显示为链上核验通过。


## Latest continuation audit and implementation status (2026-11-10)

### PR #119 — Batch 07 transaction-verification extension (merged)

- PR #119 merged to `main` as `d3891f2665e37220b3c0a24e332222c546c579e4`. Its exact final PR head `5c62468ddae2008e57a02fffaefea379ff53d835` passed backend pytest and Android unit tests + Debug APK assembly in Actions run `42716740435`.
- Direct project accounting now persists atomic multi-wallet entries, per-wallet reward allocation, reward-kind/valuation completeness, and independent unchecked-by-default anonymous opt-in.
- EVM public contribution verification uses server-side provider receipts, selected network/wallet sender matching, confirmation checks, historical Chainlink quote lookup, fee-component completeness, ±20% amount tolerance, and server-keyed chain+hash replay fingerprints. Provider/price/confirmation gaps remain retryable; public rejection never deletes the private ledger.
- Failed EVM transactions can still be represented as Gas actually paid, but are not labelled successful activity. L2 fees with incomplete L1 components cannot be Tier 3.
- The backend currently enables the documented EVM adapters only. TRON, Bitcoin, and Solana remain private-ledger-only for this verification path until deterministic adapters are implemented.
- **Status: complete in code and CI.** Pending: configure server-only `ETHERSCAN_API_KEY` and stable `COMMUNITY_TX_FINGERPRINT_SECRET` in Render, then perform live provider verification. Do not claim production readiness before those checks. Actual reward/profit remains user-confirmed and is not verified by a Gas receipt.

### Batch 08 — community snapshot data pipeline audit

- Existing implementation was inspected on the merged main branch: `community_snapshots.py` computes overall/7d/3d/1h windows from anonymous contribution rows; `community_snapshot_schedule.py` implements the UTC+8 due-window rules; `community_snapshot_storage.py` writes immutable versioned JSON and atomically switches the manifest; `publish_community_snapshots.py` builds all due windows from one run timestamp; Android `CommunitySnapshotRepository` reads Room first and silently revalidates the configured HTTPS CDN.
- Snapshot payloads already contain separate Gas totals for `user_reported`, `transaction_linked`, and `amount_verified`, added alongside PR #119's verified contribution fields. Snapshot privacy excludes raw wallet addresses and Tx Hash values.
- PR #69 provides the core snapshot builder/publisher and Android cache integration; PRs #82–#85 add conditional manifest validation, CDN cache checks, deployment smoke checks, and recursive privacy assertions.
- The production workflow currently exposes manual dispatch only; the recurring cron remains deliberately paused. R2/S3 credentials, public CDN base URL, production database configuration, and a successful real deployment smoke/publish run remain pending.
- **Status: complete in code; pending production configuration and operational verification.** Do not rerun or rebuild this pipeline unless a concrete code defect is found.

### Batch 09 — community UI and social metrics (current implementation)

- Existing main code already provides compact project cards, project-detail Gas/reward statistics, rating eligibility/reputation labels, rankings, favorites, and catalog adoption links. The 2026-11-11 override replaces the former four-window waterfall detail layout with one unified three-tab card (总览、近3天、近1小时). Reuse existing data/repository behavior while following the latest visible UI rule.
- PR #119 added `gas_by_verification_tier` to static snapshots and Android DTOs. The project detail currently lists tier amounts as text, but the project cards do not show tier provenance and neither surface visualizes the relative split.
- Current Batch 09 change adds a shared deterministic three-tier breakdown bar to both project cards and each project-detail time window: user-reported uses a translucent segment, transaction-linked uses a distinct amber segment, and amount-verified uses a solid segment. Amount labels remain explicit; invalid/negative/zero/unknown tiers do not create fabricated segments. The existing Gas-vs-reward comparison remains separate and unchanged.
- Android unit tests cover stable tier ordering, exact normalized shares, invalid/negative/non-finite values, and preservation of original amounts.
- **Status: complete in code and CI** (PR #120, Actions run `42724504142`). Production CDN configuration and real-device visual acceptance remain separate gates.



## Latest continuation audit (2026-11-10, after PR #120)

### Batch 09 — community UI and social metrics (merged)

- PR #120 merged to `main` as `6a3da69221a3cf671db23143a3008b573ae7b33b`. Final head `a9f807960ce7f8c444d4c035705946501a0d6cf9` passed Android unit tests and Debug APK assembly in Actions run `42724504142`.
- The same deterministic three-tier Gas provenance bar is now shown on community project cards and each project-detail time window. Tier 1 user-reported is translucent, Tier 2 transaction-linked is amber, and Tier 3 amount-verified is solid; labels and exact USD values remain visible. Existing Gas-versus-reward comparison is unchanged.
- **Status: complete in code and CI.** Real-device visual acceptance remains part of Batch 15.

### Batch 10 — project discovery and submissions audit

- Existing code provides safe HTTPS project submissions, canonical URL/name duplicate checks, rate limits, suspicious shortener/IDN/nonstandard-port review holds, and no automatic crawling of submitted websites.
- RSS/Atom ingestion requires both an exact HTTPS host allowlist and an exact source-name content-reuse approval. Requests do not follow redirects; feed bodies are streamed with byte/item limits; malformed or oversized feeds are isolated per source; candidates remain pending until an admin-token-protected review action approves or rejects them.
- Source-health metadata records attempt/success timestamps, item counts, and sanitized error class only; query strings and fragments are removed from stored source URLs. Review credentials remain server-only and are never embedded in the APK.
- The scheduled discovery workflow is deliberately disabled unless `AIRDROP_DISCOVERY_SYNC_ENABLED=true`; no feeds are enabled by default. **Status: complete in code; pending explicit feed-rights approval, production variables/database configuration, and scheduled-run verification.** Do not activate a source until its reuse rights are confirmed.

### Batch 11 — global search, market dashboard, and unified asset detail (current implementation)

- Main-branch audit found the old global search was a generic OKLink handoff, included a stock placeholder, had no Coin/DApp category split or 300 ms debounce, and used DataStore rather than Room for history. Home placed shortcuts before the market dashboard and showed five coins rather than the requested compact BTC/ETH pair.
- Current Batch 11 implementation changes the home hierarchy to BTC/ETH first, followed immediately by the pill-shaped Coin/DApp search entry; the five-coin list remains available in global search and individual market details.
- Global search now separates Coin and DApp categories, performs local Room/cached-catalog matching first, then debounces remote lookups by 300 ms. Coin selection opens market detail; DApp selection opens its validated HTTPS URL in the built-in browser. Search history is Room-backed, account-scope isolated, deduplicated by category+normalized query, recency ordered, and capped at ten.
- Room version 19 introduces `MarketCoinEntity`, `HotCoinCacheEntity`, `SearchHistoryEntity`, `UnifiedAssetEntity`, and `CacheMetadataEntity`, with an explicit 18→19 migration and account/wallet scope keys for personal asset/history records.
- Unified asset domain/presentation policy hides unverified, unpriced, and sub-$0.10 dust from the normal list; the distribution helper returns the five largest verified priced assets plus Others and returns no chart for Bitcoin. **This does not add a cross-wallet portfolio dashboard.**
- The verified asset-balance adapters and limited per-wallet asset-detail dialog are now integrated by PR #122. EVM/Solana/Bitcoin are read-only; unverified holdings remain hidden from the normal list. Provider rotation and persistent TTL/SWR are tracked in Batch 12.
- **Status: complete in code and CI** (PR #121, Actions run `42726301273`, plus PR #122 validation below). Real-device visual acceptance and explicit free-provider coverage limits remain.

### Batch 11 continuation — market/search module merged; per-wallet asset detail integration in progress (2026-11-10)

- PR #121 merged to `main` as `fbe42a75c5934e7afb0202ec9e3d0adc70923768`. Exact PR head `ab87f125009e9464b25dec11a553890c27bdba9a` passed Android unit tests and Debug APK assembly in Actions run `42726301273`. This closes the Home BTC/ETH dashboard, Coin/DApp category search, local-first matching with 300 ms remote debounce, Room search history, and Room 18→19 market/search/unified-asset model foundation.
- The next connected implementation adds a **per-wallet** asset-detail dialog only. It does not restore the cancelled portfolio-wide asset overview, add a wallet asset tab, or aggregate balances across wallets.
- Asset data is normalized into the existing `UnifiedAsset` model and Room table, scoped to the exact guest/account token captured when the request starts. Cache is rendered first; refresh failure retains cached rows. The API is read-only and explicitly anonymous; it never requests signing or wallet credentials.
- EVM native balances are queried by explicit network for Ethereum, BNB Chain, Polygon, Arbitrum, Optimism, Base, and Avalanche. Where a public Blockscout v2 instance is available, token balances are included; token metadata is trusted only when the provider explicitly marks the contract verified. Provider-supplied exchange-rate fields are not accepted as price truth. Only exact allowlisted stablecoin contract addresses receive CoinPaprika USD quotes.
- Current free-provider coverage limitation: BNB Chain and Avalanche currently expose native-coin balances only in this integration because a verified free token-enumeration source has not been approved/configured. Do not fabricate ERC-20 rows or infer token holdings from transfer history. If token enumeration is unavailable on another network, retain native balance and show a clear coverage note.
- Solana reads native SOL plus SPL Token and Token-2022 accounts through the public RPC. Only explicitly allowlisted USDC/USDT mints receive verified metadata and a quote; other discovered mints remain visible only under the hidden-assets inspection and have no fabricated USD value.
- Bitcoin reads the native BTC balance only; no token list or pie chart is shown. BTC/USD is quoted separately from the native amount.
- Normal asset rows require verified metadata, a reliable positive USD price, and USD value >= $0.10. Smaller, unpriced, and unverified holdings are retained in Room and can be inspected deliberately; they are not deleted. Non-Bitcoin distribution charts use the top five verified priced assets plus Others.
- The Android detail surface includes explicit EVM network selection, normal/hidden asset inspection, provider/refresh status, and the non-Bitcoin distribution chart. It remains per-wallet and does not change the existing read-only transaction-history/accounting flow.
- PR #122 merged to `main` as `86ade10cee3fef40026a0351393d194f94db1c10`; exact PR head `33afff54692f4318c232ee5597c87781b0d1cc9a` passed Android unit tests + Debug APK assembly and backend pytest (`137 passed`) in Actions run `42731402904`.
- **Status: complete in code and CI.** Real-device acceptance remains Batch 15. BNB Chain/Avalanche token enumeration remains a documented free-provider coverage limitation; native balances only are shown until a verified source is available.


### Batch 12 — persistent cache, refresh control, and provider resilience (2026-11-10)

- Main-branch audit confirmed Room v19 already contains `CacheMetadataEntity`, `CacheMetadataDao`, and `CacheStore.readCacheMetadata/writeCacheMetadata`; these were foundation only and were not yet used by market/search/asset repositories. Do not create another migration or duplicate metadata table.
- A shared `CacheRefreshCoordinator` now applies the 15-minute TTL/SWR policy to market prices, OKX hot coins (also consumed by the Home BTC/ETH dashboard), DApp catalog, and per-wallet asset rows. Cache payloads remain in their existing Room tables; metadata records source, fetched/expiry time, TTL, refresh state, last error, and last manual refresh time.
- Local cached results continue to render before remote debounce. Fresh cache avoids network calls; expired cache is retained while a background refresh is attempted; a failed refresh keeps the old payload and records a 30-second retry cooldown rather than repeatedly hammering an unavailable provider. Successful refresh resets the cooldown and freshness window.
- Manual asset refresh is throttled per wallet/network/cache key to one request per 30 seconds. Concurrent requests for the same key are serialized with bounded striped mutexes; after the first succeeds, waiting callers reuse the newly fresh Room result rather than duplicating the request.
- Safe transport retries are bounded to three attempts with exponential delays, bounded jitter, and HTTP `Retry-After` handling. Non-transient client errors are not retried. Cancellation is propagated and does not become a network retry.
- The backend adds read-only provider rotation for EVM JSON-RPC, Solana JSON-RPC, and Bitcoin Esplora. HTTP 408/425/429/5xx, transport failures, and JSON-RPC error payloads move to the next public endpoint; response validation is retained. No signing, transaction submission, TLS bypass, or private credentials are introduced.
- Existing WorkManager background sync was audited: a network-constrained 15-minute dispatcher enqueues independent unique queues for community contributions, wallet activity, private ledger, and favorites. Each worker retains its own 8-hour/15-minute due cadence as applicable; transient failures use WorkManager exponential backoff (30-second initial delay) and resume when network constraints are met. Guest private ledger remains local-only.
- Android unit tests cover cache freshness boundaries, the exact 30-second manual throttle, stale/error cooldown, bounded exponential retry, and retryable failure classification. Backend tests cover endpoint rotation after HTTP 503, JSON-RPC error fallback, status classification, and bounded Retry-After delays.
- PR #123 merged to `main` as `838e91ed1444d9ac2208730d7e4c9029b7ac269c`; exact head `910874ce2368d9b9155dbe7481325b47978c9cc2` passed Android unit tests + Debug APK and backend pytest (`142 passed`) in Actions run `42735007865`.
- PR #124 merged to `main` as `ac5475bf79b08db3e85e35590f1f7f0467eca8d8`; exact head `c0e7a1049b9980ac51678759eecd0635d5fa2b61` passed Android unit tests + Debug APK assembly in Actions run `42735838726` (backend tests correctly skipped because no backend files changed).
- Home dashboard, global coin/DApp search, hot coin list, and per-wallet asset detail now expose the same persisted cache status and last-successful timestamp. Status labels distinguish fresh, refreshing, stale, and failed caches.
- Wallet asset cache-key construction is centralized and tested across guest/account, wallet, and network scopes. The repository reads/writes assets using the exact captured token scope; background workers also bind work and writes to the session token captured when work starts.
- Offline recovery audit: Room retains the last successful market/search/asset payload; failed foreground refreshes use stale data and a short retry cooldown. WorkManager background queues require network connectivity and retry transient failures with exponential backoff. Guest private ledger remains local-only.
- PR #125 extends provider rotation to wallet-activity reads for Solana JSON-RPC and Bitcoin Esplora. Etherscan V2 and TRONGrid remain single configured sources in this free-provider release; no unapproved paid or credential-dependent alternate is silently substituted.
- **Status: Batch 12 implementation complete in code and CI after PR #125 validation.** Real-device offline/account-switch acceptance remains Batch 15. Do not apply this public cache coordinator to personal ledger or task CRUD; those are not public market cache surfaces.

### Batch 13 — backend/API/data integrity audit (2026-11-10; complete in code and CI)

- Main-branch audit confirms FastAPI router registration, PostgreSQL-only async database URL normalization, JWT signature/expiry validation, Argon2 password hashing, account-owned project/task/wallet/ledger queries, private-ledger idempotency key (user_id, client_record_id), and a linear Alembic migration chain through 0017_verified_contrib. Existing cross-account negative tests cover projects, tasks, wallets, private ledger, and project-wallet assignments.
- A concrete public-data integrity defect was found in POST /api/community/projects/{id}/contributions: the server verified the transaction Gas amount but trusted client-supplied reward token rows independently of the authoritative actual_reward_usd. The snapshot builder aggregates those token rows into reward_by_token, so one contribution could publish a project reward total that disagreed with its token-level reward valuation. The previous code also rewrote every client-supplied Gas row to the full verified fee, allowing duplicate Gas token rows in snapshots.
- The Batch 13 fix validates reward breakdowns before persistence: if reward token rows are supplied, every row must have a USD valuation and their sum must match the manually confirmed contribution reward total within the database's 1e-8 USD precision. Aggregate-only USD entries with no token breakdown remain supported. For Tier 3, the backend discards all client Gas rows and emits exactly one Gas row from the verified receipt and historical Chainlink quote. For Tier 2, duplicate Gas rows are rejected and the single client row remains explicitly user-reported.
- Regression tests cover missing reward valuation, reward-total mismatch, and server-canonicalized single Gas rows. A changed duplicate payload with an inconsistent reward breakdown is rejected before database mutation; exact idempotent retries remain covered by the existing contribution tests.
- Status: complete in code and CI after PRs #126–#128. The cross-module audit covered account/auth behavior, private/public endpoint authorization, schema/migration consistency, public snapshot source isolation, provider error handling, and Android/backend DTO compatibility. No additional schema migration was needed. Production secrets, live provider checks, deployed backup/restore, and real-device acceptance remain separate Batch 14/15 gates.

- Batch 13 auth-scope correction: the current release now disables the registration API by default at the FastAPI application boundary and removes the Android registration toggle. The login screen displays the exact approved notice, retains login and guest entry, and never sends a registration request. The API's registration implementation is preserved for a later release; test fixtures explicitly enable it only during isolated automated tests. A regression test verifies the production-default 403 response.

- Additional Batch 13 privacy audit: public token snapshots expose valuation_source metadata. Because the contribution API previously accepted this string verbatim from the client, a malicious client could place a wallet address or Tx Hash inside that field and bypass the existing key-name-only snapshot privacy checker. The API now normalizes valuation sources against an explicit allowlist (CoinPaprika, CoinGecko, manual/user-reported, legacy, and supported Chainlink network labels); unknown strings are reduced to user_reported. Tests cover provider-label normalization and sanitization of address/hash-shaped values.

- PR #126 merged as b9cca64c51ca6dcbb4dd325e79d89d0388f88629. Exact final head 5173464a3f6977cf9eba0bfb7512162c150acf4c passed backend pytest (146 passed) in Actions run 42740036923.
- PR #127 merged as d99c44be5430def0c820b1c65567ce8eed330227. Exact final head 2abaafa2d8cdcb734600556549037424862dfd1c passed Android unit tests + Debug APK assembly and backend pytest (147 passed) in Actions run 42740821819.
- PR #128 merged as 5e54cc95af4b4d1e67d08427b9fc3c4ed374305e. Exact final head d8b310f0f87c9c4cdb25f86880afe4b170dcb443 passed backend pytest (148 passed) in Actions run 42741787777.

### Batch 14 — production operations and recovery (2026-11-10; in progress)

- Existing code already includes a manual deployment-readiness smoke test that checks API database/migration readiness, the HTTPS CDN manifest and all four snapshot windows, same-origin redirect safety, JSON response bounds, manifest validators, immutable snapshot cache headers, and recursive private-field checks. It requires repository variables API_BASE_URL and COMMUNITY_SNAPSHOT_BASE_URL and does not require exposing credentials.
- Existing PostgreSQL backup/restore drill uses pg_dump custom archives, restores only into a separately provisioned database whose name ends in _restore_check, never drops/cleans the target, and verifies Alembic revision plus public table set. It is intentionally manual because the restore target must be empty and separately provisioned; no unattended repeated restore is safe.
- Batch 14 safety hardening: the restore script now checks the target database for existing application relations, functions, user-defined types, and non-default schemas **before** creating the source archive or running pg_restore. Any non-empty or uncheckable target fails closed; no --clean/dropdb path is introduced. Regression tests cover empty, non-empty, and unparseable preflight results. This prevents a misconfigured target from being partially modified before pg_restore encounters an existing object.
- PR #133 merged to `main` as `c9cf11afa700f276ad088d1c9e3ee60250e9de6b`. Exact head `18e38fa2f0f6d10766fc552f9a0c73953a4c7f5d` passed backend pytest (`153 passed`) in Actions run `42747428235`; Android build was correctly skipped because only backend recovery tooling and documentation changed.
- Existing Render blueprint creates a free FastAPI service, uses a generated JWT secret, and keeps DATABASE_URL out of source control. The readiness path is now /health/ready rather than the shallow /health endpoint, so Render checks database connectivity and Alembic revision before treating the service as healthy. Blueprint prompts for server-only ETHERSCAN_API_KEY and COMMUNITY_TX_FINGERPRINT_SECRET; no secret values are committed.
- Community snapshot publishing now has a five-minute scheduled trigger, but the job remains gated by COMMUNITY_SNAPSHOT_PUBLISH_ENABLED=true and still safely skips if the R2/S3 bucket, endpoint, credentials, or public CDN URL are absent. Its existing due-window logic chooses 1h every five minutes, 3d at 00/06/12/18 UTC+8, and overall+7d at 08:00 UTC+8.
- Approved airdrop discovery now has a daily 08:00 UTC+8 scheduled trigger, but remains gated by AIRDROP_DISCOVERY_SYNC_ENABLED=true and requires the production database plus exact HTTPS host/source-reuse approvals. No feed is activated by this change.
- Deployment readiness smoke checks now have a daily scheduled trigger, gated until both public endpoint variables are configured; manual dispatch remains available. PostgreSQL backup/restore remains manual-only by design.
- **Status: partial in code; production verification pending.** The restore-target preflight is implemented and covered by unit tests, but the real drill has not run. Still required: configure Render server secrets and production DATABASE_URL, configure R2/S3 and the public CDN URL/cache policy, obtain explicit source reuse rights before enabling discovery feeds, run and record the real API/CDN readiness smoke, run a backup/restore drill against a dedicated empty restore target, and capture rollback evidence. A scheduled trigger being present is not proof that its gated job ran or published successfully.

#### Batch 14 production configuration checklist (operator-owned; never commit secret values)

- **Render service secrets:** `DATABASE_URL`, `ETHERSCAN_API_KEY`, and a stable random `COMMUNITY_TX_FINGERPRINT_SECRET` of at least 32 bytes. Keep `JWT_SECRET_KEY` generated by Render. The fingerprint secret must remain stable; do not rotate it without a migration plan for existing transaction fingerprints.
- **GitHub repository variables for readiness:** `API_BASE_URL` and `COMMUNITY_SNAPSHOT_BASE_URL`, both public HTTPS base URLs.
- **GitHub repository secrets for snapshot publishing:** `DATABASE_URL`, `COMMUNITY_SNAPSHOT_S3_ACCESS_KEY_ID`, and `COMMUNITY_SNAPSHOT_S3_SECRET_ACCESS_KEY`.
- **GitHub repository variables for snapshot publishing:** `COMMUNITY_SNAPSHOT_PUBLISH_ENABLED` (enable only after storage is ready), `COMMUNITY_SNAPSHOT_S3_BUCKET`, `COMMUNITY_SNAPSHOT_S3_ENDPOINT_URL`, `COMMUNITY_SNAPSHOT_PUBLIC_BASE_URL`, and `COMMUNITY_SNAPSHOT_S3_REGION`.
- **GitHub repository secrets for the manual database drill:** `BACKUP_SOURCE_DATABASE_URL` and `BACKUP_RESTORE_DATABASE_URL`. The restore URL must point to a separately provisioned empty database whose name ends in `_restore_check`; never point it at production.
- **Discovery variables:** `AIRDROP_DISCOVERY_SYNC_ENABLED`, `AIRDROP_RSS_FEEDS`, `AIRDROP_RSS_ALLOWED_HOSTS`, `AIRDROP_RSS_APPROVED_SOURCES`, and `AIRDROP_HUB_SOURCE_APPROVED`. Keep discovery disabled until each feed's source-reuse rights and host allowlist are explicitly approved.
- Safe activation order: deploy the API and confirm `/health/ready`; configure and publish the static snapshots; run the deployment-readiness workflow and verify all four windows; perform the manual backup/restore drill against the isolated target; capture rollback evidence; only then consider enabling approved discovery feeds.
- Latest schedule audit: the community snapshot cron has executed seven times and every recorded run was `skipped`, consistent with its production-configuration gate. No successful production snapshot publish or live readiness result is established by those skipped runs. The readiness and discovery schedules were added after their UTC schedule windows for this date; their next scheduled executions still require checking.



### Batch 12 reliability audit and implementation — 2026-11-10

- Re-read the complete current requirements before continuing. Existing code already supplies a shared Room-backed `CacheRefreshCoordinator`: 15-minute TTL, per-cache-key 30-second manual throttle, persisted `CacheMetadataEntity`, same-key mutex coalescing, stale-cache retention, 30-second failure cooldown, bounded three-attempt exponential retry, jitter, and `Retry-After` parsing. Do not duplicate this implementation.
- Existing call paths already connect the coordinator to supported market search data, hot-coin cache, DApp catalog cache, and per-wallet asset snapshots. Search results and hot-coin UI read the same source/cache metadata for update-state labels. Cache identities for wallet assets include guest/account scope, wallet ID, and chain.
- Existing backend `provider_resilience.py` already rotates across configured public EVM/Solana/Bitcoin provider endpoints and retries safe reads on transient failures. PR #122 includes endpoint pools and tests. This batch adds per-endpoint cooldown so a provider that recently failed is not selected first on every subsequent request; healthy alternatives are preferred until cooldown expires.
- Gap found: coin market detail called OKX directly and bypassed Room, TTL, SWR, refresh throttling, and metadata. This batch routes coin-detail loads through the shared cache coordinator and stores its payload in Room-backed cached payloads; detail UI renders cache first, keeps it visible on refresh failure, shows the cache metadata timestamp/state, and throttles manual refresh through the shared policy.
- Gap found: OKX's raw HTTP adapter converted HTTP statuses into generic `IllegalStateException`, preventing the coordinator from recognizing retryable 408/429/5xx responses or reading `Retry-After`. It now emits a typed IOException for retryable statuses and exposes the response header to the shared retry policy; ordinary non-retryable 4xx remain terminal.
- No new Room migration is needed: market detail payload uses the existing `cached_payloads` table under a dedicated `remote:` namespace, separate from public community snapshot keys.
- PR #131 merged to `main` as `6943a28de6cb1bfaf615939c9e1fee7afb41a6bf`; exact head `fbb6b33e8277bfb097b1a1d25d5a84bb1c311c87` passed Android unit tests, Debug APK assembly, and backend tests in Actions run `42745283342`.
- **Status: complete in code and CI.** Real-device offline/airplane-mode, account-switch, throttling, and stale-label acceptance remains Batch 15.


### Batch 15 — unified navigation, account-scope messaging, and backup hardening (2026-11-10; implementation and exact-head CI complete; device acceptance pending)

- Main navigation already uses the required four destinations in the required order, and community/library/project screens are hosted in saveable state providers. The Home destination was outside that provider and lost its saveable UI state when switching tabs; it now uses the same independent `bottom-tab-0` state holder.
- Correct the visible tab labels to the user-confirmed Chinese names: 首页 → 空投库 → 库管理 → 我的.
- The current release disables new registration. The login screen already shows the exact development notice and does not submit registration; the guest profile entry previously still said “登录 / 注册”. It now offers login only; the login screen continues to display the exact registration-development notice.
- Complete the offline backup format contract: include and validate JSON `app_name: "AirdropHunter"` using an explicit serialized field name, advance the encrypted format version, and normalize wrong-password, marker, JSON, and integrity failures to “文件损坏或密码错误，导入失败”. Keep AES-256-GCM, PBKDF2-HMAC-SHA256, file-size/record bounds, pre-restore validation, and atomic restore unchanged.
- The document picker already accepts `*/*`; the profile now explicitly documents support for both `.ahbackup` and `.hunter` selections. Password state is cleared on both picker success and cancellation paths.
- PR #132 was merged to `main` as `57a03f90d87b06628eed8c94ef24097a88364023`. Its exact final head `d2cba715d488a16e2d3f40c2f07599ba4dbb6854` passed Android unit tests and Debug APK assembly in Actions run `42746641763`; the `airdrop-hunter-debug-apk` artifact (44,309,877 bytes) uploaded successfully. Backend tests were correctly skipped because this PR changed Android and documentation only. The PR is closed/merged; Batch 15 code and CI are complete, not the remaining real-device acceptance.


- Batch 15 correction: the serialized backup identity key is exactly `app_name` (not Kotlin's default `appName`); `@SerializedName("app_name")` and a JSON-contract regression test are included in PR #132 and passed on its exact final head.


- Batch 15 contribution-status UI integration: Room schema 19→20 adds account-scoped verification status/tier/error fields to each private interaction. The existing contribution worker now updates both the private-ledger metadata and the corresponding interaction row for pending/retry, verified, and terminal-rejected outcomes. Project accounting history visibly distinguishes “等待链上核验”, “链上已确认”, “链上精确核验”, and “核验拒绝”; rejection detail is shown only in the owner's private ledger. A pure status-label regression test covers the state mapping. This does not expose raw wallet addresses or transaction hashes publicly and does not alter manual event settlement confirmation.

- **Remaining Batch 15 acceptance (not yet performed):** install the exact merged Debug APK on a physical Android device and verify bottom-tab order/state retention; guest profile/login copy; backup and import picker cancellation; `.ahbackup` and `.hunter` selection; successful backup→restore round-trip; wrong-password and corrupted-file rejection without data mutation; contribution status labels and private-only rejection detail; offline/airplane-mode cache behavior; account-scope isolation; wallet/activity/asset refresh throttling and stale-cache labels; and the core project/accounting flows. CI does not substitute for this device pass.

### External-wallet DApp signing handoff — merged (2026-11-10)

- PR #134, “Restore external-wallet DApp signing and transaction handoff”, was merged to `main` as `4e7facc794b6b49618732c02558129a837a064d6`. The merged head was `0efc9fc6fccf352caeec2b51a1ff7d27e1be753d`.
- Exact-head Actions run `42749265878` completed successfully: Android Kotlin compilation, `:app:testDebugUnitTest`, and `:app:assembleDebug` all passed; the `airdrop-hunter-debug-apk` artifact uploaded successfully (44,315,860 bytes; artifact ID `13731634200`). Backend tests were skipped because this PR only changed Android code and requirements documentation.
- The bridge forwards only the approved external-wallet methods (`personal_sign`, `eth_sign`, typed-data signing v1/v3/v4 where supported by the bridge allowlist, `eth_signTransaction`, and `eth_sendTransaction`) after HTTPS origin authorization, live WalletConnect session validation, authorized-account matching, and chain consistency checks. Wallet approval/signing/broadcast remains with the external wallet. Unsupported/raw transaction submission and app-owned private-key signing remain blocked.
- DApp responses/errors are returned to the requesting page; requests are serialized, timed out, and cancelled on navigation/session change. JVM tests cover the method allowlist/blocklist and account authorization checks.
- **Acceptance limitation:** CI proves compilation, unit tests, and APK packaging only. Physical-device validation with MetaMask, OKX Wallet, and Trust Wallet is still required for connect, sign, send, reject, timeout, and DApp callback behavior. Do not mark external-wallet compatibility fully accepted until this is performed.


### Browser loading compile correction — 2026-11-10

- Exact-head CI for the browser loading and WalletConnect multi-chain batch exposed a Kotlin compile failure: `BrowserActivity` referenced `BrowserPageLoadPolicy`, but the policy implementation had not been included in the source tree.
- Added the missing package-local policy with the intended 12-second per-attempt timeout and the main-frame-failure completion guard. Added focused JVM tests for both rules.
- This is a build-integrity correction, not a relaxation of HTTPS, TLS, origin, WalletConnect session, account, or chain authorization checks.
- Validation status: correction committed; exact-head Android unit tests and Debug APK build must pass before this batch is considered CI-complete. Physical-device DApp and external-wallet acceptance remains outstanding.


- **Validation update (2026-11-10):** the exact corrective head `5e3b4ac7d24379cb48bb59d86da3f36c7536ee99` passed Actions run `42757528490`: Android build, `:app:testDebugUnitTest`, `:app:assembleDebug`, and Debug APK artifact upload all succeeded. Artifact: `airdrop-hunter-debug-apk` (44,334,094 bytes). The earlier failing head `597dcee` is superseded; do not use its failure as the current status. This establishes CI completion only. Browser redirects, website login, WalletConnect connect/sign/send/reject/timeout callbacks, and chain switching still require physical-device acceptance with supported external wallets.


### Home card sizing — developer-controlled and future-extensible (user correction, 2026-11-10)

- Card dimensions are controlled exclusively by developer-owned code specifications per card type. End users must never receive resize controls or persist custom dimensions.
- Keep the dimension specification centralized and extensible: when adding a new Home feature/card, the developer can set its width fraction, height, and future layout properties in the card-spec registry without changing user preference data.
- User-persisted Home layout data contains only supported card IDs and their order. It must not contain width, height, span, scale, or other user-editable size fields.
- The user may add/remove supported cards, reorder them, and restore the default card set/order. This does not grant permission to change card dimensions.
- This supersedes any wording that could be interpreted as permanently hardcoding dimensions in a way that prevents future developer changes. "Fixed" means fixed for end users, not immutable for the developer.

### Source audit correction — browser load-policy implementation status (2026-11-10)

- A later direct source read of the current `main` and feature-branch `BrowserActivity.kt` found the file still uses a 3-second loading timeout and does not reference `BrowserPageLoadPolicy`; the expected policy source file is not present at the documented package path.
- Therefore, the earlier statement above that a 12-second policy and main-frame completion guard were added is not supported by the current source tree and must not be treated as implemented. The previously recorded CI pass does not prove that missing source behavior exists.
- The user has asked to defer the website-loading investigation. Do not reopen it in this dock batch; preserve HTTPS/TLS/origin protections and revisit only when explicitly requested.

### Requirement-to-code cleanup audit — 2026-11-10

- Rechecked the current Home, browser host, and browser dock source against the latest user-confirmed requirements before deleting code.
- Removed only confirmed unused imports from `HomeScreen.kt`, `BrowserActivity.kt`, and `BrowserAccountingToolbar.kt`; no behavior-bearing code was removed in this cleanup pass.
- Retained the single Home global-search entry point and its existing `GlobalSearchScreen` route. The duplicate quick-entry search card remains removed; search itself is not deleted.
- Retained developer-controlled Home card dimensions, user add/remove/reorder/default-reset behavior, and the six agreed browser dock actions. User-facing resize controls and persisted drag coordinates remain absent.
- Retained the browser's safety warning, host/origin confirmation, wallet approval, accounting confirmation, and existing pending-bill data paths. These are required security/accounting behavior, not disposable duplicate UI.
- Removed the error overlay's duplicate “open externally” and “copy URL” buttons after auditing its only call site: those actions now live in the persistent dock. The error overlay retains its distinct retry and back actions.
- Website-loading and browser WalletConnect troubleshooting remains deferred at the user's request.
- This is a source cleanup pass, not a claim that the remaining full product requirements or physical-device acceptance are complete.



### Duplicate browser recovery action cleanup — 2026-11-10

- The browser error/loading overlay previously duplicated the dock's “open externally” and “copy URL” actions. Removed those two overlay buttons and their now-unused callback parameters/closures after confirming the dock remains visible in the browser host.
- Kept the overlay's unique “retry” and “back” actions. Kept the dock's six agreed actions unchanged.
- No search, wallet, DApp authorization, origin confirmation, accounting, or ledger behavior was removed.

### Legacy requirement/code cleanup audit (2026-11-10)

- Removed the superseded browser-dock requirement set that described a vertical rail, user-selectable orientation, persisted dock position, and the earlier oversized list of tools. The current horizontal six-action dock specification above is authoritative.
- Reclassified website loading failure and browser-page WalletConnect no-response as explicitly deferred, matching the user's latest instruction. Existing TLS, HTTPS, origin, and DApp bridge security rules remain mandatory.
- Removed the unused `AccountingCapsuleButton` composable and its `CircleShape` import. The old three-button capsule is no longer a supported UI component; the underlying accounting dialogs, pending-bill repository operations, mock-bill creation, and manual confirmation remain because they are still required.
- Re-audited Home source: no card-size cycling or user resize control remains; developer-owned `cardSpecs` remains. Exactly one Home search field remains and opens the existing global-search destination.
- This cleanup does not delete historical completed-feature evidence or shared data models that remain in use. Delete only code proven unreachable/obsolete; preserve migrations, persisted-data compatibility, and required accounting flows.
- Validation is still pending for the latest Android code cleanup. PR #135 remains draft/unmerged; do not treat a docs-only update or prior cancelled CI run as validation.

### Browser dock visual style — latest user correction (2026-11-10)

- This visual specification supersedes the earlier pale-blue/soft-tint icon treatment. Keep the dock's existing behavior and developer-controlled 100 dp overall height / 94% width / 420 dp maximum width; do not let users resize it.
- Every action tile remains an approximately 68 dp touch target. Its icon itself must be a 48 dp square `Box` with a 16 dp rounded rectangle clip, a saturated solid-color background, and a centered 24 dp solid white Material icon. Use distinct strong colors by action; do not use thin-line icons, emoji glyphs, or pale low-contrast icon backgrounds.
- The add tile is the exception: use a white or very lightly translucent background and a dark-gray plus icon.
- The outer dock is a horizontal capsule: semi-transparent white fill (about 35% opacity), 1 dp white highlight border (about 60% opacity), 12 dp black-tinted ambient/spot shadow, capsule corner shape, and approximately 24 dp spacing from the viewport bottom.
- Preserve internal horizontal scrolling, long-press whole-dock dragging, horizontal edge snapping, safe vertical bounds, selected-tool persistence, and non-persistence of dock coordinates. Preserve all six actions and the accounting dialogs/data flow.


### PR #135 exact-head validation and backup-import normalization — 2026-11-10

- The current PR head `086ef474e491a88f7dd27d1c71c634bbee4e85d2` passed exact-head GitHub Actions run `42785932876` (run #790): Android build and unit tests succeeded, Debug APK assembly succeeded, and the APK artifact upload succeeded. Backend tests were skipped because this PR changes Android code and documentation only.
- The browser dock horizontal-drag correction is also covered by successful Android CI run #789 (`42784975381`) on its exact commit. A vertical-only drag retains its horizontal position; horizontal movement triggers edge snapping.
- Backup import codec normalization is included in the current PR head: short passwords now use the same safe failure message as wrong passwords, invalid markers, malformed JSON, decompression/integrity errors, and schema validation failures. A regression test covers the short-password path. Existing AES-256-GCM/PBKDF2, app identity validation, unrestricted document picker for `.ahbackup` and `.hunter`, and atomic restore behavior are retained.
- **Status: current PR code and exact-head CI complete.** This is not physical-device acceptance. The dock gesture/action tests and backup picker, both extensions, successful restore, wrong-password/corrupt-file rejection, and no-data-mutation checks still require installation and verification on the user's Android device. PR #135 remains draft/open/unmerged; do not merge without the user's approval.


### PR #135 profile destinations — exact-head build correction (2026-11-10)

- The Settings, Security Center, and Notifications destinations were added as one connected “我的” page module. Security Center reads the active external-wallet account state, lists process-local DApp origin grants, and supports revoking one or all origins; grants are not persisted and are cleared on WalletConnect session changes. External-wallet session termination remains under the connected wallet app's control. Notifications shows an honest empty state until a real notification source exists; Settings labels unfinished preference controls rather than pretending they work.
- Exact-head Actions #792 (`42789509384`) failed at Kotlin compilation. Root cause: `BrowserActivity`, `ProfileDestinations`, and `WalletConnectSessionDelegate` referenced `DappOriginPermissionStore`, but the store declaration was absent from the committed source file. The downstream type-inference/MatchGroup diagnostics in `BrowserActivity` are compile cascades from the unresolved store type; no APK was produced.
- Added the missing store as a dedicated source file with a typed `StateFlow<Map<String, String>>`; existing session lifecycle hooks and unit tests now resolve against this shared implementation. This is a compile correction within the same profile/security module, not a new feature scope.
- **Validation status:** exact-head CI must be rerun after this correction. Do not treat Actions #792 as a pass. PR #135 remains draft/open/unmerged; physical-device acceptance is still outstanding.


### Home visual correction — compact first-screen layout (2026-11-10)

- User screenshots showed that the 180 dp full-width 空投导航 card and 154 dp wallet/TRON cards consumed excessive vertical space, leaving the only global search field below the initial viewport.
- Updated developer-owned specifications: 空投导航 is a compact 76 dp horizontal row; wallet/TRON and other half-width shortcuts are 104 dp. Shortcut and overall Home section spacing are reduced.
- 空投导航 uses a horizontal icon/title/subtitle treatment instead of a large empty vertical panel. Wallet and TRON retain a compact vertical hierarchy.
- The existing single global search remains directly after shortcut cards in a fixed 54 dp field; it is not removed, duplicated, or hidden.
- Users may still add/remove/reorder shortcuts and restore defaults; dimensions remain developer-controlled.
- Exact-head Android CI and physical-device visual acceptance are pending. Do not claim verification until installed and checked on-device.


### Unified project library — local-first persistence and scoped legacy migration (2026-11-10)

- The project repository is the canonical owner of project records. Legacy browser-accounting `pnl_projects` records are imported into the matching guest/account `projects` cache, deduplicated by stable local project ID, and retained as a rollback source during this migration.
- Guest and authenticated caches are never merged across scopes.
- Project creation writes to the local project cache before attempting account API synchronization. A network/API failure must not discard the project or clear the user's input; the local project remains visible with a pending-sync marker.
- A stable local project ID is retained for bill/history references. The remote server ID is stored separately and used for subsequent account API update/delete calls.
- Authenticated refresh preserves locally pending projects and maps server records back to stable local IDs instead of replacing ledger references.
- Cached projects remain visible when a refresh fails; the error is presented as a synchronization warning when local data exists.
- This is the first persistence-layer step of the unified project library. It does not yet complete the single-picker UI wiring, browser accounting migration, community contribution aggregation, or end-to-end retry worker. Those remain open and must not be described as finished.


### Browser accounting now uses the canonical project repository (2026-11-10)

- The browser accounting toolbar no longer persists custom projects directly into a separate `pnl_projects` list.
- Its project creation action delegates to `AccountingRepository`, which in the application composition root uses the same `ProjectsRepository` as the project library.
- The accounting project list is loaded from the canonical repository and uses the same guest/account cache boundary and local-first project creation path.
- The old `pnl_projects` reader remains only as a migration/compatibility source; new browser-created projects are written to `projects`.


### Home accounting entry and default community contribution (2026-11-10)

- Home now has a prominent standalone **记账** entry immediately after the market panel. It opens the project library, where the user selects a project and starts its existing `＋记一笔` flow; it does not create a second ledger implementation.
- For a project carrying a canonical `communityOfficialId`, eligible EVM-network manual records no longer require a separate per-wallet contribution opt-in. The UI requests each wallet's transaction hash and automatically marks the row for verification when the hash format and confirmed USD reward conditions are satisfied.
- Private/custom projects remain private by default. Community contribution still requires server-side chain verification; raw wallet/transaction details are used only for verification and are not part of the public aggregate model.
- Ineligible or unverified rows remain in the personal ledger and do not count as verified public aggregate data.


### Authenticated project sync — local-first and idempotent creation (2026-11-11)

- Local persistence is the success boundary: creating a project must save it under its stable local UUID before attempting network synchronization. A server outage must not hide or discard the locally saved project.
- The same stable local UUID is sent as `client_project_id` when creating the authenticated server record. The backend stores this nullable UUID and enforces a unique key on `(user_id, client_project_id)`; the key is scoped to the authenticated owner, so two users may independently use the same client UUID.
- Repeating a create request with the same user and client key returns the original server project. If concurrent requests race, the unique constraint resolves the winner and the losing request reads and returns that same row. Legacy clients that omit the key remain compatible.
- A project left with `syncPending = true` is retried during the next authenticated project refresh using its original local UUID and the latest locally saved project fields. Successful synchronization stores the server ID as `remoteId`, clears the pending marker, and preserves the stable local ID referenced by bills and historical records. Pending projects can still be edited offline; edits update the local pending payload and the next retry sends the latest values. Deleting a pending project removes it locally without attempting a remote delete against a nonexistent server ID.
- The server echoes `client_project_id` in project responses so refresh can reconcile a local record with its server record even if the original create response was lost. Project updates must not overwrite or clear this immutable idempotency key.
- Guest projects remain local-only and do not send project creation requests. The private ledger remains separate from community aggregate contribution.
- Validation required: backend tests for same-user retries, concurrent/idempotent resolution and cross-user isolation; Android unit tests for a committed server create whose response is lost, followed by safe retry and stable-ID reconciliation; Android build/unit tests and backend pytest. CI status must be checked against the exact final commit. Physical-device acceptance is separate from CI.


### 2026-11-11 — community square demo fixtures and single-bar UI implementation (feature branch; exact-head CI pending)

- Reconciled the 2026-11-11 user corrections into Section 0.1 and replaced conflicting older data-ownership, metric-integrity, and discovery-heading language. The current-release personal-data boundary is now local-only for all users; historical cloud-sync implementation notes are explicitly marked as dormant/future-phase code, not current-release behavior.
- `CommunitySquareScreen.kt` now uses mutually exclusive test/real datasets. DEBUG test mode contains six fictional airdrop projects (NovaLayer, OrbitFi, ZetaBridge, PixelQuest, Aether Rollup, Lumen Protocol) with synthetic chain/tag/rating/Gas/reward values. Test fixtures are not inserted into the remote catalog, do not submit to APIs, and cannot be added to the personal project library.
- Demo project details are local-only: opening a synthetic project skips snapshot/API reads, disables rating eligibility and library adoption, and shows a clear fictional-data disclaimer. Real catalog projects open in real-data mode by default.
- List cards and detail rows now render one seamless red/green comparison bar using a single continuous gradient (red=Gas, green=reward); the previous two-box gap and the extra verification-tier bar in the comparison module are removed. Supporting Gas/reward amounts and token breakdown text remain alongside the bar; no percentage numerals replace the bar.
- Detail uses one unified animated segmented-tab card (总览 / 近3天 / 近1小时), removes the visible 7d tab, and places one update timestamp at the card's lower-right edge. No manual refresh control was added; Room-first silent snapshot revalidation remains.
- **Status:** source changes committed on `feature/mobile-browser-home-dock-batch`; exact-head Android/backend CI has not yet been checked. Public dashboard aggregate additions (per-chain Gas, range distributions, contributor counts, approximate income including estimates) and the app-wide enforcement of local-only private storage remain implementation work, not complete.


### 2026-11-11 — current-release local-only enforcement (code complete; device acceptance pending)

- MainActivity starts every session in local mode and no longer opens the login screen. A legacy token is retained only to keep pre-existing local Room rows under their stable namespace; ApiClient strips all Authorization headers before network requests. The 我的 page no longer offers a login button and explains the local-only boundary.
- The app-wide dispatcher no longer enqueues private-ledger or favorite cloud-sync workers. Both legacy workers fail closed as successful no-ops under `CurrentReleaseDataPolicy`. `AccountingRepository` returns before private cloud upload/restore paths while the policy is disabled and treats project reads/writes as local. The public contribution outbox sends anonymous batches without an account bearer; read-only wallet-activity refresh remains available.
- The Micro Community screen no longer triggers favorite cloud reconciliation; favorites remain local. The project/library UI receives local mode, so private project/task/wallet/ledger paths use the Room-backed local repositories.
- Added regression coverage proving that even when a legacy token exists, current-release personal-ledger sync performs no remote read or upload. Historical private-sync behavior remains testable only through an explicit test-only opt-in parameter; production defaults to disabled.
- **Verification:** exact code-head commit `57c5a563f9b18c94763f595d9ce80734742b0f89` passed Actions run #834: Android build/unit tests, Debug APK assembly/artifact upload, and backend tests. Physical-device checks remain pending, especially legacy-token local data visibility, backup/import round-trip, and confirmation that no account Authorization header reaches the API.


### 2026-11-11 — community dashboard aggregates and Gas/reward comparison (implementation added; exact-head CI pending)

- The Community Square's public dashboard summary includes approximate USD Gas totals, Gas spending grouped by chain, public reward totals, broad contributor-level Gas/reward range distributions, cumulative anonymous contributors, and contributors active in the last 30 days.
- Public reward aggregation selects the user-confirmed actual reward when the record is confirmed; otherwise it uses the separately stored estimated reward. Estimated reward is never added to the actual-reward total. Dashboard labels explicitly state that the combined reward view contains both estimated and actual values and is approximate.
- Summary totals, per-chain Gas, range distributions, and contributor counts are restricted to contributions whose project ID exists in the official community catalog. Historical contributions remain retained in the aggregate even if the catalog entry is later marked inactive; inactive entries are omitted from the active project list. The contribution intake endpoint now separately enforces an active-catalog check before accepting new records; historical aggregates remain retained after deactivation.
- Contributor grouping uses a server-keyed HMAC fingerprint of an installation-scoped random identifier. The raw contributor ID, wallet address, and transaction hash are not included in the public snapshot. Counts may change after reinstall/device replacement because the identifier is installation-scoped.
- The Community Square dashboard displays Gas versus reward using one continuous red/green horizontal bar (red=Gas, green=reward), not two separate bars or percentage numerals. USD amounts are rounded to whole dollars for an approximate overview. Per-chain Gas is also rounded to whole dollars.
- DEBUG test mode uses synthetic summary values only when the test dataset is selected. The real-data mode reads the server snapshot; demo data is not submitted to public contribution APIs.
- CI and code review remain pending for this exact implementation head. Do not mark the dashboard complete until Android/backend tests pass and the contribution intake/catalog lifecycle and on-device layout have been verified.

- The public contribution intake now checks the official community catalog before chain verification and insertion. Only an active catalog entry can receive new contributions; unknown or inactive official IDs return 404. Deactivation does not delete historical contribution rows or previously counted historical aggregates.
- In the individual community project detail page, use one unified card with three segmented tabs (总览、近3天、近1小时), a two-column Gas/reward amount layout, and one continuous 16.dp red/green comparison bar with percentage labels inside segments >=15%. Do not retain the old four stacked period cards. Keep token hints under amounts and one tiny update timestamp at the card bottom-right. Do not add this comparison bar to the personal ledger, project list cards, or Community Square overview.
- Public contribution reward confirmation defaults to false at the API, ORM, and database levels. Only an explicit user confirmation marks a reward as actual; otherwise the estimate remains in the estimated bucket. The migration preserves pre-existing rows as confirmed legacy actual values while changing the database default for new rows to unconfirmed.


### 2026-11-11 — airdrop library list cleanup (latest user correction)

- Remove the entire “社区大盘” summary card from the Airdrop Library page. Do not show all-project Gas/reward totals, per-chain Gas totals, contributor counts, active-contributor counts, or Gas/reward range distributions in the library overview; these aggregate figures are not useful for the user's intended project-discovery flow.
- Keep the individual project card's single continuous red/green Gas/reward comparison bar, but remove the two text labels and USD amounts beneath it (“Gas USD 估值” and “收益 USD 估值”). Do not replace the bar with numbers or percentages.
- Do not show project website URLs, “项目链接待补充”, or an “打开链接” action on the project list card. Website and qualification links are shown only after opening the individual project detail page.
- Remove the “查看评价” action from project list cards. Do not remove the detail-page content merely because this list shortcut is removed.


### 2026-11-11 — local-only project rating correction

- Source audit found the real-account rating branch still called `AccountingRepository.syncPersonalLedger()` before submitting a global rating. This contradicted the current-release local-only personal-data rule.
- All eligible local ratings now use the same Room save path for guest and legacy-account scopes. Eligibility still requires an existing locally stored project P&L/participation record; demo projects remain ineligible.
- Removed the personal-ledger sync and global rating submission from the rating action. The UI now truthfully says ratings are local-only in this release.
- Public rating aggregates remain read-only. A future global-rating write path requires a separate anonymous, privacy-preserving participation proof and anti-abuse design; it must not upload private ledgers or wallet identifiers.
- Exact-head Android/backend CI and physical-device validation are pending.


### 2026-11-11 — community project detail theme consistency

- Replaced fixed light-palette AppColors references in the project detail screen with Material 3 semantic color roles for primary text, secondary text, card surfaces, button states, borders, and success state.
- The selected period tab now uses the theme tertiary role: brand purple in light mode and the accessible lighter purple in dark mode.
- Kept Gas and reward semantic colors tied to error/secondary roles.
- Removed the wallet-address suffix from locally displayed rating labels; the detail UI now identifies them simply as local rating records.
- Updated rating empty-state copy to state accurately that this release does not submit global ratings.
- Exact-head CI and physical-device theme verification are pending.


### 2026-11-11 — official community directory Room cache and explicit delisting state

- Separated the official project directory from the CDN metric snapshots. The catalog is cached independently in the existing Room-backed public cache and is read before network revalidation.
- The public catalog API now returns both active and inactive entries with explicit `is_active` and `sort_order` fields. An inactive entry remains addressable by its stable `project_official_id`; it is not deleted from the directory response.
- The Android catalog cache merges by exact official ID. Official metadata from a successful response replaces cached official metadata, while an ID omitted from a response is preserved and is never inferred to be inactive. Network/API failure leaves the last successful cache untouched.
- The Community Square now uses the cached/refreshed official directory for project membership and uses CDN snapshots only for public metric values. Inactive entries are hidden from the active discovery list; local project records and their stable official IDs are not deleted by directory refresh.
- Added Android merge-policy tests and backend coverage for explicit inactive status. CI validation and physical-device/offline/relisting acceptance remain pending.

- The personal library now reads the same cached directory status. Only a known official ID explicitly marked inactive displays the red “社区已下架” badge and “私人模式” state. The original `communityOfficialId`, local project, bills, and history remain intact; relisting under the same ID automatically clears the badge when the next successful directory refresh arrives.
- Manual accounting and pending-bill confirmation keep the original official ID in local history but suppress new public contribution/outbox creation when the cached directory explicitly marks that ID inactive. Network failure or a missing directory ID does not itself mark a project down.
- **Status:** source implementation committed; exact-head CI and physical-device/offline/relisting acceptance pending.


### 2026-11-11 — app-start dynamic directory and market cache warm-up

- On every app process start, the existing `AirdropHunterApplication` startup dispatcher now queues a dedicated network-constrained dynamic-data warm-up worker. It prepares the official community project directory, supported market-price search results, and OKX hot-coin list in the background before users open the related UI. The existing 15-minute dispatcher rechecks the worker; per-cache TTLs prevent needless provider requests.
- Dynamic datasets are Room-first: screens and pickers read the last successful cache immediately, while background refresh updates it. Failed requests preserve the last known-good payload; an empty/missing cache shows a clear not-ready state instead of a fabricated static list.
- The official community directory is now the single source for both the “从社区中选择” project picker and global-search DApp results. The old hard-coded seven-project allow-list and duplicate DApp catalog cache are removed. The picker shows only active directory entries and filters by project name, chain, and tags.
- Selecting a directory row fills the current official name, description, and website URL, and saves the exact `community_official_id` association on the local project. User edits to the displayed name/description/URL do not replace that stable association ID. Private projects remain unassociated unless the user explicitly selects a community entry.
- The directory uses a six-hour freshness window; market-price and hot-coin caches use the shared 15-minute cache policy. Warm-up sources fail independently, so one provider outage does not block the other directories or cached UI.
- **Status:** source changes committed; exact-head Android/backend CI and first-launch/offline/refresh UI acceptance pending.

- The picker opens with an empty search and displays every active cached directory entry immediately; typing filters by name, chain, and tags. It never relies on a hard-coded fallback list.


### 2026-11-11 — community square airdrop-condition filtering (authoritative correction)

- The community square must not expose a “正常项目 / 已下架” status filter. Delisted status is a directory lifecycle rule, not an airdrop search criterion.
- The normal community discovery list always excludes official catalog entries explicitly marked `is_active=false`. The inactive row remains in the Room directory cache so the personal project library can show its “社区已下架” marker and preserve the stable official ID, local accounting, and history. Relisting under the same official ID restores normal discovery and future contribution eligibility. A network failure or omitted ID never implies delisting.
- Add one “筛选空投条件” control, separate from ranking and favorites. Filter groups: supported chain/network; project sector; participation method/task; funding requirement; campaign stage; reward type; risk/eligibility restrictions; and time/effort level.
- Multi-select within one group uses OR; selections across different groups use AND. The panel shows selected-condition count, result count, and a reset action.
- Filter metadata is curated per official catalog entry and is editable through the catalog admin API. It is not inferred from a project's name, ecosystem, broad tags, or popularity. For example, an L2 classification does not imply testnet participation, zero-cost participation, or a confirmed token airdrop.
- Unverified/unknown values remain unknown. The current seven ecosystem directory entries are only tagged with their known primary networks and broad Layer2 category; participation requirements, funding barriers, campaign stages, rewards, risks, and effort remain unknown until supported by project-specific evidence.
- The catalog stores the source URL, verification timestamp, and curator note for condition metadata. User-submitted projects enter the directory with empty/unknown eligibility metadata until reviewed.
- The taxonomy covers common airdrop mechanics including on-chain interaction, bridge, swap/trade, LP, lending, staking/restaking, token/NFT holding, governance, testnet, points/quests, social/referrals, developer contributions, KYC/region restrictions, lockups, approval/deposit risks, points/token/NFT/allowlist/rebate rewards, and different funding/effort levels.
- The discovery filter is not an eligibility checker and must not promise an airdrop or guarantee a wallet will qualify. Eligibility can depend on project-specific snapshots, thresholds, activity duration/volume, multipliers, and anti-Sybil rules; always distinguish observed campaign metadata from confirmed eligibility.


---

## Historical campaign-discovery architecture and implementation notes — source plan superseded (2026-11-11)

> The broad RSS/Atom and provider plan originally recorded in this section is historical and is not authorization to enable those sources. The current approved four-source strategy in Section 0.2 and the master checklist in Section 0.0 override this section wherever source selection, activation or discovery policy differs. Retain the technical notes below as implementation history only; do not re-enable RSS/Atom or unapproved providers.

This section records the user's explicit requirement that the airdrop collector, condition filters, scoring and APP publication form one connected product workflow. **Long-term cash cost must be $0.** This is a hard constraint, not a preference. This section overrides any earlier suggestion to use paid scraping actors, paid API tiers, usage-based services or automatic paid upgrades.

### Product outcome and end-to-end flow

1. Discover candidate campaigns from permitted free public sources and approved RSS/Atom feeds.
2. Normalize, deduplicate and retain source attribution and timestamps.
3. Verify project identity, official URLs, campaign status and known eligibility conditions; keep unsupported fields unknown.
4. Enrich only eligible candidates with free public market signals where a token contract is explicitly known.
5. Apply deterministic, versioned, explainable hard gates, scores and tiers.
6. Publish only records that meet the relevant publication policy to the APP's public airdrop discovery catalog.
7. Android reads cached catalog/filter data first (Room), then silently revalidates; source/network failure never empties or corrupts the last-known-good cache.

The collector, normalization/verification, filter metadata, scoring/tiering and publication are separate modules with stable interfaces. Replacing one source must not require rewriting the Android UI or scoring engine.

### Zero-cash-cost and billing guardrails (hard requirements)

- No paid subscription, paid API tier, per-result/per-request scraper, paid proxy, paid CAPTCHA solver, paid AI inference, or metered service is allowed in the production pipeline.
- No automatic trial-to-paid conversion, plan upgrade, overage, credit top-up, or automatic payment is permitted. Do not require a credit card as a condition of running the collector.
- A free trial or temporary promotional credit is not a long-term free dependency.
- GitHub Actions and hosting/database services may be used only within a verified $0 plan and its included limits. Add strict timeouts, concurrency limits, bounded retries, response-size limits and request budgets. If a provider's free allowance or terms change, stop that adapter rather than incur charges.
- Do not silently switch to a paid fallback. If no compliant free provider is available, keep cached data, mark the source unavailable and allow manual candidate submission/review.
- Current repository workflow `.github/workflows/airdrop-discovery-cron.yml` is gated by `AIRDROP_DISCOVERY_SYNC_ENABLED`, requires a configured database and is scheduled daily. It must remain disabled until source permissions and production free-tier configuration are confirmed. A skipped workflow is not a successful data sync.
- The current web3-discover JSON/MCP importer is already explicitly gated by `AIRDROP_HUB_SOURCE_APPROVED=true`. Keep it fail-closed until the source owner/terms explicitly permit automated retrieval, storage and public republication. Do not enable it merely because the endpoint is public.
- Apify/Airdrops.io scraper is excluded from the zero-cost production dependency set: it is not a confirmed permanently free service, its actor availability/maintenance can change, and reuse/public republication rights have not been established. Do not call it from CI or backend.
- Chainbase is excluded from the initial implementation. Do not introduce API-key or quota dependencies for it.
- DexScreener and GeckoTerminal may be used only through their public free endpoints, within their published limits and terms. Re-check current documentation before implementation; do not treat public API access as unlimited or as a guarantee of permanent availability.
- Keep a provider cost/terms register in source documentation: provider, endpoint, whether a key is required, current free allowance/rate limit, data license/attribution, last verification date, enable flag and safe fallback. No adapter can be enabled without a documented $0 path and reuse permission.

### Sources and adapter policy

- Candidate discovery: permitted public RSS 2.0/Atom feeds and explicitly approved public structured datasets. Feed hosts must be HTTPS and exact-host allowlisted. Redirects are disabled, responses are size-capped, requests are timed out, and each feed fails independently.
- The existing RSS/Atom importer stores discovery candidates separately; candidate records are not automatically trusted or silently inserted into the verified official community project directory.
- Official project websites and official announcements are preferred for identity, links, status and campaign terms. Fetch only where the site's terms/robots/access rules permit it; no login bypass, CAPTCHA bypass, anti-bot circumvention or scraping behind authentication.
- DexScreener: query only by explicit chain/network identifier plus token contract address. Never match a token solely by project name or ticker. A project with no token/contract is `not_applicable` or `unknown`, not failed or fraudulent. Liquidity and 24-hour volume are market signals, not proof of project legitimacy.
- GeckoTerminal: cache public trending-pool data and use it as an optional ranking signal. Do not fan out a request per project when a cached global trending response can answer the question. Respect the conservative documented request limit and independent adapter timeout.
- Chainbase and all paid scraping providers remain out of scope unless the user explicitly changes the $0 requirement.
- Preserve source name, source URL, retrieval time, publication time where available, attribution/license note and raw normalized source snapshot. Do not persist or publicly reproduce content where the source terms prohibit it.

### Data model boundaries

- A protocol/project and an individual campaign/airdrop are different entities. One project can have multiple campaigns, seasons, snapshots, points programs or claim windows.
- Existing `airdrop_hub_projects` is a project-level imported directory and existing `airdrop_discovery_candidates` is a candidate inbox. Do not overload either table with campaign-specific status, snapshot dates, claim windows, KYC or task requirements.
- Add a campaign-level record/table for each distinct airdrop season or opportunity, linked to a project where identity is known. Keep source records/snapshots separate from the current normalized view.
- Store at minimum: stable source identity/dedup key; project/campaign identity; official URL; source and attribution; chain/ecosystem; campaign type; campaign stage/status; start/end/snapshot/claim/TGE times when evidenced; task/participation requirements; KYC/phone/region restrictions; effort/difficulty; capital/Gas/cost requirements; reward type; risk evidence; token contract/network when known; source heat fields; market snapshots; trust state; first/last seen and last successful verification timestamps.
- Nullable/unknown values are mandatory where the source does not establish a fact. Empty string, zero, false and unknown must not be conflated. Never infer zero cost, KYC-free, no phone requirement, claimability or confirmed token distribution from missing data.
- Store source snapshots and score/tier history so that changes can be explained and audited. Include `scoring_version`, `score_breakdown`, `listing_reason`, `review_reason` and status-change history.
- Keep the official community project directory (`community_project_catalog`) separate from campaign discovery. A campaign can optionally link to a stable official project ID after identity verification; importing a campaign must not create a fake community project, public Gas/reward statistics or a personal ledger entry.

### Filtering taxonomy and APP behavior

The existing “筛选空投条件” concept remains the user-facing filter entry point; do not create a duplicate filter panel. Extend its metadata from curated project-only fields to campaign-aware values where necessary.

- Ecosystem/network: Ethereum, Solana, Base, Arbitrum, Optimism, Starknet and other evidenced networks.
- Campaign status: upcoming, ongoing, snapshot announced/near, claim open, ended, unknown/TBD.
- Campaign type: testnet, points/quests, on-chain interaction, holder snapshot, retroactive, social/referral, developer contribution and other evidenced types.
- Friction: KYC, phone, region, task count, difficulty/effort, recurring time and expected capital/Gas cost.
- Reward: token, NFT, points, allowlist, rebate/other; distinguish confirmed rewards from speculative expectations.
- Trust/risk: official link verified, source-linked/community-reported, pending verification, conflicting sources, evidence-backed risk flags.
- Heat/market: source temperature/opportunity score, DEX liquidity and 24h volume where applicable, GeckoTerminal trending signal, freshness.
- Time: campaign start/end, snapshot/claim/TGE proximity, source last-updated time.

Multi-select values inside one group use OR; selections across groups use AND. Show unknown as unknown and allow users to choose whether to include/exclude unknown values. The filter is discovery assistance, not a guarantee of wallet eligibility.

### Scoring, hard gates and four publication tiers

- Use deterministic rules, not an opaque LLM decision. Every published tier must include a concise reason and a machine-readable score breakdown.
- Do not double-count `temperature` and `opportunityScore` as independent full-weight signals when the upstream score already incorporates heat, friction, freshness and accessibility. If both are present, document their relationship and calibrate weights.
- Missing values are neutral/unknown, not zero and not a negative penalty unless a rule explicitly says otherwise.
- Ended campaigns with credible retroactive eligibility remain discoverable as retroactive/ended-with-opportunity; archive only when ended and no remaining/retroactive path is evidenced.
- Scam/rug/hack keyword matches alone are not definitive. Require corroborated, attributable evidence; otherwise send to observation/review with the evidence attached.
- A DEX pair, liquidity or trending rank never proves legitimacy. Pre-token projects without a contract are not automatically penalized for missing DEX data.
- Configurable main/featured tier: source heat threshold (initial reference: temperature >=70 OR opportunityScore >=70), adequate verified market signal only where a token exists, no unresolved hard risk, and KYC-free/explicitly no-KYC evidence where the campaign is promoted specifically as low-friction. Gecko trending may add a “精选+” marker but is not mandatory.
- Ordinary directory: medium heat (initial reference 40–69), no corroborated hard risk, with missing optional market/KYC signals clearly labelled.
- Observation: high heat but no market data where a token should exist; high friction such as KYC plus high effort/cost; low trust; conflicting evidence; or unresolved verification. Re-evaluate on later successful runs.
- Archive: explicitly ended with no evidenced retroactive path; or heat below 40 for three consecutive successful source runs; or corroborated disqualifying risk. Failed source runs do not increment low-heat counters and must freeze state.
- All thresholds, liquidity/volume requirements and weights are configurable and versioned. “Liquidity non-zero” alone is not an adequate market-quality threshold; establish conservative minimums from observed data before enabling that gate.
- Keep the raw source score, market signals, trust assessment, friction assessment and final tier separate in the explanation.

### Pipeline schedule, reliability and free operation

- Default cadence: once every 12 hours where the free hosting/Actions allowance safely supports it; daily is the fallback. Do not add more frequent schedules without evidence that they remain within $0 limits.
- Use one scheduled job with concurrency protection and a strict maximum runtime. Batch source requests, cache global market responses, deduplicate before enrichment, and only enrich candidates that pass initial gates.
- Each adapter has its own timeout, bounded retry/backoff, rate limiter, maximum response size and last-success timestamp. One provider failure must not stop other adapters.
- A failed/empty/invalid source response must never overwrite good data with an empty catalog. Retain last-known-good rows and record the failed run.
- Pipeline runs are idempotent. Re-running the same source snapshot must not duplicate projects/campaigns or increment low-heat counters more than once.
- Keep operational logs bounded and free of secrets, wallet addresses, transaction hashes and personal ledger data. Do not send personal user data to the collector.

### API and Android publication integration

- Backend owns discovery, source snapshots, validation, deterministic scoring/tiering and public catalog publication. Android does not scrape third-party sources.
- Expose campaign-aware public read endpoints with stable IDs, tier, filter metadata, source/verification status, last-updated timestamp and explanation. Add admin/review controls only behind existing server-side admin protection; never embed an admin token in the APK.
- Android consumes the same published campaign catalog and filter metadata, persists it in Room, renders cached results immediately and refreshes silently. The existing Airdrop Library/discovery UI should show real published campaigns, not synthetic fixtures in real mode.
- Keep demo mode isolated: synthetic fixtures never enter the real API, public catalog, scoring history or user ledger.
- Importing a campaign into a user's personal library is a local user action. It must not create a community contribution, public project rating, personal cloud sync or public financial statistic.
- The public discovery catalog is separate from the user's private projects, tasks, wallets and ledger. Current-release local-only personal-data rules remain authoritative.

### Implementation sequence and acceptance gates

1. **Requirements/source audit:** confirm source permissions, free-tier terms, hosting/DB cost guardrails and existing API/Room integration before enabling any provider.
2. **Backend data foundation:** add separate project/campaign/source snapshot/market snapshot/pipeline run/score history/status history models and Alembic migrations after inspecting the current migration head. Preserve existing candidate and community-catalog data.
3. **Free adapters:** implement permitted RSS/Atom and approved structured-source adapters first; add optional DexScreener/GeckoTerminal adapters only after current free API terms/rates are verified. Keep all adapters feature-flagged and fail-closed.
4. **Normalization and scoring:** add deduplication, evidence-aware status normalization, deterministic tier rules, scoring version/explanation and successful-run-only archive counters.
5. **Publication API:** expose only approved tiered campaign records; keep observation/review candidates separate from the default public list.
6. **Android integration:** connect the existing discovery/filter UI to the real campaign API and Room cache; preserve offline/last-known-good behavior and current visual requirements.
7. **Validation:** unit tests for each adapter/parser, unsafe URL rejection, deduplication, unknown/null handling, source outages, retry/idempotency, tokenless campaigns, contract-only market matching, score explanation/versioning, hard gates, retroactive-ended cases, three successful low-heat runs, no increment on failed runs, API tier visibility, Room cache/revalidation and demo/real isolation. Run Android build/unit tests, backend pytest and exact-head Actions.
8. **Release gate:** do not enable scheduled production sync or claim automatic publication works until source rights, free hosting, API integration, exact-head CI and real-data APP rendering are all verified. Physical-device acceptance is separate from CI.

**Current status at adoption:** the repository already has an RSS/Atom candidate importer, a separate project-level `airdrop_hub_projects` table, a gated structured-source importer, a daily GitHub Actions workflow that is disabled unless explicitly configured, an existing filter metadata migration and a public catalog filter API. These are partial foundations only. The end-to-end campaign-aware pipeline, evidence-aware scoring/tiering, approved free market enrichment, campaign publication API and Android real-campaign integration are not yet proven complete. Do not describe the feature as finished based on the presence of the current importer or filter UI.


---

## 2026-11-11 implementation progress — campaign publication API

- Added `backend/app/api/airdrop_campaigns.py` and registered `GET /api/airdrop-campaigns`.
- The endpoint exposes only records explicitly marked published and in the featured/ordinary tiers. Observation and archived records are not exposed by the public endpoint.
- Supports bounded pagination and filters for tier, chain, campaign status, campaign type, KYC state and text query. Response includes the evidence-facing fields, source heat, market fields, scoring version and listing reason.
- Added API serialization and pagination-bound tests.
- This is a read-only publication surface. It does not ingest sources, approve candidates, change publication state or activate any scheduled job.
- Next implementation work: build the approved zero-cost RSS/Atom candidate-to-campaign adapter with strict host allowlisting, source snapshots, deduplication and fail-closed behavior; then add a reviewed/approved promotion step and connect Android Room caching.


### 2026-11-11 implementation progress — Android campaign data client

- Added Kotlin campaign DTOs matching the public campaign API.
- Added query/filter request model with stable cache keys.
- Added a Room-backed public cache repository using the existing `CacheStore` and bounded stale-while-revalidate coordinator. Cache data is public catalog content only; no wallet or personal ledger fields are sent.
- Registered the repository in the application composition root and added the Retrofit endpoint binding.
- The existing UI is intentionally unchanged in this step; the next step is a focused campaign-list presentation using the new repository, after API/CI verification. The collector and source adapters remain disabled until permitted $0 feeds are configured.


### 2026-11-11 implementation progress — campaign list in Android APP

- Added a dedicated campaign-list screen under the existing Airdrop Hub flow. The project directory remains intact; a clear entry opens the campaign list, avoiding conflating project identities with individual campaign seasons.
- The campaign list consumes the new read-only campaign API through the Room-backed repository. It offers featured/ordinary tier switching, displays known status/KYC/score and listing reason, and opens only the official URL supplied by the backend.
- No synthetic records are inserted. Empty states explicitly say there are no approved public campaigns yet. Observation and archived tiers remain excluded by the backend.
- UI integration is now present in code, but exact-head Android CI and real backend data validation are still required before calling it verified.


### 2026-11-11 implementation progress — RSS candidate observation bridge

- Added a separate adapter that mirrors only pending, allowlisted RSS/Atom candidates into campaign-level records with stable `rss-candidate:<source_key>` identities.
- RSS article URLs are retained as source evidence only; they are never copied into `official_url`. Chain, campaign type/status, KYC, token contract, heat and market data remain unknown/null until independently verified.
- New records are always `unverified`, `observation`, and `is_published=false`; they do not enter the public campaign endpoint or community project catalog.
- Source snapshots use a canonical SHA-256 hash, so unchanged content is not stored repeatedly. Re-running the sync is idempotent by campaign source key.
- Subsequent RSS refreshes update only last-seen metadata on an existing campaign row; they do not overwrite human-reviewed fields or publication decisions.
- Connected this bridge to the existing daily sync script after RSS candidate intake. The cron remains gated by `AIRDROP_DISCOVERY_SYNC_ENABLED`, database configuration and source approval.
- Added focused tests for unknown-field preservation, official-link separation, unpublished observation defaults and snapshot-hash stability.
- This is a candidate-to-observation bridge, not a complete automated campaign verifier or publisher. No RSS source is enabled by this change, and no paid provider/API is introduced.


### 2026-11-11 scoring safety correction — official URL is a publication gate

- Tightened the scoring engine so both featured and ordinary campaigns require an explicitly verified official URL before `is_published=true`.
- A score in the ordinary heat band (40–69) without official-link verification now remains in observation with an explicit review reason; it is not enough that a source reports the campaign or that the trust label alone is present.
- Updated the retroactive-ended scoring fixture to represent a verified official URL, and added positive/negative tests for ordinary publication.
- This is a fail-closed scoring rule. It does not imply that an RSS article link is an official URL or that the current RSS observation adapter can mark a campaign verified.


### 2026-11-11 — persisted campaign scoring pipeline

- Added a persistence adapter that translates campaign model fields into the existing evidence-aware scoring engine and stores score/tier/publication decisions.
- The adapter treats an official URL as verified only when the campaign trust status is explicitly `verified` and the URL is HTTPS with a hostname and no embedded credentials. RSS item/source URLs are never used for this check.
- Score changes create `AirdropCampaignScoreHistory` rows; unchanged decisions do not create duplicate history records. The operation is idempotent and records the scoring version, breakdown and listing reason.
- The existing scheduled sync now runs scoring after RSS intake in a separate failure boundary, so an RSS outage does not prevent already-stored campaigns from being re-evaluated.
- New RSS observations remain unverified and therefore stay unpublished. Only records whose stored evidence satisfies the scoring gates can be promoted to featured/ordinary.
- Added tests for URL verification, RSS-link isolation, safe publication, score-history creation and idempotent re-runs.
- This does not enable any RSS source or add a paid data provider. Live source configuration and physical production-database validation remain pending.


### 2026-11-11 — free DEX market enrichment adapter

- Added `backend/app/services/airdrop_market_data.py` using only public HTTP endpoints; no API key, paid plan, paid scraper or extra Python dependency is introduced.
- DexScreener lookup is contract-address-only and chain-scoped. Requests use the documented `/tokens/v1/{chainId}/{tokenAddresses}` endpoint, batch at most 30 addresses, and never use project name/ticker search. The implementation also rechecks the returned chain and exact base/quote contract address before accepting a pair.
- To avoid inflated market signals from adding overlapping pools together, the campaign's current liquidity/24h-volume fields use the strongest single matched pair; pair count is recorded separately. Individual market snapshots preserve the selected pair identity and source values.
- The adapter uses a conservative 2-second spacing between DexScreener batch calls (maximum 30 calls/minute, below the published 300 calls/minute endpoint limit), bounded retries, response-size limits, timeouts and `Retry-After` handling.
- GeckoTerminal uses one global `/networks/trending_pools` request per refresh cycle, not one request per campaign. Its result is cached in-process for five minutes; requests are spaced by at least six seconds and a 429 response is respected with a conservative minimum retry delay. The adapter sends the documented API version header.
- Only supported explicit network identifiers and non-empty contract addresses are queried. Unsupported networks, missing contracts and tokenless campaigns do not trigger name-based discovery or per-campaign GeckoTerminal calls.
- Provider errors preserve the last-known campaign market fields and do not create false zero-liquidity observations. A successful empty DexScreener pair list is represented separately as zero matched pairs.
- The daily sync script now runs market enrichment after candidate intake and before scoring. Market enrichment has its own error boundary so provider outages do not block campaign scoring or the rest of the sync.
- Added tests for 30-address batching, duplicate removal, exact chain/contract matching, best-pair selection, token relationship parsing, one global GeckoTerminal call and last-known-good preservation.
- The adapter is code-complete but production use remains gated by the existing sync flag, real database/migration validation and exact-head CI. No source is automatically marked verified by market data; DEX liquidity and trending remain ranking signals only.

### 2026-11-11 — source-aware successful heat-run accounting

- Added a persistence boundary for campaign heat observations. It can only be called after a complete heat-provider batch has been fetched and validated; RSS intake and DEX market enrichment are explicitly not heat-success runs.
- A stable caller-created pipeline run UUID is mandatory and reused on retries. Replaying an existing run does not increment counters or duplicate source snapshots.
- Only explicit finite heat values in the 0–100 range are accepted. Missing heat is omitted, never synthesized as zero; invalid or duplicate campaign observations reject the batch before writes.
- Successful observations update the campaign's canonical integer temperature, source snapshot, and last-success timestamp. A heat value below 40 increments the streak only when it comes from the same source; changing providers restarts the streak at one. Heat 40 or above resets it to zero.
- Added last_heat_source_name with migration 0022_campaign_heat_source; existing campaign counters are not retroactively inferred from old snapshots.
- The scoring engine already archives at three consecutive low-heat successes. This adapter supplies the missing persisted counter input, but no heat provider is currently configured or wired into the scheduled sync. Therefore no campaign will be newly archived by this adapter until an approved, free provider integration is added and validated.
- Provider failures must use a failure path that marks the run failed and does not call the success recorder. Production database migration execution and exact-head CI remain required before deployment.

- Follow-up scoring correction: the three-run archival threshold is evaluated against the latest persisted `temperature` observation, not the maximum of `temperature` and the separate `opportunity_score`. The latter remains part of ranking only; a stale/high opportunity score cannot suppress a verified low-temperature streak.
- Migration-history regression test now expects the new linear head `0022_campaign_heat_source` and verifies its parent is `0021_airdrop_campaigns`.

- Pipeline run lifecycle is now explicit: persist a `running` run before provider I/O; after a fully validated batch mark it `success`; on timeout, provider error or invalid payload mark it `failed`. Only a running run can be completed successfully, and failed/successful terminal runs cannot be converted into the other state.
- Failure records preserve the run/error evidence but never touch campaign heat counters or temperature. Retrying a failed provider attempt must use a new run UUID; replaying a successful UUID is a no-op.


### Implementation checkpoint — trusted-source discovery refactor (2026-11-11)

- Removed RSS/Atom candidate intake and RSS-candidate promotion from the scheduled `sync_airdrop_hub.py` workflow. Legacy RSS parser/model code remains dormant for compatibility; it is not called by the scheduled sync.
- Added catalog-level `trust_level` and `publication_status` fields with migration `0023_trusted_catalog_source`. Existing rows default conservatively to `TRUST_LEVEL_0` / `OBSERVATION`; no legacy row is silently upgraded.
- The catalog importer now has a code-controlled `TRUSTED_AIRDROP_PROVIDER_LABELS` allowlist. Only a provider explicitly added after confirming a free documented airdrop-directory endpoint and reuse terms can map to `TRUST_LEVEL_1` / `PUBLISHED_SAFE`. Environment variables cannot elevate an arbitrary source.
- The existing `web3-discover` JSON/MCP importer is now disabled because it is not approved as a head-tier provider and has no confirmed republication permission. No active trusted airdrop provider is configured yet; sync safely returns `no_trusted_free_airdrop_provider` without network calls.
- `GET /api/airdrops/hub` now returns `data_source`, `trust_level`, and `publication_status`, and only exposes rows in `PUBLISHED_SAFE`.
- Added regression tests for source allowlisting, automatic trust/publication state mapping, API provenance fields, and disabled sync with no approved free airdrop provider.
- Provider availability research: DefiLlama free endpoints do not document an airdrop directory; CoinGecko free API is asset/market data, not airdrop discovery; CoinMarketCap's Airdrops endpoint is not in its free API plan; CryptoRank's Drophunting endpoint is Business-tier. These are not activated as free airdrop sources. The next engineering step is to identify and verify an eligible provider or keep discovery paused—never substitute scraping or paid calls.
- Follow-up hardening: trusted catalog destinations must be HTTPS and must not embed URL credentials. The sync-status endpoint reports `source: "none"` and a non-successful refresh result while no eligible provider is active. Exact-head CI and production migration execution remain pending; do not treat this code checkpoint as validated deployment.
### 2026-11-11 — full Binance announcement detail in the app

- Added a backend detail endpoint keyed by the Binance CMS article code stored with each announcement. It reads the official public CMS detail endpoint and returns the original article title, HTML body, publication timestamp, catalog label and original URL.
- The detail response preserves the original Binance HTML content rather than generating a summary or inventing missing reward/time/eligibility fields. Oversized or unavailable content fails closed and the client can retry or open the Binance original.
- Added an in-app announcement detail view. It renders the full official body with JavaScript, file access, DOM storage and mixed content disabled; only HTTPS navigation is allowed. The original Binance page remains available as a separate action.
- Added backend tests for original HTML/metadata normalization, detail endpoint retrieval and static /status route precedence.
- This is an in-app rendering of the source announcement body, not a translation or independent verification of the claims. Any terms shown are exactly what Binance published.
- Explicit user-clicked HTTPS links in the official announcement body open in the app's existing built-in browser, keeping the announcement detail screen available underneath. Non-HTTPS navigation, subframe navigation and automatic/non-gesture redirects are blocked; if the browser cannot be launched, the detail WebView does not silently navigate away.

### 2026-11-11 — Binance announcement detail build verification

- Fixed the Android WebView navigation gesture check to invoke `request.hasGesture()` correctly; the previous form failed Kotlin compilation.
- Pull-request CI run #1044 for commit `9dc11f750a163cf2e918c41e3a5e40bd5e62b0ed` completed successfully: Android unit tests, debug APK build/artifact upload, and backend `pytest -q` all passed.
- The workflow produced the `airdrop-hunter-debug-apk` artifact. This confirms a CI build artifact exists; it does not establish installation or runtime validation on a physical Android device.
- A public Binance announcement page is discoverable, but the web inspection path did not expose the raw CMS HTML/image attributes. Therefore image lazy-loading behavior (for example, `src` versus `data-src`) and table scrolling remain unverified against a real announcement body. Do not change source HTML or enable JavaScript based on assumptions; validate with an actual CMS detail response or device test first.
- PR #135 remains Draft/Open and unmerged.

### 2026-11-11 — testable Binance announcement HTML policy

- Extracted the in-app Binance announcement HTML wrapper into a package-internal pure Kotlin function so its rendering policy can be covered by local unit tests without launching Android WebView.
- Added tests asserting that the official CMS body is embedded unchanged, the restrictive CSP continues to block scripts/connectivity/frames while allowing HTTPS/data images, and responsive media/table overflow styles remain present.
- This is a regression test for the authored wrapper only. It does not prove that Binance's current CMS HTML uses directly loadable image attributes, that remote images load on a device, or that horizontal table scrolling behaves correctly on a physical phone.
- JavaScript remains disabled. Do not add speculative data-src rewriting or relax the WebView policy without a real CMS response fixture and a security review.

### 2026-11-11 — Browser Dock whole-component drag refactor

- Rebuilt the floating browser Dock with one outer movable Box. The capsule background and horizontal LazyRow are children of that same Box, and the outer Box owns the offset and detectDragGesturesAfterLongPress gesture handler.
- The inner capsule container clips its translucent background, border and LazyRow to RoundedCornerShape(50), preventing tool icons from painting outside the pill.
- Normal horizontal scrolling remains assigned to LazyRow; moving the entire Dock requires a long press followed by a drag. Animatable drives the release/cancel snap animation to the left or right edge while keeping the vertical position within the available range.
- Automated build/test and physical-device gesture acceptance are separate gates. Do not claim the gesture conflict or visual alignment is fixed on hardware until tested on the user's phone.


### 2026-11-11 — Browser Dock import cleanup after source review

- A follow-up source review found a literal escaped newline embedded in the `LazyColumn` import line and removed the duplicate `items` import.
- Corrected the import without changing Dock gesture, layout, clipping, snapping, or persistence behavior.
- Commit `81314ddd1d1dfe3297eba309965d29cf6afdb846` contains the source cleanup. A fresh exact-head CI run is required; the prior Dock run was still in progress and is not treated as a pass.


### 2026-11-11 — Browser Dock ordinary-swipe cancellation guard

- Added an explicit `dragStarted` guard around the outer `detectDragGesturesAfterLongPress` detector.
- Edge snapping now runs only after `onDragStart` confirms the long-press whole-Dock drag actually began. A normal horizontal LazyRow swipe that cancels the parent detector must not trigger Dock movement or edge snapping.
- Kept the outer Box as the single movable target, retained the clipped capsule/LazyRow child and Animatable edge-snap animation. This is source-level hardening; gesture arbitration and feel still require physical-device acceptance.
- Commit `dfa1a0a39d3419d603bf2c520fb4a590c16f4ec8` contains the guard. The prior import cleanup is in `81314ddd1d1dfe3297eba309965d29cf6afdb846`.


### 2026-11-11 — Browser Dock drag callback coroutine fix

- CI run #1057 exposed a Kotlin compile error: `Animatable.snapTo` is suspending and was called directly from the non-suspending drag callback.
- Moved both bounded `snapTo` updates into the existing composition coroutine scope. The gesture still consumes the drag event, updates both axes together in one launched coroutine, and retains Animatable for movement and edge snapping.
- Backend tests passed in run #1057; Android build failed at the two direct `snapTo` calls. The correction is commit `1e6beda76d3b93a4f4b0be41107b4dd25c2c4823` and requires a fresh Android CI run.


### 2026-11-11 — Home accounting/search navigation correction

- Removed the standalone Home accounting card above Quick Entry. Accounting is now a first-class, default-visible Quick Entry shortcut with its own destination; tapping it opens the pending-bill/accounting flow, never the Project Library tab.
- Added a dedicated Accounting overlay route and reused the existing pending-bill and confirmation workflow without rendering a browser Dock on the accounting-only screen.
- Moved the single global coin/DApp search field directly below the market dashboard and above Quick Entry cards.
- Existing saved Home layouts are migrated by inserting the accounting shortcut if it is missing; user card dimensions remain developer-controlled and user ordering/removal remains supported.
- This is a navigation/layout correction. It does not establish physical-device visual acceptance; exact-head Android CI and on-device verification remain required.


### 2026-11-11 — Dock whole-capsule drag gesture arbitration

- Corrected the Dock gesture implementation after real-device feedback showed that the child LazyRow was scrolling its icons while the translucent capsule stayed stationary.
- The outer Dock now observes pointer events in the Initial pass, waits for a stationary long press, and then consumes drag movement before the child LazyRow can claim it. Both X/Y Animatable offsets are updated from the outer pointer gesture coroutine, moving the capsule background, border, clipping region, and all tools as one unit.
- A normal swipe that moves beyond touch slop before the long-press timeout is left to LazyRow, preserving horizontal tool scrolling.
- This fix targets gesture ownership, not visual restyling. Exact-head CI and physical-device verification are still required; the prior device screenshot remains evidence that the earlier implementation was not acceptable.


### 2026-11-11 — Dock gesture coroutine compile correction

- Exact-head CI #1067 confirmed backend tests passed but Android Kotlin compilation failed because Animatable.snapTo() was called directly inside the restricted awaitEachGesture pointer scope.
- Moved both bounded axis updates into the composition coroutine scope while keeping the parent pointer listener responsible for recognizing and consuming long-press Dock drags.
- This is a compile correction only; the gesture still requires a fresh exact-head CI run and physical-device acceptance.


### 2026-11-11 — Dock long-press cancellation boundary

- Review of the first successful build for the parent-owned Dock drag found that the timeout block could report success after an early release or pre-timeout movement.
- Corrected the rule: the Dock drag begins only when the stationary pointer remains pressed until the full long-press timeout expires. Early release or movement beyond touch slop cancels the parent drag and leaves normal LazyRow scrolling available.
- The previous build artifact is not the final device-test build; exact-head CI must pass for this correction first.


### 2026-11-11 — Repository-wide CI cost and batching policy

- Historical Actions API reports 895 total workflow runs, with latest run number #1072. The run number is not the run count; run records may have gaps because of deleted/expired history or numbering semantics. The API's current total_count is the count to use for this repository's available run history.
- Of the 895 recorded runs, 855 were named "Android debug build and backend tests"; 737 were pull_request events. Among all recorded runs, 397 concluded success, 160 failure, 302 cancelled, and 36 skipped.
- The high pull-request run volume is not an acceptable development rhythm for a solo, zero-budget project. CI must be a final verification gate for a coherent module, not an interactive compiler.
- From this point, related code edits must be reviewed and grouped into one coherent module change before committing. Do not create separate code commits for each small correction. Documentation-only updates should remain documentation-only and must not trigger Android builds (the workflow currently ignores docs/** and Markdown paths).
- Before the single module validation run: inspect the full diff, call sites, imports, coroutine/suspend constraints, UI hierarchy/gesture ownership, and available local/static tests. Run Android and backend suites only when their corresponding code changed.
- If CI fails, inspect the exact failing job and logs, fix the root cause, then perform one deliberate re-validation after the whole correction set is ready. Do not repeatedly rerun unchanged jobs or upload stale APKs.
- Keep PR #135 Draft/Open/unmerged until the user explicitly authorizes merge. A successful CI build is not physical-device acceptance.



### 2026-11-11 — Approved discovery source input hardening

- Hardened GitHub Raw configuration to accept only HTTPS files on the exact `raw.githubusercontent.com` host, with a valid owner/repository/ref/file path and no credentials, query strings, or fragments.
- GitHub JSON lists now require explicit `website`, `official_url`, or `officialUrl` fields; generic `url` values are not assumed to be official project websites. Markdown project links remain HTTPS-only.
- Binance announcement article codes containing unexpected characters are rejected rather than silently rewritten into a potentially different article identifier.
- Added backend regression tests for exact-host validation, explicit official-site fields, HTTPS Markdown links, and malformed Binance article codes.
- This hardens ingestion boundaries only. It does not certify a GitHub repository's license, configure any external list, validate live provider terms, or mark the four-source discovery pipeline end-to-end complete.


### 2026-11-11 — Incremental CI event-range validation

- Same-repository branch pushes now use the exact GitHub push event range (before SHA to after SHA), which includes every commit in a multi-commit push and does not require querying previous workflow runs.
- The corresponding same-repository PR synchronize event runs only the lightweight change-detection job; it does not duplicate Android/backend suites. PR opened/reopened and fork synchronize events retain a complete base-to-head diff because fork pushes do not trigger workflows in the base repository.
- Push events ignore documentation and workflow-only paths. A mixed PR may still start a lightweight PR changes job because GitHub evaluates PR path filters against the cumulative PR diff.
- Manual workflow dispatch continues to honor its explicit Android/backend/both scope. The change detector still runs Android and backend suites independently according to their respective file groups.
- Acceptance passed on 2026-11-11: push run #1078 for commit `218981bc0c819153908dcfeb939a2cdc7fd3c60a` compared the exact range `ed5fb7d8df89af7525264f4666ef5a49661009d1..218981bc0c819153908dcfeb939a2cdc7fd3c60a`, ran Backend tests successfully, and skipped Android. PR synchronize run #1079 completed successfully with both heavy suites skipped.



### 2026-11-11 — GitHub Raw source configuration edge-case hardening

- The configured Raw URL parser now catches malformed port values when the parsed port is accessed, rejects user-info/port variants, and checks decoded path segments for traversal or encoded path separators.
- Malformed percent escapes and whitespace/control characters are rejected before a configured source is fetched.
- Removed redundant environment parsing in the fetch adapter; all configured URLs now pass through the single strict validator.
- Added regressions for malformed/out-of-range ports, empty user-info, encoded traversal/separators, duplicate URLs, and the 20-source cap.


### 2026-11-11 — Retire the unreachable legacy public-directory adapter

- Removed the unused `web3-discover` JSON/MCP URLs, generic payload normalizer, MCP response parser and dead fetch function from the active airdrop hub service. The importer continues to use only its four explicitly listed source adapters.
- Removed unit tests that only exercised the retired generic directory normalizer; retained the regression that confirms the legacy source label cannot be treated as trusted/publishable.
- Renamed the scheduled workflow to describe configured source observations and removed unused RSS and legacy approval environment variables. The daily schedule remains gated by `AIRDROP_DISCOVERY_SYNC_ENABLED=true`; it runs the current four-source importer and its separate market-enrichment/scoring steps, not RSS ingestion.
- RSS parser, candidate models and review endpoints remain dormant compatibility code; they are not invoked by the importer. No old RSS feed is re-enabled.


### 2026-11-11 — GitHub Raw manual license and attribution approval gate

- Replaced the URL-only GitHub Raw configuration with `AIRDROP_GITHUB_RAW_SOURCE_APPROVALS`. A source is fetched only when the operator supplies a complete record containing unique source ID, matching repository identity, exact HTTPS Raw URL pinned to a full commit SHA, license identifier/reference, explicit attribution text, approved review status and review date.
- The complete configuration fails closed on missing/invalid metadata, unapproved status, duplicate source IDs/URLs, unsafe URL/path, branch-name refs, malformed review dates or more than 20 entries. The legacy `AIRDROP_GITHUB_RAW_SOURCES` URL-only setting is ignored.
- License URLs are recorded, not fetched or treated as legal proof. The operator must inspect the actual repository terms and decide whether automated retrieval, storage and public-catalog reuse are allowed. The code does not claim to perform legal review.
- Reviewed license and attribution details are carried into imported lead provenance. These records remain internal observation leads and are not promoted into confirmed campaigns.
- Removed the stale test that still called the deleted `web3-discover` generic normalizer; retained a direct regression that the retired source label remains untrusted.
- This module has not yet passed exact-head CI at the time of this entry. The preceding run #1081 had 209 tests pass and one stale-test failure; a single batched backend validation is required after this complete change.


### 2026-11-11 — Keep internal campaign scoring out of public discovery

- The public campaign response and Android DTO no longer expose internal score, scoring version, listing rationale, trust flags, token contract identifiers, CoinGecko trend flags or DexScreener pool/liquidity/volume telemetry.
- Removed the visible “综合评分 / 评分数据不足” row and internal listing-reason text from campaign cards. The visible card retains campaign name, concise description, chain, status, KYC information and official action.
- Retained the backend scoring engine and persistence/history for internal publication decisions; this is a presentation/API-boundary cleanup, not deletion of the scoring gate.
- Added API regression assertions that internal scoring, trust and market telemetry fields are absent from the public response.

### 2026-11-11 — Home accounting entry and background airdrop refresh correction
- The Home “记账” shortcut now opens the “确认本次记账” form directly by creating a local draft; it no longer opens or waits on the “待确认账单” picker. Existing drafts remain available through the separate pending-bills tool.
- The dynamic catalog warm-up worker now refreshes the approved public Binance airdrop announcement hub in the background when the app enters the foreground, with a six-hour success cadence. It does not block the Home UI; failed refreshes preserve existing server data and are retried on a later foreground entry.
- This refresh does not fabricate announcements: the visible hub continues to display only the approved Binance announcement records returned by the backend. An empty list after a successful refresh means the backend currently has no eligible announcement rows to show.
- Exact-head Android build/unit tests and artifact upload are pending for this correction.

### 2026-11-11 — Public campaign API regression-test correction
- Final list-copy audit found and removed the remaining “数据来自已审核目录” implementation wording; the Android list count now shows only the public campaign count, matching the concise user-facing contract.

- Exact-head Push workflow #1085 for commit dd69e9fdb57f158d1ab9603a6385719d3d900fa1 completed Android unit tests and Debug APK assembly successfully; artifact airdrop-hunter-debug-apk (artifact ID 13829965278) was uploaded.
- Backend pytest reported one failure because the public API regression test both asserted that listing_reason was absent and later asserted that the same field contained a value. The latter assertion was stale and contradicted the intended public contract; the API response model was correct.
- Corrected the regression test to assert absence of the complete set of internal scoring, trust, token-identity and market-telemetry fields. No internal field was restored to the public API.
- Exact-head backend revalidation for this correction is pending. PR #135 remains Draft/Open/unmerged; the successful lightweight PR synchronize run #1086 skipped both heavy test suites and is not counted as full validation.


### 2026-11-11 — homepage-triggered airdrop hub refresh reliability

- Entering the Home screen now queues the public airdrop refresh worker without blocking navigation or foreground UI work. The existing six-hour successful-refresh cadence remains the request throttle.
- The worker only records a successful refresh timestamp when the server response reports `ok=true`; unsuccessful server responses remain retryable on a later Home entry.
- The airdrop hub repository retries an empty list when the server has no successful sync recorded, including the case where an earlier attempt exists but failed. A previously successful empty result is not repeatedly synchronized.
- Added repository regression coverage for retry-after-failure and no-repeat-after-success. This is pending exact-head CI validation; server data availability and physical-device acceptance remain separate checks.


### 2026-11-11 — place Binance official activities inside the airdrop library

- The Home dashboard has one **空投列表** entry for the verified public campaign catalog (`AirdropCampaignsScreen`); it no longer has a separate Binance card.
- Inside the airdrop library's fetched campaign list, **币安官方活动** occupies the first campaign-card position (the slot where the first fetched airdrop would otherwise appear). The original first airdrop is retained and follows it as the next list item; no campaign is deleted. Tapping the Binance entry opens the independent Binance announcement/activity hub (`AirdropHubScreen`).
- Binance announcements remain separate from the verified public campaign list: separate screen, endpoint, data contract and refresh path. They are not inserted as ordinary campaign rows.
- Existing saved Home layouts are migrated to remove the misplaced Binance shortcut while ensuring the airdrop library entry exists. Later user layout edits remain respected.
- Home entry queues background dynamic catalog warmup without blocking navigation. The public campaign repository continues to use its Room-backed cache and six-hour TTL. Binance official feed remains independently refreshed by its own worker cadence.
- Exact-head Android build and unit tests are pending; real device navigation and live-data verification remain pending.

### 2026-11-11 — Correct Binance announcement category IDs

- Exact-head CI for the prior `(128, 49)` change passed, but a subsequent comparison against Binance's own announcement category pages exposed a semantic mapping error: category `93` is **Latest Activities**, while `49` is **Latest Binance News**. Category `128` is **Crypto Airdrop**.
- Corrected the importer and its regression test to request `(128, 93)`. The Latest Activities feed remains keyword-filtered; unrelated general Binance news is not added to the airdrop hub.
- Official category pages checked: `/en/support/announcement/list/128` (Crypto Airdrop) and `/en/support/announcement/list/93` (Latest Activities); `/en/support/announcement/list/49` is Latest Binance News.
- This correction supersedes the immediately preceding catalog mapping. Exact-head Push workflow #1111 for commit `2546778c` passed: `pytest -q` reported 213 passed, 3 warnings in 8.54s. Android build was skipped because this commit changed backend files only.

### 2026-11-11 — Binance CMS list endpoint compatibility

- Follow-up source review found the current RSSHub Binance announcement adapter using the newer observed `/bapi/apex/v1/public/apex/cms/article/list/query` route, while this importer still used the older `/bapi/composite/v1/public/cms/article/list/query` route.
- Updated the importer to try the apex route first and fall back to the composite route per category. Added MockTransport regression tests for both primary and fallback paths.
- The endpoint is an observed Binance web-CMS interface, not a documented supported Binance API. Exact-head backend CI and a real live-provider smoke test remain separate checks.

### 2026-11-11 — Binance endpoint self-discovery

- Implemented automatic endpoint-path discovery by reading the public RSSHub Binance announcement adapter source, the same kind of source-code inspection used during engineering investigation.
- The backend extracts only a constrained Binance CMS list path, fixes the destination host to `www.binance.com`, and verifies the live response structure and requested catalog ID before importing anything.
- The reference repository supplies no announcement content to Airdrop Hunter; all imported records and article detail HTML still come directly from Binance. Discovery is cached for six hours, with apex/composite fallback paths retained.
- Added tests for safe path extraction, successful dynamic discovery, and fallback when the discovery reference is unavailable. Exact-head CI and real provider connectivity are pending.


### 2026-11-11 — Endpoint discovery implementation note

- Tightened the discovery parser to extract only the route path after the source adapter's `${baseUrl}` marker. The reference cannot supply or change the destination host.
- Exact-head automated test status is pending after the final parser correction; do not treat the earlier cancelled workflow attempt as validation.
- Live provider availability remains a separate requirement from MockTransport test success.


### 2026-11-11 — Unified multi-source discovery queue
- Connected the four configured observation adapters (Binance, CoinGecko, manually approved commit-pinned GitHub Raw lists, and DexScreener) to the shared `airdrop_discovery_candidates` queue.
- Candidate records retain source attribution and original source links; each provider observation receives a stable provider-scoped SHA-256 key.
- Candidate refreshes preserve manual review decisions. Non-HTTPS or missing item links are rejected before persistence.
- This queue is not the public Airdrop Library: market/list observations remain unverified until official campaign evidence is checked. No source is automatically promoted to a published campaign.


## 2026-11-11 — discovery pipeline responsibility correction

The active architecture has exactly three stages: source acquisition; one centralized filter; and the Airdrop Library. Result organization belongs to the filter, not a separate stage. Adapters retain parseable observations and provenance; the central filter owns category selection, timestamp freshness, liquidity thresholds, top-N ranking, deduplication and merge decisions. Market-data providers (CoinGecko and DexScreener) are signals only and cannot independently prove an active airdrop campaign. Filtered observations remain stored with reasons and structured evidence separate from human review status.

Project identity and campaign identity are distinct. Project-name similarity alone must never merge records; one project can have multiple campaigns and one campaign can have evidence from multiple providers. Full cross-provider clustering remains unfinished and must not be represented as complete.


## 2026-11-11 — centralized identity classification

Project and campaign identity classification is a responsibility of the unified discovery filter, not source adapters. Strong identity evidence is limited to chain+contract, exact HTTPS official project URL, and exact HTTPS campaign/activity URL. Never infer identity from names or tickers. Keep project and campaign identity separate. Store match status, evidence and matched source key without deleting, merging or suppressing raw source records. Same-project/different-campaign records remain distinct. No strong evidence means insufficient identity evidence, not a guessed match.
## 2026-11-11 — DexScreener query fault isolation

- A single failed DexScreener search term no longer aborts the entire provider run. Each configured term is requested independently; failures are logged and remaining terms continue.
- Successful term results are retained when another term fails. The provider is marked failed only when no valid observation rows are produced; all-failed runs preserve the previous source data through the existing per-provider transaction boundary.
- Added regression tests for partial success and all-term failure. Exact-head workflow #43006252003 passed: **234 passed, 3 warnings, 0 failed**. Android build was skipped because this batch changed backend code only.
- Configuration review: CoinGecko discovery is disabled until COINGECKO_DEMO_API_KEY is configured; GitHub Raw discovery is disabled until at least one manually approved, license-reviewed, commit-pinned source is configured. The example DexScreener terms (SOL, ETH, USDC) are broad market searches, not proof of airdrop campaigns. Binance remains a separate official-announcement observation source.
- No production live-data claim is made from unit tests. Real provider credentials/configuration, live responses, and the end-to-end filter-to-library display still require verification. Scheduled production sync remains disabled.


### Latest continuation checkpoint — 2026-11-11 (detail-route guard revalidated)

- Re-read the actual branch implementation and found the Binance detail endpoint did not enforce the same visibility conditions as the list endpoint, despite an older progress note claiming the guard was present.
- Restored the guard: only Binance records with `publication_status == OBSERVATION` and `filter_status != filtered` can be opened through the public detail route. Other source records and filtered records return 404.
- Added a regression assertion using a known UUID for a centrally filtered record. Exact-head backend push workflow #43007560087 passed: **234 passed, 3 warnings, 0 failed**. Android build was skipped because only backend API/tests changed.
- Current code commit: `51b2b6cc49704388bf6f576a3b2abf38b98efecc`. PR #135 remains Draft/Open/unmerged.
- Continue with source-specific readiness/status visibility and four-provider live-data validation. Do not claim live data until the actual runtime environment is verified.


### 2026-11-11 — third-party airdrop directory permissions and source attribution

- High-heat campaigns remain the priority: the campaign catalog already orders featured campaigns before ordinary campaigns, then sorts each tier by the centralized score in descending order. Low-heat/uncertain observations are not automatically placed in the public catalog.
- Do not scrape or republish AirdropAlert or Airdrops.io content without explicit permission covering automated retrieval and display in Airdrop Hunter. AirdropAlert's terms restrict reuse beyond personal use unless prior written permission is granted; its RSS endpoint is not a content-republication license. Airdrops.io's terms restrict copying, public display and mirroring. These providers remain integration candidates only after permission is obtained.
- Public campaign responses now include source_name and source_url so users can see where a campaign record came from. Internal trust flags, scoring telemetry, contract identifiers and market metrics remain private.
- Provider selection, popularity ranking, deduplication and publication remain centralized; adapters only acquire and normalize source observations. Do not equate a generic token/market feed with a reviewed airdrop campaign source.
- Real-data cleanup is deferred until a live source has passed end-to-end validation. At that point remove only production/demo records and seed data, never automated test fixtures or regression tests.


## 2026-11-11 — SolvoHQ retired; live market-signal adapters corrected
- Retired SolvoHQ CC0 JSON from the active pipeline because its snapshot timestamp was stale by months; stale records are not imported.
- Added a centralized seven-day source freshness guard for generatedAt/updatedAt and common snake_case equivalents, including CoinGecko last_updated. A stale or malformed upstream timestamp fails the whole provider batch and preserves the previous successful data.
- CoinGecko now uses /coins/categories/list to identify the Airdrop category, /coins/markets to retrieve at most 20 market-ranked entries, and /coins/{id} to resolve the actual official homepage. It does not assume /coins/categories itself contains 20 coin records or websites.
- DexScreener now searches the configured term (default airdrop), then centralized selection keeps only the top 10 pairs with liquidity strictly greater than USD 10,000. Volume and transactions remain ranking evidence; they do not establish airdrop eligibility.
- Both CoinGecko and DexScreener records remain market/category observations, not published campaigns. Official activity evidence and the unified campaign filter are still required.
- CoinGecko Demo key remains server-side in COINGECKO_DEMO_API_KEY; never embed it in the Android APK. Android calls the Airdrop Hunter backend, which owns adapters and central filtering.
- Adapters are implemented in Python because the live discovery pipeline, credentials and central filter run in the backend. Do not duplicate provider networking in Android Kotlin.


## 2026-11-11 — scheduled ingestion failure reporting and current checkpoint

- Fixed the scheduled importer so a failed discovery sync, campaign market enrichment, or scoring stage makes the process exit non-zero. A successful later stage can no longer mask a failed source-ingestion stage as a green GitHub Actions run.
- Added regression tests for successful runs and non-zero exits when discovery or enrichment fails.
- Exact-head backend CI run for commit `60edc2fe2041c78610207bf36ee7bb11e5307cd0`: **241 passed, 3 warnings, 0 failed**. Android build was skipped because this commit only changes backend code/tests.
- PR #135 remains Draft/Open/unmerged. Do not merge or enable scheduled ingestion as part of this checkpoint.
- Runtime configuration is still absent/unverified. No live provider run, real-data import, or end-to-end Android display has been claimed.
- Current source roles remain deliberately distinct: Binance official announcements are unverified observation leads; CoinGecko and DexScreener are internal market/category signals only; GitHub Raw requires a manually reviewed, license-attributed, commit-pinned allowlist. None of these market signals is by itself proof of an active airdrop campaign.
- The scheduled workflow remains gated by `AIRDROP_DISCOVERY_SYNC_ENABLED=true` and a configured database secret. CoinGecko additionally needs `COINGECKO_DEMO_API_KEY`; GitHub Raw needs `AIRDROP_GITHUB_RAW_SOURCE_APPROVALS`; DexScreener queries are optional and configurable. Keep all secrets in GitHub Actions secrets, not repository files or the Android APK.
- After an authorized source is configured and a complete live fetch/filter/import/API/Android display cycle is verified, remove only production demo/seed records. Preserve fixtures and automated regression tests.


### 2026-11-11 — live discovery smoke harness review and current checkpoint

- Rechecked the live-smoke runner and all four provider adapters at branch head `af0e652b7f4212fda9c53480973acb0be1befd9f`. The runner calls the adapters directly with a bounded 30-second HTTP client timeout, prints per-provider result counts/sample labels, and does not create a database session or invoke import/publication code.
- Adapter behavior reviewed: Binance fails when both catalog endpoints fail without records; CoinGecko fails closed when its category/market/detail path yields no eligible observations; GitHub Raw and DexScreener continue across individually failing configured sources/queries, but fail when no valid rows are produced. Partial provider results are intentionally retained and must be read as partial success, not proof every configured sub-source worked.
- Smoke exit code is deliberately successful when at least one configured provider returns parseable records; optional CoinGecko/GitHub adapters are skipped when their required configuration is absent. A green smoke result would prove fetch/parse connectivity only—not freshness, campaign eligibility, publication permission, database import, or Android rendering.
- Exact PR head workflow #1234 (run `43022231694`) completed successfully, but its only active `changes` job ran; both `backend-tests` and `android-build` were skipped for this documentation-only commit. Do not count #1234 as test coverage. The last exact backend code validation remains push run `43022038023` on parent code commit `c51a5e1256c7b29071cbb0b4f10062b4a81a04bd`: 244 passed, 3 warnings, 0 failed; Android skipped because it was backend-only.
- Verified that `.github/workflows/airdrop-discovery-smoke.yml` and the smoke script do not exist on the default `main` branch (404); GitHub manual dispatch therefore cannot start this PR-only workflow yet. No dispatch-capable GitHub action is available in the connected toolset. Do not merge PR #135 or modify `main` without the user's explicit authorization. The database-writing scheduled workflow remains unsuitable for this read-only test and stays disabled.
- PR #135 remains Draft/Open/unmerged. No live provider run has occurred; no live provider counts, campaign publication, production import, or phone acceptance may be claimed.



### 2026-11-11 — zero-key discovery defaults and live smoke diagnostics

- The default production discovery set now contains only the no-key public adapters: DexScreener and Binance public announcements.
- CoinGecko is added only when `COINGECKO_DEMO_API_KEY` is present; approved GitHub Raw lists are added only when `AIRDROP_GITHUB_RAW_SOURCE_APPROVALS` is present. Neither optional provider is required for the default run.
- The read-only smoke runner already treated those two providers as optional and attempted DexScreener/Binance by default. Its failure output now includes a bounded exception detail so the actual HTTP status or empty-result reason can be diagnosed from Actions logs.
- Latest code commit: `466ee3fc14fa0e753e49b38671653e8806b6d837`. Automated validation and a repeat live smoke run are still pending; the earlier live run failed with Binance HTTP status errors and a DexScreener adapter RuntimeError. No live source is currently verified as returning usable rows.
- No API key is required for the default source set. The live smoke workflow remains read-only; production scheduled sync remains disabled. PR #135 remains Draft/Open/unmerged.



### 2026-11-11 — provider HTTP diagnostics

- Public-source adapters now include the HTTP status code (when available) in Binance and DexScreener failure summaries, instead of reporting only the generic `HTTPStatusError` class.
- This is diagnostic-only: it does not convert provider errors into success, bypass rate limits, or change publication eligibility.
- Commit: `fcb7bca5cc50ea73dd6a55e979bbf64a5b8581b0`. CI status and a repeat live smoke result are not yet available through the current GitHub run lookup; do not mark live providers validated.


### 2026-11-11 — follow-up to live provider smoke failure

- The 21:14 read-only smoke run showed Binance catalog 128 and 93 returning HTTP 400. The response status alone does not identify the invalid request field, so do not guess at a fix or treat this as a WAF block. The Binance adapter now includes only bounded JSON error metadata (code, msg, message) in its diagnostic label when the response provides it; response bodies are not logged.
- The same run showed DexScreener was not queried because the Actions variable was present but contained no usable terms. The adapter now falls back to the free public search term airdrop when the variable is absent, blank, or whitespace/separator-only; explicitly configured non-empty terms remain supported and deduplicated.
- Added regression tests for the DexScreener default and configured-term behavior.
- Commits: 6565861679d6b7c94b2548953f4b1339fd340f00 (adapter fix/diagnostics), e48367b3ab08499c744dcc7e42f5b0c1acfa16b6 (tests).
- Automated tests and a new live smoke run have not yet been observed. Binance remains unverified until its new bounded error metadata identifies the HTTP 400 cause and a subsequent live request returns parseable records. DexScreener also remains unverified until the fallback query returns parseable records.
- Production scheduled sync remains disabled; no database writes or publishing were performed; PR #135 remains Draft/Open/unmerged.


### 2026-11-11 — correction after follow-up CI caught a syntax regression

- The first smoke attempt after the DexScreener fallback change did not reach either provider: CI caught a Python SyntaxError (`await` outside async function) in the Binance adapter declaration. This was introduced by the previous edit and is explicitly recorded as a code regression, not a provider failure.
- Corrected the declarations: the Binance error-label helper is synchronous, while `_fetch_binance_observations` remains async. Fix commit: 2c978d5d6934ec63a288b4ede45822367a2f7a71.
- A fresh Android/backend workflow was automatically queued for that commit when last checked; completion and tests are still pending. The live provider smoke has not yet been rerun against the corrected branch code.
- Keep production sync disabled and PR #135 Draft/Open/unmerged. Do not claim live-source success until a smoke run checks the corrected branch and reports parseable records.

### 2026-11-12 — user correction: unified refresh orchestration includes private data domains

**This latest correction supersedes the earlier “public/shared data only” restriction for refresh orchestration.** The previous wallet scope audit correctly identified that private wallet payloads must not use public cache keys or shared payload storage, but it incorrectly treated that storage-isolation requirement as a reason to remove wallet data from unified refresh coordination. Removing coordination must not make refresh dependent on opening a screen.

**Approved architecture and requirements:**
- One shared refresh/synchronization orchestration layer manages scheduling, single-flight work, bounded retries, cancellation, refresh state and observability across public and private data domains. “Unified” means unified lifecycle/orchestration, **not** one shared payload namespace or one universal TTL.
- Continue using the shared coordinator for market search, OKX market data, public airdrop campaign directory, community catalog and public community snapshots.
- Re-integrate wallet transaction history and wallet balances with the shared orchestration layer. Their payloads and refresh metadata must both remain isolated by the existing account/session scope plus wallet and chain/ecosystem identity. Never use public cache keys, cross-account metadata, or shared wallet payloads. Preserve existing local data; do not clear, re-key or migrate it as a side effect.
- Personal ledger, private projects and tasks remain durable user records, not expiring cache payloads. They must not be deleted, overwritten or marked stale by TTL. Their eligible background synchronization may use the shared scheduler/orchestration facilities while preserving their existing local-first writes, conflict rules, privacy rules and pending-sync queue semantics.
- Refresh must not depend on the user opening a destination. Background work must refresh eligible data on its configured cadence; screen entry must show available local data immediately and may trigger a non-blocking refresh without waiting for it to finish.
- A background refresh must not block navigation or other app use. Failed requests retain last-known-good data and expose a visible refresh/error state where the UI supports it.
- Explicit user refresh must have a clear result: refreshed, already current, throttled, or failed. A caller must not silently report success when the coordinator returns `throttled`.
- Keep the current-release account boundary unchanged: registration/login, account switching, guest-to-login migration and private cloud restore remain disabled/out of scope. Do not redesign identity or synchronization ownership as part of this correction.

**Acceptance criteria:**
1. Wallet transaction payload reads/writes and refresh metadata use the same account/session + wallet + chain scope; tests prove two account scopes cannot read, suppress, throttle or overwrite each other's refresh state.
2. Wallet balances have the same isolation guarantees and retain cached-first display while background refresh runs.
3. Wallet transaction and balance refreshes can run on their configured background schedule without the corresponding screen being opened; the actual scheduler/cadence and worker result handling are verified.
4. Entering a screen renders cached data without waiting for network completion; refresh completion updates the screen state without blocking other destinations.
5. Ledger/project/task local writes and pending-sync records survive cache expiry, process restart, offline use and refresh failure unchanged; only their existing approved sync path may process pending work.
6. Manual refresh UI distinguishes a throttled result from a successful fetch; source-specific TTL and refresh cadence are documented and tested.
7. Exact-head CI passes; device acceptance is recorded separately from code/CI.

**Implementation status at this requirements update:** approved design; implementation and full call-chain/background-scheduler audit pending. No account architecture, registration, guest migration or cloud restore work is authorized by this change.

**Implementation/audit follow-up — 2026-11-12 (in progress):**
- Re-integrated wallet transaction refresh with the shared coordinator in code commit `7abdef1813d6fc78a7bc07a32bda03c2dbbddc46`. The transaction payload remains in the existing guest/account-session Room store; coordinator metadata now uses a separate key containing account scope, wallet ID and ecosystem, with an 8-hour TTL. Normal refresh is cache-first; the detail-screen refresh button passes force=true. Added repository regression coverage for scoped metadata and TTL/force behavior. Exact-head Android CI #43146843496 is **in progress**; do not mark this slice CI-complete until it finishes.
- Audit correction: transaction refresh already has a WorkManager path. The app starts a 15-minute global dispatcher; wallet transaction work is queued independently and currently has an 8-hour account-scoped cadence. Therefore it was inaccurate to say wallet transactions only refresh when opened. The missing piece was unified per-wallet cache coordination and ensuring metadata isolation, not inventing a new background worker from scratch.
- Wallet balance audit: `WalletAssetDetailDialog` constructs its repository locally and currently reads the scoped Room payload, then directly fetches only when the dialog is opened or manually refreshed. No wallet-balance background worker is currently present in the inspected sync dispatcher. This is a real gap against the approved “refresh without opening the screen” requirement and remains pending implementation.
- Campaign-directory audit: the repository uses the shared coordinator with a 6-hour TTL, but the current global dynamic-catalog worker does not call it. `AirdropCampaignsViewModel` also does not publish `readCached()` to UI state before awaiting `load()`. Background campaign warming and immediate cached rendering therefore remain pending.
- Current-release personal ledger sync remains explicitly disabled; personal ledger records remain durable local data. This cache correction does not authorize private cloud sync, registration, account switching, guest migration or changes to pending queues.

### 2026-11-12 — wallet manual-refresh rate limit and user feedback

User correction: tapping refresh repeatedly must **not** cause one real provider request per tap. Wallet transaction history and wallet balances use a shared manual-refresh cooldown. Within the cooldown, keep showing the latest cached snapshot, do not start another network request, and show a brief in-app transient message (toast/snackbar/“弹幕提示”) that refresh is temporarily unavailable and when the user can try again.

- Reuse the coordinator's existing `MANUAL_REFRESH_THROTTLE_MILLIS` policy (currently 30 seconds) rather than inventing a separate per-screen interval.
- Wallet screen manual refresh must call the coordinator with `manual=true` and must not pass `force=true`; force is reserved for explicit internal/test paths that are allowed to bypass cooldown, not ordinary repeated user taps.
- Both transaction-history and balance UI must surface `throttled` as a visible transient message, not a silent no-op or a false “refreshed” success. Keep cached data visible and do not show a blocking spinner when throttled.
- Apply the same cooldown per isolated wallet/account/network metadata key, so refreshing one wallet does not throttle another wallet or another account's private data.
- Background refresh continues to follow its own cadence and failure cooldown; this manual limit must not stop scheduled refreshes.
- Acceptance: first eligible manual tap causes at most one fetch; repeated taps within 30 seconds cause zero additional fetches and show the transient message; after cooldown, a new tap can fetch; account/wallet/network scopes remain independent; UI retains the previous snapshot throughout.

Implementation status: requirement approved; existing coordinator already has a 30-second manual throttle, but wallet transaction UI currently bypasses it with `force=true`. Balance repository/UI must also be verified to return and display the throttle state.


### 2026-11-12 — wallet manual-refresh cooldown implementation verified

- Exact-head Android workflow **#43147830389** completed successfully on code commit `38768e5c6f3ef0f58406e1941636010adae2ed5d`: Android build, unit tests, Debug APK assembly and artifact upload all passed. Backend tests were skipped because this change set was Android-only.
- Wallet transaction history now passes `manual=true` for user-triggered refresh rather than bypassing the cooldown with `force=true`; the repository returns the coordinator's `throttled` state. The existing background worker's `force` API remains compatible.
- Wallet balances now use the shared refresh coordinator while retaining the existing account/wallet/chain-scoped Room payload. The balance and transaction screens display a short Android Toast when the shared 30-second manual cooldown blocks a request: “刷新太频繁，请30秒后再试”. Cached snapshots remain the displayed data; a throttled attempt is not represented as a successful fetch.
- Added/updated repository regression coverage for manual cooldown behavior. This verifies the repository/coordinator path under unit tests; it does not replace physical-device confirmation of Toast visibility or network behavior.
- **This slice status: Complete in code + exact-head CI; physical-device acceptance pending.** The prior paragraph saying implementation was still pending is superseded for the wallet manual-throttle slice only.
- Still pending next: implement a scheduled wallet-balance background worker; make the balance UI render cached data immediately before network completion; route the Airdrop Campaign background worker through its coordinator and publish cached campaign rows before awaiting refresh; audit all coordinator callers for ignored `throttled` results. No registration, login, account switching, guest-to-login migration, private cloud restore, or private-record TTL caching is authorized.
- PR #135 remains **Draft / Open / unmerged** at the verified code head above. No production sync, production deployment or physical-device test is claimed.
- Progress estimate after this passing slice: **code/CI implementation ~69%**; **overall release readiness ~42%**. These are rounded project-level estimates, not test coverage. Live-data validation, physical-device acceptance, production/recovery and final release gates remain separate and incomplete.


### 2026-11-12 — wallet balance background refresh and campaign cache-first delivery

- Code commit `66b5e0b89b59cb7922824c7d60057361edb039b8` adds `WalletAssetSyncWorker`, queued independently by the existing 15-minute global dispatcher. It loads the current guest/session wallet list and refreshes supported asset networks (EVM: Ethereum, BNB Chain, Polygon, Arbitrum, Optimism, Base, Avalanche; BTC; Solana) on an 8-hour per-account + wallet + network cadence. TRON remains excluded because the current asset-detail UI/provider contract does not expose it.
- Balance payloads remain in the existing account/wallet/chain-scoped Room storage; the worker uses the shared coordinator only for refresh lifecycle and metadata. It does not write personal balance data into the public cache namespace. A cold/missing Room snapshot must still be fetched even if an old cadence marker exists.
- The dynamic-catalog background worker now warms the published campaign directory through `AirdropCampaignRepository` and its six-hour coordinator TTL. `AirdropCampaignsViewModel` now publishes cached Room rows before awaiting refresh. Existing wallet-balance detail UI already reads its scoped Room snapshot before the network call.
- Exact-head Android workflow **#43151808498** passed on `66b5e0b89b59cb7922824c7d60057361edb039b8`: Android compilation, unit tests, Debug APK assembly and artifact upload succeeded; backend tests were skipped because this batch changed Android files only.
- Follow-up caller audit found that the Airdrop Hub manual refresh still passed `force=true`, bypassing the shared cooldown; that bypass is removed. Market hot-coin and campaign manual-refresh methods now surface `throttled` as an error instead of silently returning cached data as if a fetch succeeded. Campaign UI displays the refresh error while retaining its existing rows. OKX detail already surfaces `throttled`; community catalog/snapshot and remote market-search calls are background/read-through paths rather than explicit manual refreshes.
- **Status:** wallet balance scheduled refresh and campaign cache-first/background warm-up are implemented and exact-head CI verified. Physical-device behavior, provider live responses and real wallet-balance correctness remain pending. Manual-throttle caller audit fixes are in this follow-up batch and await its CI.
- Progress estimate after the passing batch: **code/CI implementation ~70%**; **overall release readiness ~43%**. This is a rounded project-level estimate, not a statement of device, live-data or production completion.


### 2026-11-12 — refresh-throttle caller audit follow-up verified

- Exact-head Android workflow **#43152173285** completed successfully on code commit `a7dc2647596f6a2f2c41cec900dfb0a49578560f`: `changes` and `android-build` jobs passed. `backend-tests` was skipped by the workflow's change filtering; this is not a backend test pass.
- The follow-up removes the Airdrop Hub manual-refresh `force=true` bypass, and makes Market hot-coin and campaign manual refresh paths surface the coordinator's throttled result rather than silently treating cached fallback as a successful refresh. Campaign UI retains existing rows while showing the refresh error.
- Wallet balance background worker and campaign cache-first/background warm-up from commit `66b5e0b89b59cb7922824c7d60057361edb039b8` remain covered by passing exact-head Android CI #43151808498. The follow-up CI above verifies the additional caller/UI changes on top.
- **Status:** this caller-audit follow-up is complete in code and exact-head Android CI. This does not claim physical-device acceptance, provider live-data validation, or production deployment.
- PR #135 remains **Draft / Open / unmerged**. No production sync was enabled.
- Updated estimate: **code/CI implementation ~72%**; **overall release readiness ~44%**. Estimates remain rounded project-level indicators, not test coverage. Remaining work includes a deeper all-caller/scope audit, missing regression tests where appropriate, physical-device acceptance, live provider validation and release gates.


### 2026-11-12 — background worker must not treat stale fallback as successful refresh

**Audit finding:** the coordinator intentionally returns last-known-good cached data after a provider failure, together with `stale=true` and `errorMessage`. The wallet asset repository currently drops those result fields when constructing `WalletAssetSnapshot`; the wallet activity snapshot also lacks equivalent status fields. Both WorkManager workers can therefore mistake a stale fallback for a successful refresh and advance the 8-hour `SyncCadence`, delaying the next retry despite the provider request having failed.

**Required correction (approved as reliability work within the existing wallet refresh scope):**
- Preserve coordinator `stale`, `errorMessage`, and `throttled` in both wallet snapshot models; never erase the old cached payload.
- Workers may mark the 8-hour cadence successful only when the coordinator result is genuinely fresh/successful (or the cached payload is already fresh). A stale fallback with an error must cause `Result.retry()` and must not advance the success cadence.
- Cancellation must continue propagating; no account/session or wallet/network scope may be broadened.
- Add repository/worker regression tests proving provider failure + cached payload keeps the payload, exposes stale/error state, and does not mark cadence success; successful refresh still marks cadence.
- This is background refresh reliability, not private cloud sync or a change to registration/account behavior.

**Status:** audit-confirmed defect; requirement recorded before implementation. Implementation and exact-head CI pending.


### 2026-11-12 — wallet background stale-fallback retry fix verified

- Exact-head Android workflow **#43152987166** passed on `baba1209e780c9bb4c9ce1b54a27ba41bcd500a1`: Android compilation, unit tests, Debug APK assembly and artifact upload succeeded. Backend tests were skipped by the workflow's Android-only change filter.
- Wallet activity and balance repositories now preserve coordinator stale/error/throttle status in their returned snapshots. Both background workers use a shared cadence-success policy and do not advance their 8-hour success timestamp when a stale fallback, error or throttle is returned.
- Added regression tests for wallet-activity provider failure with a retained cached snapshot and for the cadence-success decision. These tests passed in the exact-head workflow.
- **Status:** implementation and exact-head Android CI complete. Physical-device verification, live wallet provider correctness and background scheduling under real Android battery/network constraints remain pending.
- PR #135 remains **Draft / Open / unmerged**. No production sync, deployment or account-flow change was made.


### 2026-11-12 — campaign cache fallback must expose refresh failure in the list

**Audit finding:** `AirdropCampaignRepository.load()` returns `result.data` whenever cached rows exist, even when the coordinator reports `stale=true` and an `errorMessage` after a failed provider refresh. `AirdropCampaignsViewModel.load()` then clears its UI error. The old rows remain visible (correct), but the user cannot tell that the refresh failed (incorrect).

**Required correction:** expose a typed campaign load result containing rows + stale/error status; keep the existing simple `load()` API compatible for background callers, while the screen ViewModel uses the status-aware path and displays a non-blocking warning above the retained campaign list. A successful/fresh cache read must not show an error. Manual throttling behavior remains unchanged. Add repository/ViewModel regression coverage as feasible.

**Status:** audit-confirmed UI observability gap; requirement recorded before implementation. Implementation and exact-head CI pending.


**Implementation follow-up — code submitted, CI pending (2026-11-12):**
- Code commit `456d923c3d6056c39e2866f58a175aad809c8929` adds `AirdropCampaignLoadResult` with rows/stale/error fields and a status-aware repository method. The campaign screen ViewModel now uses that method, so stale fallback rows remain visible and the existing list warning slot can show the provider failure.
- Added a focused unit test for warning visibility on stale fallback and suppression on fresh/successful data.
- Exact-head Android workflow **#43153515681** is currently in progress; backend tests are skipped because this is Android-only. Do not mark complete until the exact-head run finishes.


**Verification update — 2026-11-12:**
- Exact-head Android workflow **#43153515681** completed successfully on code commit `456d923c3d6056c39e2866f58a175aad809c8929`: Android build/unit tests and Debug APK artifact steps passed; backend tests were skipped by Android-only path filtering.
- The list warning uses the existing non-blocking inline error slot while retaining cached rows. Manual refresh throttling continues to surface the repository's explicit “刚刚刷新过，请30秒后再试” error; this is not treated as a successful network refresh.
- Physical-device confirmation of the visual warning and Toast/snackbar behavior remains pending.


### 2026-11-12 — dynamic catalog worker must retry failed stale-cache revalidation

**Audit finding:** `DynamicCatalogWarmupWorker` calls the campaign repository's compatibility `load()` method, which returns last-known-good rows even when revalidation ended with `stale=true` and an error. The worker increments its success count and returns WorkManager success. Community catalog/snapshot warmups also need their failures to remain visible to the dispatcher rather than being silently counted as successful cache refreshes.

**Required correction:** use status-aware campaign loading and treat stale/error fallback as a failed refresh attempt for WorkManager scheduling, while preserving the cached rows. Track each catalog warm-up outcome; if any configured warm-up failed, return `Result.retry()` (successful cache reads remain cheap due to TTL, so already-refreshed sources are not refetched). Cancellation must propagate. Do not change private ledger/project/task persistence or enable private cloud sync.

**Status:** audit-confirmed background retry gap; recorded before implementation. Code and exact-head CI pending.


**Implementation follow-up — code submitted, CI pending (2026-11-12):**
- Code commit `dba5163dbed4c6bb9c663ebfa78908a1bbf01314` adds status-aware refresh methods for the public community catalog and community snapshots while preserving their existing list/map compatibility APIs.
- `DynamicCatalogWarmupWorker` now evaluates stale/error/throttled outcomes for campaign, community-directory and community-snapshot refreshes. It returns `Result.retry()` if any warm-up failed, without discarding last-known-good cache payloads; already-fresh sources remain cheap on retry due to their TTLs.
- Exact-head Android workflow **#43155017970** is running; backend tests are expected to be skipped for Android-only paths. Do not mark implementation verified until the run completes.



### 2026-11-12 — dynamic warm-up must report partial provider failures independently

**Follow-up audit finding after the first retry implementation:**
- `MarketSearchRepository.warmUpMarketCaches()` catches price and hot-coin failures separately but throws only when **both** fail. A partial failure is therefore silently counted as a successful warm-up, so WorkManager will not retry the failed market source.
- `DynamicCatalogWarmupWorker` currently runs community-snapshot refresh and market warm-up inside the same `try` block. If snapshot refresh throws, market warm-up is skipped, despite the comment claiming it continues independently.
- The Airdrop Hub refresh catch currently retains cached data but does not set the worker's aggregate failure flag. A failed hub refresh can therefore end in `Result.success()` and wait for a later dispatcher cycle rather than using WorkManager retry.
- A configured but invalid community snapshot base URL (for example, a non-HTTPS URL) currently returns cached rows as a normal success. That can conceal a configuration/provider failure from the worker. An intentionally blank base URL remains a supported disabled configuration and should be treated as a no-op, not an error.
- The dynamic warm-up queue uses WorkManager exponential backoff with a 30-second initial delay and unique-work KEEP semantics. Verify that a retry remains queued and that unrelated worker queues are not blocked.

**Required correction (reliability work within the existing public-data refresh scope):**
1. Preserve per-source outcomes for market prices and OKX hot coins. Any configured source that fails must be represented as a failed outcome so WorkManager retries; successful sources remain cheap on retry because their own cache TTL is already fresh.
2. Run community snapshots, market prices/hot coins, community directory, campaigns and Airdrop Hub in isolated error boundaries. One source failure must not skip another source's attempt.
3. Mark Airdrop Hub failure in the aggregate WorkManager result while retaining the existing last-known-good behavior.
4. Distinguish intentionally disabled snapshot configuration (blank base URL) from invalid configured URLs and incomplete/invalid manifests. The latter must return an error/stale result, not a successful empty/cached result.
5. Keep exponential backoff and unique source queues; add regression tests for partial market failure, isolated source execution, and invalid snapshot configuration/manifest status where test seams permit.
6. Do not change personal ledger/project/task persistence, wallet account scopes, registration/account flows, or enable private cloud sync.

**Status:** audit-confirmed gaps; requirements recorded before implementation. Code and exact-head CI pending.


**Implementation follow-up — code submitted, exact-head CI pending (2026-11-12):**
- Code commit `24f2db7022b1443a4131ff69a56c73534cc1e1f1` separates community-snapshot refresh from market warm-up, so a snapshot exception no longer skips market refresh. Airdrop Hub exceptions now mark the worker attempt failed and enter WorkManager retry while retaining cached announcements.
- `MarketSearchRepository.warmUpMarketCaches()` now treats either failed source (CoinPaprika market prices or OKX hot coins) as a failed warm-up. A successful source remains cheap on retry through its own TTL. Commit `e1bf8a00dbd9322ba0aa2cd1ea539ee3d866b349` adds four policy tests covering both-success, both-failure and each partial-failure case.
- A configured invalid/non-HTTPS snapshot base URL now fails instead of returning a successful cached result; a 304 response without cached snapshots and an empty manifest are also rejected. An intentionally blank base URL remains a supported disabled/no-op configuration.
- Community directory, snapshot, campaign, market and Airdrop Hub attempts now have independent failure boundaries. WorkManager retains exponential backoff (30-second initial delay) and unique-work KEEP behavior.
- Exact-head Android CI is pending. Device/background scheduling and real provider validation remain separate acceptance items.


### Verification update — dynamic catalog retry implementation (2026-11-12)

- Exact-head Android workflow **#43155017970** completed successfully on code commit `dba5163dbed4c6bb9c663ebfa78908a1bbf01314`: Android compilation, unit tests, Debug APK assembly and artifact upload passed. Backend tests were skipped by Android-only path filtering.
- This verifies the initial status-aware worker retry implementation only. The later partial-provider-failure and independent-error-boundary corrections are in a separate code batch and still await exact-head CI.
- PR #135 remains **Draft / Open / unmerged**. No physical-device acceptance, live provider validation, production sync or deployment is claimed.


### Verification update — partial dynamic warm-up failure handling (2026-11-12)

- Exact-head Android workflow **#43155885856** completed successfully on code commit `e1bf8a00dbd9322ba0aa2cd1ea539ee3d866b349`: Android compilation, unit tests, Debug APK assembly and artifact upload passed. Backend tests were skipped because the changes were Android-only.
- The passing run includes the four market warm-up outcome tests and compiles the worker/snapshot validation changes.
- The earlier dynamic worker retry commit `dba5163dbed4c6bb9c663ebfa78908a1bbf01314` also passed exact-head CI #43155017970.
- **Code/CI status for this reliability slice: complete.** Real Android background scheduling, device-visible refresh states, live provider responses and battery/network behavior remain unverified.
- PR #135 remains **Draft / Open / unmerged**. No production sync, deployment, registration/account-flow changes or private cloud sync were introduced.
- Updated rounded project estimate: **code/CI implementation ~73%**; **overall release readiness ~45%**. These are scope-level estimates, not test coverage. Physical-device acceptance, live-data validation, remaining feature work and release/deployment gates are still outstanding.


### 2026-11-12 — Airdrop Hub manual-refresh cooldown must remain visible

**Audit finding:** the Airdrop Hub repository correctly throws a user-facing cooldown message when the shared coordinator throttles a manual refresh, but `AirdropHubViewModel` catches the exception and replaces it with the generic “数据源暂时不可用” warning. The cached project list remains visible, but the user cannot tell that the request was intentionally throttled rather than a provider failure.

**Required correction:**
- Preserve the existing Airdrop Hub cached project list during cooldown.
- When the repository reports the 30-second manual-refresh cooldown, show the specific retry-after message instead of the generic provider error.
- Keep genuine provider failures mapped to the existing non-technical retained-cache warning.
- Add a small ViewModel/policy regression test. Do not change the coordinator cooldown or bypass it with force.

**Status:** audit-confirmed UI messaging defect; requirement recorded before implementation. Code and exact-head CI pending.


**Implementation follow-up — code submitted, exact-head CI pending (2026-11-12):**
- Code commit `4f94d343fbe70165afc48784a5cbaed85d80c174` changes the Airdrop Hub cooldown text to “刚刚刷新过，请30秒后再试” and preserves that exact message through the ViewModel. Genuine provider failures still use the generic warning that cached data was retained.
- Added three unit tests for cooldown-message preservation, provider-failure fallback and missing-error fallback.
- Exact-head Android CI is pending; physical-device confirmation of the displayed message remains separate.


**CI correction note — 2026-11-12:**
- Exact-head workflow **#43156338310** failed at Kotlin compilation because the new catch branch referenced `error.message` without binding the caught exception. This was a source-code compile error; the previous passing CI does not cover this later commit.
- Corrected in code commit `81529302595dc2909283826653f9679c88a58499` by binding the caught exception as `failure` and passing `failure.message` to the cooldown-message policy.
- The exact-head Android build/unit-test workflow for this correction is pending. Do not mark the Airdrop Hub cooldown UI change verified until it passes.


### 2026-11-12 — Home market dashboard must render Room cache before network refresh

**Audit finding:** `HomeViewModel.load()` currently calls `MarketSearchRepository.loadHotCoins()` before publishing any local hot-coin rows to UI state. When the cache is stale/missing, the shared coordinator waits for provider refresh/retries before returning, so the Home market dashboard can show a loading state even when last-known-good Room rows exist. This violates the required cache-first, non-blocking page-entry behavior.

**Required correction:**
- Add a read-only cached-hot-coins path that reads Room without triggering network work.
- On Home entry, publish cached BTC/ETH rows and their cache timestamp/status immediately; then perform stale revalidation in the background while retaining the visible rows.
- If there is no cached data, show the initial loading state until the first fetch completes.
- A failed refresh must keep the last-known-good rows and show a non-blocking warning; cancellation must propagate.
- Manual refresh continues to use the existing shared 30-second throttle and must not bypass it with `force`.
- Add policy/unit coverage for cached rows being visible before refresh and for preserving cached data on refresh failure where the existing test seams allow.

**Scope guard:** do not change wallet/account scoping, personal ledger/project/task persistence, registration/account flows, private cloud sync, provider selection or cache TTL.

**Status:** audit-confirmed cache-first UI gap; requirement recorded before implementation. Code and exact-head CI pending.


**Implementation follow-up — code submitted, exact-head CI pending (2026-11-12):**
- Code commit `1dc66b2c4473708b6ab81d0d89ae98ce9f6d0e75` adds `MarketSearchRepository.readCachedHotCoins()`, a Room-only read path that does not trigger provider requests.
- `HomeViewModel` now publishes cached BTC/ETH rows and their metadata into UI state before invoking coordinator revalidation. Cached rows remain visible during refresh and on provider failure; an empty cache retains the initial loading state. Cancellation is rethrown rather than rendered as a provider failure.
- Added four policy tests for cached-vs-empty initial loading, retaining cached rows after failed refresh, and replacing rows after successful refresh.
- The preceding cooldown-message build exposed an unbound catch variable; corrected in commit `81529302595dc2909283826653f9679c88a58499`. Its exact-head CI #43158102664 was still running at this checkpoint.
- Cache-first Home dashboard code is queued in exact-head Android workflow **#43158350021**. Neither workflow is marked passed yet; physical-device rendering and live provider behavior remain pending.


**Additional cancellation-safety audit note (2026-11-12):**
- Review of the cache-first implementation found that using Kotlin `runCatching` around the initial Room read could swallow `CancellationException` and incorrectly continue as if the cache were empty. The cache read and metadata read must explicitly rethrow cancellation while allowing ordinary storage-read failures to fall back safely.
- This is an implementation-safety correction within the cache-first requirement; no product-scope expansion.


**Cancellation-safety implementation follow-up — 2026-11-12:**
- Code commit `464406e64ebfbbcaeee4e4097af94ffc0d2f7e8a` replaces the cache-read `runCatching` with explicit cancellation propagation and safe handling of ordinary Room/metadata read failures.
- The first cache-first code commit `1dc66b2c4473708b6ab81d0d89ae98ce9f6d0e75` is awaiting exact-head CI; this cancellation correction is a newer code head and must receive its own exact-head verification.


## 2026-11-12 cache-first screen audit — wallet history

**Audit finding and approved correction (before code changes):**
- Wallet transaction history already reads the account/session-scoped Room snapshot before calling the refresh coordinator, and its background worker uses an 8-hour cadence. Preserve that architecture and the existing account/wallet/ecosystem metadata isolation.
- The screen currently clears its displayed transaction list at the start of every refresh effect, including a user-triggered refresh. This can temporarily hide last-known-good rows and show a full-page loading state while Room is reread, contrary to the cache-first requirement.
- Do not clear visible transactions or network-coverage information when a manual refresh begins. Keep the existing rows visible while the coordinator refreshes in the background; show loading affordance separately. Replace the rows only when a successful fresh result arrives. If refresh fails or is throttled, retain rows and show the warning/transient cooldown message.
- Initial entry may show a loading state only when there is no already-loaded snapshot; read the scoped Room snapshot before remote refresh. A wallet/account identity change must not leak the previous scope's rows.
- Keep the 30-second manual-refresh throttle, `manual=true`, no ordinary `force=true`, the existing local payload namespace, and the 8-hour worker cadence. Do not change registration, login, account migration, private cloud sync, ledger, project/task, or pending-sync behavior.
- Acceptance: cached rows remain visible throughout a manual refresh; successful refresh replaces them; failed/throttled refresh preserves them; empty cache shows initial loading; account/wallet scope changes never display another scope's rows; cancellation propagates.

**Status:** requirement recorded; implementation and exact-head CI pending. Physical-device acceptance remains separate.


## 2026-11-12 manual refresh feedback — Home market dashboard

- The manual-refresh cooldown applies to all user-facing refresh controls using the shared coordinator, not only wallet history and balances. Ordinary manual refresh must never bypass the 30-second cooldown with `force=true`.
- Audit finding: Home's manual hot-coin refresh throws the coordinator's throttle message, but `HomeViewModel` converts it to a generic stale-cache warning and the Home screen does not surface the specific cooldown feedback.
- Correction: preserve last-known-good BTC/ETH rows, expose the explicit “刚刚刷新过，请30秒后再试” message and show a brief transient Toast when the Home manual refresh is throttled. Do not show a success state or issue another provider request during cooldown.
- Keep background refresh independent of manual cooldown; no changes to TTL, provider selection, cache payloads or unrelated features.
- Acceptance: a second manual tap inside 30 seconds makes no extra request, keeps existing rows visible, and shows the cooldown message; a tap after cooldown can fetch again; network failure retains old rows and shows the normal failure warning.
- **Status:** requirement recorded; implementation and CI pending.


## 2026-11-12 global search hot-coins cache-first correction

**Audit finding and approved correction (before code changes):**
- The Global Search landing state currently calls `loadHotCoins()` before assigning the hot-coin list. The shared coordinator may await network refresh/retries, so users can see an empty hot-coins section despite Room already containing last-known-good rows.
- On entry, read and publish Room hot-coin rows and their cache metadata first, then run the ordinary coordinator refresh. Keep cached rows visible while refresh is pending; replace them only after a successful fresh result. On failure, preserve the cached rows and show the coordinator's stale/error metadata.
- Propagate `CancellationException`; do not wrap this lifecycle refresh in `runCatching` that turns cancellation into an empty list.
- Keep the existing shared cache key/source, TTL, retry/cooldown rules and public-data scope. No new provider, forced refresh, private-data cache, registration or sync behavior.
- Acceptance: cached hot coins render before any network wait; refresh success replaces them; failure retains them and displays stale/error status; an empty cache shows an honest empty/loading state; lifecycle cancellation propagates.

**Status:** requirement recorded; implementation and CI pending.


## 2026-11-12 global search query cancellation

- Audit finding: the coin and DApp remote-search branches wrap suspending `withContext(Dispatchers.IO)` calls in `runCatching`. Because `runCatching` catches `CancellationException`, a query/category change can cancel the old search yet allow its catch/continuation to update the new screen state with stale results.
- Replace those wrappers with explicit cancellation propagation and ordinary exception handling. Do not let a cancelled old query write results or error state after a newer query has started.
- Keep the existing 300 ms debounce, local-first search, shared cache coordinator and provider behavior unchanged.
- Acceptance: changing query/category cancels the previous remote lookup; only the active query may update results/errors; ordinary provider failures still preserve local results and show the existing fallback message.

**Status:** requirement recorded; implementation and CI pending.


## 2026-11-12 — APP 启动、行情看板、浏览器 Dock 与记账 UI 优化清单

用户提交的九项优化与 Bug 修复，作为同一批产品需求登记。实现必须遵循当前 Android Jetpack Compose 项目架构；用户示例中的 Flutter API（PanGesture、showModalBottomSheet、DropdownButton、Flexible 等）是交互意图，不是要求引入 Flutter 或重写技术栈。

### 优先级、范围与验收标准

1. **启动时间与白屏（P0）**
   - 测量冷启动到首屏可交互耗时，识别 Application/Activity/首屏中的阻塞任务和重依赖。
   - 将行情网络请求、非首屏必需初始化和 WebView 创建移出首屏同步路径；按实际使用延迟初始化。
   - 使用 Android SplashScreen API/system splash 与品牌主题，确保启动过渡期间无白屏/闪屏。
   - 不得为了启动优化删除首屏必需功能、破坏深链/恢复状态或在后台启动无界任务。
   - 验收：首屏不等待行情或 WebView；冷启动有系统 Splash；记录优化前后实测耗时。未在真机测量前不得宣称达到特定秒数。

2. **行情看板自定义（P1）**
   - 默认热门币列表补充 TRX、SOL、BNB 等常用币种（先核实当前行情 provider 是否支持和返回有效数据）。
   - 行情看板标题右侧提供编辑/添加入口，进入币种选择列表，支持勾选、取消勾选和保存。
   - 用户选择持久化到本地数据库；首页以本地保存的选择和行情缓存优先渲染，随后后台更新行情。
   - 为旧安装提供安全默认值/迁移；空选择、重复币种、未知交易对和行情缺失必须有明确处理。不得把用户配置仅放内存或随缓存 TTL 丢失。
   - 验收：默认列表包含已验证币种；编辑保存后重启仍保留；首页不因行情网络等待而空白；缺行情币种不会导致整个看板失败。

3. **内置网页 Dock 拖动与吸边（P0）**
   - 拖动手势绑定整个外层 Dock 容器，而不是单个图标；拖动时胶囊背景与内部图标整体移动。
   - 拖动结束按屏幕可用区域计算最近左/右边缘，以动画吸附；处理系统栏/安全区、横竖屏和屏幕尺寸变化。
   - 保留 Dock 内部横向滑动、添加小工具等既有行为；整体位置不持久化，重启/重新进入按既定默认位置，不记忆上次拖动位置。
   - 验收：拖动任意空白/图标区域时整体移动；松手吸边；内部横滑与点击仍可用；背景不残留/错位；位置不被持久化。

4. **记账取消后留白（P0）**
   - 取消“先导航到记账新页面再弹窗”的重复导航流程。
   - 从当前页面直接打开记账表单弹层（Compose ModalBottomSheet/Dialog 等）；取消只关闭弹层，不留下空白页/空白占位。
   - 保存成功后按原有记账数据持久化流程关闭弹层并刷新列表；取消不写入数据。
   - 验收：打开、取消、再次打开、保存、系统返回/点击遮罩流程均无空白页面和重复页面；未保存内容不落账。

5. **记账表单选择器（P1）**
   - Gas 币种改为可搜索/可选择的下拉或选择列表，预置常用链 Gas 资产（例如 ETH、BNB、TRX、SOL、MATIC/POL 等，具体按应用支持链校验）。
   - 归属项目改为从用户现有项目库选择，并保留适当的“无归属/自定义”路径；不可因改成选择器而限制用户新增私人项目。
   - 保留现有金额、链、交易类型、备注等字段与验证；保存的数据仍关联正确项目/币种 ID，不仅存展示名称。
   - 验收：可选择预置币种和已有项目；选项与链匹配；用户已有项目可选；历史记录显示正确；离线记账与待同步数据语义不变。

6. **“模拟账单”文案修正（P1）**
   - 全面检索用户可见的“模拟账单”标签，按真实数据状态改为“交易记录”或“链上交互”；未上链/等待用户确认的数据标为“待确认交易”。
   - 不得仅改标题而把模拟/预估数据伪装成真实链上交易；状态和数据来源必须准确。
   - 验收：相关页面、卡片、空状态、筛选和详情文案一致，状态语义真实。

7. **首页快捷入口文案溢出（P1）**
   - “记录 Gas 消耗与实际收益”精简为“记录Gas与收益”。
   - Compose 中对文本使用合理的宽度约束、maxLines 和 TextOverflow.Ellipsis/必要的 weight，避免挤压图标和卡片布局。
   - 验收：常见窄屏/大字体设置下不越界、不重叠；文案完整或优雅省略；不改变用户不可调整的卡片尺寸设定。

8. **移除重复的首页空投列表入口（P1）**
   - 删除首页快捷入口中的“空投列表”，保留底部导航“空投库”作为唯一主入口。
   - 只移除重复入口，不删除空投库页面、数据、路由目标或其他必要的空投详情跳转。
   - 验收：首页不再出现重复快捷入口；底部空投库入口和现有导航正常。

9. **“我的”页面底部内容被遮挡（P1）**
   - 检查页面滚动容器、底部导航和 WindowInsets/SafeArea 处理；为滚动内容添加正确的 bottom content padding/insets，避免设置项被底部导航/系统导航栏遮挡。
   - 不机械固定添加 80dp；根据实际底部导航高度和系统 inset 计算，避免多余空白。
   - 验收：最后一项设置可滚动完整显示并点击；手势导航/三键导航和不同屏幕尺寸下均正常。

### 执行规则
- 本批需求已登记；先审查当前代码与已有功能，避免重复实现或覆盖用户已有配置。
- 优先处理 P0：启动路径、Dock 拖动、记账取消留白；随后按依赖推进行情自定义、表单选择器和其余 UI 清理。
- 每个实现批次先更新本文件的实现状态，再改代码；提交后记录 commit、精确 SHA 的 CI 结果和未完成的真机验收。
- 不引入 Flutter 依赖，不改注册/登录/账号体系，不启用私人云同步，不改变个人账本/项目/离线新增/待同步数据的持久化语义。
- **状态：需求已登记；代码审计与分批实施待开始。**


### 首轮代码审计（2026-11-12）

- **启动：** `MainActivity.onCreate()` 在 Compose 首次组合时通过 `remember { TokenStore.readTokenBlocking() }` 同步读取 DataStore/解密 token；这是首屏主线程阻塞风险。Application 启动时还同步初始化 WalletConnect Core/AppKit 并安排后台任务。WebView 并非主页面启动时创建，而是在用户打开独立 `BrowserActivity` 时创建，因此不应为了本项把 WebView 提前初始化；继续保持按需创建。启动 Splash 主题/API 当前未配置。
- **Dock：** `BrowserAccountingToolbar` 已把长按拖动手势放在最外层 Box，背景胶囊和 LazyRow 同属该容器，松手已有左右吸边动画；但每次 pointer move 都启动新的协程分别 snap X/Y，存在更新竞争风险。应在 pointerInput 手势协程内顺序更新两个 Animatable，并保留现有不持久化位置行为。
- **快捷入口：** 当前默认卡片含 `airdrop_library`，副标题确实是“记录 Gas 消耗与实际收益”；首页卡片文本尚需明确宽度/行数约束。当前 MainActivity 底部导航实际是“主页、社区、项目、我的”，**并不存在“空投库”底部导航项**。因此暂不能直接删除首页空投入口，否则会让空投库失去唯一入口；需由产品决策确定新增第五个底部项，或将某个现有项替换/改名。
- **记账项目：** 归属项目已是项目选择按钮 + 项目选择列表，且支持本地新建，不是纯文本输入；保留现状并验证即可。Gas 币种仍是自由文本输入，应改为选择器。
- **“模拟账单”：** 代码中存在明确生成 `MOCK-` hash 和模拟 Gas 样本的演示数据。不能把这些数据改名为“链上交易”而误导用户；建议展示“本地演示记录/演示数据”，真正链上待确认记录才使用“待确认交易”。原始数据与识别状态不改。
- **我的页面：** ProfileScreen 主内容使用普通 Column，未见 verticalScroll，长内容确实可能被裁切；需要滚动容器和正确 bottom content padding。
- **记账取消留白：** 首页点击记账当前切换到 `AppOverlay.ACCOUNTING` 全屏二级目的地。下一步需定位该目的地的具体表单/弹窗状态，改为首页直接弹层；不能只改标题或简单隐藏导航而留下空白占位。

**首轮状态：** 已确认的实现缺口和已有实现已分开记录；开始实施 Splash/首屏阻塞、Dock 更新竞争、Profile 滚动与快捷入口文案。空投入口迁移待用户确认底部导航目标。记账弹层待进一步定位实现文件。


### 记账入口交互修正的实现方案（2026-11-12）

- 当前 `AccountingCenterScreen` 通过 `initialCreateMockBill=true` 自动写入一条 MOCK 草稿，再弹出确认 Dialog；取消后底层没有内容，正是“取消后留白”的原因。
- 改为 Home 直接打开 Material3 `ModalBottomSheet`，不再切换到全屏 `AppOverlay.ACCOUNTING`；底层使用已有待确认记录，若有记录直接打开首条确认表单，无记录则显示待确认记录空状态/列表，不再自动创建模拟账单。
- 表单取消/系统返回/保存成功后关闭整个 Home 记账弹层，不遗留空白页面。浏览器 Dock 原有调用不受影响。
- 该修正不自动新增链上交易，不伪造 tx hash，不改变用户手动确认实际 Gas/收益的既有规则。
- **状态：方案已记录，代码待实施。**


### 首轮实现记录（2026-11-12，CI 待验证）

**已提交到当前分支：**
- System Splash：新增 AndroidX Core SplashScreen 依赖、启动主题和 Manifest 启动主题；启动 token 的 DataStore 读取从 Compose 首次组合移至 `Dispatchers.IO`，Splash 等待读取完成，避免主线程 `runBlocking`。
- 启动审计确认 WebView 只在用户打开 `BrowserActivity` 时创建，未在 Home 首屏预初始化；保留按需加载。Application 中 WalletConnect SDK 初始化仍是潜在耗时点，需后续拆分评估并用真机启动测量，当前不宣称已消除全部 4–5 秒启动耗时。
- Dock：最外层容器本来已持有拖动手势和吸边动画；已把 X/Y 的 `Animatable.snapTo` 改为在同一个 pointerInput 手势协程中顺序执行，消除每个 pointer move 启动独立协程造成的更新竞争。保留长按、内部 LazyRow 横滑和不持久化位置。
- 首页快捷入口文案已改为“记录Gas与收益”，副标题添加单行省略。
- Profile 主 Column 已改为可滚动并增加底部内容空间，避免“设置”被裁切；真机不同导航模式仍待验收。
- Gas 币种输入已改为按当前链优先提供原生 Gas 资产选择，并保留自定义币种；归属项目选择原本就已有本地项目列表/新建入口，本轮保留。
- Home 记账入口已从全屏二级导航改为 Material3 ModalBottomSheet；不再在打开时自动创建 MOCK 草稿。若有待确认记录则直接打开首条表单；没有记录则显示空列表；取消/关闭/保存成功会关闭整个弹层，不留空白页。
- “模拟账单”在工具入口/历史条目中改为“本地演示记录”，真正非 MOCK 记录使用“待确认交易”；保留 MOCK 数据身份，不把演示记录伪装成真实链上交易。
- OKX/Home 支持列表增加 TRX，首页现显示当前 OKX 支持的 BTC、ETH、SOL、BNB、TRX、XRP；这只是默认热门币展示扩展，**自定义编辑入口和 Room 持久化选择尚未实现**。
- 需求 8 暂缓：当前实际底部导航只有“主页、社区、项目、我的”，没有“空投库”Tab。未删除首页“空投列表”入口，以免造成空投库无入口。等待用户决定新增第五个 Tab，或替换/改造现有 Tab。

**当前代码头：** `7aab7706c6ea2603c9ed2109cec6d143c5e73fbb`。

**CI：** Android workflow **#43163290443** 正在执行；Android 编译/单元测试尚未完成，backend tests 因 Android-only 变更按路径规则跳过。未标记通过。PR #135 继续保持 Draft/Open/Unmerged。

**仍待实施：** 行情看板编辑入口 + Room 持久化选择；空投入口底部导航决策；剩余文案全局检索；启动时 WalletConnect 初始化延迟化评估；真机冷启动/拖动/弹层/滚动验收。记账交互、启动优化和新增币种的 CI 通过前不得标记为完成。


## 2026-11-12 — 首页快捷入口移除“空投列表”，记账按正式功能呈现

**用户根据首页截图明确更正（优先级 P0，覆盖此前“等待底部导航决策”的暂缓状态）：**

- 删除首页“快捷入口”区域右下方的“空投列表”卡片；空投内容统一从底部导航“空投库”进入。只删除重复快捷入口，不删除空投库页面、数据、路由或详情跳转。
- 首页快捷入口中的“记账”是正式记账入口，显示名称统一为“记账”，不得命名为“模拟账单”“本地演示记录”等，也不得让用户误以为此入口只提供模拟账单。
- 点击“记账”进入正式记账流程，允许用户录入实际 Gas 消耗、实际收益、链、Gas 资产、项目、交易信息及备注，并按现有本地持久化规则保存为真实用户账目；不得自动创建演示账单，不得伪造链上交易哈希或声称已上链。
- 真实账目、链上待确认交易、用户主动创建的普通记账记录、明确标识的本地演示样本必须按数据来源/状态区分。演示样本仅用于测试或演示，不得成为正式记账流程的默认内容，也不得混入真实交易记录统计。
- 验收：截图所示快捷入口只保留“记账、钱包管理、TRON 能量租赁”等其他有效入口，不再出现“空投列表”；底部“空投库”正常进入原有空投库；点击“记账”直接打开正式记账表单，取消不写入、保存后生成真实本地账目并可在交易记录中查看；无自动 MOCK 草稿。

**执行状态：** 本条已先写入需求文档；代码审计与实现待进行。此前“空投列表入口需等用户选择新增/替换 Tab”的结论作废，因为用户已明确指出底部导航已有“空投库”。


### 2026-11-12 更正后的首轮实现记录（CI 待重新验证）

- 首页快捷入口：已从默认卡片、可添加卡片白名单和旧版布局迁移中移除 `airdrop_library`，旧安装升级时也会清理已保存的重复卡片；底部导航“空投库”及其页面不受影响。
- 正式记账：移除浏览器 Dock 的“本地演示记录/mock_bill”工具入口，改为“新增记账”；Home 的“记账”入口继续打开交易记录/记账弹层。记录列表增加“新增记账”，创建明确标识为“手动记账（非链上交易）”的本地待填写记录，用户录入 Gas、实际收益、项目及资产明细后确认入个人账本。手动记录不伪装成链上交易，不参与链上核验/社区贡献；真实待确认链上交易仍单独标识。
- 旧 Dock 工具偏好中的 `mock_bill` 自动映射到 `new_accounting`，避免升级后用户原有工具配置丢失或出现空 Dock。
- 同步修复此前 Android workflow #43163290443 在 `BrowserAccountingToolbar.kt:309-310` 的 restricted suspension 编译错误：拖动回调将偏移更新排入受控 Job，释放时等待最后一次更新后再执行吸边动画。
- **代码提交：** Home 快捷入口删除 `99b51695ae70df1ee6847f88d43bffe002c2148c`；记账新增手动记录与 Dock 更新 `9c796f0cf4b806a46726399ddb1bff6b826e4930`、`b4932ed8c95f1571b58157603a3459e356bc3262`、`6b2ab8c06e4dcf05205293342970890b44e029d3`、`465eec341deaa96d1486239816338e6ac01b8bf7`。
- **CI：** #43163290443 在旧代码头失败，失败点为 Dock drag 回调的 restricted suspension；上述修复后的最新代码头尚未取得新的 CI 结果，不标记通过。真机记账/升级迁移验收仍待执行。
