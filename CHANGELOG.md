# Changelog

## 1.2.0

### Added, ported from the game
- `powerups`: `PowerupType` and `ActivePowerups` - the game's gadgets (Camera Jammer, Guard Shield,
  Invisibility Cloak, Stealth Boots, Checkpoints, Remote Trigger).
- `mechanisms.Lever` - the game's lever.
- `progress`: `Checkpoint` and `CheckpointProgress` - the game's checkpoint securing (trigger zones
  in order, or automatic every 250 units).
- `actors.Noise` / `NoiseLevel` - the game's footstep noise check.
- `MovingPlatform` / `MovingPlatformDef`: `crushesOnlyFromBelow` and `squeezes`, as in the game.
- `HookCrate.reset(atSweepClock)` and `HookCrate.isLooseAndNotFlat`, as in the game.
- A usage guide: [docs/GUIDE.md](docs/GUIDE.md).

## 1.1.0
- `HookCrate` (swinging / detachable load handing off to `BoxPhysics`) and `RoughBlock`.

## 1.0.0
- Initial release: geometry, vision, guards, cameras, lasers, moving platforms, conveyors,
  `SmoothFollow`, `ScreenLayout`, `BoxPhysics`.
