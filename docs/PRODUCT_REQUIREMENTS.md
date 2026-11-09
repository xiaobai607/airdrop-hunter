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
- Public project directory and internal project search remain active; this is distinct from the cancelled public website-discovery search.

## 7. Micro Community

- Public project directory/cards, categories, tags, and project details.
- Public aggregate Gas, actual rewards, participation count, and net P&L presentation.
- Rankings: 吸金榜、避坑榜、新星榜.
- Categories/tags may include L2, zero-cost, testnet-required, high-Gas, confirmed airdrop, and similar classifications.
- Favorites/following and personal to-do tracking.
- Personal achievement card: project count, Gas, rewards, and win rate.
- Global participants and 24-hour interaction heat where supported by reliable data.
- Ratings are 1–5 stars only; only actual participants with qualifying confirmed accounting may rate. No text reviews/comments.
- Abuse prevention, event idempotency, and validation of eligible participation.
- Public views must only use aggregate contribution data and must never expose personal addresses or transaction-level records.

## 8. Search and market

- Existing global search and public market data for BTC, ETH, SOL, BNB, and XRP.
- Tapping a coin opens its market detail page, not a search-field autofill.
- Search failures must show a recoverable error/retry state and must not crash the app.
- Public automatic discovery/search of airdrop websites is cancelled for now as unreliable.
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
- Automatic public discovery/search of airdrop websites.
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

### Current audit findings

- Confirmed in main source: guest confirmed bills are written to a guest-scoped local personal-ledger cache and guest-scoped local P&L/project/interaction tables.
- Confirmed in main source: guest community outbox entries contain only project ID, aggregate Gas, aggregate actual reward, interaction count, and sync metadata; the sync request does not send wallet address, transaction hash, or personal ledger fields.
- Confirmed in main source: guest outbox batches use no Authorization header; logged-in batches use the user's bearer token.
- Confirmed in main source: guest wallet addresses are saved through the app-local cache; logged-in wallet addresses are loaded from the authenticated wallet API and cached under an account-specific key. Room project, P&L, interaction, pending-bill, and outbox records are scoped by token user ID or guest scope.
- Confirmed in main source: private personal-ledger endpoint requires authentication and scopes reads/deletes/upserts to the authenticated user. Android AccountingRepository.syncPersonalLedger() returns before any private cloud call when no token exists.
- PR #46 and PR #47 are merged: new public contribution rows store user_id as null; migration 0005 clears historical account links while preserving aggregate totals.
- Source audit confirms guest wallet addresses are stored in the guest-scoped app cache and authenticated wallet lists are fetched from the private wallet API and cached by account scope. Device-level app-data clearing and account-switch behavior still require real-device acceptance; production database backup/restore also remains unverified.
- Community implementation: PR #48 adds guest/account-scoped local favorites; cross-device favorite sync remains pending. PR #49 adds rolling 24-hour confirmed interaction heat and uses it for the new-star ranking. PR #50 adds global 1–5 star aggregates for logged-in accounts after server verification against confirmed private-ledger participation; guest ratings remain local-only. PR #51 adds in-app search over the existing seven-project catalog; this does not search external websites. The catalog is still a fixed seven-project list; dynamic catalog management remains pending. Public metrics count contribution events/interactions, not unique people, to avoid introducing account/device tracking into anonymous public contributions.
- Personal achievement: implemented in PR #47. Guest metrics read the guest-scoped local ledger; logged-in metrics read the authenticated private cloud ledger. Win rate uses only projects with confirmed ledger interactions.
- Task management now supports create, rename/edit, complete, and delete for guest-local and authenticated projects. PR #52 adds the edit flow and backend validation. Reordering tasks and richer task scheduling/reminders are not yet implemented unless separately requested.
- Real-device acceptance for browser/wallet bridge, Android transaction import, guest data clearing, and cross-device restore remains pending.

### Status discipline

Use only these states: **complete in code**, **partial**, **in progress**, **pending verification**, **deferred**, **cancelled**. A PR merge/CI pass is code-level evidence only; device and production checks remain separate.
