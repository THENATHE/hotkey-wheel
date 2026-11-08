# Unpublished 1.0.9 on a dedicated server's first editor opening

This run uses the unchanged `hotkey-wheel-1.0.9+26.3.jar` (SHA-256 `b3f6fcf41e0ee23728515434caada70ec2eea92f0ac448b0e06e24e6c8a12c0f`) with the 1.0.10 `ServerCameraChoiceTest` fixture. 1.0.9 was packaged locally but never published. This is a baseline, not an acceptance result.

The fixture joins a dedicated Minecraft 26.3 server that the client gametest API starts in the same JVM. The profile is the integrated one, with the official Buildprint 0.1.38+26.3 and Blocktone 0.1.38. On a server it has not seen before, Buildprint asks whether the editor should use freecam or the player's camera.

1.0.9 fails at "The wheel opener closes the editor started from the camera question". When the question appears, Buildprint's `open()` returns false, 1.0.9 drops its claim, and the editor started from the answer is not wheel-owned. 1.0.10 keeps the claim while the question is shown and passes the same fixture.

The folder keeps the report, provenance with every mod hash, and the executed fixture sources. The console log is kept locally and not committed.
