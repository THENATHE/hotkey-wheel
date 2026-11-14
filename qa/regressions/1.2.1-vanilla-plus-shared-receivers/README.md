# 1.2.0 with Vanilla+ Features on the client: Vanilla+ Features stops hearing the server

`ContainerActionsTest` joined a separate Fabric 26.3 dedicated server running Vanilla+ Features 1.0.0+26.3, with Vanilla+ Features 1.0.0+26.3 on the client as well (`run.py --vpf --vpf-client`, server from `test_server.py`).

- `release-1.2.0/`: the published `hotkey-wheel-1.2.0+26.3.jar` (SHA-256 `4bdc737e…0c639`). Hotkey Wheel's own actions became available, but the fixture stops at "Vanilla+ Features' own client still receives the server's hello (its tooltip hint shows)". A shulker box's tooltip is only `Shulker Box`.
- `release-1.2.1/`: `hotkey-wheel-1.2.1+26.3.jar` (SHA-256 `e6edfc91…1556`). The tooltip shows Vanilla+ Features' `Right-click to open` hint, and the rest of the fixture passes.

Why: Fabric API keeps one global receiver per message. Client initializers ran Hotkey Wheel's first, so it registered `vanilla_plus_features:hello`, `list` and `open_result`, and Vanilla+ Features' own registration of the same three silently failed. Its client then never learned that the server supports opening, so its tooltip hint, opening from the creative inventory and its refusal messages stopped working. Which mod won depended on initializer order, so on another machine Hotkey Wheel's actions could have been the ones left unavailable.

1.2.1 registers Hotkey Wheel's receivers once the client has started, after every initializer, takes over whatever receiver is already there and passes each message to it before handling it, so both mods hear the server. Its list request IDs start at 2^20, so Vanilla+ Features' client, which numbers its own from 1 on the same connection, never takes a list meant for Hotkey Wheel or the reverse.
