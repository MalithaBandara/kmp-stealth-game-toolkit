# Demo

A small playable stealth level built on nothing but this library and Compose Desktop shapes - no
image assets. It's a standalone Gradle build that pulls the library from JitPack
(`com.github.MalithaBandara.kmp-stealth-game-toolkit:kmp-stealth-game-toolkit-jvm:v1.2.0`), the
same way any outside project would.

## Run

From the repository root (reuses the root Gradle wrapper):

```bash
./gradlew -p demo run
```

## Controls

| Key | Action |
| --- | --- |
| A / D or ← / → | Move |
| Space / W / ↑ | Jump |
| S / ↓ | Crouch - slower, smaller, and silent |
| E | Throw a lever |
| 1 | Camera Jammer - cameras stop and can't see for 10 s |
| 2 | Guard Shield - protects from 1 laser contact |
| 3 | Invisibility Cloak - invisible to guards and cameras for 10 s (lasers pass through too) |
| 4 | Stealth Boots - silent movement for the rest of the level |
| 5 | Remote Trigger - throws the nearest lever you haven't reached |
| Enter | Play again after reaching the exit |

### How the demo teaches the gadgets

- **Briefing:** the level opens with a panel listing every gadget, its key, and what it does, using
  the game's store names and descriptions.
- **Quick-slot tray** (bottom left): one box per gadget with its key and stock, and a draining timer
  bar while it runs, like the game's in-level tray.
- **Section hints** (top centre): as you reach each obstacle, a panel gives the skill route and
  names the gadget that makes it easy, with its key. Once that gadget is used up, the hint says so.
- **Feedback:** firing a gadget flashes its description. Each one has a visible effect:
  - a jammed camera loses its cone and fizzes with static
  - an invisible player turns see-through
  - the shield shows as a bubble around the player
  - the stealth boots get soft pads
  - the remote trigger draws a signal from you to the lever it threw

You start with one of each, as if bought from the store. **Checkpoints** isn't on a key: like the
game's respawn button, it's spent for you the first time you're caught. From then on, being caught
sends you back to the last flag secured. The game only secures a checkpoint while nobody can see
you. Without Checkpoints, being caught restarts the level.

Walking makes noise. Any guard within earshot with a clear line to you turns to look, so crouch
near them. Being seen fills the suspicion meter at the top. Walk into the fallen crate to push it.

## The level

Every section can be passed on skill alone. The gadgets are shortcuts.

| Section | Skill route | Gadget route | Library pieces |
| --- | --- | --- | --- |
| Guard between two crates | Onto the low crate, up to the catwalk when he walks away, along it over his head | Invisibility Cloak | `actors.Guard`, `actors.Noise`, `vision.VisionSystem` |
| Gap | Ride the lift | | `platforms.MovingPlatform` |
| Camera over the pillar | Run through as it sweeps away, over the pillar | Camera Jammer | `sentry.Camera` |
| Tilted timed laser | Go when it switches off | Guard Shield | `hazards.Laser` |
| Laser gate | Throw its lever | Remote Trigger | `mechanisms.Lever`, `Laser.disable()` |
| Conveyor under a camera | Ride the belt as the camera sweeps away | Camera Jammer | `platforms.ConveyorDef` / `ConveyorCrate` |
| Ledge | Throw the hook lever, push the crate to the wall, climb it | Remote Trigger | `platforms.HookCrate` → `physics.BoxPhysics` |
| Guard on the ledge | Along the duct over him, crouch out behind him to the door | Stealth Boots | `actors.Guard`, `actors.Noise` |
| Throughout | | | `progress.CheckpointProgress`, `powerups.ActivePowerups` / `PowerupType`, `terrain.RoughBlock`, `follow.SmoothFollow`, `layout.DeviceScreen` / `ScreenLayout` |

## Tests

```bash
./gradlew -p demo test
```

`PlaythroughTest` drives `Game` through the same `Controls` the keyboard produces:

- finishes the level **with no gadgets at all** without being caught
- finishes it using every gadget, and checks each one does its job
- finishes it once for each hook-crate drop timing across a full swing, since the crate lands
  somewhere different each time
- checks that walking straight through gets you caught
- checks both respawn rules: at the last checkpoint with Checkpoints, from the start without
- checks the quick-slot refuses before spending, as the game does: a gadget already running, a
  remote trigger with no lever left, or one you have none of

## Working on the library

To run the demo against local library changes instead of JitPack, publish the JVM artifact to
mavenLocal and pass `-Pstealthkit.local`:

```bash
./gradlew publishJvmPublicationToMavenLocal
```

```bash
./gradlew -p demo run -Pstealthkit.local
```
