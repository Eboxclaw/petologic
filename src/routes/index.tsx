import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";

import paladinAsset from "../assets/paladin.png.asset.json";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "Petologic — Tiny Intelligence Inside Your Phone" },
      {
        name: "description",
        content:
          "Petologic is a local-first AI familiar for Android. It guards your privacy, uses your tools, and only calls the cloud when you say so.",
      },
      { property: "og:title", content: "Petologic — Tiny Intelligence Inside Your Phone" },
      {
        property: "og:description",
        content:
          "A local-first AI familiar for Android. No clouds, no leaks, just pure code — with a pixel paladin standing guard.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: Index,
});

function Index() {
  const [email, setEmail] = useState("");
  const [joined, setJoined] = useState(false);

  return (
    <div className="min-h-screen bg-navy font-body text-white selection:bg-cyan selection:text-navy">
      {/* Scanline overlay */}
      <div className="pointer-events-none fixed inset-0 z-50 overflow-hidden opacity-10" aria-hidden="true">
        <div className="h-1 w-full animate-[scanline_4s_linear_infinite] bg-white" />
      </div>

      {/* Navigation */}
      <nav className="sticky top-0 z-40 border-b-4 border-black bg-navy/80 px-6 py-4 backdrop-blur-sm">
        <div className="mx-auto flex max-w-7xl items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="pixel-border-gold grid size-8 place-items-center bg-royal">
              <div className="size-2 bg-cyan shadow-[0_0_8px_var(--color-cyan)]" />
            </div>
            <span className="font-display text-xl tracking-tighter text-gold">PETOLOGIC</span>
          </div>
          <div className="hidden gap-8 font-mono text-xs uppercase tracking-widest text-white/60 md:flex">
            <a href="#familiar" className="transition-colors hover:text-cyan">The familiar</a>
            <a href="#modules" className="transition-colors hover:text-cyan">Security</a>
            <a href="#modules" className="transition-colors hover:text-cyan">Tools</a>
          </div>
          <a
            href="#waitlist"
            className="pixel-border bg-gold px-4 py-2 font-display text-xs text-navy transition-transform hover:translate-y-1"
          >
            ENLIST
          </a>
        </div>
      </nav>

      {/* Hero */}
      <header id="familiar" className="relative overflow-hidden px-6 pb-32 pt-20">
        <div className="mx-auto grid max-w-7xl items-center gap-16 md:grid-cols-2">
          <div>
            <div className="mb-6 inline-block border border-royal bg-royal/20 px-3 py-1 font-mono text-[10px] uppercase tracking-[0.2em] text-cyan">
              Protocol initialized: v2.0.4
            </div>
            <h1 className="mb-8 text-balance font-display text-5xl leading-[1.1] lg:text-7xl">
              Tiny <span className="text-gold">intelligence</span> inside your phone.
            </h1>
            <p className="mb-10 max-w-md text-lg leading-relaxed text-white/70">
              Petologic is a local-first AI familiar that guards your privacy while managing your
              digital life. No clouds, no leaks, just pure code.
            </p>
            <div className="flex flex-wrap gap-4">
              <a
                href="#waitlist"
                className="pixel-border-gold group bg-royal px-8 py-4 font-display text-sm tracking-wide transition-all hover:bg-royal/90 active:translate-y-1"
              >
                INSTALL ON ANDROID{" "}
                <span className="ml-2 inline-block transition-transform group-hover:translate-x-1">→</span>
              </a>
            </div>
          </div>

          <div className="relative flex items-center justify-center animate-[float_4s_ease-in-out_infinite]">
            <div className="pixel-border relative z-10 flex aspect-square w-full max-w-[400px] items-center justify-center border-8 border-black bg-card p-8">
              <div className="dither-pattern absolute inset-0 opacity-20" aria-hidden="true" />
              <img
                src={paladinAsset.url}
                alt="0xPaladino, the Petologic pixel knight mascot, in royal blue and gold armor with glowing cyan eyes"
                className="relative z-10 h-full w-full object-contain shadow-[0_0_40px_rgba(42,78,214,0.3)] [image-rendering:pixelated]"
              />
              <div className="absolute -top-2 -right-2 bg-cyan px-2 font-mono text-[10px] font-bold text-navy">
                LVL 99
              </div>
              <div className="absolute -bottom-2 -left-2 size-6 border-4 border-gold bg-navy" aria-hidden="true" />
            </div>
            <div
              className="absolute inset-0 -z-10 scale-75 rounded-full bg-royal/20 blur-[100px]"
              aria-hidden="true"
            />
          </div>
        </div>
      </header>

      {/* Feature grid */}
      <section id="modules" className="border-y-4 border-black bg-black/30 px-6 py-24">
        <div className="mx-auto grid max-w-7xl gap-8 md:grid-cols-3">
          <div className="group relative border-t-4 border-royal bg-card p-8">
            <div className="mb-4 font-mono text-[10px] tracking-tighter text-royal">[ MODULE_01 ]</div>
            <h3 className="mb-4 font-display text-xl transition-colors group-hover:text-cyan">LOCAL BRAIN</h3>
            <p className="mb-6 font-body text-sm leading-relaxed text-white/50">
              Runs entirely on your device's NPU. Your data never leaves the hardware. Zero latency,
              total control.
            </p>
            <div className="flex gap-1" aria-hidden="true">
              <div className="h-1 w-full bg-royal" />
              <div className="h-1 w-1/2 bg-royal/30" />
            </div>
          </div>

          <div className="group relative border-t-4 border-gold bg-card p-8">
            <div className="mb-4 font-mono text-[10px] tracking-tighter text-gold">[ MODULE_02 ]</div>
            <h3 className="mb-4 font-display text-xl transition-colors group-hover:text-gold">TOOL USE</h3>
            <p className="mb-6 text-sm leading-relaxed text-white/50">
              Petologic can navigate apps, set reminders, and manage notifications with
              high-precision intent parsing.
            </p>
            <div className="flex gap-1" aria-hidden="true">
              <div className="h-1 w-full bg-gold" />
              <div className="h-1 w-1/4 bg-gold/30" />
            </div>
          </div>

          <div className="group relative border-t-4 border-cyan bg-card p-8">
            <div className="mb-4 font-mono text-[10px] tracking-tighter text-cyan">[ MODULE_03 ]</div>
            <h3 className="mb-4 font-display text-xl transition-colors group-hover:text-cyan">CLOUD RELAY</h3>
            <p className="mb-6 text-sm leading-relaxed text-white/50">
              Connect to Claude, GPT-4, or OpenRouter only when you need massive compute power. You
              choose the link.
            </p>
            <div className="flex gap-1" aria-hidden="true">
              <div className="h-1 w-full bg-cyan" />
              <div className="h-1 w-3/4 bg-cyan/30" />
            </div>
          </div>
        </div>
      </section>

      {/* Bottom CTA */}
      <section id="waitlist" className="relative px-6 py-32 text-center">
        <div
          className="absolute top-0 left-1/2 h-24 w-px -translate-x-1/2 bg-gradient-to-b from-royal to-transparent"
          aria-hidden="true"
        />
        <div className="mx-auto max-w-2xl">
          <div className="mb-8 animate-pulse font-display text-xs uppercase tracking-widest text-cyan">
            Waiting for command...
          </div>
          <h2 className="mb-12 font-display text-4xl">
            Ready to deploy your <span className="text-gold">familiar</span>?
          </h2>
          {joined ? (
            <div className="pixel-border inline-block border-4 border-black bg-card px-8 py-4 font-display text-sm text-cyan">
              COMMAND RECEIVED. WELCOME, TRAVELER.
            </div>
          ) : (
            <form
              className="pixel-border inline-flex border-4 border-black bg-card p-1"
              onSubmit={(e) => {
                e.preventDefault();
                if (email.trim()) setJoined(true);
              }}
            >
              <label htmlFor="waitlist-email" className="sr-only">
                Email address
              </label>
              <input
                id="waitlist-email"
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="ENTER EMAIL"
                className="w-64 border-none bg-navy px-6 py-4 font-mono text-sm outline-none focus:ring-2 focus:ring-cyan md:w-80"
              />
              <button
                type="submit"
                className="bg-royal px-8 py-4 font-display text-sm transition-colors hover:bg-cyan hover:text-navy active:translate-y-1"
              >
                JOIN WAITLIST
              </button>
            </form>
          )}
          <div className="mt-12 flex justify-center gap-6 opacity-40 grayscale">
            <span className="font-mono text-[10px]">OPENROUTER</span>
            <span className="font-mono text-[10px]">ANTHROPIC</span>
            <span className="font-mono text-[10px]">OPENAI</span>
            <span className="font-mono text-[10px]">GEMINI</span>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t-4 border-black bg-navy px-6 py-12">
        <div className="mx-auto flex max-w-7xl flex-col items-center justify-between gap-8 md:flex-row">
          <div className="font-mono text-[10px] text-white/30">
            © 2026 PETOLOGIC INTEL. BUILT FOR ANDROID. BYPASSING THE CLOUD.
          </div>
          <div className="flex gap-8">
            <a href="#" className="font-mono text-[10px] text-white/50 hover:text-cyan">TWITTER</a>
            <a href="#" className="font-mono text-[10px] text-white/50 hover:text-cyan">DISCORD</a>
            <a href="#" className="font-mono text-[10px] text-white/50 hover:text-cyan">GITHUB</a>
          </div>
        </div>
      </footer>
    </div>
  );
}
