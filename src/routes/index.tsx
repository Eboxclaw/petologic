import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useRef, useState } from "react";

import paladinAsset from "../assets/pets/0xpaladino.png";
import paladinIdle from "../assets/pets/paladino_idle.gif";
import mewsashiAsset from "../assets/pets/mewsashi.png";
import monkaiAsset from "../assets/pets/Monkai.png";
import darktwinAsset from "../assets/pets/darktwin.png";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "Petologic: Tiny Intelligence Pets For Your Android" },
      {
        name: "description",
        content:
          "Petologic is a tiny intelligence infrastructure built on top of Android. Raise specialized pixel pets that act for you, always inside your boundaries.",
      },
      { property: "og:title", content: "Petologic: Tiny Intelligence Pets For Your Android" },
      {
        property: "og:description",
        content:
          "Raise 0xPaladino and a roster of tiny intelligence pets that live on your phone, run on your hardware, and act only inside the fence you draw.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: Index,
});

/* --- Tiny original 8-bit MMORPG-style loop, synthesized with Web Audio --- */
const MELODY: Array<[number, number]> = [
  [69, 1], [76, 1], [74, 0.5], [72, 0.5], [74, 1], [76, 2],
  [72, 1], [79, 1], [77, 0.5], [76, 0.5], [74, 1], [72, 2],
  [69, 1], [76, 1], [81, 1], [79, 0.5], [77, 0.5], [76, 1], [74, 1], [72, 2],
  [67, 1], [69, 1], [72, 1], [76, 1], [74, 2], [69, 2],
];
const BASS: Array<[number, number]> = [
  [45, 2], [45, 2], [41, 2], [41, 2],
  [40, 2], [40, 2], [45, 2], [45, 2],
  [45, 2], [48, 2], [43, 2], [41, 2],
  [40, 2], [40, 2], [45, 4],
];
const ARP: Array<[number, number]> = [
  [69, 0.5], [72, 0.5], [76, 0.5], [72, 0.5], [69, 0.5], [72, 0.5], [76, 0.5], [72, 0.5],
  [65, 0.5], [69, 0.5], [72, 0.5], [69, 0.5], [65, 0.5], [69, 0.5], [72, 0.5], [69, 0.5],
  [64, 0.5], [67, 0.5], [71, 0.5], [67, 0.5], [64, 0.5], [67, 0.5], [71, 0.5], [67, 0.5],
  [69, 0.5], [72, 0.5], [76, 0.5], [72, 0.5], [69, 0.5], [72, 0.5], [76, 0.5], [72, 0.5],
];

const midiToFreq = (m: number) => 440 * Math.pow(2, (m - 69) / 12);

// Public binary distribution; source code remains in the private application repository.
const APK_RELEASE = "https://github.com/Eboxclaw/petologic-downloads/releases/download/v0.2.1-preview";
const APK_URL = `${APK_RELEASE}/petologic-0.2.1-preview-arm64.apk`;

function startChiptune(ctx: AudioContext) {
  const master = ctx.createGain();
  master.gain.value = 0.16;

  // Gentle bus compression so the low end stays thick without clipping.
  const glue = ctx.createDynamicsCompressor();
  glue.threshold.value = -18;
  glue.knee.value = 24;
  glue.ratio.value = 6;
  glue.attack.value = 0.004;
  glue.release.value = 0.18;
  master.connect(glue).connect(ctx.destination);

  // Shared white-noise buffer for the drum kit.
  const noiseBuffer = ctx.createBuffer(1, ctx.sampleRate * 0.5, ctx.sampleRate);
  const nd = noiseBuffer.getChannelData(0);
  for (let i = 0; i < nd.length; i++) nd[i] = Math.random() * 2 - 1;

  const beat = 0.3;
  let t = ctx.currentTime + 0.06;

  const playTrack = (
    notes: Array<[number, number]>,
    type: OscillatorType,
    vol: number,
    detune = 0,
  ) => {
    let cursor = t;
    for (const [note, beats] of notes) {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = type;
      osc.frequency.value = midiToFreq(note);
      osc.detune.value = detune;
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

  // Liquid bass: saw through a resonant lowpass that opens and closes per note.
  const playLiquidBass = (notes: Array<[number, number]>, vol: number) => {
    let cursor = t;
    for (const [note, beats] of notes) {
      const dur = beats * beat;
      const osc = ctx.createOscillator();
      osc.type = "sawtooth";
      osc.frequency.value = midiToFreq(note - 12);
      const filter = ctx.createBiquadFilter();
      filter.type = "lowpass";
      filter.Q.value = 12;
      filter.frequency.setValueAtTime(160, cursor);
      filter.frequency.exponentialRampToValueAtTime(1100, cursor + dur * 0.35);
      filter.frequency.exponentialRampToValueAtTime(180, cursor + dur);
      const gain = ctx.createGain();
      gain.gain.setValueAtTime(0.0001, cursor);
      gain.gain.exponentialRampToValueAtTime(vol, cursor + 0.02);
      gain.gain.setValueAtTime(vol, cursor + dur * 0.75);
      gain.gain.exponentialRampToValueAtTime(0.0001, cursor + dur);
      osc.connect(filter).connect(gain).connect(master);
      osc.start(cursor);
      osc.stop(cursor + dur);

      // Sub sine doubling one octave lower for real weight.
      const sub = ctx.createOscillator();
      sub.type = "sine";
      sub.frequency.value = midiToFreq(note - 24);
      const subGain = ctx.createGain();
      subGain.gain.setValueAtTime(0.0001, cursor);
      subGain.gain.exponentialRampToValueAtTime(vol * 0.9, cursor + 0.02);
      subGain.gain.setValueAtTime(vol * 0.9, cursor + dur * 0.7);
      subGain.gain.exponentialRampToValueAtTime(0.0001, cursor + dur);
      sub.connect(subGain).connect(master);
      sub.start(cursor);
      sub.stop(cursor + dur);

      cursor += dur;
    }
    return cursor;
  };

  const kick = (at: number) => {
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = "sine";
    osc.frequency.setValueAtTime(150, at);
    osc.frequency.exponentialRampToValueAtTime(42, at + 0.12);
    gain.gain.setValueAtTime(1.1, at);
    gain.gain.exponentialRampToValueAtTime(0.0001, at + 0.22);
    osc.connect(gain).connect(master);
    osc.start(at);
    osc.stop(at + 0.24);
  };

  const noiseHit = (at: number, dur: number, hp: number, vol: number) => {
    const src = ctx.createBufferSource();
    src.buffer = noiseBuffer;
    const filter = ctx.createBiquadFilter();
    filter.type = "highpass";
    filter.frequency.value = hp;
    const gain = ctx.createGain();
    gain.gain.setValueAtTime(vol, at);
    gain.gain.exponentialRampToValueAtTime(0.0001, at + dur);
    src.connect(filter).connect(gain).connect(master);
    src.start(at);
    src.stop(at + dur);
  };

  const snare = (at: number) => {
    noiseHit(at, 0.16, 1400, 0.5);
    const body = ctx.createOscillator();
    const bg = ctx.createGain();
    body.type = "triangle";
    body.frequency.setValueAtTime(220, at);
    bg.gain.setValueAtTime(0.3, at);
    bg.gain.exponentialRampToValueAtTime(0.0001, at + 0.1);
    body.connect(bg).connect(master);
    body.start(at);
    body.stop(at + 0.12);
  };

  const playDrums = (bars: number) => {
    // 4 beats per bar, 16th-note hats.
    for (let b = 0; b < bars * 4; b++) {
      const at = t + b * beat;
      const step = b % 4;
      if (step === 0 || step === 2) kick(at);
      if (step === 1 || step === 3) snare(at);
      if (step === 2) kick(at + beat * 0.5);
      noiseHit(at, 0.05, 7000, 0.14);
      noiseHit(at + beat * 0.5, 0.04, 7000, 0.09);
    }
    return t + bars * 4 * beat;
  };

  const timeouts: number[] = [];

  const scheduleLoop = () => {
    const endA = playTrack(MELODY, "square", 0.34);
    const endA2 = playTrack(MELODY, "square", 0.16, 9);
    const endB = playLiquidBass(BASS, 0.6);
    const endC = playTrack(ARP, "square", 0.1);
    const endD = playDrums(8);
    const loopEnd = Math.max(endA, endA2, endB, endC, endD);
    timeouts.push(
      window.setTimeout(scheduleLoop, (loopEnd - ctx.currentTime) * 1000 - 120),
    );
    t = loopEnd;
  };
  scheduleLoop();
  return () => {
    for (const id of timeouts) window.clearTimeout(id);
    master.disconnect();
    glue.disconnect();
  };
}

/* --- Scroll reveal --- */
function useReveal() {
  useEffect(() => {
    const els = Array.from(document.querySelectorAll<HTMLElement>(".reveal"));
    const io = new IntersectionObserver(
      (entries) => {
        for (const e of entries) {
          if (e.isIntersecting) {
            e.target.classList.add("is-visible");
            io.unobserve(e.target);
          }
        }
      },
      { threshold: 0.15 },
    );
    els.forEach((el) => io.observe(el));
    return () => io.disconnect();
  }, []);
}

const STATES = [
  { name: "IDLE", note: "Breathing, blinking, cape drifting. The default watch." },
  { name: "THINKING", note: "Head tilt and question particles while the model reasons." },
  { name: "WORKING", note: "Sword and shield in motion during a tool call." },
  { name: "WAITING", note: "Standing by for your approval before the next step." },
  { name: "LISTENING", note: "Audio rings pulse whenever the microphone opens." },
];

type Pet = {
  name: string;
  cls: string;
  img: string;
  tag: "OWNED" | "PREMIUM" | "SECRET";
  lore: string;
  accent: string;
};

const PETS: Array<Pet> = [
  {
    name: "0xPALADINO",
    cls: "Guardian class",
    img: paladinAsset,
    tag: "OWNED",
    lore: "The first tiny intelligence. Forged to stand between your data and everything else. Loyalty, intelligence, action, always with you.",
    accent: "royal",
  },
  {
    name: "MEWSASHI",
    cls: "Blade class",
    img: mewsashiAsset,
    tag: "PREMIUM",
    lore: "A one cut duelist that slices noise out of your inbox and your notifications. Fast, silent, never asks twice.",
    accent: "gold",
  },
  {
    name: "MONKAI",
    cls: "Monk class",
    img: monkaiAsset,
    tag: "PREMIUM",
    lore: "Keeper of focus. Bends your calendar into order, breathes through long tasks, and returns only when the work is done.",
    accent: "gold",
  },
  {
    name: "DARKTWIN",
    cls: "Unknown class",
    img: darktwinAsset,
    tag: "SECRET",
    lore: "Every paladin casts a shadow. Reach level 99 with 0xPaladino and something answers from the other side of the mirror.",
    accent: "cyan",
  },
];

function Index() {
  const [email, setEmail] = useState("");
  const [joined, setJoined] = useState(false);
  const [musicOn, setMusicOn] = useState(false);
  const [stateIdx, setStateIdx] = useState(0);
  
  const audioRef = useRef<{ ctx: AudioContext; stop: () => void } | null>(null);

  useReveal();

  useEffect(() => {
    const id = window.setInterval(() => setStateIdx((i) => (i + 1) % STATES.length), 2600);
    return () => window.clearInterval(id);
  }, []);

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
        className="pixel-border fixed bottom-4 right-4 z-50 border-4 border-black bg-card px-3 py-2 font-display text-[9px] text-gold transition-transform hover:-translate-y-1 active:translate-y-1 sm:bottom-6 sm:right-6 sm:px-4 sm:py-3 sm:text-[10px]"
      >
        {musicOn ? "\u266a MUSIC: ON" : "\u266a MUSIC: OFF"}
      </button>

      {/* Navigation */}
      <nav className="sticky top-0 z-40 border-b-4 border-black bg-navy/90 px-4 py-3 backdrop-blur-sm sm:px-6 sm:py-4">
        <div className="mx-auto grid max-w-7xl grid-cols-[minmax(0,1fr)_auto] items-center gap-3">
          <div className="flex min-w-0 items-center gap-2 sm:gap-3">
            <div className="pixel-border-gold grid size-7 shrink-0 place-items-center bg-royal sm:size-8">
              <div className="size-2 animate-[twinkle_1.6s_steps(2)_infinite] bg-cyan shadow-[0_0_8px_var(--color-cyan)]" />
            </div>
            <span className="truncate font-display text-base tracking-tighter text-gold sm:text-xl">PETOLOGIC</span>
          </div>
          <div className="hidden gap-8 font-mono text-xs uppercase tracking-widest text-white/60 lg:flex">
            <a href="#lore" className="transition-colors hover:text-cyan">Lore</a>
            <a href="#roster" className="transition-colors hover:text-cyan">Roster</a>
            <a href="#states" className="transition-colors hover:text-cyan">States</a>
            <a href="#showcase" className="transition-colors hover:text-cyan">Showcase</a>
            <a href="#modules" className="transition-colors hover:text-cyan">Modules</a>
          </div>
          <a
            href="#waitlist"
            className="pixel-border shrink-0 bg-gold px-3 py-2 font-display text-[10px] text-navy transition-transform hover:translate-y-1 sm:px-4 sm:text-xs"
          >
            ENLIST
          </a>
        </div>
        <div className="-mx-4 mt-3 flex gap-5 overflow-x-auto px-4 font-mono text-[10px] uppercase tracking-widest text-white/50 [scrollbar-width:none] lg:hidden">
          <a href="#lore" className="shrink-0">Lore</a>
          <a href="#roster" className="shrink-0">Roster</a>
          <a href="#states" className="shrink-0">States</a>
          <a href="#showcase" className="shrink-0">Showcase</a>
          <a href="#modules" className="shrink-0">Modules</a>
        </div>
      </nav>


      {/* Hero */}
      <header className="relative overflow-hidden px-4 pb-16 pt-10 sm:px-6 sm:pb-28 sm:pt-20">
        <div className="grid-bg pointer-events-none absolute inset-0 opacity-40 [mask-image:radial-gradient(ellipse_at_top,black,transparent_70%)]" aria-hidden="true" />
        <div className="relative mx-auto grid max-w-7xl items-center gap-10 md:grid-cols-2 md:gap-16">
          <div className="reveal">
            <div className="mb-5 inline-block border border-royal bg-royal/20 px-3 py-1 font-mono text-[9px] uppercase tracking-[0.2em] text-cyan sm:mb-6 sm:text-[10px]">
              Protocol initialized: v2.0.4 / Android first
            </div>
            <h1 className="mb-6 text-balance font-display text-[2rem] leading-[1.15] sm:text-5xl sm:leading-[1.1] lg:text-7xl">
              Tiny <span className="text-gold">intelligence</span> pets, living in your phone.
            </h1>
            <p className="mb-6 max-w-md text-base leading-relaxed text-white/70 sm:text-lg">
              Petologic is a tiny intelligence infrastructure built on top of Android. Start with 0xPaladino:
              local chat, private notes and an animated companion, inside your boundaries.
            </p>

            <ul className="mb-10 space-y-2 font-mono text-xs text-white/50">
              <li><span className="text-cyan">&gt;</span> Runs locally on Android, offline after setup</li>
              <li><span className="text-cyan">&gt;</span> Every action logged in your quest log</li>
              <li><span className="text-cyan">&gt;</span> Cloud models are a summon, never a leash</li>
            </ul>
            <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:gap-4">
              <a
                href={APK_URL}
                rel="noopener"
                className="pixel-border-gold group bg-royal px-6 py-4 text-center font-display text-xs tracking-wide transition-all hover:bg-royal/90 active:translate-y-1 sm:px-8 sm:text-sm"
              >
                DOWNLOAD APK{" "}
                <span className="ml-2 inline-block transition-transform group-hover:translate-x-1">&rarr;</span>
              </a>
              <a
                href="#roster"
                className="pixel-border bg-card px-6 py-4 text-center font-display text-xs text-cyan transition-all hover:bg-card/70 active:translate-y-1 sm:px-8 sm:text-sm"
              >
                MEET THE PETS
              </a>
            </div>

            <p className="mt-4 font-mono text-[10px] uppercase tracking-[0.2em] text-white/40">
              Android 12+ · ARM64 · 0.2.1 preview · local model downloads separately (219 MB)
            </p>
            <details className="mt-3 max-w-xl font-mono text-xs leading-relaxed text-white/60">
              <summary className="cursor-pointer text-cyan">Installation steps and checksum</summary>
              <ol className="mt-3 list-decimal space-y-2 pl-5">
                <li>Download the preview APK on your Android phone and open it.</li>
                <li>If Android asks, allow this browser to install apps, then confirm installation.</li>
                <li>Open Paladino and download or import LFM2.5-350M Q4_K_M in Settings (229 MB). Choose Tiny to chat locally.</li>
                <li>In Settings → Sprite and widget, enable the floating Sprite and grant display-over-apps access. Tap Paladino to chat or drag to move.</li>
                <li>On 0.1.6 or later? Just install over — conversations and settings stay. Downloads now auto-resume from where the connection dies instead of getting stuck near the end. On 0.1.5 or earlier, uninstall first, then install this build.</li>
              </ol>
              <p className="mt-3">Preview software. A developer debug build uses a different signature and cannot be updated with this APK; keep any important data before changing installations.</p>
              <p className="mt-3">Download stuck near the end? Retry — every attempt gets a fresh link, and Wi-Fi helps for the 96 MB APK and the 229 MB model.</p>
              <a className="mt-3 inline-block text-cyan underline" href={`${APK_RELEASE}/SHA256SUMS.txt`}>Verify SHA-256 checksum</a>
              <a className="mt-3 ml-4 inline-block text-cyan underline" href={`${APK_RELEASE}/INSTALL-ANDROID.md`}>Guia de instalação em português</a>
            </details>
          </div>


          <div className="relative flex items-center justify-center">
            <div className="pixel-border relative z-10 flex aspect-square w-full max-w-[420px] animate-[glow-pulse_5s_ease-in-out_infinite] items-center justify-center border-8 border-black bg-card p-8">
              <div className="dither-pattern absolute inset-0 opacity-20" aria-hidden="true" />
              <img
                src={paladinIdle}
                alt="0xPaladino, the Petologic pixel paladin pet, idling in royal blue and gold armour"
                className="relative z-10 h-full w-full animate-[float_4s_ease-in-out_infinite] object-contain [image-rendering:pixelated]"
              />
              <div className="absolute -top-2 -right-2 bg-cyan px-2 font-mono text-[10px] font-bold text-navy">
                LVL 99
              </div>
              <div className="absolute -bottom-2 -left-2 size-6 border-4 border-gold bg-navy" aria-hidden="true" />
              <div className="absolute bottom-3 left-1/2 z-20 -translate-x-1/2 whitespace-nowrap border-2 border-black bg-navy/90 px-3 py-1 font-mono text-[9px] tracking-widest text-gold">
                0xPALADINO / {STATES[stateIdx]?.name}
              </div>
            </div>
            <div className="absolute inset-0 -z-10 scale-75 rounded-full bg-royal/25 blur-[110px]" aria-hidden="true" />
          </div>
        </div>
      </header>

      {/* Marquee */}
      <div className="overflow-hidden border-y-4 border-black bg-royal/20 py-3">
        <div className="flex w-max animate-[marquee_28s_linear_infinite] gap-10 font-mono text-[10px] uppercase tracking-[0.3em] text-cyan/70">
          {Array.from({ length: 2 }).map((_, dup) => (
            <span key={dup} className="flex gap-10">
              <span>Same soul, infinite context</span>
              <span>Small changes, big expressions</span>
              <span>Built for Android</span>
              <span>Private by design</span>
              <span>Loyalty / Intelligence / Action</span>
              <span>One paladino, many moments</span>
            </span>
          ))}
        </div>
      </div>

      {/* Lore */}
      <section id="lore" className="relative px-4 py-16 sm:px-6 sm:py-24">
        <div className="mx-auto max-w-4xl text-center">
          <div className="reveal mb-6 inline-block border border-gold bg-gold/10 px-3 py-1 font-mono text-[10px] uppercase tracking-[0.2em] text-gold">
            Codex entry 001
          </div>
          <h2 className="reveal mb-8 font-display text-3xl sm:text-4xl leading-tight">
            The phone became a <span className="text-cyan">kingdom</span>.
          </h2>
          <div className="reveal pixel-border mx-auto max-w-3xl border-4 border-black bg-card p-8 text-left">
            <p className="mb-4 leading-relaxed text-white/70">
              Every app wanted a gate, a key, a copy of you. So Petologic opened a different door: a
              tiny intelligence layer that lives on the device itself, where the model is small, the
              memory is yours, and nothing leaves without permission.
            </p>
            <p className="mb-4 leading-relaxed text-white/70">
              The first pet through that door was 0xPaladino. A guardian written in eight hundred
              kilobytes of stubbornness, sworn to a single traveller. He does not sleep. He blinks,
              breathes, and waits for a quest.
            </p>
            <p className="leading-relaxed text-white/70">
              Others followed. A blade. A monk. And something wearing the same armour in the wrong
              colours. Raise them, train them, and they learn how you like things done.
            </p>
          </div>
        </div>
      </section>

      {/* Roster */}
      <section id="roster" className="border-y-4 border-black bg-black/30 px-4 py-16 sm:px-6 sm:py-24">
        <div className="mx-auto max-w-7xl">
          <div className="reveal mb-4 text-center font-mono text-[10px] uppercase tracking-[0.3em] text-cyan">
            Party slots: 1 of 4 unlocked
          </div>
          <h2 className="reveal mb-10 text-center font-display text-3xl sm:mb-14 sm:text-4xl">THE ROSTER</h2>
          <div className="grid gap-5 sm:grid-cols-2 sm:gap-8 lg:grid-cols-4">
            {PETS.map((pet) => {
              const locked = pet.tag !== "OWNED";
              return (
                <article
                  key={pet.name}
                  className="reveal pixel-border group relative flex flex-col border-4 border-black bg-card p-4 transition-transform duration-200 hover:-translate-y-2 sm:p-5"
                >
                  <div className="relative mb-4 grid aspect-square place-items-center overflow-hidden border-2 border-black bg-navy">
                    <div className="dither-pattern absolute inset-0 opacity-15" aria-hidden="true" />
                    <img
                      src={pet.img}
                      alt={locked ? "Locked Petologic pet silhouette" : `${pet.name}, a ${pet.cls} Petologic pet`}
                      className={`relative z-10 h-4/5 w-4/5 animate-[bob_3s_ease-in-out_infinite] object-contain [image-rendering:pixelated] ${
                        locked ? "opacity-50 brightness-0" : ""
                      }`}
                    />
                    {locked ? (
                      <span className="absolute z-20 font-display text-xs text-gold">? ? ?</span>
                    ) : null}
                    <span
                      className={`absolute top-1 right-1 z-20 px-1.5 py-0.5 font-mono text-[8px] font-bold ${
                        pet.tag === "OWNED"
                          ? "bg-cyan text-navy"
                          : pet.tag === "PREMIUM"
                            ? "bg-gold text-navy"
                            : "bg-royal text-white"
                      }`}
                    >
                      {pet.tag === "OWNED" ? "OWNED" : pet.tag === "PREMIUM" ? "LOCKED / PREMIUM" : "LOCKED / SECRET"}
                    </span>
                  </div>
                  <h3 className="font-display text-sm text-gold">{locked ? "???????" : pet.name}</h3>
                  <div className="mb-3 font-mono text-[9px] uppercase tracking-widest text-white/40">
                    {locked ? "Unknown class" : pet.cls}
                  </div>
                  <p
                    className={`text-xs leading-relaxed text-white/60 ${
                      locked ? "select-none blur-[4px]" : ""
                    }`}
                    aria-hidden={locked ? true : undefined}
                  >
                    {pet.lore}
                  </p>
                  {locked ? (
                    <div className="mt-3 font-mono text-[9px] uppercase tracking-widest text-cyan/70">
                      Record sealed
                    </div>
                  ) : null}
                  <div className="mt-4 flex gap-1" aria-hidden="true">
                    <div className={`h-1 w-full bg-${pet.accent}`} />
                    <div className={`h-1 w-1/3 bg-${pet.accent}/30`} />
                  </div>
                </article>
              );
            })}
          </div>
          <p className="reveal mt-10 text-center font-mono text-[10px] uppercase tracking-widest text-white/30">
            Three records still sealed. They open in a later season.
          </p>
        </div>
      </section>


      {/* Animation states */}
      <section id="states" className="px-4 py-16 sm:px-6 sm:py-24">
        <div className="mx-auto max-w-7xl">
          <div className="reveal mb-4 text-center font-mono text-[10px] uppercase tracking-[0.3em] text-gold">
            Core animation set 1 of 5
          </div>
          <h2 className="reveal mb-12 text-center font-display text-3xl sm:text-4xl">
            SMALL CHANGES. <span className="text-cyan">BIG EXPRESSIONS.</span>
          </h2>
          <div className="grid grid-cols-2 gap-4 sm:gap-6 md:grid-cols-5">
            {STATES.map((s, i) => (
              <button
                key={s.name}
                type="button"
                onMouseEnter={() => setStateIdx(i)}
                onClick={() => setStateIdx(i)}
                className={`reveal pixel-border border-4 border-black bg-card p-4 text-left transition-transform hover:-translate-y-1 ${
                  stateIdx === i ? "ring-2 ring-cyan" : ""
                }`}
              >
                <div className="relative mb-3 grid aspect-square place-items-center border-2 border-black bg-navy">
                  <div className="dither-pattern absolute inset-0 opacity-10" aria-hidden="true" />
                  <img
                    src={paladinIdle}
                    alt={`0xPaladino in the ${s.name.toLowerCase()} state`}
                    className="relative z-10 h-4/5 w-4/5 object-contain [image-rendering:pixelated]"
                  />
                  <span className="absolute bottom-1 right-1 z-20 font-mono text-[8px] text-cyan">
                    0{i + 1}
                  </span>
                </div>
                <div className="mb-1 font-display text-[11px] text-gold">{s.name}</div>
                <p className="font-mono text-[9px] leading-relaxed text-white/50">{s.note}</p>
              </button>
            ))}
          </div>
          <div className="reveal mx-auto mt-10 max-w-xl text-center font-mono text-[10px] uppercase tracking-widest text-white/35">
            8 frames / 0.6s loop / 256 by 256 px / png transparent
          </div>
        </div>
      </section>

      {/* Phone showcase */}
      <section id="showcase" className="border-y-4 border-black bg-black/30 px-4 py-16 sm:px-6 sm:py-24">
        <div className="mx-auto grid max-w-7xl items-center gap-16 md:grid-cols-2">
          <div className="reveal flex items-center justify-center">
            <div className="pixel-border relative w-full max-w-[300px] animate-[float_6s_ease-in-out_infinite] border-8 border-black bg-card p-3">
              <div className="mx-auto mb-3 h-4 w-24 border-4 border-black bg-navy" aria-hidden="true" />
              <div className="relative overflow-hidden border-4 border-black bg-navy p-4">
                <div className="dither-pattern absolute inset-0 opacity-10" aria-hidden="true" />
                <div className="relative z-10">
                  <div className="mb-1 flex items-center justify-between font-mono text-[8px] text-white/40">
                    <span>PETOLOGIC OS</span>
                    <span className="text-cyan">&#9679; ONLINE</span>
                  </div>
                  <div className="mb-3 border-2 border-royal bg-royal/10 p-3 text-center">
                    <img
                      src={paladinIdle}
                      alt="0xPaladino idling on the phone home screen"
                      className="mx-auto mb-2 size-24 object-contain [image-rendering:pixelated]"
                    />
                    <div className="font-display text-[10px] text-gold">0xPALADINO</div>
                    <div className="font-mono text-[8px] text-white/50">GUARDIAN CLASS / LVL 99</div>
                  </div>
                  <div className="mb-3 space-y-2">
                    {[
                      { l: "HP", v: "980/980", w: "w-full", c: "bg-cyan" },
                      { l: "MANA", v: "640/800", w: "w-4/5", c: "bg-royal" },
                      { l: "TRUST", v: "MAX", w: "w-full", c: "bg-gold" },
                    ].map((b) => (
                      <div key={b.l}>
                        <div className="mb-1 flex justify-between font-mono text-[8px] text-white/60">
                          <span>{b.l}</span>
                          <span>{b.v}</span>
                        </div>
                        <div className="h-2 border border-black bg-navy">
                          <div className={`h-full ${b.w} ${b.c}`} />
                        </div>
                      </div>
                    ))}
                  </div>
                  <div className="border-2 border-gold/40 bg-card p-2">
                    <div className="mb-1 font-display text-[8px] text-gold">QUEST LOG</div>
                    <ul className="space-y-1 font-mono text-[8px] text-white/60">
                      <li><span className="text-cyan">&#10003;</span> Silence spam notifications</li>
                      <li><span className="text-cyan">&#10003;</span> Summarize 42 unread chats</li>
                      <li><span className="animate-pulse text-gold">&#9656;</span> Guarding your data...</li>
                    </ul>
                  </div>
                </div>
              </div>
              <div className="mx-auto mt-3 h-1 w-16 bg-white/20" aria-hidden="true" />
            </div>
          </div>

          <div>
            <div className="reveal mb-6 inline-block border border-gold bg-gold/10 px-3 py-1 font-mono text-[10px] uppercase tracking-[0.2em] text-gold">
              Field manual
            </div>
            <h2 className="reveal mb-10 font-display text-3xl sm:text-4xl leading-tight">
              One phone. <span className="text-cyan">Many pets.</span>
            </h2>
            <ul className="space-y-8">
              {[
                ["SPAWN SPECIALIZED PETS", "0xPaladino is the only launch pet. Future roles will share the same local model rather than download a separate brain."],
                ["SET THE BOUNDARIES", "You draw the fence. Pets act only inside the permissions you grant, and every action lands in the quest log."],
                ["RUNS ON YOUR HARDWARE", "Native to Android. Tiny runs locally on your phone after model setup, even when the signal drops."],
                ["LEVEL THEM UP", "Start with conversations and private notes. More skills and roles are planned; this preview does not train model weights."],
              ].map(([title, body], i) => (
                <li key={title} className="reveal flex gap-4">
                  <div className="pixel-border mt-1 grid size-10 shrink-0 place-items-center bg-royal font-display text-xs">
                    0{i + 1}
                  </div>
                  <div>
                    <h3 className="mb-1 font-display text-sm text-gold">{title}</h3>
                    <p className="text-sm leading-relaxed text-white/60">{body}</p>
                  </div>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </section>

      {/* Feature grid */}
      <section id="modules" className="border-b-4 border-black bg-navy px-4 py-16 sm:px-6 sm:py-24">
        <div className="mx-auto grid max-w-7xl gap-8 md:grid-cols-3">
          {[
            { n: "01", t: "LOCAL BRAIN", c: "royal", b: "LFM2.5-350M runs locally in Tiny after setup. Cloud requests require your review and a connected provider." },
            { n: "02", t: "TOOL USE", c: "gold", b: "Paladino can search private notes and propose saving a note for your approval. More tools are planned." },
            { n: "03", t: "CLOUD RELAY", c: "cyan", b: "Connect OpenRouter, OpenAI or Z.ai with your API key for Maxx text replies. Browser sign-in is still planned." },
          ].map((m) => (
            <div
              key={m.n}
              className={`reveal group relative border-t-4 bg-card p-8 transition-transform duration-200 hover:-translate-y-2 border-${m.c}`}
            >
              <div className={`mb-4 font-mono text-[10px] tracking-tighter text-${m.c}`}>
                [ MODULE_{m.n} ]
              </div>
              <h3 className="mb-4 font-display text-xl transition-colors group-hover:text-cyan">{m.t}</h3>
              <p className="mb-6 text-sm leading-relaxed text-white/50">{m.b}</p>
              <div className="flex gap-1" aria-hidden="true">
                <div className={`h-1 w-full bg-${m.c}`} />
                <div className={`h-1 w-1/2 bg-${m.c}/30`} />
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* Bottom CTA */}
      <section id="waitlist" className="relative px-4 py-20 text-center sm:px-6 sm:py-32">
        <div
          className="absolute top-0 left-1/2 h-24 w-px -translate-x-1/2 bg-gradient-to-b from-royal to-transparent"
          aria-hidden="true"
        />
        <div className="mx-auto max-w-2xl">
          <div className="reveal mb-8 animate-pulse font-display text-xs uppercase tracking-widest text-cyan">
            Waiting for command...
          </div>
          <h2 className="reveal mb-12 font-display text-3xl sm:text-4xl">
            Ready to raise your <span className="text-gold">tiny intelligence</span>?
          </h2>
          {joined ? (
            <div className="pixel-border inline-block border-4 border-black bg-card px-8 py-4 font-display text-sm text-cyan">
              COMMAND RECEIVED. WELCOME, TRAVELER.
            </div>
          ) : (
            <form
              className="pixel-border inline-flex flex-wrap justify-center border-4 border-black bg-card p-1"
              onSubmit={(e) => {
                e.preventDefault();
                if (email.trim()) setJoined(true);
              }}
            >
              <label htmlFor="waitlist-email" className="sr-only">Email address</label>
              <input
                id="waitlist-email"
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="ENTER EMAIL"
                className="w-full min-w-0 border-none bg-navy px-4 py-4 sm:w-64 font-mono text-sm outline-none focus:ring-2 focus:ring-cyan md:w-80"
              />
              <button
                type="submit"
                className="bg-royal px-8 py-4 font-display text-sm transition-colors hover:bg-cyan hover:text-navy active:translate-y-1"
              >
                JOIN WAITLIST
              </button>
            </form>
          )}
          <div className="mt-12 flex flex-wrap justify-center gap-6 opacity-40 grayscale">
            {["OPENROUTER", "ANTHROPIC", "OPENAI", "GEMINI"].map((p) => (
              <span key={p} className="font-mono text-[10px]">{p}</span>
            ))}
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t-4 border-black bg-navy px-6 py-12">
        <div className="mx-auto flex max-w-7xl flex-col items-center justify-between gap-8 md:flex-row">
          <div className="font-mono text-[10px] text-white/30">
            &copy; 2026 PETOLOGIC. BUILT FOR ANDROID. PRIVATE BY DESIGN.
          </div>
          <div className="flex gap-8">
            {["TWITTER", "DISCORD", "GITHUB"].map((s) => (
              <a key={s} href="#" className="font-mono text-[10px] text-white/50 hover:text-cyan">{s}</a>
            ))}
          </div>
        </div>
      </footer>
    </div>
  );
}
