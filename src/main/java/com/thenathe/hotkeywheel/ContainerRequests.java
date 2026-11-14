package com.thenathe.hotkeywheel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.slf4j.LoggerFactory;

/**
 * Asks a Vanilla+ Features server which shulker boxes and ender chests the player can reach, and to open one.
 * Nothing is sent before the server's hello on this connection. One request is out at a time, at most four go out
 * in any second (the server allows five), replies are matched by request ID, and an open request carries exactly
 * the path and fingerprint the server listed.
 */
public final class ContainerRequests {
    private static final long ANSWER_TIMEOUT_MILLIS = 5000;
    private static final int REQUESTS_PER_SECOND = 4;
    // Vanilla+ Features' own client numbers its requests from 1 on the same connection and sees every list too;
    // starting far above keeps either mod from taking the other's answer for its own.
    private static final int FIRST_REQUEST_ID = 1 << 20;
    private static final ArrayDeque<Long> recentSends = new ArrayDeque<>();
    private static ContainerProtocol.Hello hello;
    private static Pending pending;
    private static int nextRequestId = FIRST_REQUEST_ID;

    private enum Purpose { CHOOSE, ENDER_CHEST, OPEN }

    private record Pending(Purpose purpose, int requestId, long sentAt) {}

    private ContainerRequests() {}

    static void initialize() {
        ContainerProtocol.initialize(ContainerRequests::onHello, ContainerRequests::onList, ContainerRequests::onOpenResult);
        // INIT comes before the connection handles any message, so a hello sent on join is never wiped.
        ClientPlayConnectionEvents.INIT.register((handler, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientTickEvents.END_CLIENT_TICK.register(ContainerRequests::tick);
    }

    private static void reset() {
        hello = null;
        pending = null;
        recentSends.clear();
        nextRequestId = FIRST_REQUEST_ID;
    }

    /** Why the shulker box chooser cannot be used right now, or null when it can. */
    public static String chooserUnavailable() {
        String reason = protocolUnavailable();
        if (reason != null) return reason;
        if ((hello.flags() & (ContainerProtocol.FLAG_OPEN_SHULKER | ContainerProtocol.FLAG_OPEN_ENDER_CHEST)) == 0) {
            return "the server has switched off opening shulker boxes and ender chests";
        }
        return null;
    }

    /** Why Open ender chest cannot be used right now, or null when it can. */
    public static String enderChestUnavailable() {
        String reason = protocolUnavailable();
        if (reason != null) return reason;
        if ((hello.flags() & ContainerProtocol.FLAG_OPEN_ENDER_CHEST) == 0) return "the server has switched off opening ender chests";
        return null;
    }

    private static String protocolUnavailable() {
        if (!ContainerProtocol.usable()) return "another mod on this client handles Vanilla+ Features' messages; see the game log";
        if (hello == null) return "needs Vanilla+ Features on the server";
        if (hello.protocolVersion() != ContainerProtocol.VERSION) {
            return "the server's Vanilla+ Features speaks protocol " + hello.protocolVersion() + ", this Hotkey Wheel speaks "
                    + ContainerProtocol.VERSION;
        }
        if (!ContainerProtocol.serverListens()) return "the server does not take container requests";
        return null;
    }

    static void chooseContainer(Minecraft client) {
        String reason = chooserUnavailable();
        if (reason != null) {
            notice(client, "Open shulker box: " + reason + ".");
            return;
        }
        requestList(client, Purpose.CHOOSE);
    }

    static void openEnderChest(Minecraft client) {
        String reason = enderChestUnavailable();
        if (reason != null) {
            notice(client, "Open ender chest: " + reason + ".");
            return;
        }
        requestList(client, Purpose.ENDER_CHEST);
    }

    private static void requestList(Minecraft client, Purpose purpose) {
        if (!readyToSend(client)) return;
        int requestId = nextRequestId++;
        if (!ContainerProtocol.send(new ContainerProtocol.ListRequest(requestId))) {
            notice(client, "Hotkey Wheel: the request could not be sent.");
            return;
        }
        sent(purpose, requestId);
    }

    /** Opens a container the server listed, from the chooser. */
    static void open(Minecraft client, ContainerProtocol.Entry entry) {
        if (protocolUnavailable() != null || !kindAllowed(entry.kind()) || !readyToSend(client)) return;
        if (!ContainerProtocol.send(new ContainerProtocol.Open(entry.kind(), entry.path(), entry.fingerprint()))) {
            notice(client, "Hotkey Wheel: the request could not be sent.");
            return;
        }
        sent(Purpose.OPEN, 0);
    }

    private static boolean readyToSend(Minecraft client) {
        long now = Util.getMillis();
        if (pending != null) {
            notice(client, "Hotkey Wheel: still waiting for the server's answer.");
            return false;
        }
        while (!recentSends.isEmpty() && now - recentSends.peekFirst() >= 1000) recentSends.removeFirst();
        if (recentSends.size() >= REQUESTS_PER_SECOND) {
            notice(client, "Hotkey Wheel: wait a moment before asking again.");
            return false;
        }
        return true;
    }

    private static void sent(Purpose purpose, int requestId) {
        long now = Util.getMillis();
        recentSends.addLast(now);
        pending = new Pending(purpose, requestId, now);
    }

    private static void onHello(ContainerProtocol.Hello received) {
        hello = received;
        // Settings changed on the server; a chooser listing the old answer closes.
        if (Minecraft.getInstance().gui.screen() instanceof ContainerRadialScreen radial) radial.onClose();
    }

    private static void onList(ContainerProtocol.ListReply reply) {
        Minecraft client = Minecraft.getInstance();
        if (pending == null || pending.purpose() == Purpose.OPEN || pending.requestId() != reply.requestId()) return;
        Purpose purpose = pending.purpose();
        pending = null;
        if (reply.count() > ContainerProtocol.MAX_ENTRIES) {
            LoggerFactory.getLogger("hotkey-wheel").warn("Ignoring a container list of {} entries; the protocol allows {}",
                    reply.count(), ContainerProtocol.MAX_ENTRIES);
            return;
        }
        List<ContainerProtocol.Entry> usable = new ArrayList<>();
        for (ContainerProtocol.Entry entry : reply.entries()) {
            if (entry.valid() && kindAllowed(entry.kind())) usable.add(entry);
        }
        if (client.player == null) return;
        if (purpose == Purpose.ENDER_CHEST) {
            ContainerProtocol.Entry enderChest = usable.stream()
                    .filter(entry -> entry.kind() == ContainerProtocol.KIND_ENDER_CHEST).findFirst().orElse(null);
            if (enderChest == null) notice(client, "No ender chest within reach.");
            else open(client, enderChest);
            return;
        }
        // The answer came after the player opened something else; that screen stays.
        if (client.gui.screen() != null) return;
        if (usable.isEmpty()) {
            notice(client, "No shulker boxes or ender chests within reach.");
            return;
        }
        client.gui.setScreen(new ContainerRadialScreen(usable));
    }

    private static void onOpenResult(ContainerProtocol.OpenResult result) {
        if (pending == null || pending.purpose() != Purpose.OPEN) return;
        pending = null;
        Minecraft client = Minecraft.getInstance();
        String fallback = switch (result.status()) {
            case ContainerProtocol.OpenResult.OPENED -> "";
            case ContainerProtocol.OpenResult.UNSUPPORTED -> "The server does not open that here.";
            case ContainerProtocol.OpenResult.STALE -> "That container moved or changed. Open the chooser again.";
            case ContainerProtocol.OpenResult.NOT_ALLOWED -> "The server does not allow opening that.";
            default -> "The server refused the request.";
        };
        Component message = result.message().getString().isBlank() ? Component.literal(fallback) : result.message();
        if (client.player != null && !message.getString().isEmpty()) client.player.sendOverlayMessage(message);
    }

    private static boolean kindAllowed(int kind) {
        if (hello == null) return false;
        int flag = kind == ContainerProtocol.KIND_ENDER_CHEST ? ContainerProtocol.FLAG_OPEN_ENDER_CHEST : ContainerProtocol.FLAG_OPEN_SHULKER;
        return (hello.flags() & flag) != 0;
    }

    private static void tick(Minecraft client) {
        if (pending != null && Util.getMillis() - pending.sentAt() > ANSWER_TIMEOUT_MILLIS) {
            pending = null;
            notice(client, "Hotkey Wheel: the server did not answer.");
        }
    }

    private static void notice(Minecraft client, String text) {
        if (client.player != null) client.player.sendOverlayMessage(Component.literal(text));
    }
}
