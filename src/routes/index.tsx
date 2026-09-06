import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useRef, useState } from "react";

import paladinAsset from "../assets/paladin.png.asset.json";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "Petologic — Tiny Intelligence Inside Your Phone" },
      {
        name: "description",
        content:
          "Petologic is a tiny intelligence infrastructure built on top of Android. Create and run specialized pets that do anything for you — inside your boundaries.",
      },
      { property: "og:title", content: "Petologic — Tiny Intelligence Inside Your Phone" },
      {
        property: "og:description",
        content:
          "A tiny intelligence infrastructure on Android. Spawn specialized pets that work for you — no clouds, no leaks, just pure code.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: Index,
});

/* --- Tiny original 8-bit MMORPG-style loop, synthesized with Web Audio --- */
const MELODY: Array<[number, number]> = [
  // [midi note, beats] — a cheerful overworld-style loop
  [76, 0.5], [79, 0.5], [81, 1], [79, 0.5], [76, 0.5], [74, 1],
  [72, 0.5], [74, 0.5], [76, 1], [69, 2],
  [76, 0.5], [79, 0.5], [81, 1], [84, 0.5], [81, 0.5], [79, 1],
  [76, 0.5], [74, 0.5], [72, 1], [72, 2],
];
const BASS: Array<[number, number]> = [
  [48, 1], [48, 1], [45, 1], [45, 1],
  [41, 1], [41, 1], [43, 1], [43, 1],
  [48, 1], [48, 1], [45, 1], [45, 1],
  [41, 1], [43, 1], [48, 2],
];
const midiToFreq = (m: number) => 440 * Math.pow(2, (m - 69) / 12);

function startChiptune(ctx: AudioContext) {
  const master = ctx.createGain();
  master.gain.value = 0.12;
  master.connect(ctx.destination);

  const beat = 0.28; // seconds per beat
  let t = ctx.currentTime + 0.05;

  const playTrack = (notes: Array<[number, number]>, type: OscillatorType, vol: number) => {
    let cursor = t;
    for (const [note, beats] of notes) {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = type;
      osc.frequency.value = midiToFreq(note);
      const dur = beats * beat;
      gain.gain.setValueAtTime(vol, cursor);
      gain.gain.setValueAtTime(vol, cursor + dur * 0.8);
      gain.gain.linearRampToValueAtTime(0.0001, cursor + dur);
      osc.connect(gain).connect(master);
      osc.start(cursor);
      osc.stop(cursor + dur);
      cursor += dur;
    }
    return cursor;
  };

  const scheduleLoop = () => {
    const endA = playTrack(MELODY, "square", 0.5);
    const endB = playTrack(BASS, "triangle", 0.9);
    const loopEnd = Math.max(endA, endB);
    const id = window.setTimeout(scheduleLoop, (loopEnd - ctx.currentTime) * 1000 - 100);
    t = loopEnd;
    return id;
  };
  const timeoutId = scheduleLoop();
  return () => {
    window.clearTimeout(timeoutId);
    master.disconnect();
  };
}

function Index() {
  const [email, setEmail] = useState("");
  const [joined, setJoined] = useState(false);
  const [musicOn, setMusicOn] = useState(false);
  const audioRef = useRef<{ ctx: AudioContext; stop: () => void } | null>(null);

  const toggleMusic = () => {
    if (musicOn) {
      audioRef.current?.stop();
      void audioRef.current?.ctx.close();
      audioRef.current = null;
      setMusicOn(false);
    } else {
      const ctx = new AudioContext();
      const stop = startChiptune(ctx);
      audioRef.current = { ctx, stop };
      setMusicOn(true);
    }
  };

  useEffect(
    () => () => {
      audioRef.current?.stop();
      void audioRef.current?.ctx.close();
    },
    [],
  );

  return (
    <div className="min-h-screen bg-navy font-body text-white selection:bg-cyan selection:text-navy">
      {/* Scanline overlay */}
      <div className="pointer-events-none fixed inset-0 z-50 overflow-hidden opacity-10" aria-hidden="true">
        <div className="h-1 w-full animate-[scanline_4s_linear_infinite] bg-white" />
      </div>

      {/* Music toggle */}
      <button
        type="button"
        onClick={toggleMusic}
        aria-pressed={musicOn}
        className="pixel-border fixed bottom-6 right-6 z-50 border-4 border-black bg-card px-4 py-3 font-display text-[10px] text-gold transition-transform hover:-translate-y-1 active:translate-y-1"
      >
        {musicOn ? "♪ MUSIC: ON" : "♪ MUSIC: OFF"}
      </button>

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
            <a href="#pets" className="transition-colors hover:text-cyan">The pets</a>
            <a href="#showcase" className="transition-colors hover:text-cyan">Showcase</a>
            <a href="#modules" className="transition-colors hover:text-cyan">Modules</a>
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
      <header id="pets" className="relative overflow-hidden px-6 pb-32 pt-20">
        <div className="mx-auto grid max-w-7xl items-center gap-16 md:grid-cols-2">
          <div>
            <div className="mb-6 inline-block border border-royal bg-royal/20 px-3 py-1 font-mono text-[10px] uppercase tracking-[0.2em] text-cyan">
              Protocol initialized: v2.0.4
            </div>
            <h1 className="mb-8 text-balance font-display text-5xl leading-[1.1] lg:text-7xl">
              Tiny <span className="text-gold">intelligence</span> inside your phone.
            </h1>
            <p className="mb-10 max-w-md text-lg leading-relaxed text-white/70">
              Petologic is a tiny intelligence infrastructure built on top of Android. Create and
              run different specialized pets that can do anything for you — always inside your
              boundaries.
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
                alt="0xPaladino, a Petologic pixel knight pet in royal blue and gold armor with glowing cyan eyes"
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

      {/* Phone showcase */}
      <section id="showcase" className="border-y-4 border-black bg-black/30 px-6 py-24">
        <div className="mx-auto grid max-w-7xl items-center gap-16 md:grid-cols-2">
          {/* Phone mockup */}
          <div className="flex items-center justify-center">
            <div className="pixel-border relative w-full max-w-[300px] border-8 border-black bg-card p-3">
              {/* Notch */}
              <div className="mx-auto mb-3 h-4 w-24 border-4 border-black bg-navy" aria-hidden="true" />
              {/* Screen */}
              <div className="relative overflow-hidden border-4 border-black bg-navy p-4">
                <div className="dither-pattern absolute inset-0 opacity-10" aria-hidden="true" />
                <div className="relative z-10">
                  <div className="mb-1 flex items-center justify-between font-mono text-[8px] text-white/40">
                    <span>PETOLOGIC OS</span>
                    <span className="text-cyan">● ONLINE</span>
                  </div>
                  <div className="mb-3 border-2 border-royal bg-royal/10 p-3 text-center">
                    <img
                      src={paladinAsset.url}
                      alt="0xPaladino pet idle on the phone screen"
                      className="mx-auto mb-2 size-24 object-contain [image-rendering:pixelated]"
                    />
                    <div className="font-display text-[10px] text-gold">0xPALADINO</div>
                    <div className="font-mono text-[8px] text-white/50">GUARDIAN CLASS · LVL 99</div>
                  </div>
                  {/* Stat bars */}
                  <div className="mb-3 space-y-2">
                    <div>
                      <div className="mb-1 flex justify-between font-mono text-[8px] text-white/60">
                        <span>HP</span><span>980/980</span>
                      </div>
                      <div className="h-2 border border-black bg-navy">
                        <div className="h-full w-full bg-cyan" />
                      </div>
                    </div>
                    <div>
                      <div className="mb-1 flex justify-between font-mono text-[8px] text-white/60">
                        <span>MANA</span><span>640/800</span>
                      </div>
                      <div className="h-2 border border-black bg-navy">
                        <div className="h-full w-4/5 bg-royal" />
                      </div>
                    </div>
                    <div>
                      <div className="mb-1 flex justify-between font-mono text-[8px] text-white/60">
                        <span>TRUST</span><span>MAX</span>
                      </div>
                      <div className="h-2 border border-black bg-navy">
                        <div className="h-full w-full bg-gold" />
                      </div>
                    </div>
                  </div>
                  {/* Quest log */}
                  <div className="border-2 border-gold/40 bg-card p-2">
                    <div className="mb-1 font-display text-[8px] text-gold">QUEST LOG</div>
                    <ul className="space-y-1 font-mono text-[8px] text-white/60">
                      <li><span className="text-cyan">✓</span> Silence spam notifications</li>
                      <li><span className="text-cyan">✓</span> Summarize 42 unread chats</li>
                      <li><span className="animate-pulse text-gold">▸</span> Guarding your data…</li>
                    </ul>
                  </div>
                </div>
              </div>
              {/* Home bar */}
              <div className="mx-auto mt-3 h-1 w-16 bg-white/20" aria-hidden="true" />
            </div>
          </div>

          {/* Feature list */}
          <div>
            <div className="mb-6 inline-block border border-gold bg-gold/10 px-3 py-1 font-mono text-[10px] uppercase tracking-[0.2em] text-gold">
              Field manual
            </div>
            <h2 className="mb-10 font-display text-4xl leading-tight">
              One phone. <span className="text-cyan">Many pets.</span>
            </h2>
            <ul className="space-y-8">
              <li className="flex gap-4">
                <div className="pixel-border mt-1 grid size-10 shrink-0 place-items-center bg-royal font-display text-xs">01</div>
                <div>
                  <h3 className="mb-1 font-display text-sm text-gold">SPAWN SPECIALIZED PETS</h3>
                  <p className="text-sm leading-relaxed text-white/60">
                    Breed a pet for every job — one guards your messages, one tames your calendar,
                    one hunts spam. Each runs its own tiny brain.
                  </p>
                </div>
              </li>
              <li className="flex gap-4">
                <div className="pixel-border mt-1 grid size-10 shrink-0 place-items-center bg-royal font-display text-xs">02</div>
                <div>
                  <h3 className="mb-1 font-display text-sm text-gold">SET THE BOUNDARIES</h3>
                  <p className="text-sm leading-relaxed text-white/60">
                    You draw the fence. Pets act only inside the permissions you grant — and every
                    action is logged in the quest log.
                  </p>
                </div>
              </li>
              <li className="flex gap-4">
                <div className="pixel-border mt-1 grid size-10 shrink-0 place-items-center bg-royal font-display text-xs">03</div>
                <div>
                  <h3 className="mb-1 font-display text-sm text-gold">RUNS ON YOUR HARDWARE</h3>
                  <p className="text-sm leading-relaxed text-white/60">
                    Native to Android, powered by your device's NPU. Pets keep working offline —
                    the cloud is a summon, never a leash.
                  </p>
                </div>
              </li>
              <li className="flex gap-4">
                <div className="pixel-border mt-1 grid size-10 shrink-0 place-items-center bg-royal font-display text-xs">04</div>
                <div>
                  <h3 className="mb-1 font-display text-sm text-gold">LEVEL THEM UP</h3>
                  <p className="text-sm leading-relaxed text-white/60">
                    Pets learn your habits and gain skills over time. Train a party that knows
                    exactly how you like things done.
                  </p>
                </div>
              </li>
            </ul>
          </div>
        </div>
      </section>

      {/* Feature grid */}
      <section id="modules" className="border-b-4 border-black bg-navy px-6 py-24">
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
              Pets can navigate apps, set reminders, and manage notifications with high-precision
              intent parsing.
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
            Ready to raise your <span className="text-gold">tiny intelligence</span>?
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
