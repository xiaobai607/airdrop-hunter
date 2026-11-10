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

## Document map and current implementation baseline (2026-11-10)

This document remains the complete source of truth. The optimization below adds navigation and status guidance; it does **not** delete, weaken, or silently reinterpret any previously confirmed feature. The latest account-scope correction explicitly defers new registration and user-facing cross-device private-ledger restore; see Section 1.

- **Product boundaries and privacy:** Sections 1–4 and 10–11.
- **Personal accounting and project lifecycle:** Sections 5–6, including the new quick-entry and project-library requirements.
- **Community catalog, metrics, discovery, and search:** Sections 7–8.
- **Refresh/cache/reliability:** Section 9.
- **Implementation evidence and 15-batch roadmap:** Sections 12–14.
- **Naming rule:** the user-facing personal-project destination is named **“库管理”**. “空投库” is the public project-discovery/catalog destination. This is a display/navigation rename only; it does not remove or alter project management, tasks, wallet associations, ledger history, or any other existing capability.
- **Code status at this baseline:** Batches 01–13 have merged code-level implementations with their recorded CI evidence. Batch 14 is partial in code and awaits production configuration, live smoke checks, backup/restore drill, and rollback evidence. Batch 15 implementation and exact-head CI are complete after PR #132; real-device acceptance remains pending. Do not conflate code/CI completion with production or device acceptance.

## 1. Product and engineering boundaries

### Current-release account and recovery scope (user-confirmed, 2026-11-10)

- Do not allow new users to register in the current release. Hide or disable registration entry points; if a user reaches one, show **“注册功能开发中，请等待后续更新”** and do not start an account-creation request.
- Keep guest mode fully available for the app's core functions. Guest personal ledger, wallets, and project data remain app-private/local; clearing app data or uninstalling loses those details.
- Defer user-facing cross-device private-cloud restore. Do not advertise cloud recovery, show a restore entry point, or make cross-device recovery a release acceptance condition.
- Do not delete already-implemented authentication, account-isolation, or sync code merely because registration/restore is deferred. Preserve it for a later phase; any existing account flow must not be expanded or described as offering cross-device restore.
- This deferral applies to cloud-based private user data recovery only. Offline encrypted file backup/import is separately approved below. It does not cancel the separate PostgreSQL backup/restore drill for production operations, nor anonymous public aggregate contribution.


- Product: Airdrop Hunter, an Android application for airdrop projects, task tracking, wallet-address management, Gas/reward accounting, and community project statistics.
- Stack: Android Kotlin + Jetpack Compose, FastAPI, PostgreSQL/Neon, Render, and GitHub Actions APK builds.
- Keep the project affordable for a solo developer. Prefer free tiers, cache responsibly, batch coherent changes, and avoid unnecessary CI runs.
- Guest use is the primary experience. **New-user registration is disabled for the current release**; if a registration entry is reached, show exactly: “注册功能开发中，请等待后续更新”. Do not submit registration requests or imply that account creation is available. Keep the existing guest experience fully usable. Existing authentication/backend code is retained, but registration and cross-device cloud recovery are deferred.
- No custody, seed phrase/private-key collection, signing, transfers, swaps, or approvals.
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

| Data layer | Guest | Logged-in user |
|---|---|---|
| Detailed personal ledger (wallet addresses, Gas details, rewards, project participation, transaction references, timestamps) | Stored in app-private local storage only | Stored locally and uploaded to the user's private cloud ledger |
| Private cloud restore | Not available; clearing app data or uninstalling loses guest details | Cross-device restore is deferred and must not be offered or promised in the current release |
| Portable local backup | User can export an encrypted backup file and import it on another device; file/password remain under the user's control | Same, scoped to the active session; no credentials are exported |
| Public community contribution | Valid confirmed Gas, actual rewards, and project participation may contribute | Same |
| Public contribution storage | Anonymous aggregate only; no wallet address, tx hash, personal ledger row, or account identity | Same; must not be linked to the user's account |
| Clearing/uninstalling app data | Local private data is lost; no server copy exists | Do not promise cross-device recovery in the current release; explain that this capability is deferred |

Additional rules:
- Guest personal details are saved in the app-private local database only. They are not uploaded to a private cloud vault. Clearing app data or uninstalling the app loses guest personal details.
- Public aggregate data is separate from the private ledger. Never expose individual bills, addresses, hashes, or account identifiers in public project statistics.
- Only user-confirmed actual Gas/reward values are eligible for public totals. Estimates and mock/demo records are excluded.
- Contributions must be idempotent and retry-safe.
- Logged-in private ledger endpoints require authentication and must scope every read, update, and delete to the authenticated account.
- Switching accounts must not expose another account's local private cache.
- The accounting dialog includes an unchecked-by-default opt-in checkbox: “将本笔数据匿名贡献至全网大盘”. Unchecked means the entry remains private/local (or follows the existing private-ledger sync policy for authenticated users) and creates no public contribution.
- When the user opts in, a transaction hash (Tx Hash) is mandatory. The user must select the chain/network and the wallet address already associated with the accounting row; validate hash syntax locally, but treat the backend as authoritative.
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
- A verified transaction fee verifies Gas only. It does not verify reward/token income, reward valuation, project participation, or the user's realized profit. Those values retain their own provenance and only user-confirmed actual values are eligible under the existing public contribution rules.
- Provider/API timeout, rate limit, outage, or incomplete response means **pending verification with retry**, not a false rejection and not an automatic downgrade to a falsely verified amount. Invalid hash, wrong payer, insufficient finality, unsupported attribution, or replay conflict is a terminal public-contribution rejection; the private ledger remains intact.
- Dashboard visualization must distinguish provenance: Tier 1 user-reported values use lighter/semitransparent bars; Tier 2 transaction-linked values use a separate explicit “链上已确认” state and must not be visually conflated with Tier 3; Tier 3 receipt/fee-verified values use solid/high-confidence bars. Show labels and totals by provenance so unverified and verified amounts are never silently blended as if equally trustworthy.
- Keep the existing anonymous outbox/idempotency path; do not create a second contribution pipeline. Preserve global replay protection and privacy: raw Tx Hash/address must not appear in public tables, snapshots, public APIs, analytics, or ordinary logs; persist only server-keyed fingerprints plus minimal verification metadata.
- This verification flow never auto-completes an event or settlement. Actual final results remain pending until the user manually enters and confirms the realized amount under Section 5.
- Only after successful chain verification and replay checks may the opted-in anonymous aggregate enter the public contribution pipeline. Invalid/missing hash, unsupported chain attribution, failed/unconfirmed transaction, or replay conflict must not create a public contribution; the user private ledger entry remains intact and can be corrected/retried.
- The checkbox is per accounting entry/batch row. For multi-wallet batch entry, each wallet row must have its own matching transaction hash; never reuse one hash across several selected addresses. Preserve atomic private-ledger batch behavior, but public verification failures must not erase or roll back private accounting data.

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
- Preserve guest-local private ledger behavior and existing account isolation. Guest private rows never upload to a private vault. Cross-device private-ledger restore is deferred for the current release; do not expose or promise it. Retain existing authentication/cloud code without expanding this feature until registration and recovery are deliberately resumed. Only eligible confirmed anonymous aggregates may enter the public contribution outbox; no address, transaction hash, account identity, or private ledger row may be sent.
- For USD totals and net P&L, unknown/unissued/points rewards must be represented as incomplete rather than treated as "$0" realized income. Preserve the native token/points detail even when USD valuation is unknown.

## 5.2 Explicit opt-in and transaction-linked public contribution verification (new user-confirmed requirements, 2026-11-10)

- The accounting dialog includes an unchecked-by-default opt-in checkbox: “将本笔数据匿名贡献至全网大盘”. Unchecked means the entry remains private/local (or follows the existing private-ledger sync policy for authenticated users) and creates no public contribution.
- When the user opts in, a transaction hash (Tx Hash) is mandatory. The user must select the chain/network and the wallet address already associated with the accounting row; validate hash syntax locally, but treat the backend as authoritative.
- The backend verifies on the correct chain that the transaction exists and is sufficiently confirmed/finalized, and that its chain-specific fee-payer/sender identity matches the selected address. Use deterministic chain adapters (for example EVM transaction from-address, TRON owner address, and Solana fee payer). For chains/transactions where the fee payer cannot be attributed unambiguously (including ambiguous multi-input Bitcoin cases), reject public contribution and keep the private accounting entry.
- Enforce global replay protection using a unique key for the normalized chain/network + transaction hash across all users and projects. A transaction hash already used by any contribution cannot be submitted again. Retries of the same idempotent contribution are accepted without double counting; a different payload reusing the same hash is rejected.
- Keep the raw transaction hash and selected wallet address out of public aggregate tables, public snapshots, public APIs, analytics, and ordinary logs. The verifier may use them transiently to query the chain; persist only a server-keyed HMAC/fingerprint of the normalized chain+hash for global replay protection, plus minimal verification metadata. Do not attach the contribution to a user account.
- Verification authority: use the three-tier provenance, half-automated receipt parsing, historical-price valuation, chain-specific fee completeness, ±20% tolerance, pending/retry behavior, mismatch handling, and privacy rules defined in Section 2. This section only defines when the accounting UI may enqueue an opted-in public contribution; do not retain the superseded hash-link-only/no-receipt-parsing rule.
- Only after successful chain verification and replay checks may the opted-in anonymous aggregate enter the public contribution pipeline. Invalid/missing hash, unsupported chain attribution, failed/unconfirmed transaction, or replay conflict must not create a public contribution; the user private ledger entry remains intact and can be corrected/retried.
- The checkbox is per accounting entry/batch row. For multi-wallet batch entry, each wallet row must have its own matching transaction hash; never reuse one hash across several selected addresses. Preserve atomic private-ledger batch behavior, but public verification failures must not erase or roll back private accounting data.

- Keep theoretical estimates, user-entered actual amounts, and transaction-verification status as separate fields. A valid transaction link must not automatically confirm an event or replace the user's existing manual final-result confirmation.
- The public dashboard may aggregate only eligible values and must disclose that Gas/reward amounts are user-declared unless a future approved receipt parser verifies them. Do not infer a Gas amount from transaction existence alone.

## 6. Projects and tasks

- Project directory, project details, statuses, personal project tracking, and history.
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
3. **Private sync layer:** existing authenticated API and account-isolation code is retained; guests do not sync private ledger details to any cloud endpoint. New-user registration and cross-device restore are deferred from the current release and must not be surfaced as available.
4. **Public contribution layer:** both guest and authenticated sessions may send only project ID + confirmed aggregate Gas/actual reward + an opaque event ID for idempotency. The server stores public contribution rows with no account identity; idempotency prevents duplicate retries.
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

- Existing main code already provides compact project cards, project-detail waterfall layout with four windows, exact Gas/reward comparison bars, rating eligibility/reputation labels, rankings, favorites, and catalog adoption links. Reuse these features; do not rebuild them.
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
