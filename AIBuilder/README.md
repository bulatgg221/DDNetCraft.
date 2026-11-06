# AI Builder Advanced Building 0.7.0 — Fabric 1.21.1

**Minecraft Java 1.21.1 + Fabric Loader + Fabric API + Java 21.** AI Builder
places real blocks in a local **singleplayer Creative Mode** world.
Make a backup before construction. The mod has no dedicated-server support.

## Quick start
1. Remove every old `aibuilder-fabric-0.x.jar` from the active Minecraft profile's `mods` folder.
2. Install the **0.7.0** JAR, Fabric API and restart Minecraft.
3. Load a singleplayer world in Creative Mode.
4. Press **F8** or enter `/ai key` to configure Polza AI/OpenAI, model and API key.
5. Press **F7** or enter `/ai prompt` for the **multiline 12,000-character prompt editor**.
6. Paste a detailed description of the house/castle/spawn and press *Построить по промпту*.
7. Watch `/ai status`; use `/ai undo` to reverse last completed build.

No API key needed to test the building engine:
- `/ai demo`: original medieval spawn
- **`/ai house`: detailed two-storey demo house** with actual working doors,
  roof stairs, windows, furniture, beds and staircase

## Supported advanced blocks and shapes
In addition to boxes/spheres/cylinders/arches/lines/trees:
- Two-block **working doors** (oak/spruce/dark oak/birch/iron/cherry/crimson/warped):
  both halves and facing/hinge states
- Real two-block **beds** with facing and foot/head parts
- Facing **stairs**, gabled stair roofs, staircases, slabs (bottom/top), trapdoors
- Fences, fence gates, panes, lanterns, bookshelves, carpets and flower decorations
- Procedural **tables** and **chairs** for furnishing rooms
- Building stages: **foundation → walls → roof → openings → details → interior → landscape**
- World modifications are bounded and batched by game ticks. You can cancel and undo.

### Example prompt

> Design a realistic 19×15 two-storey Tudor house with genuine spruce doors,
> a walkable staircase, a dark oak stair-block roof, windows made of glass panes,
> a furnished living room with table and chairs, two beds upstairs, lanterns,
> garden fence, front path, trees and flowerbeds. No solid filled rooms.
> Keep openings clear. Make the floors traversable.

For shorter requests, `/ai build <description>` still works. Prompts longer
than standard chat command lengths should be entered through the **F7 GUI**.

## Polza AI settings (optional third-party API)
- Provider: **Polza AI**
- Model ID: `openai/gpt-4.1-mini`
- Endpoint (built into the mod): `https://polza.ai/api/v1/chat/completions`
- A separate Polza AI key is required; it is saved locally in
  `config/aibuilder/polza-key.txt`. OpenAI keys are saved separately.
- API usage charges are separate from ChatGPT subscriptions.
- Do not paste any API key into Minecraft chat or share it with anyone.

## Limitations
- Models generate *plans*, not verified in-game architectural masterpieces.
  A malformed/invalid JSON plan is safely rejected without changing the world.
- This update compiles under Gradle; real in-game functional testing is still required.
- Some decorative blocks depend on adjacency/support and may be replaced by vanilla
  neighbor updates. Bed/door support, clearance and materials matter.
- No chunk persistence of build queues, no undo across restarts, no native structure files.
- 100 max operations by default, maximum 30,000 blocks and configurable spatial bounds.
- Using a modded Minecraft launcher (especially LabyMod) can alter keybindings/UI.
  When in doubt test a clean Fabric profile.

## Build
GitHub Actions → **Build AI Builder Fabric** on branch `ai-builder` → Artifacts
→ **AIBuilder-Advanced-1.21.1**. Download ZIP and extract the JAR.

## New in 0.7.0: correcting invalid material IDs
LLM output may invent names such as `light_oak_planks` or
`pale_oak_planks`, which are not available in Minecraft 1.21.1.
The interpreter now resolves common aliases (e.g. light oak → birch)
against its safe palette without making another chargeable API request.
Unknown or unsafe IDs are still rejected. GitHub Actions includes
Java-only smoke tests for the aliases.

## Malformed AI JSON recovery (0.7.0)
LLM replies can contain Markdown fences, trailing commas, missing commas/colons,
truncated arrays, or invalid objects. Plans now pass through a constrained
normalizer and structural checks. Only syntax is repaired locally. Unsafe
unknown blocks and hopelessly broken plans still fail closed. Compatible GPT
chat models use JSON response format; Polza's unsupported JSON mode falls back
from HTTP 400 without changing providers or leaking API keys.
A second (potentially **billable**) request is made once with stricter and
shorter output instructions only if normalization still fails or output is truncated.
Set `"retryMalformedJson": false` in `config/aibuilder/config.json` to disable.
The retry never sends the malformed response or any secrets to the model.
JUnit parser regression tests are run during Gradle build.

## New in 0.7.0: repairing malformed operation type names
Some models confuse geometric primitive types with Minecraft materials, e.g.
`{"type":"lantern"}` instead of `{"type":"block","block":"lantern"}`.
The compiler now accepts valid palette material IDs as operation names,
normalizing them into safe block/door/bed operations before compilation.
It also supports a bounded set of architecture terms like `window`,
`floor`, `pillar`, `roof`, `stairs`. Unrecognized or unsafe names
still fail closed. This local correction consumes **no extra API tokens**.
JUnit tests cover lanterns, panes, stairs, trapdoors and safe rejection.

## Version 0.7.0 — model presets and stale JAR detection
Run `/ai version` to read the version actually loaded by Fabric; if unknown,
check the active LabyMod profile and remove older AI Builder JARs.
Added explicit safe `flower_pot` operation alias + regression tests.
F8 settings offer three provider-aware model presets: GPT-4.1 mini
(economy), GPT-5.4 mini (recommended quality) and GPT-4.1
(instruction precision). Use **Только настройки** after selecting a preset:
existing API keys stay on disk and unchanged. New Polza configurations
default to GPT-5.4 mini; existing configurations keep their chosen model.
Models can have different API costs and response-parameter compatibility.
Improved structure-first architect prompt and retry token compatibility.

## Fix in 0.7.0 — incompatible optional block properties
AI output sometimes attaches `axis=x` to `smooth_stone_slab`,
which does not support the `axis` state property. Such *optional*
model-supplied decorative state hints are now skipped when the actual
Minecraft block does not define them or rejects their value. Valid
slab_type, log axis and stair facing properties remain applied.
Required properties (two-block doors/beds, procedural stair roofs)
retain strict validation. The model system prompt now explains
which properties apply to which block families.

## New in 0.7.0 — deterministic layout for standalone houses

Freeform LLM coordinates often misalign doors, walls, window openings, roofs
and stairwells even when all material names are correct. A new **house**
operation lets the model specify only dimensions, number of floors,
orientation and safe block palette; the engine computes the exact geometry.

Example:
```json
{"name":"Timber cottage","operations":[{"type":"house","x":6,"y":0,"z":6,
 "w":17,"d":13,"floors":2,"front":"north","wall":"birch_planks",
 "roof":"dark_oak_stairs","door":"spruce_door"}]}
```

House mode includes 1–2 supported floors, two-block doors with carved openings,
symmetrical glazed windows, gabled stair roofs with gable infill, a real
staircase with floor clearance, practical furnishing and a front walkway.
Dimensions must be w=11..25, d=9..23. `front` supports N/S/E/W.

**`/ai house`** is now a free, offline demo of the parametric builder.

A new layout-stage overlap policy also prevents an operation from
overwriting a higher-priority roof/opening/window merely because its JSON
object is listed later. Door and bed positions are protected.

Existing freeform operations still work for castles, spawns, trees and
non-rectangular structures, with less deterministic placement quality.
No model upgrade is required: GPT-4.1 can benefit from the new scheme.
Back up your world first. Real gameplay validation remains necessary.

## Version 0.7.0 — genuinely different architecture styles

The previous `house` macro was effectively a single design used for
almost every prompt. The upgraded parametric engine now has seven distinct
house families: medieval, modern, Japanese, Nordic, Alpine, rustic, and
classical brick. Roof profiles include true 4-sided hip roofs, gables, and
flat roof terraces with parapets; facade beam layouts and window dimensions,
entry porches, balconies, chimneys and gardens vary by style.

Free local demos in singleplayer Creative Mode, no API usage:
`/ai house medieval`, `/ai house modern`, `/ai house japanese`,
`/ai house nordic`, `/ai house alpine`, `/ai house rustic`,
`/ai house brick`. Plain `/ai house` intentionally remains the default
medieval demo, unlike a model-generated F7 building request.

The F7 user prompt parser detects explicitly requested architectural style
and sizes in Russian or English. It overrides generic example defaults in
the model's `house` operation so a Japanese request does not end up with a
copied medieval gable roof. Optional `style`, `roof_style`, `window_style`,
`roof_block`, `porch`, `balcony`, `chimney` and `garden` fields can
be chosen by the model. The original material whitelist and world bounds
remain enforced.

All layouts are deterministic for a given set of parameters, and real
in-game testing is still recommended. Terrain may require preparation.

## 0.7.0 — castles, safer sizes, longer F7 input

F7 now accepts 12,000 characters. Larger prompts may increase API token costs. Invalid zero dimensions are normalized; oversized geometry is reduced to the allowed world perimeter and overall block budget. Unsafe out-of-bounds origins still fail.

Use `/ai castle` to test an aligned 35x35 castle without an API request. Model plans can use a single `castle` operation (25–39 blocks wide/deep) to generate walls, four towers, gate, courtyard, and keep. Back up your test world first.
