# Active movement is a client-computed constraint; Tentacles are cosmetic

While a Rig is Active the client moves the wearer as a point that must stay Held (within Reach of at least two solid block faces) along the Heading, and sends the motion to the server, which mirrors the velocity, keeps the vanilla float check quiet (creative-flight attribute while Active) and drains Air. The drawn Tentacles and their stepping gait are animation only; Grips are the only real world contact and they never collide, push or damage.

We rejected a server-authoritative mover because vanilla player position is client-authoritative and the server only validates, so a server-driven takeover fights the movement packets. We rejected physical tentacles (entities or colliders) because the whole feature is reachability, not force.

## Consequences

- Server validates Held-ness and Air; it never computes paths.
- Fighting mobs with Tentacles (v2 idea) would be a separate hit-check, not a side effect of animation.
- Do not use the `isChangingDimension` trick to silence float kicks: it makes the player invulnerable.
