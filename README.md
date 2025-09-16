## Ecoland: AI Ecosystem Simulator (Java + JavaFX)

An interactive, agent-based ecosystem simulator written in Java 17 with JavaFX. It generates a procedural world (Perlin-noise terrain and biomes) and simulates populations of species (herbivores, carnivores, omnivores, apex predators, decomposers, plants, scavengers) with energy/health dynamics, reproduction, and optional neural-network-driven behavior. The UI renders the world, provides tools to place entities, and offers real-time charts and gene statistics.

### Demo

<video
  poster="assets/ecoland-demo-poster.png"
  autoplay
  muted
  loop
  playsinline
  controls
  style="max-width: 800px; width: 100%; height: auto; border-radius: 8px;">
  <source src="https://raw.githubusercontent.com/Lilllllly06/ecoland-simulator/main/assets/ecoland-demo.mp4" type="video/mp4" />
  Your browser does not support the video tag.
</video>

## Key Features
- **Procedural world generation**: Perlin-noise-based elevation, temperature, and moisture maps create varied terrain and biomes (ocean, lake, desert, plains, forest, swamp, mountains).
- **Multiple species**: `HERBIVORE`, `CARNIVORE`, `OMNIVORE`, `APEX_PREDATOR`, `DECOMPOSER`, `PLANT`, `SCAVENGER`.
- **Life simulation**: Energy/health, movement, feeding, death with dead bodies that decompose over time.
- **Neural-network brains (optional)**: Simple feed-forward networks with inheritance/crossover via `AnimalBrain` and specialized brains per species.
- **Interactive UI**: Zoom/pan, placement tools, inspector, speed control, AI toggles, and keyboard shortcuts.
- **Data logging and charts**: Periodic population logging with a real-time line chart and a gene statistics table (min/max/avg/std-dev).
- **Save/Load**: Serialize the full simulation state to disk and restore later.

## Tech Stack
- **Language**: Java 17
- **UI**: JavaFX 17 (controls, graphics)
- **Build**: Gradle (wrapper included)

## Project Structure
- `src/main/java/com/ecoland/EcolandApplication.java`: JavaFX entry point and UI (controls, charts, renderer, event loop).
- `src/main/java/com/ecoland/simulation/Simulation.java`: Core simulation loop, population initialization, world updates, save/load (`SimulationState`).
- `src/main/java/com/ecoland/simulation/EntityManager.java`: Tracks entities, queries, additions/removals, counts.
- `src/main/java/com/ecoland/model/*`: `World`, `Tile`, enums (`TerrainType`, `BiomeType`).
- `src/main/java/com/ecoland/generator/*`: World generators (`PerlinNoiseGenerator`, `SimpleLandWaterGenerator`), `WorldGenerator` interface.
- `src/main/java/com/ecoland/entity/*`: `Entity` base, `Genes`, concrete species, `SpeciesType`.
- `src/main/java/com/ecoland/ai/nn/*`: `NeuralNetwork`, `AnimalBrain` and per-species brains, `SpeciesBrainFactory`.
- `src/main/java/com/ecoland/ui/WorldRenderer.java`: Canvas renderer for terrain and entities with zoom/pan.
- `src/main/java/com/ecoland/data/*`: `DataLogger` for populations and gene stats, `SimulationSerializer`.
- `src/main/java/com/ecoland/common/Constants.java`: Global constants.

## Getting Started
### Prerequisites
- JDK 17+
- macOS/Linux/Windows

JavaFX dependencies are handled via the Gradle JavaFX plugin in `build.gradle`.

### Build and Run
Using the Gradle wrapper (recommended):

```bash
./gradlew run
```

Build distributables/JAR:

```bash
./gradlew build
```

Artifacts are produced under `build/` (including `build/libs/Ecoland.jar`). Running the JAR directly may require JavaFX module flags; prefer `./gradlew run` which configures JavaFX for you.

## Using the App
When the app starts you’ll see a setup dialog:
- Set world width/height (tiles).
- Choose initial counts for herbivores, carnivores, decomposers, apex predators, omnivores.

Then the main window opens with:
- **Center**: World canvas (zoom/pan supported).
- **Right panel**: Controls, AI toggles, placement tools, stats, inspector.
- **Bottom**: Tabs for population charts and gene statistics.

### Controls
- **Start/Pause**: Toggles the simulation loop.
- **Step**: Advance one tick while paused.
- **Save Simulation**: Serialize full state to `.dat`.
- **Load Simulation**: Restore a previously saved state.
- **Save Data Log**: Export populations CSV from `DataLogger`.
- **AI Controls**: Enable/disable neural behavior per species type.
- **Speed**: Slider from 1–60 ticks/sec.
- **Zoom**: Slider and Reset View.
- **Placement Tools**: Place Herbivore, Carnivore, Omnivore, Apex Predator, Decomposer, Plant, or add tile Food.
- **Inspector**: Click a tile to view terrain data and entity info (species, energy/health, genes).

### Mouse and Keyboard
- Mouse wheel: Zoom in/out.
- Right or middle drag: Pan.
- Space: Start/Pause.
- Right Arrow: Step (only when paused).
- Esc: Clear current placement tool.

## Data and Analytics
- The `DataLogger` records populations at a configurable interval (default every 10 ticks).
- The Population Trends chart shows recent history for each species and total population.
- Gene statistics tab computes min/max/avg/std-dev across entities of the selected species for genes such as `speed`, `visionRange`, `maxEnergy`, `energyEfficiency`, `reproductionThreshold`, `reproductionCost`, `maxHealth`, `spreadChance`.
- Use “Save Data Log” to export CSV for external analysis.

## Save/Load
- Save creates a `SimulationState` snapshot including world tiles and all living entities (positions, genes, energy/health).
- Load replaces the current simulation/world and resets the renderer view.

## Architecture Notes
- The simulation advances by discrete ticks. Each tick:
  - Updates all entities (`Entity.update(simulation, world)`), removing those that die.
  - Applies queued additions/removals via `EntityManager.updateEntityList()`.
  - Logs populations via `DataLogger.recordTick(...)`.
  - Updates world state (e.g., passive plant regrowth, dead body decomposition).
- Rendering draws visible tiles, dead bodies (X marks), and live entities as colored circles. Health bars render at higher zoom levels.
- Neural behavior can be toggled per-species at runtime.

## Configuration
- Change defaults in `com.ecoland.common.Constants` and initial counts via the setup dialog in the UI.
- World generation thresholds and Perlin settings are in `PerlinNoiseGenerator`.

## Troubleshooting
- Prefer `./gradlew run` to ensure JavaFX modules are configured.
- If you run a built JAR directly and hit JavaFX errors, use Gradle or add proper `--module-path` and `--add-modules` flags for JavaFX.

## Roadmap Ideas
- Spatial indexing (quadtrees) for faster proximity queries in `EntityManager`.
- More behaviors (migration, flocking), weather/season cycles.
- Extended learning and evolution controls; persistence of AI across sessions.
- UI polish, presets, screenshot/recording tools.

## License
Choose a license (e.g., MIT) and add it here.

---
Made with Java 17 + JavaFX. Contributions and feedback are welcome!
Made with Java 17 + JavaFX. Contributions and feedback are welcome!
