package com.thenathe.hotkeywheel;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.slf4j.LoggerFactory;

/**
 * The client side of Vanilla+ Features' container protocol (version 1), with codecs of Hotkey Wheel's own. It
 * never loads a Vanilla+ Features class.
 *
 * <p>Payload types can only be registered once. Without Vanilla+ Features on this client, Hotkey Wheel registers
 * them. With it, Vanilla+ Features has registered them already (its common initializer runs before any client
 * one), so its payload objects arrive here and must be sent back. Those are converted through the wire format,
 * which is all the two mods share: written with the registered codec and read with this one, or the reverse.
 */
public final class ContainerProtocol {
    public static final int VERSION = 1;
    public static final int MAX_ENTRIES = 128;
    public static final int MAX_PATH_STEPS = 4;
    public static final int KIND_SHULKER_BOX = 0;
    public static final int KIND_ENDER_CHEST = 1;
    public static final int CONTAINER_INVENTORY = 0;
    public static final int CONTAINER_ENDER_CHEST = 1;
    public static final int CONTAINER_BUNDLE = 2;
    public static final int CONTAINER_SHULKER_BOX = 3;
    public static final int FLAG_OPEN_SHULKER = 1;
    public static final int FLAG_OPEN_ENDER_CHEST = 1 << 1;
    public static final int FLAG_NESTED_OPEN = 1 << 2;
    private static final String NAMESPACE = "vanilla_plus_features";

    public static final CustomPacketPayload.Type<Hello> HELLO = type("hello");
    public static final CustomPacketPayload.Type<ListRequest> LIST_REQUEST = type("list_request");
    public static final CustomPacketPayload.Type<ListReply> LIST = type("list");
    public static final CustomPacketPayload.Type<Open> OPEN = type("open");
    public static final CustomPacketPayload.Type<OpenResult> OPEN_RESULT = type("open_result");

    private static boolean ownTypes;
    private static boolean receiving;
    private static boolean bridgeFailed;

    private ContainerProtocol() {}

    public record Step(int container, int index) {}

    public record Entry(int kind, List<Step> path, ItemStack display, long fingerprint) {
        /** Whether the entry follows the protocol's rules; anything else is dropped. */
        boolean valid() {
            if (kind != KIND_SHULKER_BOX && kind != KIND_ENDER_CHEST) return false;
            if (path.isEmpty() || path.size() > MAX_PATH_STEPS) return false;
            for (int step = 0; step < path.size(); step++) {
                Step part = path.get(step);
                if (part.index() < 0) return false;
                // A path starts in the inventory or the ender chest's contents and goes on only into bundles and
                // shulker boxes; the ender chest's contents can never be inside anything.
                boolean start = part.container() == CONTAINER_INVENTORY || part.container() == CONTAINER_ENDER_CHEST;
                boolean nested = part.container() == CONTAINER_BUNDLE || part.container() == CONTAINER_SHULKER_BOX;
                if (step == 0 ? !start : !nested) return false;
            }
            return true;
        }
    }

    public record Hello(int protocolVersion, int flags) implements CustomPacketPayload {
        static final StreamCodec<RegistryFriendlyByteBuf, Hello> CODEC = StreamCodec.of(
                (buffer, hello) -> {
                    buffer.writeVarInt(hello.protocolVersion());
                    buffer.writeByte(hello.flags());
                },
                buffer -> new Hello(buffer.readVarInt(), buffer.readUnsignedByte()));

        @Override
        public Type<Hello> type() { return HELLO; }
    }

    public record ListRequest(int requestId) implements CustomPacketPayload {
        static final StreamCodec<RegistryFriendlyByteBuf, ListRequest> CODEC = StreamCodec.of(
                (buffer, request) -> buffer.writeVarInt(request.requestId()),
                buffer -> new ListRequest(buffer.readVarInt()));

        @Override
        public Type<ListRequest> type() { return LIST_REQUEST; }
    }

    /** {@code count} is the number of entries the server said it sent, kept so an oversized list can be refused. */
    public record ListReply(int requestId, int count, List<Entry> entries) implements CustomPacketPayload {
        static final StreamCodec<RegistryFriendlyByteBuf, ListReply> CODEC = StreamCodec.of(ListReply::write, ListReply::read);

        private static void write(RegistryFriendlyByteBuf buffer, ListReply reply) {
            buffer.writeVarInt(reply.requestId());
            buffer.writeVarInt(reply.entries().size());
            for (Entry entry : reply.entries()) {
                buffer.writeByte(entry.kind());
                writePath(buffer, entry.path());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, entry.display());
                buffer.writeLong(entry.fingerprint());
            }
        }

        private static ListReply read(RegistryFriendlyByteBuf buffer) {
            int requestId = buffer.readVarInt();
            int count = buffer.readVarInt();
            // Nothing is sized from the count; a list longer than the packet runs out of bytes and is refused.
            List<Entry> entries = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                int kind = buffer.readByte();
                List<Step> path = readPath(buffer);
                ItemStack display = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
                entries.add(new Entry(kind, path, display, buffer.readLong()));
            }
            return new ListReply(requestId, count, entries);
        }

        @Override
        public Type<ListReply> type() { return LIST; }
    }

    public record Open(int kind, List<Step> path, long fingerprint) implements CustomPacketPayload {
        static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.of(
                (buffer, open) -> {
                    buffer.writeByte(open.kind());
                    writePath(buffer, open.path());
                    buffer.writeLong(open.fingerprint());
                },
                buffer -> new Open(buffer.readByte(), readPath(buffer), buffer.readLong()));

        @Override
        public Type<Open> type() { return OPEN; }
    }

    public record OpenResult(int status, Component message) implements CustomPacketPayload {
        public static final int OPENED = 0;
        public static final int UNSUPPORTED = 1;
        public static final int STALE = 2;
        public static final int NOT_ALLOWED = 3;
        public static final int INVALID = 4;
        static final StreamCodec<RegistryFriendlyByteBuf, OpenResult> CODEC = StreamCodec.of(
                (buffer, result) -> {
                    buffer.writeByte(result.status());
                    ComponentSerialization.STREAM_CODEC.encode(buffer, result.message());
                },
                buffer -> new OpenResult(buffer.readUnsignedByte(), ComponentSerialization.STREAM_CODEC.decode(buffer)));

        @Override
        public Type<OpenResult> type() { return OPEN_RESULT; }
    }

    private static void writePath(RegistryFriendlyByteBuf buffer, List<Step> path) {
        buffer.writeVarInt(path.size());
        for (Step step : path) {
            buffer.writeByte(step.container());
            buffer.writeVarInt(step.index());
        }
    }

    private static List<Step> readPath(RegistryFriendlyByteBuf buffer) {
        int steps = buffer.readVarInt();
        List<Step> path = new ArrayList<>();
        for (int step = 0; step < steps; step++) path.add(new Step(buffer.readByte(), buffer.readVarInt()));
        return List.copyOf(path);
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(NAMESPACE, path));
    }

    static void initialize(Consumer<Hello> hello, Consumer<ListReply> list, Consumer<OpenResult> openResult) {
        ownTypes = !FabricLoader.getInstance().isModLoaded(NAMESPACE) && registerTypes();
        receiving = receive(HELLO, Hello.class, Hello.CODEC, hello)
                & receive(LIST, ListReply.class, ListReply.CODEC, list)
                & receive(OPEN_RESULT, OpenResult.class, OpenResult.CODEC, openResult);
        if (!receiving) {
            LoggerFactory.getLogger("hotkey-wheel").info("Another mod on this client handles Vanilla+ Features' container messages; "
                    + "Hotkey Wheel's open shulker box and ender chest actions stay unavailable");
        }
    }

    private static boolean registerTypes() {
        try {
            PayloadTypeRegistry.clientboundPlay().register(HELLO, Hello.CODEC);
            PayloadTypeRegistry.clientboundPlay().register(LIST, ListReply.CODEC);
            PayloadTypeRegistry.clientboundPlay().register(OPEN_RESULT, OpenResult.CODEC);
            PayloadTypeRegistry.serverboundPlay().register(LIST_REQUEST, ListRequest.CODEC);
            PayloadTypeRegistry.serverboundPlay().register(OPEN, Open.CODEC);
            return true;
        } catch (IllegalArgumentException alreadyRegistered) {
            // Another copy of the protocol, such as Vanilla+ Features under a different ID, registered first.
            LoggerFactory.getLogger("hotkey-wheel").info("Vanilla+ Features' container messages are registered by another mod; Hotkey Wheel shares them");
            return false;
        }
    }

    /** Listens for one message, converting another mod's payload object into Hotkey Wheel's own when needed. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends CustomPacketPayload> boolean receive(CustomPacketPayload.Type<T> type, Class<T> ownClass,
                                                                   StreamCodec<RegistryFriendlyByteBuf, T> codec, Consumer<T> handler) {
        // Typed as any payload, so another mod's object reaches the conversion instead of failing a cast.
        CustomPacketPayload.Type<CustomPacketPayload> anyPayload = (CustomPacketPayload.Type) type;
        try {
            return ClientPlayNetworking.registerGlobalReceiver(anyPayload, (payload, context) -> {
                T own = ownClass.isInstance(payload) ? ownClass.cast(payload)
                        : convert(payload, RegisteredCodecs.clientbound(type.id()), codec);
                if (own != null) handler.accept(own);
            });
        } catch (RuntimeException failure) {
            LoggerFactory.getLogger("hotkey-wheel").warn("Cannot listen for {}", type.id(), failure);
            return false;
        }
    }

    /** Whether messages can be exchanged with this client's mod set; the server's hello is checked elsewhere. */
    static boolean usable() {
        return receiving && !bridgeFailed;
    }

    /** Whether the server listens for both requests on this connection. */
    static boolean serverListens() {
        try {
            return Minecraft.getInstance().getConnection() != null
                    && ClientPlayNetworking.canSend(LIST_REQUEST) && ClientPlayNetworking.canSend(OPEN);
        } catch (RuntimeException failure) {
            return false;
        }
    }

    static boolean send(ListRequest request) {
        return send(request, ListRequest.CODEC);
    }

    static boolean send(Open open) {
        return send(open, Open.CODEC);
    }

    private static <T extends CustomPacketPayload> boolean send(T payload, StreamCodec<RegistryFriendlyByteBuf, T> codec) {
        if (!usable() || !serverListens()) return false;
        CustomPacketPayload outgoing = ownTypes ? payload : convert(payload, codec, RegisteredCodecs.serverbound(payload.type().id()));
        if (outgoing == null) return false;
        ClientPlayNetworking.send(outgoing);
        return true;
    }

    /** Writes a value with one codec and reads it back with another; the two must share the wire format. */
    private static <A, B> B convert(A value, StreamCodec<RegistryFriendlyByteBuf, A> from, StreamCodec<RegistryFriendlyByteBuf, B> to) {
        var connection = Minecraft.getInstance().getConnection();
        if (from == null || to == null || connection == null) return null;
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), connection.registryAccess());
        try {
            from.encode(buffer, value);
            return to.decode(buffer);
        } catch (RuntimeException failure) {
            // A differing wire format would fail every time, so the actions stop rather than repeat the error.
            bridgeFailed = true;
            LoggerFactory.getLogger("hotkey-wheel").warn("Cannot exchange container messages with Vanilla+ Features on this client", failure);
            return null;
        } finally {
            buffer.release();
        }
    }

    // Fabric API's registry of payload codecs has no public lookup; reading it is kept here, so a change there
    // only switches the actions off.
    private static final class RegisteredCodecs {
        private static StreamCodec<RegistryFriendlyByteBuf, CustomPacketPayload> clientbound(Identifier id) {
            return lookUp(id, true);
        }

        private static StreamCodec<RegistryFriendlyByteBuf, CustomPacketPayload> serverbound(Identifier id) {
            return lookUp(id, false);
        }

        @SuppressWarnings("unchecked")
        private static StreamCodec<RegistryFriendlyByteBuf, CustomPacketPayload> lookUp(Identifier id, boolean clientbound) {
            try {
                var registered = (clientbound ? PayloadTypeRegistryImpl.CLIENTBOUND_PLAY : PayloadTypeRegistryImpl.SERVERBOUND_PLAY).get(id);
                if (registered != null) return (StreamCodec<RegistryFriendlyByteBuf, CustomPacketPayload>) registered.codec();
            } catch (RuntimeException | LinkageError failure) {
                LoggerFactory.getLogger("hotkey-wheel").warn("Cannot look up the codec for {}", id, failure);
            }
            bridgeFailed = true;
            return null;
        }
    }
}
