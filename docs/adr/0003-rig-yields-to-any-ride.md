# The Rig Stows when a Ride starts

The Rig takes over the wearer's movement keys while it is out, and every other carrier (seats, mounts, boats, minecarts, Create's chain conveyor, a Simulated zipline or handle) reads the same keys or sets the same motion: with both at once Shift could not leave a seat, boats and horses did not steer, and the Rig fought the chain conveyor. We decided that only one carrier moves the wearer: starting a Ride Stows the Rig on both sides, the Rig cannot be Activated during a Ride, and it stays Stowed after. Ship controls are not Rides: during a Control Hold the Rig keeps its Grips, holds still and leaves the movement keys to the control. A Simulated handle is the exception: held normally it sets the wearer's motion every tick to pull them to it, so it is a Ride. A handle hold started sneaking drags the Ship, not the wearer, and reads no keys: it is neither, the Rig carries on (Simulated drags only for a wearer standing on something, so it does not drag while the Rig holds the wearer).

## Considered Options

- Pause the Rig during a Ride and re-grab on stand-up: needs a new state and visuals, and grabs by surprise after leaving a seat.
- Keep the Rig running and only pass input through: Air drains on a seat and Grips are tested from a body the seat moves.

## Consequences

- The server Stows on its own when the wearer is a passenger, so clients from before this change get the fix too; no packet change.
- Seat dismount position off a flying Ship means a fall; the mid-air Double Jump catch covers it.
- The server reads Create's hanging-player list for the chain conveyor and zipline. When the chain block breaks under the rider, Create drops the stop packet and the entry lives out its 20-tick TTL: a mid-air Double Jump catch in that second is refused (Stowed) and has to be tried again. Accepted: a TTL cut-off would only shorten the window and tie the check to Create's resend period.
