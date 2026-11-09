# Airdrop Hunter — Product Requirements & Progress Ledger

> This is the project-wide source of truth. Before each implementation step, compare the current code and plan against this document. User-confirmed requirements override implementation assumptions. Cancelled and deferred items must not be reintroduced without explicit approval.

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
- Guest does not mean “do not save”: guest personal details MUST be saved in the local app database.
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
- Guest private accounting remains local-only; logged-in private accounting syncs to the authenticated cloud ledger.

## 6. Projects and tasks

- Project directory, project details, statuses, personal project tracking, and history.
- Create/edit/complete/delete tasks with progress tracking and project ownership checks.
- Associate projects with wallets/addresses, chains, Gas, rewards, and interaction history.
- Project totals include Gas, actual rewards, and net P&L.
- APP 主动发现空投项目仍是正式需求，但必须优先采用零成本、现成且可稳定访问的数据源；不为付费 API、付费订阅或复杂授权投入成本。禁止自建爬虫抓取/解析千奇百怪的网站页面。优先使用免费的公开 API、免费 RSS/Atom、免费第三方空投平台公开页面/订阅源；能稳定取得哪些公开字段就收录哪些，无法取得的字段留空，不做强求。只有在免费且授权/访问条件简单时才接入 Telegram/Discord 等来源；X/Twitter 若需要付费 API 或复杂授权则不接入。若没有合适免费数据源，就不做该来源。内部社区项目目录/搜索持续保留。

## 7. Micro Community

- Public project directory/cards, categories, tags, and project details. Project metadata may be populated from approved external APIs/RSS and user submissions; project skeleton (name, logo, official links, funding/tokenomics metadata) is distinct from community-generated participation metrics.
- **Project list card UI (user-confirmed):** keep cards concise for browsing, showing project avatar/logo, name, brief 1–5-star rating and reputation label (for example, “好评如潮” / “褒贬不一”), plus an **overall Gas cost vs actual reward** summary with both amounts and a red/green comparison bar (red = Gas cost, green = actual reward). Tapping a card opens the full project detail page. List and detail must use the same overall aggregation and currency/valuation rules.
- **Project detail UI (user-confirmed):** use a single vertically scrolling waterfall/stacked layout, not tabs. From top to bottom show project avatar, name, URL, and description; overall star rating and derived reputation wording; then five simultaneously visible Gas-vs-actual-reward comparison rows: overall, last 7 days, last 3 days, today, and last 1 hour. Each row shows per-currency amounts and comparable USD valuation where available, plus a red/green proportional bar. Retain 24-hour activity heat, participation/event counts, and other existing project statistics on the detail page. The 1-hour window is a short-term activity signal and must not replace the rolling 24-hour metric.
- **Metric integrity:** public project metrics use only eligible, user-confirmed actual Gas and actual rewards from anonymous aggregate contributions; theoretical/estimated rewards remain separate and are never displayed as realized rewards. Do not add unlike tokens together without per-token breakdown; USD estimates must be labeled as valuations. Missing/zero-denominator data must be represented honestly, not fabricated as a balanced bar or positive outcome. Reputation labels are derived from real rating aggregates and distribution, never hard-coded.
- Public aggregate Gas, actual rewards, participation count, and net P&L presentation.
- Rankings: 吸金榜、避坑榜、新星榜.
- Categories/tags may include L2, zero-cost, testnet-required, high-Gas, confirmed airdrop, and similar classifications.
- Favorites/following and personal to-do tracking.
- Personal achievement card: project count, Gas, rewards, and win rate.
- Global participants and 24-hour interaction heat where supported by reliable data.
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
- PR #49: rolling 24-hour confirmed community activity and new-star ranking; merged commit 7dcf9fecf41bbb58bda0634a4d6e57599ad21506.
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
- PR #62: community project cards display their stored URL and open-link action; merged commit e7879556a10d26ff94041d65eee90be708345982; Android and backend CI passed.\n- PR #63: admin-token-protected discovery candidate queue, approve-to-catalog/reject actions, review timestamps/status, HTTPS/name revalidation, and duplicate linking; merged commit 1a88bef3f7baea3a4f79965c3840d6ee8a258a78; backend and Android CI passed.
- PR #64: end-to-end tests for candidate submission, admin review queue, approve/reject, persisted review metadata, duplicate-action conflicts, and admin-token protection; merged commit 0e31b0af2a0c9ab7958c09eba3d13a9f51e07bbb; backend and Android CI passed.

### Current audit findings

- Confirmed in main source: guest confirmed bills are written to a guest-scoped local personal-ledger cache and guest-scoped local P&L/project/interaction tables.
- Confirmed in main source: guest community outbox entries contain only project ID, aggregate Gas, aggregate actual reward, interaction count, and sync metadata; the sync request does not send wallet address, transaction hash, or personal ledger fields.
- Confirmed in main source: guest outbox batches use no Authorization header; logged-in batches use the user's bearer token.
- Confirmed in main source: guest wallet addresses are saved through the app-local cache; logged-in wallet addresses are loaded from the authenticated wallet API and cached under an account-specific key. Room project, P&L, interaction, pending-bill, and outbox records are scoped by token user ID or guest scope.
- Confirmed in main source: private personal-ledger endpoint requires authentication and scopes reads/deletes/upserts to the authenticated user. Android AccountingRepository.syncPersonalLedger() returns before any private cloud call when no token exists.
- PR #46 and PR #47 are merged: new public contribution rows store user_id as null; migration 0005 clears historical account links while preserving aggregate totals.
- Source audit confirms guest wallet addresses are stored in the guest-scoped app cache and authenticated wallet lists are fetched from the private wallet API and cached by account scope. Device-level app-data clearing and account-switch behavior still require real-device acceptance; production database backup/restore also remains unverified.
- Community implementation: PR #48 adds guest/account-scoped local favorites; cross-device favorite sync remains pending. PR #49 adds rolling 24-hour confirmed interaction heat and uses it for the new-star ranking. PR #50 adds global 1–5 star aggregates for logged-in accounts after server verification against confirmed private-ledger participation; guest ratings remain local-only. PR #51 adds in-app search over the existing seven-project catalog; this does not search external websites. PR #59 has replaced the fixed seven-project list with a dynamic backend catalog and Android API loading. PR #60 adds RSS/Atom candidate intake and scheduled-script integration; candidates are not automatically promoted to verified projects. Actual feed configuration, source checks, and user project submission/deduplication remain pending. Public metrics count contribution events/interactions, not unique people, to avoid introducing account/device tracking into anonymous public contributions.
- Personal achievement: implemented in PR #47. Guest metrics read the guest-scoped local ledger; logged-in metrics read the authenticated private cloud ledger. Win rate uses only projects with confirmed ledger interactions.
- Task management now supports create, rename/edit, complete, and delete for guest-local and authenticated projects. PR #52 adds the edit flow and backend validation.
- Project management now exposes edit, status changes, and delete in the Android project list. PR #53 preserves confirmed ledger history when removing a project from the list. Ledger-derived project summaries remain read-only.
- PR #58 implements a separate persistent project-to-multiple-wallet assignment manager. Guest assignments are stored locally in Room under guest scope; logged-in assignments are stored in account-private backend records and cached locally under account scope. Project detail exposes multi-select controls, and the claim wallet picker prioritizes assigned addresses. Backend checks that private projects and all selected wallets belong to the authenticated user. This is complete in code; real-device verification of persistence, account switching, and claim-wallet ordering remains pending.
- PR #54 adds manual plus eight-hour background refresh of saved wallet transaction activity. Background snapshots are cached locally by guest/account scope and never auto-create ledger rows or public contributions.
- Sync is not yet app-wide: community contribution outbox has its own periodic worker; wallet activity has an eight-hour worker; PR #56 adds account-private ledger retry plus an eight-hour fallback. There is no unified refresh coordinator for every data source.
- The Airdrop Hub has a dynamic backend catalog (PR #59), RSS/Atom candidate intake (PR #60), public project submission flow (PR #61), and project-card URL display/open action (PR #62). RSS records remain unverified candidates; no feeds are enabled by default and production Cron/environment setup is not yet confirmed. Public submissions accept a project name and HTTPS URL (official or third-party listing); unique non-shortener links currently enter the catalog after syntactic URL checks, while known shorteners are held as candidates. This is not proof that a domain is trustworthy; abuse monitoring and stronger moderation remain required. No submitted website is crawled. Discovery remains free-first: only stable, permitted free APIs/feeds/listing interfaces; user submissions are also supported. Telegram/Discord are optional only when free and authorized; paid X/API and paid data sources are out of scope. Check source access, terms, rate limits, and failure isolation before enabling any feed.
- Real-device acceptance for browser/wallet bridge, Android transaction import, guest data clearing, and cross-device restore remains pending.

### Status discipline

Use only these states: **complete in code**, **partial**, **in progress**, **pending verification**, **deferred**, **cancelled**. A PR merge/CI pass is code-level evidence only; device and production checks remain separate.


### User-confirmed zero-cost discovery refinement (2026-11-09)

- Use only genuinely free, stable, and permitted discovery sources. No paid APIs, paid RSS services, or paid platform plans. If no usable free source exists, skip that source rather than spending money or building brittle workarounds.
- Prefer free public APIs and free RSS/Atom feeds from established airdrop platforms (for example, Airdrops.io if its feed is available), and use third-party airdrop listing pages as project URLs when useful. Capture only fields that are actually available; missing metadata is acceptable.
- The project directory's minimum card identity is display name + URL. Avatar/logo is optional and may use an available platform image or fallback. An official project website is NOT mandatory: a third-party airdrop platform listing URL may be the project's link.
- User submissions may provide a project name and any usable URL, including a third-party listing URL. Duplicate and URL-safety checks still apply.
- Telegram/Discord are optional, only if free and access is explicitly authorized/simple. X/Twitter is skipped if it requires paid API access or complex authorization.
- This supersedes any earlier assumption that every provider (Telegram, Discord, X, DropsTab) must be implemented. Source integration is conditional on free availability and practical reliability.


### Implemented discovery and submission slices

- PR #60 (merged): RSS/Atom candidate intake with HTTPS exact-host allowlisting, bounded parsing, source attribution, candidate API, and scheduled-script integration. No feeds are enabled by default; actual source/license checks and Render Cron/environment setup remain operational tasks.
- PR #61 (merged): public community project submission API and Android submission dialog. Required fields are project name + HTTPS URL; official websites are not mandatory, and third-party airdrop listing URLs are accepted. The backend normalizes URLs, checks name/URL duplicates against the catalog and RSS candidates, rejects unsafe URL forms, rate-limits submissions, and holds known URL shorteners as unverified candidates. It does not fetch or crawl submitted websites. CI passed; real-device verification and moderation/abuse monitoring remain pending.
- PR #62 (merged): show each catalog project's stored URL directly on its Android community card and provide an explicit open-link action. Missing URLs are labeled as awaiting supplementation; no URL is invented.
- PR #63 (merged): admin-token-protected candidate review queue, approve-to-catalog and reject actions, review timestamps/status, safe URL revalidation, and duplicate-to-existing-project linking. Android review UI remains deferred because the admin token must not be embedded in the public app.
- Project-list overall Gas/reward bar and project-detail waterfall layout above are confirmed requirements. PR #66 implements the project-card Gas-first red / actual-reward green bar, two-decimal overall USD values, derived reputation wording, and honest empty-data handling; the four-window static-newspaper detail page remains pending; the 'today' dimension is explicitly removed.
- Still pending: enable only verified free feeds after checking access and terms; production Cron/environment setup; optional free provider enrichment only when stable and authorized. Do not add paid or complex integrations.

### User-confirmed community static-newspaper snapshot mechanism (2026-11-09)

- **Only four public project-stat windows:** overall, rolling last 7 days, rolling last 3 days, and rolling last 1 hour. The "today" window is removed and must not appear in cards, details, APIs, or snapshot manifests.
- **Read-only UI:** community list and project detail have no pull-to-refresh, refresh button, swipe-triggered request, or other manual refresh affordance. Pull gestures only use normal elastic scroll/bounce behavior. The app reads cached snapshots and may perform a silent background network revalidation; users do not initiate it.
- **Server-side scheduled aggregation (Asia/Shanghai, UTC+8):**
  - Overall + last 7 days: once daily at 08:00.
  - Last 3 days: every 6 hours at 00:00, 06:00, 12:00, and 18:00.
  - Last 1 hour: every 5 minutes on the minute marks divisible by five (e.g. 10:05, 10:10, 10:15).
  - Persist UTC timestamps for ledger events; calculate exact rolling boundaries against a single UTC run timestamp, and expose the schedule/update timestamps in UTC+8. Each snapshot carries its own `update_time`.
- **Precomputed static distribution:** scheduled jobs aggregate all eligible confirmed public contribution/ledger facts server-side, then publish versioned JSON snapshots (overall, 7d, 3d, 1h) to static object storage/CDN (e.g. Cloudflare Pages/R2 or equivalent). App reads these static files; snapshot GET requests must not trigger SQL aggregation. Publish atomically: upload versioned payloads first, then replace a small manifest/index pointer only after all files are complete. Use ETag/Last-Modified and sensible CDN cache headers; never expose private ledger rows, wallet addresses, tx hashes, or user identifiers.
- **Android stale-while-revalidate cache:** Room stores the last successful JSON snapshot per scope/window (including project ID, payload/version, and update_time). On entering community list/detail, render Room immediately, then silently fetch the CDN manifest/snapshots in the background. If newer valid data arrives, upsert Room and update the UI without blocking or disruptive animation. If offline, CDN fails, or parsing/validation fails, keep displaying the last good cache without a blank screen or user-facing refresh error. First-ever offline use may show a clear no-cache placeholder.
- **Timestamp presentation:** under each non-overall comparison row show its own “更新于 …” timestamp. Overall may also show its generated time. The 1-hour row is a short-term activity signal, not a substitute for the existing rolling 24-hour interaction heat metric; retain 24-hour heat separately.
- **Data integrity:** snapshots aggregate only eligible anonymous public contributions and confirmed actual Gas/actual rewards; theoretical estimates, demo fixtures, private user details, and unconfirmed outcomes are excluded. Keep per-token amounts separate and label any USD conversion as an estimate/valuation. Missing data is shown as missing, not fabricated.
- **Operational boundary:** scheduled aggregation and CDN publication are backend/ops responsibilities. Do not add a public APK secret, do not calculate aggregate snapshots on app-open, and do not claim a production CDN/Cron is configured until the deployment is actually verified.
- **UI hierarchy:** concise project-list cards show identity, brief rating/reputation, and overall Gas-vs-actual-reward bar. Tapping opens a vertically scrolling detail page with the four windows above simultaneously visible; no tabs. Rating/reputation and rolling 24-hour heat remain distinct metrics.


### Static snapshot backend implementation progress (PR #68)

- The first backend slice adds aggregate-only snapshot generation for overall, rolling 7d, rolling 3d, and rolling 1h from the existing anonymous community_contributions table and active project catalog. Each window uses one UTC generation timestamp and a rolling lower boundary; update_time is emitted in Asia/Shanghai.
- Snapshot rows contain only project catalog metadata and aggregate amounts/counts, rating distribution, and a separate rolling 24-hour activity aggregate. No wallet address, transaction hash, user ID, or private ledger row is serialized.
- Current contribution schema stores USD-denominated aggregate values only. These are explicitly labeled as USD valuations; per-token totals are not fabricated and remain pending a future source-schema extension if token-level public contribution data is added.
- A five-minute dispatcher helper returns the 1-hour snapshot on each invocation, adds the 3-day snapshot at 00:00/06:00/12:00/18:00 UTC+8, and adds overall + 7-day at 08:00 UTC+8. Deployment must invoke the command every five minutes; this PR does not create a production scheduler or claim one is active.
- Versioned JSON objects are uploaded before the manifest pointer is changed. The S3-compatible publisher supports Cloudflare R2-style endpoints through environment variables; the local atomic publisher is for development/tests only. Production mode fails closed unless a public CDN base URL and object-storage credentials are configured.
- Android Room caching, static-manifest consumption, removal of existing manual refresh affordances, and production CDN/Cron configuration remain subsequent implementation/acceptance steps.


### Android static snapshot cache implementation (PR #69)

- Android reads the public overall snapshot from a shared Room cache before any network request. Public snapshot cache keys are deliberately not scoped to guest/account identity because the data is public and identical for all sessions.
- CDN manifest revalidation and versioned snapshot downloads use a dedicated unauthenticated OkHttp client; user Authorization tokens are never sent to the public CDN.
- The client accepts only HTTPS manifest URLs and snapshot object URLs on the configured CDN host. Each downloaded snapshot is schema/window checked and only replaces a cached copy when its generation timestamp is newer.
- Community project metadata and aggregate metrics on the square screen are derived from the overall static snapshot; per-project live stats API calls are removed from that screen.
- The CDN base URL is an optional build-time `COMMUNITY_SNAPSHOT_BASE_URL`. Until a public CDN URL and production storage/Cron are configured, the app keeps any existing Room copy and otherwise shows an unavailable state; no live database fallback is silently enabled.
- All four snapshot windows are supported by the repository API for the next detail-screen integration. No Room schema migration is required because public JSON uses the existing cached_payloads table.
