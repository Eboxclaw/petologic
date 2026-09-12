import { useCallback, useEffect, useRef, useState } from "react";

import {
  APK_STREAM_URL,
  APK_URL,
  ApkDownloadError,
  canStreamDownload,
  downloadWithResume,
  filenameFromUrl,
  saveBlob,
} from "../lib/apkDownload";

const APK_FILENAME = filenameFromUrl(APK_STREAM_URL);
const mb = (bytes: number) => `${(bytes / (1024 * 1024)).toFixed(1)} MB`;

type Phase = "idle" | "downloading" | "saved" | "fallback";

const BUTTON_CLASSES =
  "pixel-border-gold group bg-royal px-6 py-4 text-center font-display text-xs tracking-wide transition-all hover:bg-royal/90 active:translate-y-1 sm:px-8 sm:text-sm";

/**
 * DOWNLOAD APK button. On capable browsers it streams the APK in-page with stall detection and
 * Range resume (the website twin of the app's ModelDownloader); otherwise it falls back to the
 * plain direct link, which is also the automatic handoff if the accelerated download fails.
 */
export function ApkDownloadButton() {
  const [canStream, setCanStream] = useState(false);
  const [phase, setPhase] = useState<Phase>("idle");
  const [received, setReceived] = useState(0);
  const [total, setTotal] = useState<number | null>(null);
  const [resuming, setResuming] = useState(false);
  const busy = useRef(false);
  const lastRender = useRef(0);

  // Decided after hydration so SSR and the first client render agree (both plain link).
  useEffect(() => setCanStream(canStreamDownload()), []);

  const start = useCallback(async () => {
    if (busy.current) return;
    busy.current = true;
    setPhase("downloading");
    setReceived(0);
    setTotal(null);
    setResuming(false);
    try {
      // The raw mirror is CORS-open; the release URL is not. Fallback handoff keeps using the
      // release URL: the browser's own download manager — exactly what the old plain link did.
      const blob = await downloadWithResume(APK_STREAM_URL, {
        onProgress: (progress) => {
          // Throttle re-renders; chunk callbacks fire far faster than the eye needs.
          const now = Date.now();
          if (
            now - lastRender.current > 250 ||
            (progress.total !== null && progress.received >= progress.total)
          ) {
            lastRender.current = now;
            setReceived(progress.received);
            setTotal(progress.total);
            setResuming(progress.resuming);
          }
        },
      });
      saveBlob(blob, APK_FILENAME);
      setPhase("saved");
    } catch (error) {
      // No response, CORS, or stalls exhausted: hand off to the browser's own download
      // manager — exactly what the old plain link did, with a fresh signed URL.
      window.location.href = APK_URL;
      setPhase("fallback");
      if (!(error instanceof ApkDownloadError)) console.error("APK download failed", error);
    } finally {
      busy.current = false;
    }
  }, []);

  if (!canStream) {
    return (
      <a href={APK_URL} rel="noopener" className={BUTTON_CLASSES}>
        DOWNLOAD APK{" "}
        <span className="ml-2 inline-block transition-transform group-hover:translate-x-1">
          &rarr;
        </span>
      </a>
    );
  }

  const percent = total !== null ? Math.min(100, Math.round((received / total) * 100)) : null;

  return (
    <div>
      <button
        type="button"
        onClick={start}
        disabled={phase === "downloading"}
        aria-busy={phase === "downloading"}
        data-testid="apk-download"
        className={`${BUTTON_CLASSES} w-full disabled:opacity-80 sm:w-auto`}
      >
        {phase === "downloading" ? (
          <>DOWNLOADING{percent !== null ? ` ${percent}%` : "…"}</>
        ) : phase === "saved" ? (
          <>DOWNLOAD AGAIN</>
        ) : (
          <>
            DOWNLOAD APK{" "}
            <span className="ml-2 inline-block transition-transform group-hover:translate-x-1">
              &rarr;
            </span>
          </>
        )}
      </button>

      {phase === "downloading" && (
        <div className="mt-3 w-full max-w-xs" aria-live="polite" data-testid="apk-progress">
          <div className="h-2 w-full border border-royal bg-card">
            <div className="h-full bg-gold" style={{ width: `${percent ?? 0}%` }} />
          </div>
          <p className="mt-1 font-mono text-[10px] uppercase tracking-[0.15em] text-white/50">
            {mb(received)}
            {total !== null ? ` / ${mb(total)}` : ""}
            {resuming ? " · resuming from where it stopped" : ""}
          </p>
        </div>
      )}

      {phase === "saved" && (
        <p
          className="mt-3 font-mono text-[10px] uppercase tracking-[0.15em] text-cyan"
          aria-live="polite"
          data-testid="apk-saved"
        >
          ✓ {APK_FILENAME} saved — open it from the download notification or your Downloads folder.
        </p>
      )}

      {phase === "fallback" && (
        <p
          className="mt-3 font-mono text-[10px] uppercase tracking-[0.15em] text-white/50"
          aria-live="polite"
          data-testid="apk-fallback"
        >
          Browser download started. Stuck near the end? Press DOWNLOAD AGAIN — every attempt gets a
          fresh link.
        </p>
      )}
    </div>
  );
}
