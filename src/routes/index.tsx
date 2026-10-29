import { createFileRoute } from "@tanstack/react-router";
import { useEffect, type ReactNode } from "react";

import paladinIdle from "../assets/pets/paladino_idle_transparent.webp";
import { ApkDownloadButton } from "../components/ApkDownload";
import { APK_RELEASE } from "../lib/apkDownload";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "Paladino: Free Local AI Antivirus and Companion for Android" },
      {
        name: "description",
        content:
          "Paladino is a free local AI antivirus and companion for Android. Check suspicious content, understand the evidence and ask your private Liquid AI for help.",
      },
      {
        property: "og:title",
        content: "Paladino: Free Local AI Antivirus and Companion for Android",
      },
      {
        property: "og:description",
        content:
          "One Android app combining local antivirus checks, Liquid AI, private vector memory, chat, Sprite and widget.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: Index,
});

const RELEASE = {
  version: "0.0.1",
  android: "Android 12+",
  architecture: "ARM64",
  speaker: "219 MB",
  vision: "103 MB",
  vectors: "229 MB",
  speakerLfm3: "1.0 GB",
  decisions: "1.1 GB",
};

const CAPABILITIES = [
  {
    state: "AVAILABLE IN THE CURRENT PREVIEW",
    tone: "cyan",
    title: "AI companion + early Security Guard",
    body: "The downloadable preview keeps private local chat, Sprite, widget, notes and user-controlled tools together with the existing Security Guard, device status and notification triage.",
  },
  {
    state: "NEW IN 0.0.1 · OPT-IN",
    tone: "gold",
    title: "Active installed-app checks",
    body: "This release adds the Protection dashboard and, when you enable installed-app protection, checks new and updated apps and reconciles visible installed apps daily. It reads Android metadata and APK bytes without running their code.",
  },
  {
    state: "NOT AVAILABLE YET",
    tone: "royal",
    title: "Broader active protection",
    body: "Comprehensive malware rules, automatic public threat updates, call screening, VPN protection and automatic removal are not available yet.",
  },
] as const;

const CHECKS = [
  [
    "Messages + links",
    "Flags pressure, credential requests, unusual payments and suspicious URL structure.",
  ],
  [
    "Files + APKs",
    "Checks bounded files against signed hashes and the isolated native scanning pipeline.",
  ],
  [
    "Notifications",
    "Observes only the apps you choose. It records heuristic findings and never dismisses a message.",
  ],
  [
    "Screenshots",
    "The optional vision pack describes possible visual scam cues in one image you select.",
  ],
  [
    "Installed apps",
    "Checks app metadata, signing details and installed APK hashes after installs and updates, without running the app.",
  ],
] as const;

const DEFENSES = [
  {
    status: "VALIDATION BUILD",
    title: "Antivirus file checks",
    body: "Paladino calculates SHA-256 fingerprints for selected files, APKs and first-layer archive entries, then compares them with signed exact indicators. Scanning runs with strict size and time limits in an isolated Android process.",
    detail:
      "Native YARA 4.5.8 is connected, but the bundled rule is currently a harmless pipeline test rather than broad malware-family coverage.",
  },
  {
    status: "VALIDATION BUILD",
    title: "Anti-malware app review",
    body: "When you enable installed-app protection, Paladino checks visible apps after installs and updates, then reconciles them daily. It reviews permissions, installer source, signing metadata, target Android version and powerful services, and hashes base and split APKs without executing them.",
    detail:
      "These signals recommend review; they do not prove that an app is malicious or uninstall it automatically.",
  },
  {
    status: "CLIENT READY · PRIVATE SERVICE PENDING",
    title: "Private hash reputation",
    body: "The optional reputation client sends only an 8-character SHA-256 prefix. A signed response returns candidate suffixes, and the full hash is matched locally on the phone.",
    detail:
      "Full hashes, package names, filenames, signing details and Liquid AI output are never included in a reputation request. The production service is not deployed yet.",
  },
  {
    status: "AVAILABLE + EXPANDING",
    title: "Anti-scam and spam checks",
    body: "Local message and link checks look for urgency, credential requests, payment pressure, URL shorteners, mixed writing systems and parser ambiguity. Notification observation is optional and limited to apps you choose.",
    detail:
      "Paladino records redacted evidence and never dismisses or blocks a notification automatically.",
  },
  {
    status: "CLIENT READY · ENDPOINT PENDING",
    title: "Signed threat knowledge",
    body: "Threat packs use Ed25519 signatures, SHA-256 artifact verification, expiry, rollback protection and revocation. This prevents an old, altered or untrusted pack from silently becoming active.",
    detail: "The production update endpoint and signing custody still need to be provisioned.",
  },
  {
    status: "OPTIONAL LOCAL MODEL",
    title: "Private vector memory",
    body: "LFM2.5-Embedding-350M turns reviewed security knowledge and redacted findings into vectors. Similarity search retrieves related context in English and Portuguese without uploading the original check.",
    detail:
      "A similar record can help an explanation, but similarity alone can never create or change a verdict.",
  },
  {
    status: "OPTIONAL LOCAL MODEL",
    title: "Security chat and vision",
    body: "LFM2.5-VL-450M explains detector evidence and can inspect one screenshot you explicitly choose. It keeps the companion conversational while the security engine remains responsible for findings.",
    detail:
      "AI explanations cannot approve actions, lower severity or claim that an incomplete check is safe.",
  },
] as const;

const LIMITS = [
  "No result can guarantee that a file, app or device is safe.",
  "The bundled YARA coverage is currently a harmless pipeline test, not broad malware-family coverage.",
  "The included ESET Android indicator subset is historical and intentionally limited.",
  "A production threat-update endpoint is not configured in the public companion preview.",
  "The private hash-reputation service is implemented in the validation build but is not deployed yet.",
  "Paladino does not automatically remove apps, block messages, screen calls or provide VPN protection yet.",
] as const;

const TECHNOLOGY = [
  {
    label: "DETECT",
    title: "Deterministic security core",
    body: "Signed SHA-256 indicators, bounded heuristics, Android signals and an isolated native rule engine produce findings. These checks own the verdict.",
  },
  {
    label: "CONNECT",
    title: "LFM2.5-Embedding-350M",
    body: "Creates private vectors for multilingual retrieval across reviewed knowledge and redacted local findings. Similarity supplies context; it never creates a security verdict.",
  },
  {
    label: "EXPLAIN",
    title: "LFM2.5-VL-450M",
    body: "Runs the security conversation and can inspect one image you explicitly choose. Its output cannot approve actions, change severity or hide detector uncertainty.",
  },
  {
    label: "ROUTE",
    title: "d1-2B decisions",
    body: "An optional decision model scores whether a request would benefit from cloud help and surfaces a Maxx offer. It runs after your answer, suggests only, and never changes a finding or approves an action.",
  },
] as const;

function useReveal() {
  useEffect(() => {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    const elements = Array.from(document.querySelectorAll<HTMLElement>(".reveal"));
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            entry.target.classList.add("is-visible");
            observer.unobserve(entry.target);
          }
        }
      },
      { threshold: 0.12 },
    );
    elements.forEach((element) => observer.observe(element));
    return () => observer.disconnect();
  }, []);
}

function StatusPill({ children, tone }: { children: ReactNode; tone: "cyan" | "gold" | "royal" }) {
  const colors = {
    cyan: "border-cyan/50 bg-cyan/10 text-cyan",
    gold: "border-gold/50 bg-gold/10 text-gold",
    royal: "border-royal bg-royal/20 text-white/70",
  };
  return (
    <span
      className={`inline-flex min-h-8 items-center border px-3 font-mono text-[10px] tracking-[0.16em] ${colors[tone]}`}
    >
      {children}
    </span>
  );
}

function Index() {
  useReveal();
  return (
    <div className="min-h-screen bg-navy font-body text-white selection:bg-cyan selection:text-navy">
      <nav className="sticky top-0 z-40 border-b border-white/10 bg-navy/95 px-4 py-3 backdrop-blur sm:px-6">
        <div className="mx-auto flex max-w-7xl items-center gap-4">
          <a href="#top" className="flex min-w-0 items-center gap-3" aria-label="Paladino home">
            <span className="pixel-border-gold grid size-7 shrink-0 place-items-center bg-royal">
              <span className="size-2 bg-cyan" />
            </span>
            <span className="truncate font-display text-sm text-gold sm:text-lg">PALADINO</span>
          </a>
          <div className="ml-auto hidden items-center gap-7 font-mono text-[11px] uppercase tracking-wider text-white/60 lg:flex">
            <a className="hover:text-cyan" href="#today">
              What it does
            </a>
            <a className="hover:text-cyan" href="#defense">
              Defense stack
            </a>
            <a className="hover:text-cyan" href="#how">
              How it works
            </a>
            <a className="hover:text-cyan" href="#limits">
              Not yet
            </a>
            <a className="hover:text-cyan" href="#technology">
              Technology
            </a>
            <a className="hover:text-cyan" href="#changelog">
              What's new
            </a>
          </div>
          <a
            href="#download"
            className="ml-auto bg-gold px-4 py-3 font-display text-[10px] text-navy lg:ml-3"
          >
            GET THE PREVIEW
          </a>
        </div>
      </nav>

      <main id="top">
        <header className="relative overflow-hidden px-4 pb-14 pt-12 sm:px-6 sm:pb-24 sm:pt-20">
          <div
            className="grid-bg pointer-events-none absolute inset-0 opacity-25 [mask-image:radial-gradient(ellipse_at_top,black,transparent_72%)]"
            aria-hidden="true"
          />
          <div className="relative mx-auto grid max-w-7xl items-center gap-12 lg:grid-cols-[1.05fr_.95fr] lg:gap-20">
            <div>
              <StatusPill tone="cyan">
                FREE ANDROID ANTIVIRUS · POWERED BY LOCAL LIQUID AI
              </StatusPill>
              <h1 className="mt-6 max-w-3xl text-balance font-display text-3xl leading-tight sm:text-4xl lg:text-5xl">
                Free antivirus for Android. <span className="text-gold">Powered by local AI.</span>
              </h1>
              <p className="mt-6 max-w-2xl text-lg leading-relaxed text-white/72">
                Paladino keeps a private AI companion and local antivirus in one Android app. It
                checks suspicious content on your phone, while Liquid AI connects reviewed knowledge
                and explains the evidence without sending your private checks to a cloud model.
              </p>
              <div className="mt-5 flex flex-wrap gap-x-5 gap-y-2 font-mono text-[10px] tracking-[0.16em] text-cyan">
                <span>FREE TO USE</span>
                <span>RUNS ON YOUR PHONE</span>
                <span>LIQUID AI MODELS</span>
              </div>
              <div className="mt-8 flex flex-col gap-3 sm:flex-row sm:items-center">
                <ApkDownloadButton />
                <a
                  href="#today"
                  className="min-h-12 border border-white/20 bg-card px-6 py-4 text-center font-display text-xs text-cyan hover:border-cyan/60"
                >
                  SEE WHAT WORKS TODAY
                </a>
              </div>
              <p className="mt-4 max-w-xl text-sm leading-relaxed text-white/45">
                The 0.0.1 preview combines the companion with the redesigned Security Guard
                dashboard. Broader rule coverage and automatic public threat updates are still being
                validated before wider release.
              </p>
            </div>

            <div
              className="mx-auto w-full max-w-md"
              aria-label="Preview of the Paladino protection dashboard"
            >
              <div className="pixel-border border-4 border-black bg-card p-5 shadow-[0_24px_80px_rgba(0,0,0,.35)] sm:p-7">
                <div className="flex items-center gap-3 border-b border-white/10 pb-5">
                  <img
                    src={paladinIdle}
                    alt="Paladino, the private Android security guardian"
                    className="size-16 object-contain [image-rendering:pixelated] motion-safe:animate-[float_5s_ease-in-out_infinite]"
                  />
                  <div>
                    <div className="font-mono text-[10px] tracking-widest text-cyan">
                      LOCAL ON THIS PHONE
                    </div>
                    <div className="mt-1 text-xl font-semibold">Protection</div>
                  </div>
                </div>
                <div className="mt-5 border border-gold/40 bg-gold/10 p-4">
                  <div className="flex items-center justify-between gap-3">
                    <span className="text-lg font-semibold text-gold">Limited</span>
                    <span className="font-mono text-[10px] text-white/45">SETUP NEEDED</span>
                  </div>
                  <p className="mt-2 text-sm leading-relaxed text-white/65">
                    Manual checks are available. Broader rules and automatic public updates are not
                    configured yet.
                  </p>
                </div>
                <a
                  href="#how"
                  className="mt-5 flex min-h-12 w-full items-center justify-center bg-gold px-5 py-3 font-display text-xs text-navy"
                >
                  CHECK SOMETHING
                </a>
                <div className="mt-5 grid grid-cols-2 gap-3 text-sm">
                  <div className="border border-white/10 bg-navy p-3">
                    <span className="text-cyan">●</span>
                    <div className="mt-2 font-medium">Messages + links</div>
                    <div className="mt-1 text-xs text-white/45">Available</div>
                  </div>
                  <div className="border border-white/10 bg-navy p-3">
                    <span className="text-gold">●</span>
                    <div className="mt-2 font-medium">Threat memory</div>
                    <div className="mt-1 text-xs text-white/45">Needs setup</div>
                  </div>
                </div>
                <div className="mt-5 flex items-center justify-between border-t border-white/10 pt-4 text-sm">
                  <span className="text-white/55">Nothing checked yet</span>
                  <span className="text-cyan">Ask Paladino →</span>
                </div>
              </div>
            </div>
          </div>

          <div className="relative mx-auto mt-12 grid max-w-7xl gap-4 md:grid-cols-3">
            {CAPABILITIES.map((item) => (
              <article key={item.state} className="border border-white/10 bg-card/85 p-5">
                <StatusPill tone={item.tone}>{item.state}</StatusPill>
                <h2 className="mt-4 text-lg font-semibold">{item.title}</h2>
                <p className="mt-2 text-sm leading-relaxed text-white/58">{item.body}</p>
              </article>
            ))}
          </div>
        </header>

        <section
          id="today"
          className="border-y border-white/10 bg-black/20 px-4 py-16 sm:px-6 sm:py-24"
        >
          <div className="mx-auto max-w-7xl">
            <div className="max-w-3xl reveal">
              <div className="font-mono text-[10px] tracking-[0.25em] text-cyan">
                IMPLEMENTED IN THE VALIDATION BUILD
              </div>
              <h2 className="mt-4 text-balance font-display text-3xl leading-tight sm:text-4xl">
                Check what worries you. See exactly what ran.
              </h2>
              <p className="mt-5 text-lg leading-relaxed text-white/60">
                Security Guard starts with user-selected checks and opt-in observation. It stores
                redacted findings, not the submitted message or screenshot. These features are still
                being validated before the public APK is updated.
              </p>
            </div>
            <div className="mt-10 grid gap-5 sm:grid-cols-2 lg:grid-cols-5">
              {CHECKS.map(([title, body], index) => (
                <article key={title} className="reveal border-l-2 border-cyan bg-card p-6">
                  <div className="font-mono text-[10px] text-cyan">0{index + 1}</div>
                  <h3 className="mt-3 text-xl font-semibold">{title}</h3>
                  <p className="mt-3 leading-relaxed text-white/58">{body}</p>
                </article>
              ))}
            </div>
          </div>
        </section>

        <section id="defense" className="px-4 py-16 sm:px-6 sm:py-24">
          <div className="mx-auto max-w-7xl">
            <div className="max-w-4xl reveal">
              <div className="font-mono text-[10px] tracking-[0.25em] text-gold">
                WHAT PALADINO DOES
              </div>
              <h2 className="mt-4 text-balance font-display text-3xl leading-tight sm:text-4xl">
                Antivirus, anti-malware and anti-scam intelligence on your phone.
              </h2>
              <p className="mt-5 max-w-3xl text-lg leading-relaxed text-white/60">
                Fast deterministic checks produce the finding. Signed threat knowledge and private
                vector retrieval add context. Liquid AI explains the evidence, and you remain in
                control of every action.
              </p>
            </div>

            <div
              className="reveal mt-9 grid gap-px border border-white/10 bg-white/10 sm:grid-cols-5"
              aria-label="Paladino security workflow"
            >
              {[
                ["1", "Choose"],
                ["2", "Detect"],
                ["3", "Compare"],
                ["4", "Explain"],
                ["5", "You decide"],
              ].map(([number, label]) => (
                <div key={number} className="bg-navy px-4 py-4 text-center">
                  <div className="font-mono text-[10px] text-cyan">{number}</div>
                  <div className="mt-1 text-sm font-medium">{label}</div>
                </div>
              ))}
            </div>

            <div className="mt-8 grid gap-5 md:grid-cols-2 lg:grid-cols-3">
              {DEFENSES.map((item, index) => (
                <article key={item.title} className="reveal border border-white/10 bg-card p-6">
                  <div className="flex items-start justify-between gap-4">
                    <span className="font-mono text-[10px] tracking-[0.16em] text-cyan">
                      {item.status}
                    </span>
                    <span className="font-mono text-[10px] text-white/30">
                      {String(index + 1).padStart(2, "0")}
                    </span>
                  </div>
                  <h3 className="mt-4 text-xl font-semibold">{item.title}</h3>
                  <p className="mt-3 leading-relaxed text-white/65">{item.body}</p>
                  <p className="mt-4 border-l-2 border-gold/60 pl-3 text-sm leading-relaxed text-white/45">
                    {item.detail}
                  </p>
                </article>
              ))}
            </div>
          </div>
        </section>

        <section id="how" className="px-4 py-16 sm:px-6 sm:py-24">
          <div className="mx-auto max-w-6xl">
            <div className="text-center reveal">
              <div className="font-mono text-[10px] tracking-[0.25em] text-gold">
                ONE CLEAR PROTECTION LOOP
              </div>
              <h2 className="mt-4 font-display text-3xl sm:text-4xl">
                Share or choose. Check locally. Review and ask.
              </h2>
            </div>
            <ol className="mt-12 grid gap-5 md:grid-cols-3">
              {[
                [
                  "1",
                  "Share or choose",
                  "Paste a message or link, choose a file or APK, or select one screenshot. Paladino tells you what it will read before it starts.",
                ],
                [
                  "2",
                  "Check locally",
                  "Deterministic checks run with strict size and time limits. You can cancel, and an incomplete check is labelled incomplete.",
                ],
                [
                  "3",
                  "Review and ask",
                  "Read the result, performed checks, skipped checks and evidence. Ask the local model for a plain-language explanation.",
                ],
              ].map(([number, title, body]) => (
                <li key={number} className="reveal border border-white/10 bg-card p-6">
                  <div className="grid size-10 place-items-center bg-royal font-display text-sm text-white">
                    {number}
                  </div>
                  <h3 className="mt-5 text-xl font-semibold">{title}</h3>
                  <p className="mt-3 leading-relaxed text-white/58">{body}</p>
                </li>
              ))}
            </ol>
          </div>
        </section>

        <section
          id="limits"
          className="border-y border-white/10 bg-card px-4 py-16 sm:px-6 sm:py-24"
        >
          <div className="mx-auto grid max-w-6xl gap-10 lg:grid-cols-[.8fr_1.2fr]">
            <div className="reveal">
              <StatusPill tone="gold">CLEAR LIMITS</StatusPill>
              <h2 className="mt-5 font-display text-3xl leading-tight sm:text-4xl">
                What Paladino does not do yet
              </h2>
              <p className="mt-5 leading-relaxed text-white/58">
                Early access should make uncertainty visible. These limits appear in the app beside
                the affected checks, not only in legal text.
              </p>
            </div>
            <ul className="space-y-3">
              {LIMITS.map((limit) => (
                <li
                  key={limit}
                  className="reveal flex gap-3 border border-white/10 bg-navy p-4 leading-relaxed text-white/70"
                >
                  <span className="mt-1 text-gold">◇</span>
                  <span>{limit}</span>
                </li>
              ))}
            </ul>
          </div>
        </section>

        <section id="technology" className="px-4 py-16 sm:px-6 sm:py-24">
          <div className="mx-auto max-w-7xl">
            <div className="max-w-3xl reveal">
              <div className="font-mono text-[10px] tracking-[0.25em] text-cyan">
                HOW THE LOCAL AI ANTIVIRUS WORKS
              </div>
              <h2 className="mt-4 font-display text-3xl sm:text-4xl">
                Security checks find signals. Liquid AI connects context and explains.
              </h2>
              <p className="mt-5 text-lg leading-relaxed text-white/60">
                Detection stays with the security engine. Liquid AI runs locally to retrieve
                relevant reviewed knowledge, explain evidence and help you decide what to do next.
                It cannot change a verdict or approve a sensitive action.
              </p>
            </div>
            <div className="mt-10 grid gap-5 lg:grid-cols-3">
              {TECHNOLOGY.map((item) => (
                <article key={item.label} className="reveal border-t-2 border-cyan bg-card p-6">
                  <div className="font-mono text-[10px] tracking-[0.2em] text-cyan">
                    {item.label}
                  </div>
                  <h3 className="mt-4 text-xl font-semibold">{item.title}</h3>
                  <p className="mt-3 leading-relaxed text-white/58">{item.body}</p>
                </article>
              ))}
            </div>
            <div className="reveal mt-8 border border-cyan/30 bg-cyan/5 p-6">
              <h3 className="text-lg font-semibold text-cyan">
                Private by default, controlled by you
              </h3>
              <p className="mt-2 max-w-4xl leading-relaxed text-white/60">
                Selected content is processed on your phone. Findings are redacted. Models cannot
                grant permissions, delete apps, dismiss notifications or send private data. Android
                shows the final system screen whenever an action needs your approval.
              </p>
            </div>
          </div>
        </section>

        <section
          id="changelog"
          className="border-t border-white/10 bg-black/20 px-4 py-16 sm:px-6 sm:py-20"
        >
          <div className="mx-auto max-w-5xl reveal">
            <div className="font-mono text-[10px] tracking-[0.25em] text-cyan">WHAT'S NEW</div>
            <h2 className="mt-4 font-display text-3xl sm:text-4xl">New in 0.0.1</h2>
            <div className="mt-8 grid gap-5 md:grid-cols-3">
              <article className="border border-white/10 bg-card p-6">
                <h3 className="text-lg font-semibold">Antivirus checks built in</h3>
                <p className="mt-3 text-sm leading-relaxed text-white/60">
                  The Security Guard tab scans shared files, APKs, messages and links with
                  deterministic, bounded local checks, and shows the evidence and coverage of every
                  scan. Findings are redacted and stored only on your phone.
                </p>
              </article>
              <article className="border border-white/10 bg-card p-6">
                <h3 className="text-lg font-semibold">Optional LFM3 speaker</h3>
                <p className="mt-3 text-sm leading-relaxed text-white/60">
                  Settings can now download Liquid's LFM3-Omni-2B as an optional local text speaker
                  alongside the default 219 MB model. Everything still runs on your phone, offline,
                  with verified downloads.
                </p>
              </article>
              <article className="border border-white/10 bg-card p-6">
                <h3 className="text-lg font-semibold">Decisions, not verdicts</h3>
                <p className="mt-3 text-sm leading-relaxed text-white/60">
                  The optional d1-2B decision model runs after your answer and can only suggest when
                  cloud help may serve you better. It never changes a security finding or approves
                  an action.
                </p>
              </article>
            </div>
          </div>
        </section>

        <section
          id="download"
          className="border-t border-white/10 bg-black/20 px-4 py-20 text-center sm:px-6 sm:py-28"
        >
          <div className="mx-auto max-w-3xl reveal">
            <StatusPill tone="royal">CURRENT PALADINO PREVIEW</StatusPill>
            <h2 className="mt-6 font-display text-3xl sm:text-4xl">Get Paladino for Android</h2>
            <p className="mx-auto mt-5 max-w-2xl leading-relaxed text-white/60">
              Companion and antivirus live together in Paladino. The current free preview includes
              local AI chat, Sprite, widget, Security Guard checks and user-controlled tools. Liquid
              AI models are optional downloads and are verified before installation.
            </p>
            <div className="mt-8 flex justify-center">
              <ApkDownloadButton />
            </div>
            <div className="mx-auto mt-8 flex max-w-2xl flex-wrap justify-center gap-3 font-mono text-[10px] tracking-wider text-white/50">
              <span>{RELEASE.version}</span>
              <span>·</span>
              <span>{RELEASE.android}</span>
              <span>·</span>
              <span>{RELEASE.architecture}</span>
              <span>·</span>
              <span>CHAT {RELEASE.speaker}</span>
              <span>·</span>
              <span>VISION {RELEASE.vision}</span>
              <span>·</span>
              <span>VECTORS {RELEASE.vectors}</span>
              <span>·</span>
              <span>LFM3 CHAT {RELEASE.speakerLfm3}</span>
              <span>·</span>
              <span>DECISIONS {RELEASE.decisions}</span>
            </div>
            <details className="mx-auto mt-8 max-w-2xl border border-white/10 bg-card p-5 text-left text-sm text-white/60">
              <summary className="cursor-pointer font-medium text-cyan">
                Installation and verification
              </summary>
              <ol className="mt-4 list-decimal space-y-2 pl-5 leading-relaxed">
                <li>Download the preview APK on an Android phone and open it.</li>
                <li>Allow installation from this browser only when Android asks.</li>
                <li>
                  Install local chat, vision or vector memory separately from Settings when wanted.
                </li>
                <li>
                  Keep the existing app installed when updating so conversations and settings
                  remain.
                </li>
              </ol>
              <div className="mt-4 flex flex-wrap gap-5">
                <a className="text-cyan underline" href={`${APK_RELEASE}/SHA256SUMS.txt`}>
                  Verify SHA-256 checksum
                </a>
                <a className="text-cyan underline" href={`${APK_RELEASE}/INSTALL-ANDROID.md`}>
                  Guia em português
                </a>
              </div>
            </details>
          </div>
        </section>
      </main>

      <footer className="border-t border-white/10 px-4 py-10 sm:px-6">
        <div className="mx-auto flex max-w-7xl flex-col gap-3 font-mono text-[10px] tracking-wider text-white/38 sm:flex-row sm:items-center sm:justify-between">
          <span>© 2026 PETOLOGIC · PALADINO FREE AI ANTIVIRUS</span>
          <span>LOCAL LIQUID AI · USER CONTROLLED · EVIDENCE LED</span>
        </div>
      </footer>
    </div>
  );
}
