# Shipped 1.0.8 against the 2026-11 upstream releases

These runs use the unchanged released `hotkey-wheel-1.0.8+26.3.jar` (SHA-256 `ed47efefe6f06e7940a5250974b022228a2b61b441ca704571f9a44d3134d4ca`) with the 1.0.9 fixtures. They show the two problems that 1.0.9 fixes. They are baselines, not acceptance results.

| Run | Inputs | Result |
| --- | --- | --- |
| `shipping-1.0.8-reserved-keys/` | Integrated profile, official Buildprint 0.1.38+26.3, no Blocktone (0.1.25 requires Buildprint 0.1.25) | Fails at "Original opener C closes the wheel-owned editor before Buildprint's editor shortcut". Buildprint's editor key handler consumes C first. |
| `shipping-1.0.8-config/` | Minimal profile, key-management fixture only | Passes the three checks that load and save a 1.0.8 configuration. Fails at "An unreadable configuration file is copied aside before a later Save can replace it". |

The passing 1.0.8 load and save checks show that the embedded configuration fixture is read and written by 1.0.8 exactly as 1.0.9 reads and writes it. Each folder keeps the report, provenance with every mod hash, and the executed fixture sources. Console logs are kept locally and are not committed.
