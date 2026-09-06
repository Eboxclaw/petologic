Continue with the current direction, but **do not push anything to the main GitHub branch until the implementation has been verified and we have worked through the intended App / Widget UX architecture.**

## 1. Product architecture: Website → App → Sprite

I want us to think about Paladino as three related surfaces:

**Website**

* Landing page
* Documentation
* Downloads / onboarding
* Product explanation
* Account-related information where appropriate

**App**

* The full IDE / control center for Paladino.
* Model management
* Agent/session management
* Console
* Logs
* Authentication
* Permissions
* Memory
* Skills
* Tools
* MCPs
* Rules
* Agent configuration
* Advanced model settings
* Debugging / diagnostics

**Sprite Widget**

* This is ultimately the main everyday product experience.
* The sprite/pet lives outside or on top of the main app UI where Android permits it.
* It should expose a lightweight conversational and status interface without requiring the user to constantly open the full IDE.
* The full app remains the place where complicated configuration and orchestration happens.

Think of the **App as the IDE/control plane** and the **Sprite as the user-facing agent interface**.

Do not force all configuration into the Sprite.

---

# 2. UI / UX work before merging

Before merging architecture changes into main, work toward this App/Widget structure.

The current app UI can remain the primary application surface while we progressively introduce the Sprite widget.

Investigate how the Android implementation should separate:

* main application
* foreground/background agent services
* Sprite / overlay / widget
* notifications
* persistent agent state
* model runtime
* permissions
* session state

We need a clean boundary so that closing the visible app does not necessarily mean destroying the entire Paladino runtime if the user has explicitly enabled background operation.

Do not hack this together only for the prototype. Design the boundaries so the Sprite can become the main interaction layer later.

---

# 3. Multi-session architecture

Multi-session handling needs to become significantly better.

Research and implement a proper session model covering:

* multiple simultaneous conversations
* separate context per session
* independent agent state
* session persistence
* session restore
* session archival
* session context limits
* context compression / summarization
* session branching if practical
* identifying which model is attached to each session
* identifying which tools/skills are enabled for each session
* token usage per session
* context-window consumption
* tool-call history
* agent-hop history

Sessions should not accidentally contaminate each other's context.

We should eventually be able to have different sprites/agents operating with different sessions while still sharing explicitly configured global resources where appropriate.

---

# 4. Per-session capabilities and controls

Add or design controls that can be enabled/disabled per agent or session.

At minimum investigate:

* Web Search
* Tool Calling
* MCP access
* Local file access
* App access
* Memory read
* Memory write
* Skills
* Rules
* Agent instructions
* Vision
* Thinking / reasoning mode
* Background operation
* Network access
* Notifications

These should not simply be global booleans if there is a better permission model.

Think about:

**global defaults → agent permissions → session overrides**

where that hierarchy makes sense.

---

# 5. Multi-turn agent loops, hops and tool calls

This is important.

Research current best practices for implementing proper multi-turn agent execution in Koog and whatever runtime architecture we currently use.

We need to support flows such as:

1. User asks something.
2. Model reasons about the request.
3. Model selects a tool.
4. Tool executes.
5. Result returns to the model.
6. Model determines whether another tool/hop is required.
7. Additional tools execute if necessary.
8. Model produces the final answer.

Do not assume one model call = one interaction.

Investigate and properly support:

* multi-turn tool loops
* agent hops
* maximum hops
* maximum tool calls
* repeated tool protection
* timeouts
* cancellation
* retry logic
* malformed tool-call handling
* tool-result injection
* context growth during loops
* context compression during long loops
* token budgets
* loop detection
* user interruption
* resuming an interrupted execution
* execution logs
* deterministic state where practical

Research how Koog handles these concepts natively before reinventing them.

Where Koog already provides a robust primitive, use it.

Where it does not, document what Paladino needs to implement itself.

---

# 6. Model strategy

Our target device is roughly:

**Phone with at least 8 GB RAM, with approximately 3–4 GB realistically available for Paladino + local inference.**

Do not design around desktop-class memory.

## Primary local model target

The model I am particularly interested in testing is the **LFM 2.5 2.6B family**.

Potential targets include:

* LFM 2.5 2.6B VL — 32K context
* LFM 2.5 2.6B text — ~128K context where supported
* Q4_K_M
* Liquid's optimized/special Q4/QAD variant if available

**Verify the exact model/quantization names and capabilities rather than assuming them.**

The 2.6B model is heavier than our tiny models, but still below 3B parameters. Phones already run models in the Gemma/Qwen class around this size.

I want Paladino to test whether LFM 2.5 2.6B can become a serious high-quality local model option rather than assuming it is too large.

Measure it.

Do not guess.

Test:

* load time
* peak memory
* sustained memory
* KV-cache growth
* prompt processing speed
* generation speed
* thermal behavior where measurable
* crashes/OOM
* context-size impact
* Q4 variants
* practical context size on an 8 GB phone
* WebGPU / native / CPU acceleration depending on the architecture

---

# 7. Secondary vision model

Also consider **LFM 2.5 450M VL** as a secondary lightweight vision model.

This could potentially be loaded only when vision is required instead of forcing the larger VL model to stay resident.

Investigate a modular setup such as:

**2.6B text/reasoning model**
+
**450M VL visual perception model**

versus:

**single 2.6B VL model**

Compare:

* total RAM
* model switching cost
* latency
* vision quality
* context handling
* battery usage
* complexity
* whether models can coexist in memory
* whether unloading/reloading becomes disruptive

Do not decide yet which architecture is correct. Benchmark first.

Thinking/reasoning-specific models can be evaluated later.

---

# 8. Advanced model settings

We need an **Advanced Model Settings** area.

Normal users should not need this, but developers/power users need direct control over inference parameters.

Investigate exposing:

### Context

* maximum session context
* context compression threshold
* hard context limit
* minimum context reserved for response
* minimum context reserved for tool results
* system prompt budget
* memory budget
* tool-history budget

### Generation

* maximum output tokens
* thinking/reasoning token budget
* temperature
* top-p
* top-k where supported
* repeat penalty
* seed where supported
* stop sequences

### Agent execution

* maximum hops
* maximum tool calls
* maximum retries
* timeout per hop
* total execution timeout
* parallel tool calls where supported

### Runtime / memory

* KV-cache format
* KV-cache quantization
* KV-cache size
* GPU layers/offload
* threads
* batch size
* micro-batch size
* mmap
* mlock where relevant
* flash attention
* K/Q/V offload
* context size
* RoPE settings
* RoPE scaling
* model-specific performance flags

Only expose settings actually supported by the runtime.

The UI should read runtime/model capabilities rather than showing meaningless controls.

Provide:

**Safe**

* sane automatic defaults

**Advanced**

* manual overrides

**Developer**

* experimental/runtime-specific controls if needed

We should also be able to reset everything to model/runtime defaults.

---

# 9. Context-limit behavior

A session must not simply crash when context is exhausted.

Define explicit behavior.

For example:

**Normal operation**
→ context approaches configured threshold
→ trigger compression / summarization
→ preserve system instructions, rules, important memory and unresolved task state
→ continue

If compression cannot recover enough context:

→ warn the orchestration layer
→ gracefully stop or request a new session

There should be configurable values for:

* compression trigger %
* emergency compression %
* maximum context
* reserved generation tokens
* reserved tool tokens

Record compression events in the session log so debugging does not become impossible.

---

# 10. Orchestration tab

Memory, Tools, Skills, Rules and Agent configuration should live together under a more dedicated **Orchestration** area rather than being scattered throughout the app.

Potential structure:

**Orchestration**

* Agents
* Memory
* Skills
* Tools
* MCPs
* Rules
* Agent.md / system instructions
* Web Search
* Permissions
* Integrations

Use submenus rather than placing everything on one giant settings page.

The intention is that users can understand:

**What can this agent know?**
Memory

**What can this agent do?**
Tools / MCPs

**How should this agent behave?**
Rules / Agent instructions

**What specialized abilities does it have?**
Skills

**What is it allowed to access?**
Permissions

---

# 11. Cloud model providers

Cloud models should eventually support:

* OpenAI
* Anthropic / Claude
* Google Gemini

Investigate both:

### User OAuth / account authentication

Ideally:

Paladino
→ opens system browser
→ user authenticates/authorizes
→ redirect/deep-link back into Paladino
→ credential stored securely

### API key

Advanced/manual option where the provider permits it.

Do **not** assume that consumer ChatGPT/Claude/Gemini OAuth automatically gives third-party API access.

Research the actual supported authentication flows and provider terms before implementing anything.

The UI can present providers consistently even if the authentication mechanisms differ.

Credentials must use Android secure storage / KeyStore or the appropriate equivalent. Never store provider tokens or API keys in plaintext configuration files.

---

# 12. Permissions

We need a real permission system for the agent.

A user should explicitly be able to grant/revoke access to capabilities such as:

* selected files
* folders/directories where Android permits it
* photos/media
* camera
* microphone
* notifications
* contacts
* calendar
* location
* network
* supported application integrations
* background execution
* foreground service
* accessibility APIs only if truly justified
* overlay/draw-over-other-apps for Sprite behavior where required

Permissions should be transparent.

The user should be able to see something like:

**Paladino can currently access**

* Calendar ✓
* Notifications ✓
* Photos ✕
* Files: selected folder only
* Location: while using app
* Background agent: enabled

Avoid requesting broad permissions unnecessarily.

---

# 13. "Full device access"

I also want you to investigate the closest legitimate Android equivalent to a:

**"Give Paladino full device access"**

mode, similar conceptually to software such as antivirus/security applications that users intentionally authorize to operate more deeply across foreground/background activity.

However:

**Do not invent a nonexistent Android super-permission.**

Research what is actually possible through:

* runtime permissions
* Storage Access Framework
* MediaStore
* notification access
* usage access
* accessibility services
* VPNService
* foreground services
* overlay permission
* device administration / DevicePolicyManager where relevant
* app integrations
* intents/content providers
* Android sandbox restrictions

Clearly distinguish:

1. what normal Android applications can do;
2. what requires special user-granted permissions;
3. what requires Accessibility or VPN service;
4. what only Device Owner / enterprise-managed apps can do;
5. what requires root;
6. what Google Play policies may restrict.

The goal is **maximum useful agent capability with explicit user consent**, not bypassing Android security.

---

# 14. Background + foreground agent runtime

Research what Paladino needs for agents that may continue doing useful work while the user is not actively looking at the main application.

Investigate:

* Foreground Services
* WorkManager
* Android lifecycle restrictions
* Doze
* battery optimization
* notifications
* process death
* checkpointing
* execution restoration
* scheduled tasks
* event-driven tasks
* model residency versus unloading
* Sprite lifecycle

Do not assume a local LLM can simply run indefinitely in the background.

We need Android-correct behavior.

---

# 15. Learn from ZeroClaw Labs

Research ZeroClaw Labs' Rust architecture, especially if they have useful implementations around:

* multiple agents
* agent isolation
* orchestration
* task queues
* permissions
* state management
* tool execution
* message routing
* concurrency
* process lifecycle
* memory
* logging
* recovery

Do not copy architecture blindly.

Identify individual patterns Paladino could reuse or adapt.

Produce a short comparison:

**ZeroClaw pattern**
→ **what problem it solves**
→ **whether Paladino has the same problem**
→ **portable to Kotlin/Koog?**
→ **use / adapt / reject**

---

# 16. Koog evaluation

I am not yet committed to Koog.

We are learning it while building this.

Evaluate it based on evidence rather than treating the framework choice as settled.

Specifically investigate:

* startup overhead
* runtime overhead
* memory consumption
* coroutine/concurrency model
* agent loops
* tool calling
* multi-agent support
* persistence
* Android support
* background services
* streaming
* cancellation
* observability
* MCP integration
* model-provider flexibility
* local-model integration
* extensibility

Later we can compare Koog against a Rust/Tauri-style architecture or native Rust core.

Do not rewrite the project in Rust now.

Just keep architectural boundaries clean enough that the inference/orchestration core is not unnecessarily locked to UI code.

---

# 17. Architecture principle

Keep these layers reasonably separate:

```text
Sprite / UI
       ↓
Session + Agent Controller
       ↓
Orchestration Runtime
 ┌─────┼─────┐
Memory Tools Skills
       ↓
Model Router
 ┌─────┴──────────────┐
Local Models     Cloud Models
       ↓
Runtime / Hardware Layer
CPU / GPU / NPU / OS APIs
```

Do not make the Sprite responsible for orchestration.

Do not make the model runtime responsible for UI state.

Do not make session state dependent on a single visible Activity.

---

# 18. Logging and developer console

Because the App is also effectively the Paladino IDE, we need useful observability.

Log:

* session creation
* model loads/unloads
* context usage
* memory retrieval
* compression
* prompts where privacy settings allow it
* agent hops
* tool calls
* tool results
* permission failures
* model errors
* inference timing
* memory usage
* cancellations
* retries

Logs should make it possible to understand:

**why did this agent do this?**

without exposing secrets/API keys.

---

# 19. Do not push blindly

Before pushing major changes to main:

1. Inspect the existing architecture.
2. Research Koog capabilities.
3. Research Android restrictions/APIs.
4. Identify which parts already exist.
5. Avoid duplicating working functionality.
6. Implement incrementally.
7. Run tests.
8. Build the Android app.
9. Verify session persistence.
10. Verify tool loops.
11. Verify model loading.
12. Verify permission behavior.
13. Verify the App/Sprite separation.
14. Document important architectural decisions.

Use a feature branch until this is stable enough for main.

---

# 20. Deliverables

For this iteration, I want:

### A. Architecture review

Explain the recommended:

**Website → App/IDE → Sprite**

architecture.

### B. Current-code audit

Identify what already exists and what needs modification.

### C. Koog research

Determine which orchestration/session/tool-loop functionality Koog already gives us.

### D. Android capability/permission research

Document what Paladino can realistically access and under which permissions.

### E. Multi-session design

Create the session/context architecture.

### F. Agent execution loop

Implement or properly wire:

```text
prompt
→ reasoning
→ tool
→ observation
→ additional hop/tool if necessary
→ final response
```

with configurable limits.

### G. Advanced model settings design

Expose the important runtime, context and agent parameters without overwhelming normal users.

### H. Model experimentation plan

Prepare testing for:

* LFM 2.5 2.6B
* LFM 2.5 2.6B VL
* LFM 2.5 450M VL
* relevant Q4 variants

on our **8 GB RAM / ~3–4 GB available memory** target.

### I. ZeroClaw comparison

Extract relevant architectural patterns rather than simply describing the project.

### J. Verification

Only recommend merging once the build, session lifecycle, agent loops, permissions, model runtime and major UI paths have been tested.

---

## Guiding product idea

The **website explains Paladino**.

The **app configures and controls Paladino**.

The **Sprite is Paladino** from the user's everyday perspective.

Design every architectural decision with that end state in mind.
