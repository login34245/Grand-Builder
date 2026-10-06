# Europa Cinematic Assets

This is an unofficial fictional crossover scene, not an official asset pack or integration with Barotrauma, Star Wars or Counter-Strike 2.

## Portrait

- Project asset: `src/main/resources/assets/grand_builder/textures/cinematic/europa_captain.png`.
- Generated with the built-in image generation tool, then mechanically resized to 512x512 RGBA while preserving transparency.
- Original result: `C:/Users/Admin/.codex/generated_images/019ddaef-eab7-7342-8278-314af26418b8/exec-665936b4-9be5-4c78-967c-633fa42b477f.png`.
- This is an original captain portrait in the requested visual style, NOT an extracted Barotrauma character sprite.
- Generation prompt: Create one production game asset: a 2D bust portrait for a radio video call with a submarine captain on Europa, in the recognizable gritty hand-painted paper-doll art style of Barotrauma. A tired middle-aged male captain with short dark hair, stubble, a muted navy pressure-suit work jacket with rusty orange shoulder details and a utilitarian headset microphone, facing slightly right toward the viewer, stern deadpan expression. Cropped waist-up, the whole head and shoulders fully visible. Hard-edged detailed 2D painted shading, not 3D, not anime, not glossy, not futuristic superhero armor. The portrait will be placed into a rectangular submarine radio terminal in a Minecraft cinematic. No text, no logos, no frame, no background, transparent background. Output a single portrait on a square transparent canvas.

## Sound

Five original local 48 kHz mono Vorbis synth cues: `europa_radio`, `europa_relay`, `europa_charge`, `europa_beam`, `europa_flash`. They contain no game recordings, downloaded music, speech or third-party samples. The final cue uses a short noise/low-frequency impact followed by a decaying 2150 Hz tone; it is flashbang-like, NOT the original CS2 sound. The nominal five-second clip includes Vorbis padding; playback is stopped at the 100-active-tick flash boundary. Sound instances belonging to the cutscene are explicitly stopped on dismissal, scene loss or completion.

## Models

Planets, satellite relay, station hull/dish, beam, square Earth, clouds and square pressure fronts are procedural 3D geometry implemented in `EuropaGeometry`. `SpaceSceneProjection` applies a moving perspective camera, far-to-near quad ordering and native GUI winding. The projected scene uses Minecraft's GUI pipeline so world fog and shader depth cannot erase the space set; it is not a video or a static scene image. World-site effects still use existing Iris-aware 3D pipelines.
