# Decision Core V1

## Goal

Petologic should feel like one intelligent Android app, not an APK that launches a pile of unrelated engines.

The decision layer chooses the smallest safe execution path while capability code, permissions and approval policy remain authoritative.

## Runtime cascade

```text
request
  |
  v
System 0 deterministic routing
  | obvious
  +----> direct / 230M chat / 350M tool-router
  |
  | ambiguous bounded decision
  v
Liquid d1 (optional hosted provider)
  |
  | confidence >= 0.65
  +----> 230M / 350M / cloud
  |
  v
350M local fallback
```

## Model jobs

- **LFM2.5-230M QAD Q4**: short chat, acknowledgement, compact explanation and other cheap generative turns.
- **LFM2.5-350M QAD Q4**: ambiguous language understanding, argument extraction, tool routing, structured local reasoning and offline fallback.
- **Liquid d1**: bounded Choice/Noul/Score decisions only. It never writes the answer and never grants permission to run a tool.
- **Cloud LLM**: difficult reasoning/research after the app's existing cloud consent flow.

## d1 privacy rule

The hosted d1 provider is disabled unless all are true:

1. network is enabled for the session;
2. the user explicitly enabled remote decision routing;
3. a Liquid API key is available.

No user text is sent to d1 merely because the class exists.

## Why keep 350M

d1 has no downloadable weights yet. Petologic must continue to work offline. The 350M path therefore remains the authoritative fallback until a local d1 artifact exists and wins mobile benchmarks.

## Future local d1 swap

```text
DecisionProvider
  |- LocalDecisionProvider
  |- LiquidD1DecisionProvider
  `- LiquidD1LocalProvider   <- future
```

No SessionController or capability contract should need to change when this provider arrives.

## Benchmark gates

Before making d1 the default decision provider measure:

- route accuracy on Petologic's own intent/tool dataset;
- calibration and abstention quality;
- p50/p95 decision latency;
- bytes sent per turn;
- battery/network cost;
- prompt-injection resistance;
- 230M vs 350M wrong-route rate;
- d1 failure/offline fallback behavior.

The decision model is advisory. Tool registration, typed argument validation, permissions and explicit approval remain release-blocking security boundaries.
