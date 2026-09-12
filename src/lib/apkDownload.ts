/* Public binary distribution; source code remains in the private application repository. */
export const APK_RELEASE =
  "https://github.com/Eboxclaw/petologic-downloads/releases/download/v0.2.2-preview";
export const APK_URL = `${APK_RELEASE}/petologic-0.2.2-preview-arm64.apk`;
/**
 * GitHub release assets send no CORS headers, so the in-page streaming downloader cannot fetch
 * them cross-origin. The same file is mirrored in the repository (raw.githubusercontent.com sends
 * `access-control-allow-origin: *` and honors Range), which is what the accelerated download uses.
 */
export const APK_STREAM_URL =
  "https://raw.githubusercontent.com/Eboxclaw/petologic-downloads/main/apk/v0.2.2-preview/petologic-0.2.2-preview-arm64.apk";

export type DownloadProgress = {
  received: number;
  total: number | null;
  resuming: boolean;
};

export type DownloadOptions = {
  onProgress?: (progress: DownloadProgress) => void;
  idleTimeoutMs?: number;
  maxAttempts?: number;
  signal?: AbortSignal;
};

/** Thrown when the accelerated download gives up; `fallbackToBrowser` means the plain link is the right next step. */
export class ApkDownloadError extends Error {
  readonly fallbackToBrowser: boolean;
  constructor(message: string, fallbackToBrowser: boolean) {
    super(message);
    this.name = "ApkDownloadError";
    this.fallbackToBrowser = fallbackToBrowser;
  }
}

const DEFAULT_IDLE_TIMEOUT_MS = 20_000;
const DEFAULT_MAX_ATTEMPTS = 4;
const RETRY_BACKOFF_MS = 1_200;

// Safari on iOS mis-saves blob: URLs and APKs are Android-only anyway; keep the plain flow there.
function isIos(): boolean {
  return (
    /iP(hone|ad|od)/.test(navigator.userAgent) ||
    (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1)
  );
}

export function canStreamDownload(): boolean {
  return (
    typeof window !== "undefined" &&
    typeof window.fetch === "function" &&
    typeof window.ReadableStream !== "undefined" &&
    typeof window.Blob === "function" &&
    !isIos()
  );
}

export function filenameFromUrl(url: string): string {
  try {
    const last = new URL(url).pathname.split("/").pop() ?? "";
    return last || "download.bin";
  } catch {
    return "download.bin";
  }
}

export function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  // Revoke late: Chrome keeps streaming the blob into its download manager for a while.
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

type PumpResult = { failed: boolean };

/** Stream the response body, aborting when no bytes arrive within `idleTimeoutMs` (the stall watchdog). */
async function pump(
  response: Response,
  controller: AbortController,
  onChunk: (chunk: Uint8Array) => void,
  idleTimeoutMs: number,
): Promise<PumpResult> {
  const reader = response.body?.getReader();
  if (!reader) throw new ApkDownloadError("This browser cannot stream downloads", true);
  let failed = false;
  let timer: number | undefined;
  const arm = () => {
    if (timer !== undefined) window.clearTimeout(timer);
    timer = window.setTimeout(() => {
      failed = true;
      controller.abort();
    }, idleTimeoutMs);
  };
  arm();
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      arm();
      if (value && value.length > 0) onChunk(value);
    }
  } catch (error) {
    if (!failed) throw error; // genuine network error, not the watchdog — still resumable by the caller
    failed = true;
  } finally {
    if (timer !== undefined) window.clearTimeout(timer);
  }
  return { failed };
}

/**
 * Streams the file with stall detection and Range resume — the browser twin of the app's ModelDownloader:
 * if bytes stop arriving, abort and re-fetch from the last received offset instead of restarting.
 * Resolves with the complete Blob; on CORS/fetch failure it throws `fallbackToBrowser` so the caller
 * can hand off to the browser's own download manager (the previous behavior).
 */
export async function downloadWithResume(
  url: string,
  options: DownloadOptions = {},
): Promise<Blob> {
  const idleTimeoutMs = options.idleTimeoutMs ?? DEFAULT_IDLE_TIMEOUT_MS;
  const maxAttempts = options.maxAttempts ?? DEFAULT_MAX_ATTEMPTS;
  const chunks: Uint8Array[] = [];
  let received = 0;
  let total: number | null = null;

  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    const resuming = received > 0;
    const controller = new AbortController();
    const onOuterAbort = () => controller.abort();
    options.signal?.addEventListener("abort", onOuterAbort, { once: true });
    try {
      let response: Response;
      try {
        response = await fetch(url, {
          signal: controller.signal,
          redirect: "follow",
          headers: resuming ? { Range: `bytes=${received}-` } : undefined,
        });
      } catch (error) {
        if (options.signal?.aborted) throw error;
        // No response at all (CORS, offline, blocked): only the plain link can help.
        throw new ApkDownloadError(`Fetch failed before response: ${String(error)}`, true);
      }
      if (!response.ok) {
        if (response.status === 416 && total !== null && received >= total) break; // range refused but we already have everything
        throw new ApkDownloadError(`Unexpected HTTP ${response.status}`, true);
      }
      const lengthHeader = response.headers.get("content-length");
      const length = lengthHeader ? Number.parseInt(lengthHeader, 10) : Number.NaN;
      if (response.status === 206) {
        if (total === null && Number.isFinite(length)) total = received + length;
      } else {
        if (Number.isFinite(length)) total = length;
        if (resuming) {
          // Server ignored the Range header and resent the whole file: restart the buffer.
          chunks.length = 0;
          received = 0;
        }
      }
      const result = await pump(
        response,
        controller,
        (chunk) => {
          chunks.push(chunk);
          received += chunk.length;
          options.onProgress?.({ received, total, resuming });
        },
        idleTimeoutMs,
      );
      if (!result.failed && (total === null || received >= total)) {
        return new Blob(chunks as BlobPart[]);
      }
      // Watchdog stall or premature end: fall through to the resume attempt.
    } finally {
      options.signal?.removeEventListener("abort", onOuterAbort);
    }
    if (attempt < maxAttempts) await sleep(RETRY_BACKOFF_MS * attempt);
  }
  throw new ApkDownloadError(`Download kept stalling after ${maxAttempts} attempts`, true);
}
