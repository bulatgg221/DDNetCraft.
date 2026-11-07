# AI Builder Next

The rewrite is independent from the 0.10.1 implementation in ../AIBuilder.
The same mod ID is used: install exactly one version.

Pipeline: user request → one billable chat request → specification + components +
section dependency graph + primitive operations → strict JSON parsing → bounded
voxel compilation → spatial/quality report → preview → explicit confirmation →
server tick placement queue → conflict-aware undo/redo.

A language model designs every layout. There are no house/castle macros and no
fallback buildings. A failed request never produces a substitute building.
A single response contains the analysis, architecture and geometry stages so the
player controls cost. Multiple sections share one coordinate frame and one budget.
Automatic paid retries are disabled; a new request requires a player action.

## Format 1

Root: format=1, name, mode (structure|pixel_art), specification (style, features),
components, sections, operations. Coordinates are integer local offsets from the
captured origin. x,z are in [-horizontal,+horizontal], y in [-vertical,+vertical].
Dimensions w,h,d are positive block counts. Components have id, kind, bounds
{x,y,z,w,h,d}, connectedTo, optional access {x,y,z}. Access is a standing voxel.
A structure requires an entrance component and a room component with access points.
Sections have id and dependsOn. Operations refer to section and component IDs,
use stage 0..11, and within a stage preserve model order. Later writes win; the
final state map is compiled before any world mutation. The engine never scales a
structure silently. Pixel rows alone may be resampled by one cell using nearest
neighbour, with an explicit warning.

State machines and ownership: all world state belongs to the integrated server;
HTTP and compilation run on a worker. Generation tickets invalidate late results
on cancel/disconnect/dimension change. Immutable preview snapshots cross to the
client. Each placement is rechecked for world bounds/chunk availability and owner.
No server support is advertised. No arbitrary model commands, code, paths or URLs.

Validation layers: schema and bounded expansion; Minecraft registry/property
resolution; voxel connectivity, headroom, multipart consistency, roof/component
bounds and support; architecture metrics clearly labelled heuristic; world
preflight and optimistic conflict checks. Existing block entities are protected
and never overwritten. History contains no API credentials.

Security: HTTPS endpoints are fixed by provider. Responses have byte/time limits.
Keys are session-only by default; Windows may use DPAPI CurrentUser storage.
Other systems use session/environment keys rather than a bundled encryption key.
