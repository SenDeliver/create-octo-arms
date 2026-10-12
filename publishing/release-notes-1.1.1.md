Bug fix release. Update the client; the server can stay on 1.1.0 but should update too.

### Fixed

- **Client crash when watching someone climb a Ship.** With Sable Ships, players near a Rig wearer could crash while the wearer climbed a Ship (`InternalError: a fault occurred in an unsafe memory access operation` in `TentacleRenderer`). The crash was random and never hit the wearer. If the watching client had not yet received the Ship's position, the Tentacles were drawn toward the Ship's storage location far outside the world, and the renderer ran out of vertex memory. Such Grips are now skipped until the Ship position arrives, and a Tentacle can never be drawn longer than its reach.
- First-person Rig rendering no longer flushes the shared render buffer mid-frame. Safer with batching mods such as ImmediatelyFast.

### Notes

- Client-only change. 1.1.0 and 1.1.1 clients and servers can mix, but only an updated client is protected from the crash.
- If a Grip is skipped, the client log says `Grip of <player> not drawn`. Please attach that line if you report a Ship-related rendering problem.
- Requires Create 6.0.10+ (below 6.1). Sable 2.0.5+ (below 2.1) is optional and only needed for Ships.

[1.1.1]: https://github.com/SenDeliver/create-octo-arms/releases/tag/v1.1.1
