package com.voidhunt;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Networking for sharing Void-Hunt visual effects with nearby players. */
public final class VoidNet {

    /** A snapshot of one caster's active visuals. */
    public static final class Fx {
        public Vec3d mCtr, cCtr, aCtr, jPos, sCtr;  // domain / crow / arena / judgment / sea centers
        public int mT, cT, aT, uT, jT, bT, sT;      // timers (0 = off), uT=ult, bT=berserk, sT=sea
        public final List<Vec3d> drones = new ArrayList<>();
        public final List<Vec3d> crows  = new ArrayList<>();
        public final List<Vec3d> sharks = new ArrayList<>();
        public int age = 0;                        // client-side: ticks since last packet
        public boolean anyActive() {
            return mT > 0 || cT > 0 || aT > 0 || uT > 0 || jT > 0 || bT > 0 || sT > 0
                || !drones.isEmpty() || !crows.isEmpty() || !sharks.isEmpty();
        }
    }

    private static void wVec(RegistryByteBuf b, Vec3d v) {
        boolean has = v != null; b.writeBoolean(has);
        if (has) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    }
    private static Vec3d rVec(RegistryByteBuf b) {
        if (!b.readBoolean()) return null;
        return new Vec3d(b.readDouble(), b.readDouble(), b.readDouble());
    }
    private static void wBody(RegistryByteBuf b, Fx f) {
        wVec(b, f.mCtr); b.writeVarInt(f.mT);
        wVec(b, f.cCtr); b.writeVarInt(f.cT);
        wVec(b, f.aCtr); b.writeVarInt(f.aT);
        b.writeVarInt(f.uT);
        wVec(b, f.jPos); b.writeVarInt(f.jT);
        b.writeVarInt(f.bT);
        wVec(b, f.sCtr); b.writeVarInt(f.sT);
        b.writeVarInt(f.drones.size()); for (Vec3d v : f.drones) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
        b.writeVarInt(f.crows.size());  for (Vec3d v : f.crows)  { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
        b.writeVarInt(f.sharks.size()); for (Vec3d v : f.sharks) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    }
    private static Fx rBody(RegistryByteBuf b) {
        Fx f = new Fx();
        f.mCtr = rVec(b); f.mT = b.readVarInt();
        f.cCtr = rVec(b); f.cT = b.readVarInt();
        f.aCtr = rVec(b); f.aT = b.readVarInt();
        f.uT = b.readVarInt();
        f.jPos = rVec(b); f.jT = b.readVarInt();
        f.bT = b.readVarInt();
        f.sCtr = rVec(b); f.sT = b.readVarInt();
        int n = b.readVarInt(); for (int i = 0; i < n; i++) f.drones.add(new Vec3d(b.readDouble(), b.readDouble(), b.readDouble()));
        int m = b.readVarInt(); for (int i = 0; i < m; i++) f.crows.add(new Vec3d(b.readDouble(), b.readDouble(), b.readDouble()));
        int k = b.readVarInt(); for (int i = 0; i < k; i++) f.sharks.add(new Vec3d(b.readDouble(), b.readDouble(), b.readDouble()));
        return f;
    }

    /** Client -> server: my current effect snapshot. */
    public record PushC2S(Fx fx) implements CustomPayload {
        public static final Id<PushC2S> ID = new Id<>(Identifier.of(VoidHunt.MOD_ID, "fx_c2s"));
        public static final PacketCodec<RegistryByteBuf, PushC2S> CODEC =
            PacketCodec.of((v, b) -> wBody(b, v.fx), b -> new PushC2S(rBody(b)));
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Server -> client: someone else's effect snapshot. */
    public record SyncS2C(UUID owner, Fx fx) implements CustomPayload {
        public static final Id<SyncS2C> ID = new Id<>(Identifier.of(VoidHunt.MOD_ID, "fx_s2c"));
        public static final PacketCodec<RegistryByteBuf, SyncS2C> CODEC =
            PacketCodec.of((v, b) -> { b.writeUuid(v.owner); wBody(b, v.fx); },
                           b -> { UUID o = b.readUuid(); return new SyncS2C(o, rBody(b)); });
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void registerCommon() {
        PayloadTypeRegistry.playC2S().register(PushC2S.ID, PushC2S.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncS2C.ID, SyncS2C.CODEC);
    }

    /** Server relays each snapshot to nearby players in the same world. */
    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(PushC2S.ID, (payload, context) -> {
            ServerPlayerEntity from = context.player();
            from.getServer().execute(() -> {
                ServerWorld w = (ServerWorld) from.getEntityWorld();
                SyncS2C out = new SyncS2C(from.getUuid(), payload.fx());
                for (ServerPlayerEntity pl : from.getServer().getPlayerManager().getPlayerList()) {
                    if (pl == from) continue;
                    if (pl.getEntityWorld() != w) continue;
                    if (pl.squaredDistanceTo(from) > 160 * 160) continue;
                    ServerPlayNetworking.send(pl, out);
                }
            });
        });
    }
}
