**New Features:**

- Full Support for Custom Fluids: Automatically detects all registered fluid blocks via Forge's FluidRegistry (including liquids added by other mods). The infinite water control logic now correctly counts source blocks within these fluids, making it effective for mod-added liquids such as EnviroMine's toxic water, Thermal Foundation's fluids, and more.

**Core Functionality Recap:**

- Disables infinite water based on the number of connected water sources (small water bodies no longer regenerate after being drained).

- Automatic water placement upon block breaking: when breaking a solid block near sea level (configurable), if the surrounding water body is large enough, a new water source is placed.

Highly configurable: all parameters (threshold, search radius, vertical range, etc.) can be adjusted via the configuration file.

**Compatibility:**

- Compatible with most water-collecting items that follow vanilla physics (e.g., buckets, pipes).

- For deeper integration with other mods, adjust via config or extend using reflection.
