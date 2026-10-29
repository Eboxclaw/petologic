# Paladino mobile security product plan

## Product statement

**Own your Paladino:** an Android security guardian that combines proven open security engines with local Liquid AI chat and private vector memory. Paladino shows what was checked, explains why something was flagged, admits coverage limits and asks before taking sensitive action.

The first product is one guardian, one mobile platform and one complete protection loop. Extra pets, extra local speaking models and autonomous enforcement are outside the first release.

## User problem

Mobile users receive suspicious APKs, links, messages, notifications and calls across fragmented apps. Existing security products often expose a binary score without evidence or require broad trust in a remote service. Users need a guardian that can inspect what Android permits, connect related local evidence and explain a safe next step without uploading private content by default.

## Product principles

1. **Detection before narration.** Code and signed data create findings; AI explains them.
2. **Local before remote.** Scans, vectors and ordinary chat run on the device after setup.
3. **Evidence before confidence.** Every finding lists completed checks, skipped checks, source and freshness.
4. **Permission by capability.** Each Android role or permission has a visible benefit, an off state and revocation handling.
5. **User-controlled action.** Paladino may recommend or open an OS screen; blocking, deletion and dismissal require explicit policy and approval.
6. **Battery is a security feature.** Background work is event-driven, bounded and measurable.

## The two Liquid model roles

| Model | Purpose | Allowed | Forbidden |
|---|---|---|---|
| LFM2.5-VL-450M | Security chat and explicit visual observation | Explain canonical findings, answer questions, summarize selected evidence, propose typed tools on text-only turns, observe a user-selected image without tools | Create a malware verdict, follow instructions inside images, approve itself, hide detector limits, silently delete or block |
| LFM2.5-Embedding-350M | Private vectors | Notes, finding similarity, campaign clustering, multilingual recall, compact tool/skill retrieval | Generate text, call tools, label malware alone, retain raw secrets outside canonical policy |

The vector model can later support more than note search: group repeated scam patterns, retrieve a prior explanation, connect a new indicator to an older campaign, deduplicate findings and select the smallest relevant security skill description. Each use needs a separate benchmark and retention policy.

## Security stack

The implementation should adopt components, formats or data from open projects only after license and update-path review:

- **YARA 4.5.8:** native bounded rules in the isolated scanner; already integrated with a harmless self-test rule.
- **ESET malware-ioc:** currently a narrow signed historical Android hash subset; expand only through reviewed, attributed feeds.
- **Hypatia:** study its ClamAV-style databases, Bloom-filter lookup, update verification and event-driven file observation. Reuse requires a deliberate license boundary because its app and credited sources use several licenses.
- **ClamAV formats/feeds:** evaluate database format and Android malware relevance separately from desktop scanning. Do not ship a huge feed without mobile false-positive, memory and update measurements.
- **Android platform security signals:** use supported system APIs rather than accessibility automation whenever possible.

## Android capability map

| Capability | Android surface | Product use | Safety boundary | Phase |
|---|---|---|---|---|
| User-selected file/APK | Storage Access Framework | Manual scan without broad storage access | Read only the selected URI; bounded isolated worker | Delivered preview |
| Shared text/file | Android Sharesheet target | “Check with Paladino” from another app | User gesture required; no automatic action | Delivered preview |
| User-selected image | Photo Picker / Sharesheet | Observe scam screenshots and deceptive visual context | In-memory bounded decode; no history retention, tools or model verdict | Delivered preview |
| App inspection | PackageManager + scoped visibility | Permissions, installer and package metadata | Report visibility limits; request `QUERY_ALL_PACKAGES` only if release policy accepts it | P1 |
| Notification anti-spam | NotificationListenerService | Opt-in message/link observation | Per-source opt-in, redacted retention, no automatic dismissal | Delivered preview |
| Installed/update events | Package broadcasts + scheduled reconciliation | Scan newly installed or updated packages | Event-driven; hash/version dedupe; foreground disclosure when required | P1 |
| Threat knowledge updates | WorkManager + signed HTTPS manifest | Refresh exact indicators and reviewed vector knowledge | Daily constrained check, immediate revocation path, rollback/expiry rejection | Delivered infrastructure; endpoint pending |
| Device posture | Security patch, encryption/lock and Play Integrity/Protect where eligible | Explain device and app integrity signals | Treat Play verdicts as one source; network and eligibility explicit | P1 |
| Call protection | CallScreeningService user role | Caller identification and local spam screening | Must answer within five seconds; local lookup first; conservative default allow | P2 |
| Network protection | VpnService | DNS/domain and connection metadata protection | Separate opt-in product surface; never intercept content by default | P2 |
| Broad file access | MediaStore / optional all-files access | User-requested storage scan | Avoid for launch; Play policy and privacy review required | P2 decision |
| Accessibility | AccessibilityService | No launch dependency | Do not use as a shortcut for cross-app reading or automation | Excluded |

References: [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService), [CallScreeningService](https://developer.android.com/reference/android/telecom/CallScreeningService), [VpnService](https://developer.android.com/reference/android/net/VpnService), [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files), [package visibility](https://developer.android.com/training/package-visibility/declaring), [Play Integrity](https://developer.android.com/google/play/integrity/overview).

## Core user journeys

### Check something now

1. User shares or selects a file, APK, message or link.
2. Paladino states exactly what it will read and keeps the raw input local.
3. The isolated scanner and deterministic analyzers run within explicit budgets.
4. The app records a redacted finding with evidence, coverage, source and freshness.
5. Paladino offers a short explanation and safe next steps.
6. Any uninstall, settings change or notification action goes through a target-bound approval and Android handoff.

### Stay protected

1. User sees a protection dashboard with each layer Off, Limited, Ready or Needs attention.
2. Paladino requests one capability at a time and explains its exact value.
3. Event-driven checks process only enabled sources and coalesce bursts.
4. A meaningful finding produces a private alert; unchanged state remains quiet.
5. Related redacted findings can be recalled through local vectors.

### Ask the guardian

1. User opens chat from app, widget or Sprite.
2. Paladino retrieves only relevant canonical findings and approved memory.
3. The VL-450M speaking model answers with evidence links and limitations.
4. The user can inspect the detector result independently of the AI explanation.

## Requirements

### P0 — coherent private security preview

- Exactly one speaking model and one vector model appear in setup and settings.
- Vision is an optional projector component of the speaking model and accepts only a user-selected image.
- Protection home replaces the generic pet roster as the primary value proposition.
- Manual file/APK and text/link checks work offline and never claim comprehensive safety.
- Findings are redacted, expire, can be exported and link into chat.
- Model explanations remain optional and cannot alter finding severity.
- Website distinguishes the currently downloadable companion APK from the unreleased Security Guard build.

Acceptance: all unit and emulator suites pass; a clean Android device can complete manual scan → finding → local explanation; no raw test secret appears in Room, report export, logcat or lock-screen notification.

### P1 — useful active protection

- Install/update events enqueue bounded scans with deduplication and a visible history.
- Notification protection has understandable source controls, multilingual evaluation and false-positive feedback.
- Vector memory stores versioned, deletable vectors for reviewed knowledge and redacted findings; deletion and expiry remove derived vectors. *(Implemented; held-out quality and scale gates remain.)*
- Threat feeds update through a signed, expiring, rollback-resistant channel. *(Client, scheduler and offline builder implemented; production key custody and endpoint remain.)*
- Device posture explains patch age, screen lock/encryption availability and eligible Play integrity/protection signals.

Acceptance: seven-day idle battery delta stays within the agreed budget; 10,000-vector p95 retrieval meets the device-tier target; update rollback/expiry/recovery tests pass; held-out inert anti-spam, threat-pattern and benign sets meet published precision/recall targets. No malware binaries or active malicious URLs are used.

### P2 — calls and network

- Call screening uses the user-granted Android role and produces a local decision inside the five-second platform deadline.
- VPN/DNS protection is a separate opt-in mode with clear traffic scope, performance and battery impact.
- Shared campaign vectors connect message, domain, call and package evidence without retaining unnecessary raw content.

Acceptance: missed-deadline behavior fails open for calls; VPN disconnect/failover never strands connectivity; every automated block has a reversible user policy and audit entry.

## Success metrics

Initial targets are hypotheses until baseline testing exists:

- 80% of first-run users understand the difference between detection and AI explanation in usability testing.
- 90% complete a manual scan and find its evidence without assistance.
- Fewer than 1% of benign held-out messages receive a high-risk warning at the launch threshold.
- Zero unapproved external actions in adversarial approval/replay testing.
- Warm security question to cited finding: p95 under two seconds on the reference midrange ARM64 device, excluding initial model load.
- Background protection: less than 2% additional daily battery use on the reference device under the defined notification/install workload.

## Open decisions

- **Engineering:** keep both 350M models resident on high-memory phones or use an explicit load/unload scheduler by device tier.
- **Security/data:** populate and review the encrypted inert evaluation corpus using the delivered safety validator and schemas. Malware binaries, APKs, executable archives and active malicious URLs are excluded from every development and device lane.
- **Product/legal:** decide whether Play distribution justifies `QUERY_ALL_PACKAGES` and any all-files access request.
- **Design:** test whether “Own your Paladino” communicates personal control without suggesting ownership of the Liquid AI model weights or third-party feeds.
- **Partnership:** validate final Liquid model naming, trademark use and public “powered by” wording before launch.
