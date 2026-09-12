# Download "stuck at 99%" fix — evidence (2026-09-12)

## Root causes found in ModelLibrary.install (pre-fix)

1. **No auto-recovery from CDN stalls:** a 60 s read-timeout fired once and failed the whole install mid-transfer; large-CDN tail stalls (HF/GitHub signed-URL flakiness, documented since 2026-09-10) made this routine — the bar froze near 99% until a manual retry.
2. **A complete-but-unverified partial re-downloaded from zero:** if the app died during the final hash check, the leftover file was exactly model-size, so the resume offset computed to 0 and the whole multi-hundred-MB file restarted.
3. Single failure surfaced as an error even when bytes had already been received.

## Fix

`ModelDownloader` (new): 20 s idle read-timeout, up to 4 attempts with `Range: bytes=<len>-` resume and short backoff; status reports "Connection dropped near NN% — continuing from there"; servers that ignore Range are handled (truncate + restart); `install()` now **pre-verifies a size-complete leftover partial** — valid files install with zero network, corrupt ones are deleted. Verification status text kept from the earlier fix. Never fails while bytes still made progress — the surfaced error tells the user a retry continues, not restarts.

## Tests

- 6 new MockWebServer JVM tests in `ModelDownloadTest`: full-body completion, Range resume (header asserted), server ignoring Range (truncate+restart), mid-body disconnect auto-resume (status says "continuing"), exhausted attempts fail with a continue-not-restart message, and a size-complete partial never touches the network. Full JVM suite: 87 tests green.
- Live release-build test (screenshot `resume-after-kill.png`): started the 1 593 MB LFM2.5-2.6B QAD download from Hugging Face, force-stopped the app 20 s in, relaunched and tapped download again — the bar resumed at the partial offset (~15%) instead of restarting. The 230M (149 MB) had already completed end-to-end (download → hash verify → Ready → re-verified after restart) on the same build.
