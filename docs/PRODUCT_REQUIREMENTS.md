# Airdrop Hunter — Product Requirements & Progress Ledger

> This is the project-wide source of truth. Before each implementation step, compare the current code and plan against this document. User-confirmed requirements override implementation assumptions. Cancelled and deferred items must not be reintroduced without explicit approval.


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

## 1. Product and engineering boundaries

- Product: Airdrop Hunter, an Android application for airdrop projects, task tracking, wallet-address management, Gas/reward accounting, and community project statistics.
- Stack: Android Kotlin + Jetpack Compose, FastAPI, PostgreSQL/Neon, Render, and GitHub Actions APK builds.
- Keep the project affordable for a solo developer. Prefer free tiers, cache responsibly, batch coherent changes, and avoid unnecessary CI runs.
- Guest use is supported; account creation is optional for guest workflows.
- No custody, seed phrase/private-key collection, signing, transfers, swaps, or approvals.
- Public wallet addresses and read-only public chain data only.
- Test data must never contaminate real private ledgers or public statistics.

## 2. Mandatory data ownership and privacy policy

This policy is authoritative across Android storage, API requests, database models, sync workers, tests, and UI explanations.

| Data layer | Guest | Logged-in user |
|---|---|---|
| Detailed personal ledger (wallet addresses, Gas details, rewards, project participation, transaction references, timestamps) | Stored in app-private local storage only | Stored locally and uploaded to the user's private cloud ledger |
| Private cloud restore | Not available; clearing app data or uninstalling loses guest details | Account-scoped restore on another device |
| Public community contribution | Valid confirmed Gas, actual rewards, and project participation may contribute | Same |
| Public contribution storage | Anonymous aggregate only; no wallet address, tx hash, personal ledger row, or account identity | Same; must not be linked to the user's account |
| Clearing/uninstalling app data | Local private data is lost; no server copy exists | Cloud private ledger remains available to that account |

Additional rules:
- Guest personal details are saved in the app-private local database only. They are not uploaded to a private cloud vault. Clearing app data or uninstalling the app loses guest personal details.
- Public aggregate data is separate from the private ledger. Never expose individual bills, addresses, hashes, or account identifiers in public project statistics.
- Only user-confirmed actual Gas/reward values are eligible for public totals. Estimates and mock/demo records are excluded.
- Contributions must be idempotent and retry-safe.
- Logged-in private ledger endpoints require authentication and must scope every read, update, and delete to the authenticated account.
- Switching accounts must not expose another account's local private cache.

## 3. Wallet and address management

- Airdrop-specific multi-wallet and multi-chain public address management.
- Multiple addresses per chain; user labels, tags, notes, and project associations.
- Associate addresses with projects, Gas records, rewards, and transactions.
- Read-only chain activity and transaction history.
- Supported activity ecosystems currently include EVM networks (Ethereum, BSC, Polygon, Arbitrum, Optimism, Base, Avalanche), TRON, Bitcoin, and Solana.
- Manual chain selection and basic format validation are acceptable. Automatic address recognition is deferred.
- No seed phrase/private key entry, wallet custody, signing, transfers, swaps, or approvals.

## 4. Browser and DApp interaction

- Built-in WebView for project sites and DApps.
- External wallet connection for user-approved DApp interaction, with origin/session protections and an approval-guarded bridge.
- User can choose/switch the address used by a DApp and enter project claim flows.
- Associate browser interaction with a project and later with its on-chain transaction/accounting record.
- HTTPS-only navigation and strict TLS failure handling; never bypass certificate errors.
- No signing or transaction submission capabilities in the current product boundary.
- Real-device validation of WalletConnect/Reown, WebView lifecycle, origin boundaries, session expiry, and DApp compatibility remains required.

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
- Guest private accounting remains local-only. Logged-in private accounting syncs to the authenticated account-scoped cloud ledger.

## 6. Projects and tasks

- Project directory, project details, statuses, personal project tracking, and history.
- Create/edit/complete/delete tasks with progress tracking and project ownership checks.
- Associate projects with wallets/addresses, chains, Gas, rewards, and interaction history.
- Project totals include Gas, actual rewards, and net P&L.
- APP 主动发现空投项目仍是正式需求，但必须优先采用零成本、现成且可稳定访问的数据源；不为付费 API、付费订阅或复杂授权投入成本。禁止自建爬虫抓取/解析千奇百怪的网站页面。优先使用免费的公开 API、免费 RSS/Atom、免费第三方空投平台公开页面/订阅源；能稳定取得哪些公开字段就收录哪些，无法取得的字段留空，不做强求。只有在免费且授权/访问条件简单时才接入 Telegram/Discord 等来源；X/Twitter 若需要付费 API 或复杂授权则不接入。若没有合适免费数据源，就不做该来源。内部社区项目目录/搜索持续保留。

## 7. Micro Community

- Public project directory/cards, categories, tags, and project details. Project metadata may be populated from approved external APIs/RSS and user submissions; project skeleton (name, logo, official links, funding/tokenomics metadata) is distinct from community-generated participation metrics.
- **Project list card UI (user-confirmed):** keep cards concise for browsing, showing project avatar/logo, name, brief 1–5-star rating and reputation label (for example, “好评如潮” / “褒贬不一”), plus an **overall Gas cost vs actual reward** summary with separate USD valuations and a red/green comparison bar (red = Gas cost, green = actual reward). Tapping a card opens the full project detail page. The detail page exposes the underlying per-token amounts; native units are never added across different symbols.
- **Project detail UI (user-confirmed):** use a single vertically scrolling waterfall/stacked layout, not tabs. From top to bottom show project avatar, name, URL, and description; overall star rating and derived reputation wording; then four simultaneously visible Gas-vs-actual-reward comparison rows: overall, last 7 days, last 3 days, and last 1 hour. Each row shows per-currency amounts and comparable USD valuation where available, plus a red/green proportional bar only when there are comparable non-zero amounts; never render a fabricated 50/50 bar for missing or zero-denominator data.
- **Metric integrity:** public project metrics use only eligible, user-confirmed actual Gas and actual rewards from anonymous aggregate contributions; theoretical/estimated rewards remain separate and are never displayed as realized rewards. Do not add unlike tokens together without per-token breakdown; USD estimates must be labeled as valuations. Missing/zero-denominator data must be represented honestly; proportional bars must use exact ratios and a zero-valued side must have zero width, never an artificial minimum-width segment. Reputation labels are derived from real rating aggregates and distribution, never hard-coded.
- Public aggregate Gas, actual rewards, and net P&L presentation.
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

## 9. Refresh, sync, and reliability

- Manual refresh and background refresh.
- Background work must not block normal app interaction.
- WorkManager-based sync, offline queue, retry/backoff, and recovery.
- Idempotent writes and duplicate prevention.
- Clear distinction between local cache, private cloud sync, and public aggregate contribution.
- Handle expired/invalid sessions, account switching, network failures, and partial sync without data loss.
- Do not claim app-wide refresh is complete based only on one subsystem's sync worker.

## 10. Security, deployment, and release quality

- FastAPI authentication and account-level authorization for private data.
- PostgreSQL migrations must be reviewed, additive/data-preserving where possible, and never rewrite an applied production migration.
- Secrets remain in deployment environment, never source control.
- Verify database backup/restore and deployment rollback.
- Validate imported content, URLs, contract/address inputs, and WebView navigation boundaries.
- Meaningful Android and backend tests; run CI once per coherent ready-to-validate change set.
- Real-device acceptance is required for browser/wallet connection, cross-device cloud restore, data correctness, and end-to-end app flows.
- A merged PR or passing CI does not by itself prove real-device completion.
- Freeze broad UI redesign until core feature chains are complete; then perform one unified UI refinement and full regression.

## 11. Cancelled or deferred scope — do not reintroduce

- Full OKX-style portfolio/dashboard.
- Seed phrase/private-key wallet creation, custody, signing, transfers, swaps, and approvals.
- Self-written arbitrary-site crawling/scraping is cancelled. Free verified provider APIs, free allowlisted RSS/Atom feeds, freely accessible third-party airdrop platform listings, and user submissions are preferred discovery channels. Telegram/Discord are optional only when free and authorized. Paid X/API integrations and paid data services are out of scope unless the user explicitly changes the zero-cost rule.
- Automatic address recognition (deferred).
- Community chat, groups, DMs, mentoring, and teaching/social features (deferred).
- AI analysis is not a confirmed requirement.
- Old PR #31 page is not to be merged; obsolete/unsafe PR #36 is not to be merged directly.

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
3. **Private sync layer:** authenticated users sync/restore detailed ledger rows through account-scoped authenticated APIs; guests do not sync private ledger details to any cloud endpoint.
4. **Public contribution layer:** both guest and authenticated sessions may send only project ID + confirmed aggregate Gas/actual reward + an opaque event ID for idempotency. The server stores public contribution rows with no account identity; idempotency prevents duplicate retries.
5. **Community presentation layer:** server-side scheduled jobs aggregate anonymous contributions into static JSON; Android Room cache renders first and CDN revalidation updates silently. Community UI must not bypass this path with live per-project aggregation.
6. **Project/task layer:** project and task ownership, project-to-wallet assignments, and ledger links are separate from public project metadata. A transaction is linked to a project only by explicit user selection.
7. **Discovery layer:** provider APIs/RSS candidates and user submissions populate project metadata only. They must never fabricate participation, Gas, rewards, ratings, or P&L.

Recommended next implementation order, based on shared-code dependencies:

1. **Public metric cleanup (PR #73 merged):** backend, snapshot, and Android public data contracts now expose confirmed monetary aggregates, star ratings, and catalog metadata. The New ranking sorts by catalog creation time.
2. **Transaction-to-ledger reliability (PR #74 merged):** duplicate transaction identities are isolated by account scope, public event IDs remain stable for server idempotency, demo rows are excluded from sync/contribution, fee-payer attribution is conservative, and manual confirmation is required.
3. **Currency-aware accounting (PR #75 merged):** native token amount/symbol/decimals and valuation timestamp/source now flow through chain activity, pending bills, private ledger, anonymous public contributions, and per-token static snapshots. USD valuations remain separate and unlike native units are never added together.
4. **Sync/restore hardening (PR #77 merged):** bind each private-ledger sync to the token/account that started it, use explicit Authorization headers and token-scoped local cache access, atomically merge cloud rows with local rows, and reject cloud restores beyond the 10,000-row safety cap without overwriting local data. Real-device cross-device and app-data-clear acceptance remains pending.
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
- **UI hierarchy:** concise project-list cards show identity, brief rating/reputation, and overall Gas-vs-actual-reward bar. Tapping opens a vertically scrolling detail page with the four windows above simultaneously visible; no tabs.


### Static community snapshot implementation (PRs #67, #69, and #70 merged; #68 superseded)

- Backend precomputes overall, rolling 7d, rolling 3d, and rolling 1h snapshots from eligible anonymous public contribution rows.
- The scheduler computes due windows in Asia/Shanghai: overall + 7d daily at 08:00; 3d at 00:00/06:00/12:00/18:00; 1h every five minutes. Timestamps are stored/compared in UTC and presented in UTC+8.
- Snapshot payloads contain project catalog metadata, confirmed USD valuations, and token-separated native aggregates only. They do not contain wallet addresses, transaction hashes, user IDs, or private ledger rows. Gas and reward token amounts are grouped independently by token symbol and decimals; unlike native units are never added together.
- Versioned JSON objects are uploaded before the manifest is atomically switched. Android reads Room cache first, then silently revalidates the CDN using a dedicated unauthenticated HTTPS client restricted to the configured CDN host. Offline or invalid responses preserve the last good cache.
- Project list and detail screens use static snapshots rather than per-project live stats calls. The detail screen shows the four windows vertically, with each non-overall window's own update time. There is no manual refresh affordance in the micro-community. Empty/zero-denominator data is not represented by a fabricated balanced bar.
- PR #67 merged the architecture requirement; PR #68 was closed as superseded; PR #69 merged the actual backend and Android implementation as `25035c83e6f69f125d0b336170fdd0e0d5c93316`; PR #70 merged the empty-data correction as `0c57de441fcfb668dbb6e4d639ea68f9e6c443ee`. Android and backend CI passed for the merged implementation and correction.
- **Not production-complete:** the scheduled workflow is disabled until storage/CDN/database settings are configured. No production R2 bucket, public CDN endpoint, or scheduled publishing activation is claimed.

### Free scheduled snapshot publishing

- A GitHub Actions scheduled workflow invokes the snapshot dispatcher every five minutes. The dispatcher uses Asia/Shanghai internally, so the daily overall/7d and six-hour 3d windows are selected from the required UTC+8 wall clock while 1h refreshes every five minutes.
- The scheduled workflow is intentionally disabled until the repository variable `COMMUNITY_SNAPSHOT_PUBLISH_ENABLED` is set to `true`; after that, it also requires `DATABASE_URL`, `COMMUNITY_SNAPSHOT_S3_BUCKET`, `COMMUNITY_SNAPSHOT_S3_ENDPOINT_URL`, `COMMUNITY_SNAPSHOT_S3_ACCESS_KEY_ID`, `COMMUNITY_SNAPSHOT_S3_SECRET_ACCESS_KEY`, and `COMMUNITY_SNAPSHOT_PUBLIC_BASE_URL` configured. Missing configuration causes a clean skip and never publishes an empty manifest.
- Set `COMMUNITY_SNAPSHOT_BASE_URL` as a repository variable for Android builds after the public CDN endpoint is ready. No storage credential is included in the APK.
- GitHub scheduled jobs are best-effort and can start late under platform load; the dispatcher still computes due windows from Asia/Shanghai at actual execution time. This is not a real-time SLA.


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
- Private ledger asset detail is retained locally and in the authenticated account's cloud ledger. Guest details remain local; only confirmed anonymous aggregates enter public contribution rows.
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
- Real-device cross-device restore, account switching, app-data clearing, and offline retry acceptance remain pending.

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
- Add a GitHub Actions daily sync at 08:17 Asia/Shanghai. The workflow is disabled unless repository variable `AIRDROP_DISCOVERY_SYNC_ENABLED=true` and a database secret are configured.
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
- Logged-in users' detailed personal ledgers continue to sync to their authenticated, account-scoped cloud storage and can be restored after signing into that account.
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
- Per-source cadence is retained: public contribution outbox at 15 minutes; wallet activity, private ledger, and favorites at 8 hours. Wallet, private-ledger, and favorites cadence keys are scoped to the guest/account identity, so signing into a different account does not inherit the previous account's last-sync timestamp. Successful timestamps are persisted locally; a failed task is not marked successful and WorkManager retries with exponential backoff.
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
