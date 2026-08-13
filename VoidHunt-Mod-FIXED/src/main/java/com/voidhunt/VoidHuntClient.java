package com.voidhunt;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class VoidHuntClient implements ClientModInitializer {
    private static boolean huntMode  = true;
    private static boolean aimAssist = true;
    private static boolean lastUse   = false;   // edge-detect right click
    private static boolean lastH     = false;   // edge-detect H key
    private static boolean lastK     = false;   // edge-detect K key
    private static boolean lastL     = false;   // edge-detect L key (ultimate)
    private static boolean lastG     = false;   // edge-detect G key (domain expansion)
    private static LivingEntity target;
    private static LivingEntity attackedTarget = null; // for kill counting
    private static int kills = 0, combo = 0, comboTimer = 0;

    // ---- ultimate (orbital strike satellite) ----
    private static int ultTimer = 0;
    private static final int    ULT_TICKS  = 200;   // 10 seconds
    private static final double ULT_HEIGHT = 7.0;
    private static final double ULT_RADIUS = 24.0;
    private static final float  ULT_DMG    = 20.0f;

    // ---- DOMAIN EXPANSION: 領域展開 · 기계의 세계 (MACHINE WORLD) ----
    private static int    domainTimer = 0;
    private static Vec3d  domainCenter = null;
    private static final int    DOMAIN_TICKS  = 240;   // 12 seconds
    private static final double DOMAIN_RADIUS = 16.0;  // enclosed space radius
    private static final float  DOMAIN_DMG    = 6.0f;  // sure-hit tick damage
    private static final double FREEZE_RADIUS = 320.0; // 20 chunks — everything (but caster) is frozen here
    private static final java.util.Map<UUID, Vec3d> frozen = new java.util.HashMap<>();

    // ===== CROW FAN KIT — held-weapon skill set (까마귀의 부채) =====
    private static boolean lastR = false, lastC = false, lastV = false, lastX = false;
    private static int    crowDomainTimer = 0;
    private static Vec3d  crowCenter = null;
    private static final int    CROW_DOMAIN_TICKS = 300;  // 15 seconds
    private static final double CROW_RADIUS       = 30.0; // a far bigger world
    private static final float  CROW_DMG          = 5.0f; // domain tick damage
    private static int wingsTimer = 0;
    private static final int WINGS_TICKS = 160;
    private static int   sealTimer = 0;
    private static Vec3d sealCenter = null;
    private static final int SEAL_TICKS = 50;
    private static final List<Crow> crows = new ArrayList<>();
    private static final int    MAX_CROWS = 8;
    private static final double CROW_ATTACK_RANGE = 18.0;
    private static final float  CROW_PECK_DMG = 7.0f;
    private static final int GRN = 0xFF8CF06A, PUR = 0xFFB98CFF, BON = 0xFFE6E2D8;

    // ===== DUEL ARENA — 전사들의 결투장 (held Duel Greatsword) =====
    private static boolean lastB = false;
    private static int    arenaTimer = 0;
    private static Vec3d  arenaCenter = null;
    private static UUID   opponentId = null;
    private static String opponentName = "—";
    private static final int    ARENA_TICKS  = 400;   // 20 seconds
    private static final double ARENA_RADIUS = 18.0;
    private static final int GLD = 0xFFF2C044, CRM = 0xFFE23C46;
    private static LivingEntity opponentEntity = null;

    // ===== multiplayer effect sharing =====
    private static boolean renderingRemote = false;   // true while painting someone else's effects
    private static final java.util.Map<UUID, VoidNet.Fx> REMOTE = new java.util.HashMap<>();
    // sword skills: 광폭화 / 혈검술 / 전사의 심판
    private static boolean lastZ = false, lastN = false;
    private static int berserkTimer = 0;
    private static final int BERSERK_TICKS = 240;   // 12s frenzy
    private static int   slashTimer = 0;
    private static Vec3d slashPos = null, slashDir = null;
    private static final int SLASH_TICKS = 16;
    private static final float SLASH_DMG = 30.0f;
    private static int    judgmentTimer = 0;
    private static boolean judgmentDone = false;
    private static Vec3d  judgmentPos = null;
    private static final int JUDGE_TICKS = 50;

    // ===== OCEAN WORLD — 바다의 세계 (held Sea Trident) =====
    private static boolean lastJ = false, lastU = false, lastY = false, lastM = false;
    private static int    seaTimer = 0;
    private static Vec3d  seaCenter = null;
    private static final int    SEA_TICKS  = 300;   // 15s
    private static final double SEA_RADIUS = 28.0;
    private static final float  SEA_DMG    = 5.0f;
    private static int   waveTimer = 0;
    private static Vec3d wavePos = null, waveDir = null;
    private static final int WAVE_TICKS = 18;
    private static int   maelTimer = 0;
    private static Vec3d maelPos = null;
    private static final int MAEL_TICKS = 70;
    private static final List<Crow> sharks = new ArrayList<>();
    private static final int MAX_SHARKS = 6;
    private static final int AQUA = 0xFF44E0E0, SEAB = 0xFF5AA6FF;

    // ===== BIKER HIGHWAY — 폭주족들의 도로 (held Neon Pipe) =====
    private static boolean lastRp = false, lastRl = false, lastRi = false, lastRh = false;
    private static int    roadTimer = 0;
    private static Vec3d  roadCenter = null;
    private static final int    ROAD_TICKS  = 300;   // 15s
    private static final double ROAD_RADIUS = 30.0;
    private static final float  ROAD_DMG    = 5.0f;
    private static int   dashTimer = 0;
    private static final int DASH_TICKS = 20;
    private static int   hornTimer = 0;
    private static Vec3d hornPos = null, hornDir = null;
    private static final int HORN_TICKS = 16;
    private static final List<Crow> bikes = new ArrayList<>();
    private static final int MAX_BIKES = 5;
    private static final int NPINK = 0xFFFF46B4, NCYAN = 0xFF3CE6F0;

    // ===== KING'S WORLD — 왕의 세계 (held King Scepter) + Knight =====
    private static boolean lastKd = false, lastAu = false, lastGu = false, lastTr = false, lastKn = false;
    private static int    kingTimer = 0;
    private static Vec3d  kingCenter = null;
    private static UUID   knightId = null;
    private static String knightName = "—";
    private static boolean kingHpPaid = false;
    private static final int    KING_TICKS  = 400;   // 20s reign
    private static final double KING_RADIUS = 24.0;
    private static final float  KING_DMG    = 6.0f;
    private static int auraTimer = 0;   private static final int AURA_TICKS = 16;
    private static int guardTimer = 0;  private static final int GUARD_TICKS = 60;   // 3s guardian hands
    private static int knightDashTimer = 0; private static final int KDASH_TICKS = 16;
    private static final int ROYP = 0xFFB07CF0;

    private static final double RANGE = 20.0;   // detection radius
    private static final double REACH = 3.0;    // melee reach
    private static final float  AIM   = 0.20f;  // aim-assist strength

    // ---- drones ----
    private static final List<Drone> drones = new ArrayList<>();
    private static final int    MAX_DRONES = 4;
    private static final double DRONE_RANGE = 14.0;
    private static final float  DRONE_DMG   = 20.0f;  // laser damage (was 4)

    private static final int CY=0xFF41E9FF, AMB=0xFFFFB638, DIM=0xFF5B7C8A, RED=0xFFFF4D6D, VIO=0xFFB98CFF;

    private static final ItemStack DRONE_STACK = new ItemStack(VoidHunt.VOID_DRONE);
    private static final ItemStack SAT_STACK   = new ItemStack(VoidHunt.VOID_SATELLITE);
    private static final ItemStack GEAR_STACK  = new ItemStack(VoidHunt.VOID_GEAR);
    private static final ItemStack CORE_STACK  = new ItemStack(VoidHunt.VOID_CORE);
    private static final ItemStack CROW_STACK  = new ItemStack(VoidHunt.CROW);
    private static final ItemStack TOMB_STACK  = new ItemStack(VoidHunt.TOMBSTONE);
    private static final ItemStack TORII_STACK = new ItemStack(VoidHunt.TORII);
    private static final ItemStack TREE_STACK  = new ItemStack(VoidHunt.DEAD_TREE);
    private static final ItemStack MONU_STACK  = new ItemStack(VoidHunt.GRAVE_MONUMENT);
    private static final ItemStack GALLOWS_STACK = new ItemStack(VoidHunt.GALLOWS_CAGE);
    private static final ItemStack SPIKE_STACK   = new ItemStack(VoidHunt.SKULL_SPIKE);
    private static final ItemStack PILLAR_STACK  = new ItemStack(VoidHunt.ARENA_PILLAR);
    private static final ItemStack ARCH_STACK    = new ItemStack(VoidHunt.ARENA_ARCH);
    private static final ItemStack HAND_STACK    = new ItemStack(VoidHunt.GOD_HAND);
    private static final ItemStack SHARK_STACK   = new ItemStack(VoidHunt.SHARK);
    private static final ItemStack CORAL_STACK   = new ItemStack(VoidHunt.CORAL_PILLAR);
    private static final ItemStack TEMPLE_STACK  = new ItemStack(VoidHunt.SEA_TEMPLE);
    private static final ItemStack BIKE_STACK    = new ItemStack(VoidHunt.GHOST_BIKE);
    private static final ItemStack LIGHT_STACK   = new ItemStack(VoidHunt.STREET_LIGHT);
    private static final ItemStack NGATE_STACK   = new ItemStack(VoidHunt.NEON_GATE);
    private static final ItemStack THRONE_STACK  = new ItemStack(VoidHunt.KING_THRONE);
    private static final ItemStack RPILLAR_STACK = new ItemStack(VoidHunt.ROYAL_PILLAR);

    static final class Drone {
        Vec3d pos; Vec3d goal; int idx; int cd = 0;
        Drone(Vec3d p, int idx){ this.pos = p; this.idx = idx; }
    }
    static final class Crow {
        Vec3d pos; double face; int idx; int cd = 0;
        Crow(Vec3d p, int idx){ this.pos = p; this.idx = idx; }
    }

    @Override
    public void onInitializeClient() {
        // Keys are read RAW (InputUtil) instead of registered keybinds,
        // to bypass saved-binding / conflict issues entirely.
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        HudRenderCallback.EVENT.register(this::onHud);
        WorldRenderEvents.AFTER_ENTITIES.register(this::onWorldRender);
        // receive other players' effect snapshots
        ClientPlayNetworking.registerGlobalReceiver(VoidNet.SyncS2C.ID, (payload, context) ->
            context.client().execute(() -> {
                VoidNet.Fx fx = payload.fx(); fx.age = 0;
                REMOTE.put(payload.owner(), fx);
            }));
    }

    // Render the 3D drone model at each drone's world position.
    private void onWorldRender(WorldRenderContext ctx) {
        MatrixStack ms = ctx.matrixStack();
        if (ms == null) return;
        VertexConsumerProvider vcp = ctx.consumers();
        Camera cam = ctx.camera();
        net.minecraft.util.math.Vec3d camPos = cam.getPos();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        float spin = mc.player.age * 2.0f;
        int light = LightmapTextureManager.pack(15, 15);
        // DOMAIN structures (gears + central reactor core)
        if (domainTimer > 0 && domainCenter != null)
            renderDomain(ms, vcp, mc, camPos, light);
        // CROW GRAVE world + summoned crows
        if (crowDomainTimer > 0 && crowCenter != null)
            renderCrowWorld(ms, vcp, mc, camPos, light);
        // DUEL ARENA colosseum
        if (arenaTimer > 0 && arenaCenter != null)
            renderArena(ms, vcp, mc, camPos, light);
        // OCEAN WORLD (sunken temple, coral, sharks)
        if (seaTimer > 0 && seaCenter != null)
            renderSeaWorld(ms, vcp, mc, camPos, light);
        for (Crow sk : sharks)
            renderModel(SHARK_STACK, ms, vcp, mc, camPos, light, sk.pos.x, sk.pos.y, sk.pos.z, 1.1f, (float) sk.face, 0f, false);
        // BIKER HIGHWAY (neon gate, street lights, ghost bikes)
        if (roadTimer > 0 && roadCenter != null)
            renderRoadWorld(ms, vcp, mc, camPos, light);
        for (Crow bk : bikes)
            renderModel(BIKE_STACK, ms, vcp, mc, camPos, light, bk.pos.x, bk.pos.y, bk.pos.z, 1.2f, (float) bk.face, 0f, false);
        // KING'S WORLD (throne, royal pillars) + guardian hands
        if (kingTimer > 0 && kingCenter != null)
            renderKingWorld(ms, vcp, mc, camPos, light);
        if (guardTimer > 0 && mc.player != null)
            renderGuardianHands(ms, vcp, mc, camPos, light, mc.player.getPos(), mc.player.age);
        // 전사의 심판 — the giant God-Warrior hand descends onto the doomed foe
        if (judgmentTimer > 0 && judgmentPos != null) {
            int el = JUDGE_TICKS - judgmentTimer;
            float prog = Math.min(1f, el / 22f);
            double startY = judgmentPos.y + 30;
            double curY = startY - (startY - (judgmentPos.y + 2.5)) * prog;
            renderModel(HAND_STACK, ms, vcp, mc, camPos, light, judgmentPos.x, curY, judgmentPos.z, 6.0f, 0f, 0f, false);
        }
        for (Crow cw : crows) {
            renderModel(CROW_STACK, ms, vcp, mc, camPos, light, cw.pos.x, cw.pos.y, cw.pos.z,
                0.9f, (float) cw.face, 0f, false);
        }
        for (Drone d : drones) {
            ms.push();
            ms.translate(d.pos.x - camPos.x, d.pos.y - camPos.y, d.pos.z - camPos.z);
            ms.scale(0.8f, 0.8f, 0.8f);
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin));
            ms.translate(-0.5, -0.5, -0.5); // center the 1-block item model
            mc.getItemRenderer().renderItem(DRONE_STACK, ModelTransformationMode.FIXED,
                light, OverlayTexture.DEFAULT_UV, ms, vcp, mc.world, 0);
            ms.pop();
        }
        // ULTIMATE satellite hovering above the player
        if (ultTimer > 0 && mc.player != null) {
            Vec3d sat = mc.player.getEyePos().add(0, ULT_HEIGHT, 0);
            ms.push();
            ms.translate(sat.x - camPos.x, sat.y - camPos.y, sat.z - camPos.z);
            ms.scale(4.0f, 4.0f, 4.0f);
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(mc.player.age * 3.0f));
            ms.translate(-0.5, -0.5, -0.5);
            mc.getItemRenderer().renderItem(SAT_STACK, ModelTransformationMode.FIXED,
                light, OverlayTexture.DEFAULT_UV, ms, vcp, mc.world, 0);
            ms.pop();
        }
        // paint every other player's shared effects
        if (!REMOTE.isEmpty())
            for (var e : REMOTE.entrySet()) renderRemote(e.getKey(), e.getValue(), ms, vcp, mc, camPos, light);
    }

    // Render the machine-world structures inside the domain: a central reactor
    // core, standing gears around the perimeter, and big gears on the floor.
    private void renderDomain(MatrixStack ms, VertexConsumerProvider vcp, MinecraftClient mc,
                              Vec3d camPos, int light) {
        Vec3d ctr = domainCenter;
        float age = mc.player.age;
        int elapsed = DOMAIN_TICKS - domainTimer;
        float rise = Math.min(1.0f, elapsed / 14.0f);   // structures rise as domain forms

        // ---- central reactor core (grand centerpiece), grows in and stands on the floor ----
        float cScale = 2.4f * (0.25f + 0.75f * rise);
        double coreHalf = (23.75 / 16.0) * cScale;       // half-height so the base sits on the floor
        double cy = ctr.y + coreHalf;
        ms.push();
        ms.translate(ctr.x - camPos.x, cy - camPos.y, ctr.z - camPos.z);
        ms.scale(cScale, cScale, cScale);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(age * 0.6f));
        ms.translate(-0.5, -0.5, -0.5);
        mc.getItemRenderer().renderItem(CORE_STACK, ModelTransformationMode.FIXED,
            light, OverlayTexture.DEFAULT_UV, ms, vcp, mc.world, 0);
        ms.pop();

        // ---- colossal SKY HALO gear crowning the whole arena ----
        renderGear(ms, vcp, mc, camPos, light, ctr.x, ctr.y + 26 * rise, ctr.z,
            11.0f * (0.3f + 0.7f * rise), 0f, age * 0.4f, false);

        // ---- central GEAR TOWER: big concentric rings stacked up the core beam ----
        float[] towScale = {6.5f, 5.6f, 4.8f, 4.0f, 3.2f, 2.4f};
        for (int i = 0; i < towScale.length; i++) {
            double ty = ctr.y + (4.5 + i * 3.6) * rise;
            float sc = towScale[i] * (0.25f + 0.75f * rise);
            renderGear(ms, vcp, mc, camPos, light, ctr.x, ty, ctr.z, sc,
                0f, age * ((i % 2 == 0) ? 1.1f : -1.1f), false);
        }

        // ---- perimeter SPIRE TOWERS (smaller cores ring the arena like a machine city) ----
        int TOWERS = 6;
        for (int i = 0; i < TOWERS; i++) {
            double a = i * (Math.PI * 2 / TOWERS) + Math.PI / TOWERS;
            double tx = ctr.x + Math.cos(a) * (DOMAIN_RADIUS * 1.08);
            double tz = ctr.z + Math.sin(a) * (DOMAIN_RADIUS * 1.08);
            float sc = 1.35f * (0.25f + 0.75f * rise);
            double th = (23.75 / 16.0) * sc;
            ms.push();
            ms.translate(tx - camPos.x, (ctr.y + th) - camPos.y, tz - camPos.z);
            ms.scale(sc, sc, sc);
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(age * 0.3f + i * 30));
            ms.translate(-0.5, -0.5, -0.5);
            mc.getItemRenderer().renderItem(CORE_STACK, ModelTransformationMode.FIXED,
                light, OverlayTexture.DEFAULT_UV, ms, vcp, mc.world, 0);
            ms.pop();
        }

        // ---- standing gears around the perimeter (like cogs on the dome wall) ----
        int RINGN = 8;
        double rr = DOMAIN_RADIUS * 0.92;
        for (int i = 0; i < RINGN; i++) {
            double theta = i * (Math.PI * 2 / RINGN) + age * 0.01;
            double gx = ctr.x + Math.cos(theta) * rr;
            double gz = ctr.z + Math.sin(theta) * rr;
            double gy = ctr.y + (2.5 + (i % 3) * 2.5) * rise;
            float facing = (float) Math.toDegrees(theta);
            float gspin = age * ((i % 2 == 0) ? 3.5f : -3.5f);
            renderGear(ms, vcp, mc, camPos, light, gx, gy, gz, 2.8f, facing, gspin, true);
        }

        // ---- floating gears drifting at mid-air, varied sizes/tilt ----
        int FLOAT = 6;
        for (int i = 0; i < FLOAT; i++) {
            double a = i * (Math.PI * 2 / FLOAT) + age * 0.006;
            double fx = ctr.x + Math.cos(a) * (DOMAIN_RADIUS * 0.68);
            double fz = ctr.z + Math.sin(a) * (DOMAIN_RADIUS * 0.68);
            double fy = ctr.y + (7.0 + (i % 4) * 3.0) * rise
                        + Math.sin((age * 0.03) + i) * 0.8;
            float sc = (1.6f + (i % 3) * 0.7f);
            renderGear(ms, vcp, mc, camPos, light, fx, fy, fz, sc,
                (float) Math.toDegrees(a), age * ((i % 2 == 0) ? 2.2f : -2.2f), true);
        }

        // ---- big flat gears on the floor ----
        renderGear(ms, vcp, mc, camPos, light, ctr.x, ctr.y + 0.15, ctr.z, 5.0f, 0f, age * 1.4f, false);
        int FLOORN = 6;
        for (int i = 0; i < FLOORN; i++) {
            double a = i * (Math.PI * 2 / FLOORN) + Math.PI / FLOORN;
            double fx = ctr.x + Math.cos(a) * (DOMAIN_RADIUS * 0.6);
            double fz = ctr.z + Math.sin(a) * (DOMAIN_RADIUS * 0.6);
            renderGear(ms, vcp, mc, camPos, light, fx, ctr.y + 0.1, fz, 2.4f,
                0f, age * ((i % 2 == 0) ? -2.6f : 2.6f), false);
        }
    }

    private void renderGear(MatrixStack ms, VertexConsumerProvider vcp, MinecraftClient mc,
                            Vec3d camPos, int light, double x, double y, double z,
                            float scale, float facing, float spin, boolean upright) {
        ms.push();
        ms.translate(x - camPos.x, y - camPos.y, z - camPos.z);
        ms.scale(scale, scale, scale);
        if (upright) {
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(facing));
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
        }
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin));
        ms.translate(-0.5, -0.5, -0.5);
        mc.getItemRenderer().renderItem(GEAR_STACK, ModelTransformationMode.FIXED,
            light, OverlayTexture.DEFAULT_UV, ms, vcp, mc.world, 0);
        ms.pop();
    }

    // Generic world model renderer (upright=false: stand normally & face; true: lay flat like a disc).
    private void renderModel(ItemStack stack, MatrixStack ms, VertexConsumerProvider vcp, MinecraftClient mc,
                             Vec3d camPos, int light, double x, double y, double z,
                             float scale, float facing, float spin, boolean upright) {
        ms.push();
        ms.translate(x - camPos.x, y - camPos.y, z - camPos.z);
        ms.scale(scale, scale, scale);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(facing));
        if (upright) ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
        if (spin != 0) ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin));
        ms.translate(-0.5, -0.5, -0.5);
        mc.getItemRenderer().renderItem(stack, ModelTransformationMode.FIXED,
            light, OverlayTexture.DEFAULT_UV, ms, vcp, mc.world, 0);
        ms.pop();
    }

    private boolean heldFan(MinecraftClient c) {
        return c.player != null
            && (c.player.getMainHandStack().isOf(VoidHunt.CROW_FAN)
             || c.player.getOffHandStack().isOf(VoidHunt.CROW_FAN));
    }

    private boolean shadesOn(MinecraftClient c) {
        return c.player != null
            && c.player.getEquippedStack(EquipmentSlot.HEAD).isOf(VoidHunt.VOID_SHADES);
    }
    private boolean active(MinecraftClient c) { return huntMode && shadesOn(c); }

    private void onTick(MinecraftClient c) {
        // RAW key reads (edge-detected) — no keybind system, no conflicts
        long win = c.getWindow().getHandle();
        boolean noScreen = c.currentScreen == null;
        boolean hNow = noScreen && InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_H);
        boolean kNow = noScreen && InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_K);
        boolean lNow = noScreen && InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_O);
        boolean gNow = noScreen && InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_G);
        if (hNow && !lastH) huntMode = !huntMode;
        if (kNow && !lastK && arenaTimer <= 0) toggleDrones(c);   // no summons inside the arena
        if (lNow && !lastL && shadesOn(c) && ultTimer <= 0 && arenaTimer <= 0) {  // ULTIMATE
            if (drones.size() < MAX_DRONES) spawnDrones(c);
            ultTimer = ULT_TICKS;
        }
        if (gNow && !lastG && shadesOn(c) && domainTimer <= 0 && c.player != null) {  // DOMAIN EXPANSION
            domainTimer = DOMAIN_TICKS;
            domainCenter = c.player.getPos();
        }
        lastH = hNow; lastK = kNow; lastL = lNow; lastG = gNow;
        target = null;

        if (c.player == null || c.world == null || c.interactionManager == null) return;

        // CROW FAN kit runs independently of the shades (only needs the fan in hand)
        tickCrowKit(c);
        // DUEL ARENA kit — only needs the Duel Greatsword in hand
        tickArenaKit(c);
        // OCEAN WORLD kit — only needs the Sea Trident in hand
        tickSeaKit(c);
        // BIKER HIGHWAY kit — only needs the Neon Pipe in hand
        tickRoadKit(c);
        // KING'S WORLD kit (scepter) + Knight kit (knight blade)
        tickKingKit(c);
        tickKnightKit(c);
        // multiplayer: broadcast my effects, paint everyone else's
        sendLocalFx(c);
        tickRemotes(c);

        // kill tracking: a target we hit has died
        if (attackedTarget != null && (attackedTarget.isRemoved() || !attackedTarget.isAlive())) {
            kills++; combo++; comboTimer = 200; attackedTarget = null;
        }
        if (comboTimer > 0) { comboTimer--; if (comboTimer == 0) combo = 0; }

        if (!shadesOn(c)) { drones.clear(); ultTimer = 0; domainTimer = 0; frozen.clear(); return; }

        if (ultTimer > 0) tickUltimate(c);
        if (domainTimer > 0) { tickDomain(c); domainTimer--; if (domainTimer == 0) frozen.clear(); }

        // ---- AUTO-TARGET + AIM + ATTACK (only while hunt mode on) ----
        if (active(c)) {
            Box box = c.player.getBoundingBox().expand(RANGE);
            // lock on to hostiles AND other players (never yourself), only if visible
            List<LivingEntity> foes = c.world.getEntitiesByClass(LivingEntity.class, box,
                e -> e.isAlive() && e != c.player && c.player.canSee(e)
                    && ((e instanceof HostileEntity) || (e instanceof PlayerEntity)));
            target = foes.stream().min(Comparator.comparingDouble(c.player::squaredDistanceTo)).orElse(null);

            if (target != null) {
                if (aimAssist) aimAt(c.player, target);
                boolean inReach = c.player.squaredDistanceTo(target) <= REACH * REACH;
                boolean charged = c.player.getAttackCooldownProgress(0.0f) >= 1.0f;
                if (inReach && charged) {
                    c.interactionManager.attackEntity(c.player, target);
                    c.player.swingHand(Hand.MAIN_HAND);
                    attackedTarget = target;
                }
            }
            // AUTO-AIM projectiles: home the player's arrows/projectiles onto the target
            steerProjectiles(c);
        }

        // ---- DRONE COMMAND: right-click sends drones to the looked-at spot ----
        boolean useNow = c.options.useKey.isPressed();
        if (useNow && !lastUse && !drones.isEmpty()) {
            Vec3d g = (c.crosshairTarget != null)
                ? c.crosshairTarget.getPos()
                : c.player.getEyePos().add(c.player.getRotationVec(1.0f).multiply(10.0));
            for (Drone d : drones) d.goal = g;
        }
        lastUse = useNow;

        tickDrones(c);
        if (ultTimer > 0) ultTimer--;
    }

    // Steer any player-fired projectiles toward the locked target so they always hit.
    private void steerProjectiles(MinecraftClient c) {
        if (target == null || c.player == null || c.world == null) return;
        ClientPlayerEntity p = c.player;
        Vec3d aim = target.getBoundingBox().getCenter();
        List<ProjectileEntity> projs = c.world.getEntitiesByClass(ProjectileEntity.class,
            p.getBoundingBox().expand(64.0), pr -> pr.getOwner() == p);
        MinecraftServer server = c.getServer();
        for (ProjectileEntity pr : projs) {
            Vec3d dir = aim.subtract(pr.getPos());
            if (dir.lengthSquared() < 0.5) continue;
            double speed = Math.max(pr.getVelocity().length(), 1.2);
            final Vec3d nv = dir.normalize().multiply(speed);
            pr.setVelocity(nv);
            if (server != null) {
                final UUID id = pr.getUuid();
                server.execute(() -> {
                    ServerWorld sw = server.getWorld(c.world.getRegistryKey());
                    if (sw == null) return;
                    Entity se = sw.getEntity(id);
                    if (se != null) se.setVelocity(nv);
                });
            }
        }
    }

    private void spawnDrones(MinecraftClient c) {
        drones.clear();
        Vec3d base = c.player.getEyePos();
        for (int i = 0; i < MAX_DRONES; i++) {
            double a = i * (Math.PI * 2 / MAX_DRONES);
            drones.add(new Drone(base.add(Math.cos(a) * 2.0, 0.6, Math.sin(a) * 2.0), i));
        }
    }

    private void toggleDrones(MinecraftClient c) {
        if (c.player == null || !shadesOn(c)) return;
        if (drones.isEmpty()) spawnDrones(c); else drones.clear();
    }

    private void tickDrones(MinecraftClient c) {
        if (drones.isEmpty()) return;
        ClientPlayerEntity p = c.player;
        ClientWorld w = c.world;
        boolean ult = ultTimer > 0;
        Vec3d sat = p.getEyePos().add(0, ULT_HEIGHT, 0);
        double step = Math.PI * 2 / MAX_DRONES;
        for (Drone d : drones) {
            Vec3d goalPos;
            if (ult) {                                   // orbit the satellite in a fast ring
                double a = d.idx * step + p.age * 0.25;
                goalPos = sat.add(Math.cos(a) * 5.0, 0, Math.sin(a) * 5.0);
            } else if (d.goal != null) {                 // commanded position (spread around it)
                double a = d.idx * step;
                goalPos = d.goal.add(Math.cos(a) * 1.8, 1.0, Math.sin(a) * 1.8);
            } else {                                     // slow orbit around the player
                double a = d.idx * step + p.age * 0.02;
                goalPos = p.getEyePos().add(Math.cos(a) * 2.4, 0.7, Math.sin(a) * 2.4);
            }
            d.pos = d.pos.add(goalPos.subtract(d.pos).multiply(ult ? 0.25 : 0.15));

            for (Drone o : drones) {
                if (o == d) continue;
                Vec3d diff = d.pos.subtract(o.pos);
                double dist = diff.length();
                if (dist < 2.0 && dist > 0.0001)
                    d.pos = d.pos.add(diff.normalize().multiply((2.0 - dist) * 0.5));
            }

            if ((p.age % 6) == 0)
                w.addParticle(ParticleTypes.SOUL_FIRE_FLAME, d.pos.x, d.pos.y - 0.35, d.pos.z, 0, -0.01, 0);

            if (!ult) {   // normal: laser nearest hostile (ult handles its own damage)
                Box b = new Box(d.pos.subtract(DRONE_RANGE, DRONE_RANGE, DRONE_RANGE),
                                d.pos.add(DRONE_RANGE, DRONE_RANGE, DRONE_RANGE));
                List<MobEntity> near = w.getEntitiesByClass(MobEntity.class, b,
                    e -> e.isAlive() && (e instanceof HostileEntity));
                MobEntity t = near.stream()
                    .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(d.pos.x, d.pos.y, d.pos.z)))
                    .orElse(null);
                if (d.cd > 0) d.cd--;
                if (t != null && d.cd <= 0) {
                    fireLaser(w, d.pos, t.getEyePos());
                    damage(c, t, DRONE_DMG);
                    d.cd = 12;
                }
            }
        }
    }

    // ULTIMATE: orbital-strike satellite obliterates all creatures for 10s.
    private void tickUltimate(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        ClientWorld w = c.world;
        Vec3d sat = p.getEyePos().add(0, ULT_HEIGHT, 0);
        // vertical beam column
        for (int i = 0; i < 30; i++) {
            double yy = sat.y - i * 0.6;
            w.addParticle(ParticleTypes.END_ROD,
                p.getX() + (Math.random() - 0.5) * 0.6, yy, p.getZ() + (Math.random() - 0.5) * 0.6, 0, 0, 0);
        }
        List<MobEntity> mobs = w.getEntitiesByClass(MobEntity.class,
            p.getBoundingBox().expand(ULT_RADIUS), e -> e.isAlive());
        for (MobEntity m : mobs) {
            if ((p.age % 2) == 0) fireLaser(w, sat, m.getBoundingBox().getCenter());
            if ((p.age % 4) == 0) damage(c, m, ULT_DMG);
        }
    }

    // DOMAIN EXPANSION — builds an enclosed machine dome; every hostile inside
    // is guaranteed-hit (sure-hit) and bound while it lasts.
    private void tickDomain(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        ClientWorld w = c.world;
        if (domainCenter == null) domainCenter = p.getPos();
        Vec3d ctr = domainCenter;
        double R = DOMAIN_RADIUS;
        long t = p.age;
        int elapsed = DOMAIN_TICKS - domainTimer;
        double form = Math.min(1.0, elapsed / 12.0);   // 0..1 barrier-forming sweep
        double spin = t * 0.05;

        // vertical ribs (meridians) — the dome cage
        int MER = 10, SEG = 8;
        for (int m = 0; m < MER; m++) {
            double theta = m * (Math.PI * 2 / MER) + spin;
            for (int s = 0; s <= SEG; s++) {
                double elev = (Math.PI / 2) * s / SEG * form;
                double hr = R * Math.cos(elev);
                w.addParticle(ParticleTypes.END_ROD,
                    ctr.x + Math.cos(theta) * hr, ctr.y + R * Math.sin(elev), ctr.z + Math.sin(theta) * hr, 0, 0, 0);
            }
        }
        // horizontal latitude rings (every other tick)
        if ((t & 1) == 0) {
            int LAT = 3;
            for (int l = 1; l <= LAT; l++) {
                double elev = (Math.PI / 2) * l / (LAT + 1) * form;
                double hr = R * Math.cos(elev);
                double y = ctr.y + R * Math.sin(elev);
                for (int s = 0; s < 24; s++) {
                    double a = s * (Math.PI * 2 / 24) - spin * 0.6;
                    w.addParticle(ParticleTypes.SOUL_FIRE_FLAME, ctr.x + Math.cos(a) * hr, y, ctr.z + Math.sin(a) * hr, 0, 0, 0);
                }
            }
            // floor circuit-grid rings
            for (int rr = 1; rr <= 3; rr++) {
                double rad = R * rr / 3.0 * form;
                for (int s = 0; s < 24; s++) {
                    double a = s * (Math.PI * 2 / 24) + spin * 0.4;
                    w.addParticle(ParticleTypes.ELECTRIC_SPARK, ctr.x + Math.cos(a) * rad, ctr.y + 0.05, ctr.z + Math.sin(a) * rad, 0, 0, 0);
                }
            }
        }

        // ===== mystical layer: light pillar, rune mandala, glyphs, sky halo =====
        // central light pillar rising to the heavens
        for (int i = 0; i < 22; i++) {
            double yy = ctr.y + i * 1.15 * form;
            double jj = (Math.random() - 0.5) * 0.4;
            w.addParticle(ParticleTypes.END_ROD, ctr.x + jj, yy, ctr.z + jj, 0, 0.02, 0);
        }
        w.addParticle(ParticleTypes.FIREWORK, ctr.x, ctr.y + 1 + Math.random() * 20 * form, ctr.z, 0, 0.04, 0);
        // arcane glyphs swirling around the core
        double gspin = t * 0.04;
        for (int i = 0; i < 10; i++) {
            double a = i * (Math.PI * 2 / 10) + gspin;
            double gy = ctr.y + 3 + (i % 5) * 2.2;
            w.addParticle(ParticleTypes.ENCHANT, ctr.x + Math.cos(a) * R * 0.4, gy, ctr.z + Math.sin(a) * R * 0.4, 0, 0, 0);
            w.addParticle(ParticleTypes.PORTAL, ctr.x + Math.cos(-a) * R * 0.55, gy - 1, ctr.z + Math.sin(-a) * R * 0.55, 0, 0, 0);
        }
        if ((t & 1) == 0) {
            double mspin = t * 0.02;
            // big rotating rune mandala on the ground
            for (int rr = 4; rr <= 6; rr++) {
                double rad = R * rr / 6.0 * form;
                for (int s = 0; s < 24; s++) {
                    double a = s * (Math.PI * 2 / 24) - mspin;
                    w.addParticle(ParticleTypes.SOUL_FIRE_FLAME, ctr.x + Math.cos(a) * rad, ctr.y + 0.04, ctr.z + Math.sin(a) * rad, 0, 0, 0);
                }
            }
            for (int s = 0; s < 8; s++) {
                double a = s * (Math.PI / 4) + mspin;
                for (double d2 = 1.5; d2 < R * form; d2 += 1.7)
                    w.addParticle(ParticleTypes.ELECTRIC_SPARK, ctr.x + Math.cos(a) * d2, ctr.y + 0.04, ctr.z + Math.sin(a) * d2, 0, 0, 0);
            }
            // sky halo ring high above
            double hy = ctr.y + 22 * form, hr = R * 1.1;
            for (int s = 0; s < 40; s++) {
                double a = s * (Math.PI * 2 / 40) + spin * 0.3;
                w.addParticle(ParticleTypes.END_ROD, ctr.x + Math.cos(a) * hr, hy, ctr.z + Math.sin(a) * hr, 0, 0, 0);
            }
        }
        // low violet mist for mystery
        if ((t % 3) == 0)
            for (int i = 0; i < 6; i++) {
                double a = Math.random() * Math.PI * 2, d2 = Math.random() * R * form;
                w.addParticle(ParticleTypes.DRAGON_BREATH, ctr.x + Math.cos(a) * d2, ctr.y + 0.1, ctr.z + Math.sin(a) * d2, 0, 0.004, 0);
            }

        // FREEZE: lock every creature/player (but the caster) in place within 20 chunks
        freezeField(c);

        // SURE-HIT: bind + damage every hostile trapped inside the domain
        Box box = new Box(ctr.subtract(R, R, R), ctr.add(R, R, R));
        List<MobEntity> mobs = w.getEntitiesByClass(MobEntity.class, box,
            e -> e.isAlive() && (e instanceof HostileEntity) && e.getPos().distanceTo(ctr) <= R + 1.0);
        Vec3d apex = ctr.add(0, R, 0);
        for (MobEntity m : mobs) {
            Vec3d mc2 = m.getBoundingBox().getCenter();
            if ((t % 4) == 0) fireLaser(w, apex, mc2);
            if ((t % 3) == 0) domainHit(c, m, DOMAIN_DMG);
            w.addParticle(ParticleTypes.ELECTRIC_SPARK, mc2.x, mc2.y, mc2.z, 0, 0, 0);
        }
    }

    // Freeze all living entities (and other players) except the caster, in a 20-chunk
    // radius: each is held at its captured lock position. Runs on the integrated server.
    private void freezeField(MinecraftClient c) {
        if (renderingRemote) return;
        MinecraftServer server = c.getServer();
        if (server == null || domainCenter == null) return;
        UUID casterId = c.player.getUuid();
        Vec3d ctr = domainCenter;
        double R = FREEZE_RADIUS;
        server.execute(() -> {
            ServerWorld sw = server.getWorld(c.world.getRegistryKey());
            if (sw == null) return;
            Box b = new Box(ctr.subtract(R, R, R), ctr.add(R, R, R));
            for (Entity e : sw.getOtherEntities(null, b)) {
                if (!(e instanceof LivingEntity)) continue;
                if (e.getUuid().equals(casterId)) continue;
                Vec3d lock = frozen.computeIfAbsent(e.getUuid(), k -> e.getPos());
                e.setVelocity(0, 0, 0);
                e.fallDistance = 0;
                if (e instanceof net.minecraft.server.network.ServerPlayerEntity sp)
                    sp.requestTeleport(lock.x, lock.y, lock.z);   // forces the client to hold
                else
                    e.setPosition(lock.x, lock.y, lock.z);        // snaps mobs back each tick
            }
        });
    }

    // Domain sure-hit: real damage + bind (slowness/weakness) in singleplayer.
    private void domainHit(MinecraftClient c, MobEntity mob, float amt) {
        if (renderingRemote) return;
        MinecraftServer server = c.getServer();
        if (server == null) return;
        UUID id = mob.getUuid();
        server.execute(() -> {
            ServerWorld sw = server.getWorld(c.world.getRegistryKey());
            if (sw == null) return;
            Entity se = sw.getEntity(id);
            if (se instanceof LivingEntity le) {
                le.damage(sw, sw.getDamageSources().magic(), amt);
                le.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 4));
                le.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 30, 2));
            }
        });
    }

    // =====================================================================
    //  CROW FAN KIT — 세계 / 까마귀 소환 / 까마귀의 날개 / 망자 봉인
    // =====================================================================
    private void tickCrowKit(MinecraftClient c) {
        boolean held = heldFan(c);
        long win = c.getWindow().getHandle();
        boolean ns = c.currentScreen == null;
        boolean rNow = held && ns && InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_R);
        boolean cNow = held && ns && InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_C);
        boolean vNow = held && ns && InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_V);
        boolean xNow = held && ns && InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_X);
        if (rNow && !lastR && crowDomainTimer <= 0) { crowDomainTimer = CROW_DOMAIN_TICKS; crowCenter = c.player.getPos(); }
        if (cNow && !lastC && arenaTimer <= 0) summonCrows(c);   // no summons inside the arena
        if (vNow && !lastV && wingsTimer <= 0) activateWings(c);
        if (xNow && !lastX && sealTimer <= 0) startSeal(c);
        lastR = rNow; lastC = cNow; lastV = vNow; lastX = xNow;

        if (!held) crows.clear();                       // crows are bound to the fan
        if (crowDomainTimer > 0) { tickCrowDomain(c); crowDomainTimer--; }
        if (wingsTimer > 0) { tickWings(c); wingsTimer--; }
        if (sealTimer > 0) { tickSeal(c); sealTimer--; }
        if (!crows.isEmpty()) tickCrows(c);
    }

    // ---- 까마귀 소환 (summon a murder of crows that hunt for you) ----
    private void summonCrows(MinecraftClient c) {
        crows.clear();
        Vec3d base = c.player.getEyePos();
        for (int i = 0; i < MAX_CROWS; i++) {
            double a = i * (Math.PI * 2 / MAX_CROWS);
            crows.add(new Crow(base.add(Math.cos(a) * 3.0, 1.5 + Math.sin(i) * 0.5, Math.sin(a) * 3.0), i));
        }
    }

    private void tickCrows(MinecraftClient c) {
        ClientPlayerEntity p = c.player; ClientWorld w = c.world; long t = p.age;
        double step = Math.PI * 2 / Math.max(1, crows.size());
        List<MobEntity> host = w.getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(CROW_ATTACK_RANGE),
            e -> e.isAlive() && (e instanceof HostileEntity));
        for (Crow cw : crows) {
            MobEntity tgt = host.stream()
                .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(cw.pos.x, cw.pos.y, cw.pos.z))).orElse(null);
            Vec3d goal;
            if (tgt != null) goal = tgt.getBoundingBox().getCenter().add(0, 0.3, 0);
            else { double a = cw.idx * step + t * 0.05;
                goal = p.getEyePos().add(Math.cos(a) * 3.2, 1.6 + Math.sin(t * 0.05 + cw.idx) * 0.4, Math.sin(a) * 3.2); }
            Vec3d prev = cw.pos;
            cw.pos = cw.pos.add(goal.subtract(cw.pos).multiply(0.2));
            double mvx = cw.pos.x - prev.x, mvz = cw.pos.z - prev.z;
            if (mvx * mvx + mvz * mvz > 1e-4) cw.face = Math.toDegrees(Math.atan2(-mvx, mvz));
            if ((t % 4) == 0) w.addParticle(ParticleTypes.SMOKE, cw.pos.x, cw.pos.y - 0.2, cw.pos.z, 0, 0, 0);
            if (cw.cd > 0) cw.cd--;
            if (tgt != null && cw.pos.distanceTo(tgt.getBoundingBox().getCenter()) < 2.2 && cw.cd <= 0) {
                crowHit(c, tgt, CROW_PECK_DMG); cw.cd = 10;
                Vec3d tc = tgt.getBoundingBox().getCenter();
                for (int k = 0; k < 5; k++) w.addParticle(ParticleTypes.CRIT, tc.x, tc.y, tc.z, 0, 0, 0);
            }
        }
    }

    // ---- 까마귀의 날개 (a leaping glide on crow wings) ----
    private void activateWings(MinecraftClient c) {
        wingsTimer = WINGS_TICKS;
        c.player.setVelocity(c.player.getVelocity().x, 1.15, c.player.getVelocity().z);
        playerEffect(c, StatusEffects.SLOW_FALLING, WINGS_TICKS, 0);
        playerEffect(c, StatusEffects.SPEED, WINGS_TICKS, 1);
        playerEffect(c, StatusEffects.JUMP_BOOST, WINGS_TICKS, 2);
        for (int i = 0; i < 22; i++)
            c.world.addParticle(ParticleTypes.SMOKE, c.player.getX() + (Math.random() - 0.5) * 1.6,
                c.player.getY() + 1.0 + (Math.random() - 0.5), c.player.getZ() + (Math.random() - 0.5) * 1.6, 0, 0, 0);
    }

    private void tickWings(MinecraftClient c) {
        ClientPlayerEntity p = c.player; ClientWorld w = c.world;
        Vec3d back = p.getRotationVec(1.0f).multiply(-0.6);
        for (int i = 0; i < 3; i++) {
            double sx = (Math.random() - 0.5) * 1.4, sy = (Math.random() - 0.5) * 1.0;
            w.addParticle(ParticleTypes.SMOKE, p.getX() + back.x + sx, p.getY() + 1.0 + sy, p.getZ() + back.z + sx, 0, 0, 0);
        }
        w.addParticle(ParticleTypes.SOUL_FIRE_FLAME, p.getX() + back.x, p.getY() + 1.2, p.getZ() + back.z, 0, 0.01, 0);
    }

    // ---- 망자 봉인 (a sealing sigil that roots and withers the dead) ----
    private void startSeal(MinecraftClient c) {
        sealTimer = SEAL_TICKS;
        Vec3d look = c.player.getRotationVec(1.0f);
        Vec3d flat = new Vec3d(look.x, 0, look.z);
        if (flat.lengthSquared() < 1e-4) flat = new Vec3d(0, 0, 1);
        flat = flat.normalize();
        sealCenter = c.player.getPos().add(flat.multiply(5.0)).add(0, 0.1, 0);
    }

    private void tickSeal(MinecraftClient c) {
        if (sealCenter == null) return;
        ClientWorld w = c.world; long t = c.player.age; Vec3d s = sealCenter;
        int el = SEAL_TICKS - sealTimer; double gr = Math.min(1.0, el / 8.0) * 5.0; double sp = t * 0.15;
        for (int rr = 1; rr <= 3; rr++) {
            double rad = gr * rr / 3.0; int seg = 28;
            for (int i = 0; i < seg; i++) {
                double a = i * (Math.PI * 2 / seg) + (rr % 2 == 0 ? -sp : sp);
                w.addParticle(rr == 2 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.DRAGON_BREATH,
                    s.x + Math.cos(a) * rad, s.y + 0.05, s.z + Math.sin(a) * rad, 0, 0, 0);
            }
        }
        for (int i = 0; i < 5; i++) {
            double a = i * (Math.PI * 2 / 5) + t * 0.02;
            for (double d2 = 0.5; d2 < gr; d2 += 0.8)
                w.addParticle(ParticleTypes.SOUL, s.x + Math.cos(a) * d2, s.y + 0.05, s.z + Math.sin(a) * d2, 0, 0, 0);
        }
        Box b = new Box(s.subtract(5.5, 3, 5.5), s.add(5.5, 4, 5.5));
        List<MobEntity> mobs = w.getEntitiesByClass(MobEntity.class, b,
            e -> e.isAlive() && (e instanceof HostileEntity) && e.getPos().distanceTo(s) <= 6.0);
        for (MobEntity m : mobs) {
            if ((t % 3) == 0) crowHit(c, m, 9.0f);
            sealBind(c, m);
            Vec3d mc2 = m.getBoundingBox().getCenter();
            w.addParticle(ParticleTypes.SOUL_FIRE_FLAME, mc2.x, mc2.y, mc2.z, 0, 0.03, 0);
        }
    }

    // ---- 까마귀들의 무덤 domain: poison everything, drape the world in ash ----
    private void tickCrowDomain(MinecraftClient c) {
        ClientPlayerEntity p = c.player; ClientWorld w = c.world;
        if (crowCenter == null) crowCenter = p.getPos();
        Vec3d ctr = crowCenter; double R = CROW_RADIUS; long t = p.age;
        int elapsed = CROW_DOMAIN_TICKS - crowDomainTimer; double form = Math.min(1.0, elapsed / 16.0);
        double spin = t * 0.03;
        int MER = 12, SEG = 9;
        for (int m = 0; m < MER; m++) {
            double th = m * (Math.PI * 2 / MER) + spin;
            for (int sgi = 0; sgi <= SEG; sgi++) {
                double elev = (Math.PI / 2) * sgi / SEG * form; double hr = R * Math.cos(elev);
                w.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    ctr.x + Math.cos(th) * hr, ctr.y + R * Math.sin(elev), ctr.z + Math.sin(th) * hr, 0, 0, 0);
            }
        }
        if ((t & 1) == 0) {
            for (int rr = 1; rr <= 4; rr++) {
                double rad = R * rr / 4.0 * form; int seg = 30;
                for (int sgi = 0; sgi < seg; sgi++) {
                    double a = sgi * (Math.PI * 2 / seg) + spin * 0.3;
                    w.addParticle(ParticleTypes.LARGE_SMOKE, ctr.x + Math.cos(a) * rad, ctr.y + 0.1, ctr.z + Math.sin(a) * rad, 0, 0.01, 0);
                    if ((sgi % 3) == 0)
                        w.addParticle(ParticleTypes.HAPPY_VILLAGER, ctr.x + Math.cos(a) * rad, ctr.y + 0.3 + Math.random() * 0.6, ctr.z + Math.sin(a) * rad, 0, 0, 0);
                }
            }
        }
        for (int i = 0; i < 8; i++) {
            double a = Math.random() * Math.PI * 2, d2 = Math.random() * R * form;
            w.addParticle(ParticleTypes.ASH, ctr.x + Math.cos(a) * d2, ctr.y + 8 + Math.random() * 10, ctr.z + Math.sin(a) * d2, 0, -0.02, 0);
        }
        // BLOOD RAIN — dark red drops falling across the whole grave
        DustParticleEffect blood = new DustParticleEffect(0x8A0303, 1.4f);
        for (int i = 0; i < 14; i++) {
            double a = Math.random() * Math.PI * 2, d2 = Math.random() * R * form;
            w.addParticle(blood, ctr.x + Math.cos(a) * d2, ctr.y + 4 + Math.random() * 14, ctr.z + Math.sin(a) * d2, 0, -0.35, 0);
        }
        Box box = new Box(ctr.subtract(R, R, R), ctr.add(R, R, R));
        List<MobEntity> mobs = w.getEntitiesByClass(MobEntity.class, box,
            e -> e.isAlive() && (e instanceof HostileEntity) && e.getPos().distanceTo(ctr) <= R + 1.0);
        for (MobEntity m : mobs) {
            Vec3d mc2 = m.getBoundingBox().getCenter();
            if ((t % 2) == 0) w.addParticle(ParticleTypes.SOUL, mc2.x, mc2.y, mc2.z, 0, 0.02, 0);
            if ((t % 4) == 0) crowHit(c, m, CROW_DMG);
        }
    }

    // server-side effect helpers
    private void crowHit(MinecraftClient c, MobEntity mob, float amt) {
        if (renderingRemote) return;
        MinecraftServer server = c.getServer(); if (server == null) return; UUID id = mob.getUuid();
        server.execute(() -> {
            ServerWorld sw = server.getWorld(c.world.getRegistryKey()); if (sw == null) return;
            Entity se = sw.getEntity(id);
            if (se instanceof LivingEntity le) {
                le.damage(sw, sw.getDamageSources().magic(), amt);
                le.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 80, 2));
                le.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 60, 0));
                le.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 2));
            }
        });
    }
    private void sealBind(MinecraftClient c, MobEntity mob) {
        if (renderingRemote) return;
        MinecraftServer server = c.getServer(); if (server == null) return; UUID id = mob.getUuid();
        server.execute(() -> {
            ServerWorld sw = server.getWorld(c.world.getRegistryKey()); if (sw == null) return;
            Entity se = sw.getEntity(id);
            if (se instanceof LivingEntity le) {
                le.setVelocity(0, 0, 0);
                le.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20, 6));
                le.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 40, 1));
            }
        });
    }
    private void playerEffect(MinecraftClient c, RegistryEntry<StatusEffect> eff, int dur, int amp) {
        if (renderingRemote) return;
        MinecraftServer server = c.getServer(); if (server == null) return; UUID id = c.player.getUuid();
        server.execute(() -> {
            var sp = server.getPlayerManager().getPlayer(id);
            if (sp != null) sp.addStatusEffect(new StatusEffectInstance(eff, dur, amp));
        });
    }

    // Render the crow-grave structures around the domain center.
    private void renderCrowWorld(MatrixStack ms, VertexConsumerProvider vcp, MinecraftClient mc, Vec3d camPos, int light) {
        Vec3d ctr = crowCenter; float age = mc.player.age;
        int elapsed = CROW_DOMAIN_TICKS - crowDomainTimer; float rise = Math.min(1f, elapsed / 18f);
        // central grand monument
        float mScale = 3.2f * (0.25f + 0.75f * rise);
        double mLift = (22.3 * 0.852 / 16.0) * mScale;
        renderModel(MONU_STACK, ms, vcp, mc, camPos, light, ctr.x, ctr.y + mLift, ctr.z, mScale, age * 0.4f, 0f, false);
        // torii gates at 4 cardinals
        for (int i = 0; i < 4; i++) {
            double a = i * (Math.PI / 2) + Math.PI / 4;
            float sc = 2.4f * (0.3f + 0.7f * rise);
            double lift = (13.15 / 16.0) * sc;
            double tx = ctr.x + Math.cos(a) * CROW_RADIUS * 0.82, tz = ctr.z + Math.sin(a) * CROW_RADIUS * 0.82;
            renderModel(TORII_STACK, ms, vcp, mc, camPos, light, tx, ctr.y + lift, tz, sc, (float) Math.toDegrees(a) + 90, 0f, false);
        }
        // tombstones ringing the grave
        int TN = 10;
        for (int i = 0; i < TN; i++) {
            double a = i * (Math.PI * 2 / TN) + 0.3;
            float sc = 2.0f * (0.3f + 0.7f * rise);
            double lift = (9.8 / 16.0) * sc;
            double tx = ctr.x + Math.cos(a) * CROW_RADIUS * 0.5, tz = ctr.z + Math.sin(a) * CROW_RADIUS * 0.5;
            renderModel(TOMB_STACK, ms, vcp, mc, camPos, light, tx, ctr.y + lift, tz, sc, (float) Math.toDegrees(a) + 180, 0f, false);
        }
        // gnarled dead trees
        int DT = 6;
        for (int i = 0; i < DT; i++) {
            double a = i * (Math.PI * 2 / DT) + 0.9;
            float sc = 2.6f * (0.3f + 0.7f * rise);
            double lift = (17.2 / 16.0) * sc;
            double tx = ctr.x + Math.cos(a) * CROW_RADIUS * 0.66, tz = ctr.z + Math.sin(a) * CROW_RADIUS * 0.66;
            renderModel(TREE_STACK, ms, vcp, mc, camPos, light, tx, ctr.y + lift, tz, sc, (float) (i * 57 % 360), 0f, false);
        }
        // gallows with hanging cages (gruesome)
        int GN = 4;
        for (int i = 0; i < GN; i++) {
            double a = i * (Math.PI * 2 / GN) + 0.15;
            float sc = 2.2f * (0.3f + 0.7f * rise);
            double lift = (16.0 / 16.0) * sc;
            double gx = ctr.x + Math.cos(a) * CROW_RADIUS * 0.72, gz = ctr.z + Math.sin(a) * CROW_RADIUS * 0.72;
            renderModel(GALLOWS_STACK, ms, vcp, mc, camPos, light, gx, ctr.y + lift, gz, sc, (float) Math.toDegrees(a) + 90, 0f, false);
        }
        // impaled skull spikes
        int SN = 6;
        for (int i = 0; i < SN; i++) {
            double a = i * (Math.PI * 2 / SN) + 0.55;
            float sc = 1.9f * (0.3f + 0.7f * rise);
            double lift = (14.0 / 16.0) * sc;
            double sx = ctr.x + Math.cos(a) * CROW_RADIUS * 0.38, sz = ctr.z + Math.sin(a) * CROW_RADIUS * 0.38;
            renderModel(SPIKE_STACK, ms, vcp, mc, camPos, light, sx, ctr.y + lift, sz, sc, (float) (i * 63 % 360), 0f, false);
        }
        // ambient crows wheeling overhead
        int AC = 6;
        for (int i = 0; i < AC; i++) {
            double a = i * (Math.PI * 2 / AC) + age * 0.02;
            double rad = CROW_RADIUS * 0.55;
            double cx = ctr.x + Math.cos(a) * rad, cz = ctr.z + Math.sin(a) * rad;
            double cy = ctr.y + 12 + (i % 3) * 3 + Math.sin(age * 0.03 + i) * 1.2;
            renderModel(CROW_STACK, ms, vcp, mc, camPos, light, cx, cy, cz, 1.1f,
                (float) Math.toDegrees(a) + 90, 0f, false);
        }
    }

    // =====================================================================
    //  DUEL ARENA — 전사들의 결투장 : lock the nearest foe in, expel the rest
    // =====================================================================
    private void tickArenaKit(MinecraftClient c) {
        boolean held = heldSword(c);
        long win = c.getWindow().getHandle();
        boolean ns = c.currentScreen == null;
        boolean bNow = held && ns && InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_B);
        boolean zNow = held && ns && InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_Z);
        boolean nNow = held && ns && InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_N);
        if (bNow && !lastB && arenaTimer <= 0) startArena(c);
        if (zNow && !lastZ && berserkTimer <= 0) startBerserk(c);
        if (nNow && !lastN && slashTimer <= 0) bloodSlash(c);
        lastB = bNow; lastZ = zNow; lastN = nNow;
        if (arenaTimer > 0) {
            tickArena(c);
            if (arenaTimer > 0) { arenaTimer--; if (arenaTimer == 0) endArena(); }
        }
        if (berserkTimer > 0) { tickBerserk(c); berserkTimer--; }
        if (slashTimer > 0) { tickSlash(c); slashTimer--; }
        if (judgmentTimer > 0) { tickJudgment(c); judgmentTimer--; }
    }

    private void startArena(MinecraftClient c) {
        arenaCenter = c.player.getPos();
        arenaTimer = ARENA_TICKS;
        judgmentDone = false;
        drones.clear(); crows.clear();          // existing summons are banished
        opponentId = pickOpponent(c);
        buffCaster(c);
        costHealthFraction(c, 0.15f);           // the caster pays 15% of their blood
    }
    private void costHealthFraction(MinecraftClient c, float frac) {
        if (renderingRemote) return;
        MinecraftServer server = c.getServer(); if (server == null) return; UUID id = c.player.getUuid();
        server.execute(() -> {
            var sp = server.getPlayerManager().getPlayer(id);
            if (sp != null) sp.setHealth(Math.max(1.0f, sp.getHealth() - sp.getMaxHealth() * frac));
        });
    }
    private void endArena() {
        arenaTimer = 0; opponentId = null; opponentEntity = null;
    }

    private UUID pickOpponent(MinecraftClient c) {
        Vec3d ctr = arenaCenter;
        List<LivingEntity> near = c.world.getEntitiesByClass(LivingEntity.class,
            new Box(ctr.subtract(24, 24, 24), ctr.add(24, 24, 24)), e -> e.isAlive() && e != c.player);
        LivingEntity best = null; double bd = 1e9;
        for (LivingEntity e : near) if (e instanceof PlayerEntity) { double d = e.squaredDistanceTo(c.player); if (d < bd) { bd = d; best = e; } }
        if (best == null) { bd = 1e9; for (LivingEntity e : near) { double d = e.squaredDistanceTo(c.player); if (d < bd) { bd = d; best = e; } } }
        opponentEntity = best;
        if (best != null) { opponentName = best.getName().getString(); return best.getUuid(); }
        opponentName = "—"; return null;
    }

    private void buffCaster(MinecraftClient c) {
        playerEffect(c, StatusEffects.STRENGTH, 90, 1);
        playerEffect(c, StatusEffects.SPEED, 90, 1);
        playerEffect(c, StatusEffects.HASTE, 90, 1);
        playerEffect(c, StatusEffects.RESISTANCE, 90, 0);
    }

    private void tickArena(MinecraftClient c) {
        ClientPlayerEntity p = c.player; ClientWorld w = c.world;
        Vec3d ctr = arenaCenter; double R = ARENA_RADIUS; long t = p.age;
        int elapsed = ARENA_TICKS - arenaTimer; double form = Math.min(1.0, elapsed / 16.0); double spin = t * 0.02;

        if (!renderingRemote) {
            // the duel ends the instant the chosen foe falls — only then may anyone leave
            if (elapsed > 6 && (opponentEntity == null || !opponentEntity.isAlive() || opponentEntity.isRemoved())) {
                endArena(); return;
            }
            // the caster is bound inside too — an invisible wall holds them in the ring
            double pdx = p.getX() - ctr.x, pdz = p.getZ() - ctr.z, pd = Math.sqrt(pdx * pdx + pdz * pdz);
            if (pd > R * 0.94 && pd > 0.01) {
                double f = R * 0.9 / pd;
                p.setPosition(ctr.x + pdx * f, p.getY(), ctr.z + pdz * f);
                Vec3d v = p.getVelocity(); p.setVelocity(v.x * 0.1, v.y, v.z * 0.1);
            }
        }
        // glowing boundary wall of flame (rising as the arena forms)
        int seg = 60;
        for (int s = 0; s < seg; s++) {
            double a = s * (Math.PI * 2 / seg) + spin;
            double x = ctr.x + Math.cos(a) * R, z = ctr.z + Math.sin(a) * R;
            for (int h = 0; h < 3; h++)
                if (((s + h) & 1) == 0) w.addParticle(ParticleTypes.FLAME, x, ctr.y + 0.2 + h * 1.4 * form, z, 0, 0.01, 0);
        }
        // radiant ground rings
        if ((t & 1) == 0)
            for (int rr = 1; rr <= 3; rr++) {
                double rad = R * rr / 3.0 * form; int sg = 40;
                for (int s = 0; s < sg; s++) {
                    double a = s * (Math.PI * 2 / sg) - spin * 0.5;
                    w.addParticle(ParticleTypes.END_ROD, ctr.x + Math.cos(a) * rad, ctr.y + 0.05, ctr.z + Math.sin(a) * rad, 0, 0, 0);
                }
            }
        if ((t % 40) == 0) buffCaster(c);
        arenaField(c);

        // 전사의 심판 — if the foe wields a ranged weapon, the God-Warrior's hand judges them
        if (!judgmentDone && opponentEntity != null && opponentEntity.isAlive() && isRanged(opponentEntity)) {
            judgmentDone = true;
            judgmentTimer = JUDGE_TICKS;
            judgmentPos = opponentEntity.getPos();
            instakillOpponent(c);
        }
    }

    private boolean isRanged(LivingEntity e) {
        ItemStack m = e.getMainHandStack(), o = e.getOffHandStack();
        return m.isOf(Items.BOW) || m.isOf(Items.CROSSBOW) || m.isOf(Items.TRIDENT)
            || o.isOf(Items.BOW) || o.isOf(Items.CROSSBOW) || o.isOf(Items.TRIDENT);
    }

    // server side: keep the chosen foe in & weakened, hurl everyone else out
    private void arenaField(MinecraftClient c) {
        if (renderingRemote) return;
        MinecraftServer server = c.getServer(); if (server == null || arenaCenter == null) return;
        UUID casterId = c.player.getUuid(); UUID oppId = opponentId; Vec3d ctr = arenaCenter; double R = ARENA_RADIUS;
        server.execute(() -> {
            ServerWorld sw = server.getWorld(c.world.getRegistryKey()); if (sw == null) return;
            Box b = new Box(ctr.subtract(R + 30, 64, R + 30), ctr.add(R + 30, 64, R + 30));
            for (Entity e : sw.getOtherEntities(null, b)) {
                if (!(e instanceof LivingEntity le)) continue;
                UUID id = e.getUuid();
                if (id.equals(casterId)) continue;
                double dx = e.getX() - ctr.x, dz = e.getZ() - ctr.z, d = Math.sqrt(dx * dx + dz * dz);
                double ux = d < 0.01 ? 1 : dx / d, uz = d < 0.01 ? 0 : dz / d;
                if (oppId != null && id.equals(oppId)) {
                    if (d > R * 0.9) {   // keep the duelist inside the ring
                        double tx = ctr.x + ux * R * 0.8, tz = ctr.z + uz * R * 0.8;
                        if (le instanceof net.minecraft.server.network.ServerPlayerEntity sp) sp.requestTeleport(tx, e.getY(), tz);
                        else le.setPosition(tx, e.getY(), tz);
                    }
                    le.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 45, 1));
                    le.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 45, 0));
                } else if (d < R + 2) {   // expel every other soul beyond the arena
                    double tx = ctr.x + ux * (R + 5), tz = ctr.z + uz * (R + 5);
                    if (le instanceof net.minecraft.server.network.ServerPlayerEntity sp) sp.requestTeleport(tx, e.getY(), tz);
                    else le.setPosition(tx, e.getY(), tz);
                    le.setVelocity(ux * 0.6, 0.25, uz * 0.6);
                }
            }
        });
    }

    private void renderArena(MatrixStack ms, VertexConsumerProvider vcp, MinecraftClient mc, Vec3d camPos, int light) {
        Vec3d ctr = arenaCenter; float age = mc.player.age;
        int elapsed = ARENA_TICKS - arenaTimer; float rise = Math.min(1f, elapsed / 18f);
        int PN = 12; float ps = 2.2f * (0.3f + 0.7f * rise); double plift = (22.6 / 16.0) * ps;
        for (int i = 0; i < PN; i++) {
            double a = i * (Math.PI * 2 / PN) + 0.26;
            double px = ctr.x + Math.cos(a) * ARENA_RADIUS * 0.98, pz = ctr.z + Math.sin(a) * ARENA_RADIUS * 0.98;
            renderModel(PILLAR_STACK, ms, vcp, mc, camPos, light, px, ctr.y + plift, pz, ps, (float) Math.toDegrees(a) + 90, 0f, false);
        }
        int AN = 4; float as = 2.4f * (0.3f + 0.7f * rise); double alift = (18.5 / 16.0) * as;
        for (int i = 0; i < AN; i++) {
            double a = i * (Math.PI / 2);
            double ax = ctr.x + Math.cos(a) * ARENA_RADIUS * 1.04, az = ctr.z + Math.sin(a) * ARENA_RADIUS * 1.04;
            renderModel(ARCH_STACK, ms, vcp, mc, camPos, light, ax, ctr.y + alift, az, as, (float) Math.toDegrees(a) + 90, 0f, false);
        }
    }

    // ---- 광폭화 (Berserk) : frenzy self-buff ----
    private void startBerserk(MinecraftClient c) {
        berserkTimer = BERSERK_TICKS;
        playerEffect(c, StatusEffects.STRENGTH, BERSERK_TICKS, 2);
        playerEffect(c, StatusEffects.SPEED, BERSERK_TICKS, 1);
        playerEffect(c, StatusEffects.HASTE, BERSERK_TICKS, 2);
        playerEffect(c, StatusEffects.RESISTANCE, BERSERK_TICKS, 0);
        ClientPlayerEntity p = c.player; ClientWorld w = c.world;
        DustParticleEffect red = new DustParticleEffect(0xC81020, 1.6f);
        for (int i = 0; i < 44; i++)
            w.addParticle(red, p.getX() + (Math.random() - 0.5) * 1.8, p.getY() + Math.random() * 2.2, p.getZ() + (Math.random() - 0.5) * 1.8, 0, 0.02, 0);
    }
    private void tickBerserk(MinecraftClient c) {
        ClientPlayerEntity p = c.player; ClientWorld w = c.world; long t = p.age;
        if ((t % 40) == 0) {
            playerEffect(c, StatusEffects.STRENGTH, 60, 2);
            playerEffect(c, StatusEffects.SPEED, 60, 1);
            playerEffect(c, StatusEffects.HASTE, 60, 2);
        }
        DustParticleEffect red = new DustParticleEffect(0xC81020, 1.4f);
        double a = t * 0.4;
        for (int i = 0; i < 3; i++) {
            double an = a + i * 2.1;
            w.addParticle(red, p.getX() + Math.cos(an) * 0.9, p.getY() + 0.2 + i * 0.6, p.getZ() + Math.sin(an) * 0.9, 0, 0.01, 0);
        }
        if ((t % 3) == 0) w.addParticle(ParticleTypes.FLAME, p.getX() + (Math.random() - 0.5), p.getY() + Math.random() * 2, p.getZ() + (Math.random() - 0.5), 0, 0.01, 0);
    }

    // ---- 혈검술 (Blood Blade) : spend your own blood to hurl a devastating slash ----
    private void bloodSlash(MinecraftClient c) {
        ClientPlayerEntity p = c.player; ClientWorld w = c.world;
        slashTimer = SLASH_TICKS;
        slashPos = p.getEyePos(); slashDir = p.getRotationVec(1.0f);
        costHealth(c, 4.0f);                       // 2 hearts of your own blood
        Vec3d eye = slashPos, look = slashDir;
        List<MobEntity> mobs = w.getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(11), e -> e.isAlive());
        for (MobEntity m : mobs) {
            Vec3d to = m.getBoundingBox().getCenter().subtract(eye);
            if (to.lengthSquared() > 121) continue;
            if (to.normalize().dotProduct(look) < 0.55) continue;   // in the forward arc
            damage(c, m, SLASH_DMG);
        }
    }
    private void costHealth(MinecraftClient c, float amt) {
        if (renderingRemote) return;
        MinecraftServer server = c.getServer(); if (server == null) return; UUID id = c.player.getUuid();
        server.execute(() -> {
            var sp = server.getPlayerManager().getPlayer(id);
            if (sp != null) sp.setHealth(Math.max(1.0f, sp.getHealth() - amt));
        });
    }
    private void tickSlash(MinecraftClient c) {
        if (slashPos == null || slashDir == null) return; ClientWorld w = c.world;
        int el = SLASH_TICKS - slashTimer; double travel = el * 1.1;
        Vec3d center = slashPos.add(slashDir.multiply(travel));
        Vec3d right = slashDir.crossProduct(new Vec3d(0, 1, 0)).normalize();
        Vec3d u2 = right.crossProduct(slashDir).normalize();
        DustParticleEffect blood = new DustParticleEffect(0xB00818, 1.8f);
        for (int i = -8; i <= 8; i++) {
            double a = i / 8.0 * (Math.PI * 0.6);
            Vec3d off = right.multiply(Math.sin(a) * 3.2).add(u2.multiply(Math.cos(a) * 3.2 - 3.2));
            Vec3d pnt = center.add(off);
            w.addParticle(blood, pnt.x, pnt.y, pnt.z, 0, 0, 0);
            if ((i & 1) == 0) w.addParticle(ParticleTypes.CRIT, pnt.x, pnt.y, pnt.z, 0, 0, 0);
        }
    }

    // ---- 전사의 심판 : the God-Warrior's hand crushes a ranged coward ----
    private void instakillOpponent(MinecraftClient c) {
        if (renderingRemote) return;
        MinecraftServer server = c.getServer(); if (server == null || opponentId == null) return; UUID id = opponentId;
        server.execute(() -> {
            ServerWorld sw = server.getWorld(c.world.getRegistryKey()); if (sw == null) return;
            Entity se = sw.getEntity(id);
            if (se instanceof LivingEntity le) le.damage(sw, sw.getDamageSources().magic(), 100000f);
        });
    }
    private void tickJudgment(MinecraftClient c) {
        if (judgmentPos == null) return; ClientWorld w = c.world; Vec3d pos = judgmentPos;
        for (int i = 0; i < 12; i++) {
            double a = Math.random() * Math.PI * 2, r = Math.random() * 4;
            w.addParticle(ParticleTypes.LARGE_SMOKE, pos.x + Math.cos(a) * r, pos.y + 0.2, pos.z + Math.sin(a) * r, 0, 0.02, 0);
        }
        DustParticleEffect blood = new DustParticleEffect(0xB00818, 2.0f);
        for (int i = 0; i < 8; i++)
            w.addParticle(blood, pos.x + (Math.random() - 0.5) * 2, pos.y + Math.random() * 3, pos.z + (Math.random() - 0.5) * 2, 0, 0, 0);
    }

    private boolean heldSword(MinecraftClient c) {
        return c.player != null
            && (c.player.getMainHandStack().isOf(VoidHunt.DUEL_SWORD)
             || c.player.getOffHandStack().isOf(VoidHunt.DUEL_SWORD));
    }

    // ===== multiplayer: broadcast my visuals, paint everyone else's =====
    private void sendLocalFx(MinecraftClient c) {
        if (c.player == null || !ClientPlayNetworking.canSend(VoidNet.PushC2S.ID)) return;
        VoidNet.Fx f = new VoidNet.Fx();
        if (domainTimer > 0)     { f.mCtr = domainCenter; f.mT = domainTimer; }
        if (crowDomainTimer > 0) { f.cCtr = crowCenter;   f.cT = crowDomainTimer; }
        if (arenaTimer > 0)      { f.aCtr = arenaCenter;  f.aT = arenaTimer; }
        f.uT = ultTimer; f.bT = berserkTimer;
        if (judgmentTimer > 0)   { f.jPos = judgmentPos;  f.jT = judgmentTimer; }
        if (seaTimer > 0)        { f.sCtr = seaCenter;    f.sT = seaTimer; }
        if (roadTimer > 0)       { f.rCtr = roadCenter;   f.rT = roadTimer; }
        if (kingTimer > 0)       { f.kCtr = kingCenter;   f.kT = kingTimer; }
        f.gT = guardTimer;
        for (Drone d : drones) f.drones.add(d.pos);
        for (Crow cw : crows)  f.crows.add(cw.pos);
        for (Crow sk : sharks) f.sharks.add(sk.pos);
        for (Crow bk : bikes)  f.bikes.add(bk.pos);
        if (f.anyActive()) ClientPlayNetworking.send(new VoidNet.PushC2S(f));
    }

    private void tickRemotes(MinecraftClient c) {
        if (REMOTE.isEmpty() || c.world == null) return;
        var it = REMOTE.entrySet().iterator();
        while (it.hasNext()) {
            VoidNet.Fx f = it.next().getValue();
            f.age++;
            if (f.age > 8) { it.remove(); continue; }
            if (f.mT > 0) f.mT--; if (f.cT > 0) f.cT--; if (f.aT > 0) f.aT--;
            if (f.jT > 0) f.jT--; if (f.sT > 0) f.sT--; if (f.rT > 0) f.rT--;
            if (f.kT > 0) f.kT--; if (f.gT > 0) f.gT--;
            paintRemoteParticles(c, f);
        }
    }

    private void paintRemoteParticles(MinecraftClient c, VoidNet.Fx f) {
        Vec3d sm = domainCenter, sc = crowCenter, sa = arenaCenter, sj = judgmentPos, ss = seaCenter, sr = roadCenter, sk = kingCenter;
        int smt = domainTimer, sct = crowDomainTimer, sat = arenaTimer, sjt = judgmentTimer, sst = seaTimer, srt = roadTimer, skt = kingTimer;
        renderingRemote = true;
        try {
            if (f.mT > 0 && f.mCtr != null) { domainCenter = f.mCtr; domainTimer = f.mT; tickDomain(c); }
            if (f.cT > 0 && f.cCtr != null) { crowCenter = f.cCtr; crowDomainTimer = f.cT; tickCrowDomain(c); }
            if (f.aT > 0 && f.aCtr != null) { arenaCenter = f.aCtr; arenaTimer = f.aT; tickArena(c); }
            if (f.jT > 0 && f.jPos != null) { judgmentPos = f.jPos; judgmentTimer = f.jT; tickJudgment(c); }
            if (f.sT > 0 && f.sCtr != null) { seaCenter = f.sCtr; seaTimer = f.sT; tickSeaDomain(c); }
            if (f.rT > 0 && f.rCtr != null) { roadCenter = f.rCtr; roadTimer = f.rT; tickRoadDomain(c); }
            if (f.kT > 0 && f.kCtr != null) { kingCenter = f.kCtr; kingTimer = f.kT; tickKingDomain(c); }
        } catch (Exception ignored) {}
        domainCenter = sm; crowCenter = sc; arenaCenter = sa; judgmentPos = sj; seaCenter = ss; roadCenter = sr; kingCenter = sk;
        domainTimer = smt; crowDomainTimer = sct; arenaTimer = sat; judgmentTimer = sjt; seaTimer = sst; roadTimer = srt; kingTimer = skt;
        renderingRemote = false;
    }

    private void renderRemote(UUID owner, VoidNet.Fx f, MatrixStack ms, VertexConsumerProvider vcp,
                             MinecraftClient mc, Vec3d camPos, int light) {
        Vec3d sm = domainCenter, sc = crowCenter, sa = arenaCenter, ss = seaCenter, sr = roadCenter, sk = kingCenter;
        int smt = domainTimer, sct = crowDomainTimer, sat = arenaTimer, sst = seaTimer, srt = roadTimer, skt = kingTimer;
        renderingRemote = true;
        try {
            if (f.mT > 0 && f.mCtr != null) { domainCenter = f.mCtr; domainTimer = f.mT; renderDomain(ms, vcp, mc, camPos, light); }
            if (f.cT > 0 && f.cCtr != null) { crowCenter = f.cCtr; crowDomainTimer = f.cT; renderCrowWorld(ms, vcp, mc, camPos, light); }
            if (f.aT > 0 && f.aCtr != null) { arenaCenter = f.aCtr; arenaTimer = f.aT; renderArena(ms, vcp, mc, camPos, light); }
            if (f.sT > 0 && f.sCtr != null) { seaCenter = f.sCtr; seaTimer = f.sT; renderSeaWorld(ms, vcp, mc, camPos, light); }
            if (f.rT > 0 && f.rCtr != null) { roadCenter = f.rCtr; roadTimer = f.rT; renderRoadWorld(ms, vcp, mc, camPos, light); }
            if (f.kT > 0 && f.kCtr != null) { kingCenter = f.kCtr; kingTimer = f.kT; renderKingWorld(ms, vcp, mc, camPos, light); }
            if (f.gT > 0) { PlayerEntity kp = findPlayer(mc, owner); if (kp != null) renderGuardianHands(ms, vcp, mc, camPos, light, kp.getPos(), mc.player.age); }
            float spin = mc.player.age * 2f;
            for (Vec3d dp : f.drones) renderModel(DRONE_STACK, ms, vcp, mc, camPos, light, dp.x, dp.y, dp.z, 0.8f, 0f, spin, false);
            for (Vec3d cp : f.crows)  renderModel(CROW_STACK,  ms, vcp, mc, camPos, light, cp.x, cp.y, cp.z, 0.9f, 0f, 0f, false);
            for (Vec3d kp : f.sharks) renderModel(SHARK_STACK, ms, vcp, mc, camPos, light, kp.x, kp.y, kp.z, 1.1f, 0f, 0f, false);
            for (Vec3d bp : f.bikes)  renderModel(BIKE_STACK,  ms, vcp, mc, camPos, light, bp.x, bp.y, bp.z, 1.2f, 0f, 0f, false);
            if (f.uT > 0) {
                PlayerEntity op = findPlayer(mc, owner);
                if (op != null) { Vec3d s = op.getEyePos().add(0, ULT_HEIGHT, 0);
                    renderModel(SAT_STACK, ms, vcp, mc, camPos, light, s.x, s.y, s.z, 4.0f, 0f, mc.player.age * 3f, false); }
            }
            if (f.jT > 0 && f.jPos != null) {
                int el = JUDGE_TICKS - f.jT; float prog = Math.min(1f, el / 22f);
                double startY = f.jPos.y + 30, curY = startY - (startY - (f.jPos.y + 2.5)) * prog;
                renderModel(HAND_STACK, ms, vcp, mc, camPos, light, f.jPos.x, curY, f.jPos.z, 6.0f, 0f, 0f, false);
            }
        } catch (Exception ignored) {}
        domainCenter = sm; crowCenter = sc; arenaCenter = sa; seaCenter = ss; roadCenter = sr; kingCenter = sk;
        domainTimer = smt; crowDomainTimer = sct; arenaTimer = sat; seaTimer = sst; roadTimer = srt; kingTimer = skt;
        renderingRemote = false;
    }

    private PlayerEntity findPlayer(MinecraftClient mc, UUID id) {
        if (mc.world == null) return null;
        for (PlayerEntity pl : mc.world.getPlayers()) if (pl.getUuid().equals(id)) return pl;
        return null;
    }

    // =====================================================================
    //  OCEAN WORLD — 바다의 세계 / 해일 / 소용돌이 / 상어 떼
    // =====================================================================
    private boolean heldTrident(MinecraftClient c) {
        return c.player != null
            && (c.player.getMainHandStack().isOf(VoidHunt.SEA_TRIDENT)
             || c.player.getOffHandStack().isOf(VoidHunt.SEA_TRIDENT));
    }

    private void tickSeaKit(MinecraftClient c) {
        boolean held = heldTrident(c);
        long win = c.getWindow().getHandle();
        boolean ns = c.currentScreen == null;
        boolean jN = held && ns && InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_J);
        boolean uN = held && ns && InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_U);
        boolean yN = held && ns && InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_Y);
        boolean mN = held && ns && InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_M);
        if (jN && !lastJ && seaTimer <= 0)  startSea(c);
        if (uN && !lastU && waveTimer <= 0) startWave(c);
        if (yN && !lastY && maelTimer <= 0) startMaelstrom(c);
        if (mN && !lastM) summonSharks(c);
        lastJ = jN; lastU = uN; lastY = yN; lastM = mN;
        if (!held) sharks.clear();
        if (seaTimer > 0)  { tickSeaDomain(c); seaTimer--; }
        if (waveTimer > 0) { tickWave(c); waveTimer--; }
        if (maelTimer > 0) { tickMaelstrom(c); maelTimer--; }
        if (!sharks.isEmpty()) tickSharks(c);
    }

    private void startSea(MinecraftClient c) { seaCenter = c.player.getPos(); seaTimer = SEA_TICKS; buffSeaCaster(c); }
    private void buffSeaCaster(MinecraftClient c) {
        playerEffect(c, StatusEffects.WATER_BREATHING, 130, 0);
        playerEffect(c, StatusEffects.DOLPHINS_GRACE, 130, 1);
        playerEffect(c, StatusEffects.CONDUIT_POWER, 130, 0);
        playerEffect(c, StatusEffects.STRENGTH, 130, 0);
    }

    private void tickSeaDomain(MinecraftClient c) {
        ClientPlayerEntity p = c.player; ClientWorld w = c.world;
        if (seaCenter == null) seaCenter = p.getPos();
        Vec3d ctr = seaCenter; double R = SEA_RADIUS; long t = p.age;
        int elapsed = SEA_TICKS - seaTimer; double form = Math.min(1.0, elapsed / 16.0); double spin = t * 0.02;
        for (int i = 0; i < 16; i++) {
            double a = Math.random() * Math.PI * 2, d2 = Math.random() * R * form;
            w.addParticle(ParticleTypes.BUBBLE_COLUMN_UP, ctr.x + Math.cos(a) * d2, ctr.y + Math.random() * 2, ctr.z + Math.sin(a) * d2, 0, 0.1, 0);
        }
        DustParticleEffect aqua = new DustParticleEffect(0x40E0E0, 1.5f);
        int MER = 12, SEG = 8;
        for (int m = 0; m < MER; m++) {
            double th = m * (Math.PI * 2 / MER) + spin;
            for (int s = 0; s <= SEG; s++) {
                double elev = (Math.PI / 2) * s / SEG * form; double hr = R * Math.cos(elev);
                w.addParticle(ParticleTypes.UNDERWATER, ctr.x + Math.cos(th) * hr, ctr.y + R * Math.sin(elev), ctr.z + Math.sin(th) * hr, 0, 0, 0);
            }
        }
        if ((t & 1) == 0) {
            for (int mm = 0; mm < 8; mm++) {            // descending god-rays
                double th = mm * (Math.PI * 2 / 8) + spin * 0.5; double hr = R * 0.7;
                for (int s = 0; s < 6; s++) w.addParticle(aqua, ctr.x + Math.cos(th) * hr, ctr.y + 15 - s * 2.4, ctr.z + Math.sin(th) * hr, 0, -0.02, 0);
            }
            for (int rr = 1; rr <= 3; rr++) {           // floor swirl
                double rad = R * rr / 3.0 * form;
                for (int s = 0; s < 28; s++) { double a = s * (Math.PI * 2 / 28) - spin; w.addParticle(ParticleTypes.SPLASH, ctr.x + Math.cos(a) * rad, ctr.y + 0.1, ctr.z + Math.sin(a) * rad, 0, 0, 0); }
            }
        }
        if ((t % 40) == 0) buffSeaCaster(c);
        Box box = new Box(ctr.subtract(R, R, R), ctr.add(R, R, R));
        List<MobEntity> mobs = w.getEntitiesByClass(MobEntity.class, box,
            e -> e.isAlive() && (e instanceof HostileEntity) && e.getPos().distanceTo(ctr) <= R + 1.0);
        for (MobEntity m : mobs) {
            Vec3d mc2 = m.getBoundingBox().getCenter();
            if ((t % 2) == 0) w.addParticle(ParticleTypes.BUBBLE, mc2.x, mc2.y, mc2.z, 0, 0, 0);
            if ((t % 4) == 0) seaHit(c, m, SEA_DMG);
        }
    }
    private void seaHit(MinecraftClient c, MobEntity mob, float amt) {
        if (renderingRemote) return;
        MinecraftServer server = c.getServer(); if (server == null) return; UUID id = mob.getUuid();
        server.execute(() -> {
            ServerWorld sw = server.getWorld(c.world.getRegistryKey()); if (sw == null) return;
            Entity se = sw.getEntity(id);
            if (se instanceof LivingEntity le) {
                le.damage(sw, sw.getDamageSources().drown(), amt);
                le.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 2));
                le.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 40, 2));
            }
        });
    }

    // ---- 해일 (Tidal Wave) ----
    private void startWave(MinecraftClient c) {
        ClientPlayerEntity p = c.player; ClientWorld w = c.world;
        waveTimer = WAVE_TICKS; wavePos = p.getEyePos();
        Vec3d look = p.getRotationVec(1.0f); Vec3d flat = new Vec3d(look.x, 0, look.z);
        waveDir = flat.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : flat.normalize();
        List<MobEntity> mobs = w.getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(10), e -> e.isAlive());
        for (MobEntity m : mobs) {
            Vec3d to = m.getBoundingBox().getCenter().subtract(wavePos);
            if (to.lengthSquared() > 100) continue;
            Vec3d fl = new Vec3d(to.x, 0, to.z);
            if (fl.normalize().dotProduct(waveDir) < 0.4) continue;
            damage(c, m, 18f); knockback(c, m, waveDir);
        }
    }
    private void tickWave(MinecraftClient c) {
        if (wavePos == null || waveDir == null) return; ClientWorld w = c.world;
        int el = WAVE_TICKS - waveTimer; double travel = el * 1.4;
        Vec3d ctr = wavePos.add(waveDir.multiply(travel));
        Vec3d right = waveDir.crossProduct(new Vec3d(0, 1, 0)).normalize();
        for (int i = -6; i <= 6; i++) {
            Vec3d base = ctr.add(right.multiply(i * 0.9));
            for (int h = 0; h < 4; h++) {
                w.addParticle(ParticleTypes.SPLASH, base.x, base.y + h * 0.7, base.z, 0, 0.05, 0);
                if (h < 2) w.addParticle(ParticleTypes.BUBBLE, base.x, base.y + h * 0.7, base.z, 0, 0, 0);
            }
        }
    }
    private void knockback(MinecraftClient c, MobEntity mob, Vec3d dir) {
        if (renderingRemote) return;
        MinecraftServer server = c.getServer(); if (server == null) return; UUID id = mob.getUuid();
        final Vec3d v = dir.normalize().multiply(1.7).add(0, 0.5, 0);
        server.execute(() -> {
            ServerWorld sw = server.getWorld(c.world.getRegistryKey()); if (sw == null) return;
            Entity se = sw.getEntity(id); if (se != null) se.setVelocity(v);
        });
    }

    // ---- 소용돌이 (Maelstrom) ----
    private void startMaelstrom(MinecraftClient c) {
        maelTimer = MAEL_TICKS;
        Vec3d look = c.player.getRotationVec(1.0f); Vec3d flat = new Vec3d(look.x, 0, look.z);
        if (flat.lengthSquared() < 1e-4) flat = new Vec3d(0, 0, 1);
        maelPos = c.player.getPos().add(flat.normalize().multiply(6)).add(0, 0.1, 0);
    }
    private void tickMaelstrom(MinecraftClient c) {
        if (maelPos == null) return; ClientWorld w = c.world; long t = c.player.age; Vec3d s = maelPos; double sp = t * 0.35;
        for (int rr = 1; rr <= 4; rr++) {
            double rad = rr * 1.4; int seg = 20;
            for (int i = 0; i < seg; i++) {
                double a = i * (Math.PI * 2 / seg) + sp - rr * 0.5;
                w.addParticle(ParticleTypes.BUBBLE, s.x + Math.cos(a) * rad, s.y + (4 - rr) * 0.6, s.z + Math.sin(a) * rad, 0, 0, 0);
                if (rr == 1) w.addParticle(ParticleTypes.CURRENT_DOWN, s.x + Math.cos(a) * rad, s.y + 3, s.z + Math.sin(a) * rad, 0, 0, 0);
            }
        }
        Box b = new Box(s.subtract(7, 4, 7), s.add(7, 4, 7));
        List<MobEntity> mobs = w.getEntitiesByClass(MobEntity.class, b,
            e -> e.isAlive() && (e instanceof HostileEntity) && e.getPos().distanceTo(s) <= 7.0);
        for (MobEntity m : mobs) { pullTo(c, m, s); if ((t % 4) == 0) seaHit(c, m, 6f); }
    }
    private void pullTo(MinecraftClient c, MobEntity mob, Vec3d center) {
        if (renderingRemote) return;
        MinecraftServer server = c.getServer(); if (server == null) return; UUID id = mob.getUuid();
        server.execute(() -> {
            ServerWorld sw = server.getWorld(c.world.getRegistryKey()); if (sw == null) return;
            Entity se = sw.getEntity(id);
            if (se != null) { Vec3d d = center.subtract(se.getPos()); if (d.lengthSquared() > 0.5) se.setVelocity(d.normalize().multiply(0.5).add(0, -0.1, 0)); }
        });
    }

    // ---- 상어 떼 (Shark Swarm) ----
    private void summonSharks(MinecraftClient c) {
        sharks.clear(); Vec3d base = c.player.getEyePos();
        for (int i = 0; i < MAX_SHARKS; i++) { double a = i * (Math.PI * 2 / MAX_SHARKS); sharks.add(new Crow(base.add(Math.cos(a) * 3, 0.5, Math.sin(a) * 3), i)); }
    }
    private void tickSharks(MinecraftClient c) {
        ClientPlayerEntity p = c.player; ClientWorld w = c.world; long t = p.age;
        double step = Math.PI * 2 / Math.max(1, sharks.size());
        List<MobEntity> host = w.getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(18),
            e -> e.isAlive() && (e instanceof HostileEntity));
        for (Crow sk : sharks) {
            MobEntity tgt = host.stream().min(Comparator.comparingDouble(e -> e.squaredDistanceTo(sk.pos.x, sk.pos.y, sk.pos.z))).orElse(null);
            Vec3d goal;
            if (tgt != null) goal = tgt.getBoundingBox().getCenter();
            else { double a = sk.idx * step + t * 0.05; goal = p.getEyePos().add(Math.cos(a) * 3.5, 0.4 + Math.sin(t * 0.05 + sk.idx) * 0.3, Math.sin(a) * 3.5); }
            Vec3d prev = sk.pos;
            sk.pos = sk.pos.add(goal.subtract(sk.pos).multiply(0.18));
            double mvx = sk.pos.x - prev.x, mvz = sk.pos.z - prev.z;
            if (mvx * mvx + mvz * mvz > 1e-4) sk.face = Math.toDegrees(Math.atan2(-mvx, mvz));
            if ((t % 3) == 0) w.addParticle(ParticleTypes.BUBBLE, sk.pos.x, sk.pos.y, sk.pos.z, 0, 0, 0);
            if (sk.cd > 0) sk.cd--;
            if (tgt != null && sk.pos.distanceTo(tgt.getBoundingBox().getCenter()) < 2.6 && sk.cd <= 0) {
                damage(c, tgt, 10f); sk.cd = 12;
                Vec3d tc = tgt.getBoundingBox().getCenter();
                for (int k = 0; k < 4; k++) w.addParticle(ParticleTypes.SPLASH, tc.x, tc.y, tc.z, 0, 0, 0);
            }
        }
    }

    private void renderSeaWorld(MatrixStack ms, VertexConsumerProvider vcp, MinecraftClient mc, Vec3d camPos, int light) {
        Vec3d ctr = seaCenter; float age = mc.player.age;
        int elapsed = SEA_TICKS - seaTimer; float rise = Math.min(1f, elapsed / 18f);
        float ts = 3.0f * (0.25f + 0.75f * rise);
        double tLift = (22.5 * 0.991 / 16.0) * ts;
        renderModel(TEMPLE_STACK, ms, vcp, mc, camPos, light, ctr.x, ctr.y + tLift, ctr.z, ts, age * 0.3f, 0f, false);
        int PN = 8;
        for (int i = 0; i < PN; i++) {
            double a = i * (Math.PI * 2 / PN) + 0.2; float sc = 2.2f * (0.3f + 0.7f * rise); double lift = (18.5 / 16.0) * sc;
            double px = ctr.x + Math.cos(a) * SEA_RADIUS * 0.85, pz = ctr.z + Math.sin(a) * SEA_RADIUS * 0.85;
            renderModel(CORAL_STACK, ms, vcp, mc, camPos, light, px, ctr.y + lift, pz, sc, (float) (i * 47 % 360), 0f, false);
        }
        int SC = 4;
        for (int i = 0; i < SC; i++) {
            double a = i * (Math.PI * 2 / SC) + age * 0.02; double rad = SEA_RADIUS * 0.5;
            double cx = ctr.x + Math.cos(a) * rad, cz = ctr.z + Math.sin(a) * rad, cy = ctr.y + 8 + (i % 2) * 3 + Math.sin(age * 0.03 + i) * 1.2;
            renderModel(SHARK_STACK, ms, vcp, mc, camPos, light, cx, cy, cz, 1.2f, (float) Math.toDegrees(a) + 90, 0f, false);
        }
    }

    private void drawSeaOverlay(DrawContext ctx, MinecraftClient c) {
        TextRenderer tr = c.textRenderer;
        int W = ctx.getScaledWindowWidth(), H = ctx.getScaledWindowHeight();
        int elapsed = SEA_TICKS - seaTimer; long t = c.player.age;
        float dk = Math.min(1f, elapsed / 16f); if (seaTimer < 20) dk *= seaTimer / 20f;
        ctx.fill(0, 0, W, H, ((int) (80 * dk) << 24) | 0x083048);   // deep-water blue tint
        int N = 60, maxIn = Math.min(W, H) * 3 / 5, band = Math.max(1, maxIn / N) + 1;
        for (int i = 0; i < N; i++) {
            int inset = i * maxIn / N;
            int a = (int) (200 * dk * Math.pow(1 - (double) i / N, 2.2));
            if (a <= 2) continue;
            int col = (a << 24) | 0x04202E;
            ctx.fill(inset, inset, W - inset, inset + band, col);
            ctx.fill(inset, H - inset - band, W - inset, H - inset, col);
            ctx.fill(inset, inset, inset + band, H - inset, col);
            ctx.fill(W - inset - band, inset, W - inset, H - inset, col);
        }
        ctx.fill(0, 0, W, 22, 0xAA000000);
        ctx.fill(0, H - 22, W, H, 0xAA000000);
        if (elapsed < 50) {
            String jp = "海の世界";
            boolean blink = (elapsed < 26) && ((t / 3) % 2 == 0);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(W / 2f, H / 2f - 48f, 0f);
            ctx.getMatrices().scale(3.1f, 3.1f, 1f);
            ctx.drawText(tr, Text.literal(jp), -tr.getWidth(jp) / 2, 0, blink ? 0xFFFFFFFF : AQUA, true);
            ctx.getMatrices().pop();
            String kr = "바다의 세계";
            ctx.getMatrices().push();
            ctx.getMatrices().translate(W / 2f, H / 2f - 16f, 0f);
            ctx.getMatrices().scale(1.9f, 1.9f, 1f);
            ctx.drawText(tr, Text.literal(kr), -tr.getWidth(kr) / 2, 0, SEAB, true);
            ctx.getMatrices().pop();
            String en = "O C E A N   W O R L D   ·   DROWN";
            ctx.drawText(tr, Text.literal(en), W / 2 - tr.getWidth(en) / 2, H / 2 + 16, BON, true);
        } else {
            String b = "海 · 바다의 세계";
            ctx.getMatrices().push();
            ctx.getMatrices().translate(W / 2f, 4f, 0f);
            ctx.getMatrices().scale(1.3f, 1.3f, 1f);
            ctx.drawText(tr, Text.literal(b), -tr.getWidth(b) / 2, 0, AQUA, true);
            ctx.getMatrices().pop();
        }
        int barW = 180, bx = W / 2 - barW / 2, by = H - 30;
        ctx.fill(bx - 1, by - 1, bx + barW + 1, by + 4, 0xFF08222E);
        int fillW = (int) (barW * seaTimer / (double) SEA_TICKS);
        ctx.fill(bx, by, bx + fillW, by + 3, AQUA);
        String sec = (seaTimer / 20 + 1) + "s";
        ctx.drawText(tr, Text.literal(sec), bx + barW + 6, by - 3, AQUA, true);
    }
    private void drawTridentPanel(DrawContext ctx, MinecraftClient c) {
        TextRenderer tr = c.textRenderer;
        int H = ctx.getScaledWindowHeight();
        int y0 = H - 120;
        ctx.drawText(tr, Text.literal("SEA TRIDENT · 바다의 삼지창"), 8, y0, AQUA, true);
        ctx.drawText(tr, Text.literal(seaTimer > 0 ? ">> 바다의 세계 " + (seaTimer / 20 + 1) + "s" : "= 바다의 세계 (J)"), 8, y0 + 12, seaTimer > 0 ? SEAB : DIM, true);
        ctx.drawText(tr, Text.literal(waveTimer > 0 ? ">> 해일" : "= 해일 (U)"), 8, y0 + 22, waveTimer > 0 ? AQUA : DIM, true);
        ctx.drawText(tr, Text.literal(maelTimer > 0 ? ">> 소용돌이 " + (maelTimer / 20 + 1) + "s" : "= 소용돌이 (Y)"), 8, y0 + 32, maelTimer > 0 ? AQUA : DIM, true);
        ctx.drawText(tr, Text.literal("상어 떼 소환 (M)  x" + sharks.size()), 8, y0 + 42, sharks.isEmpty() ? DIM : SEAB, true);
    }

    // =====================================================================
    //  BIKER HIGHWAY — 폭주족들의 도로 / 폭주 질주 / 폭주족 소환 / 클락션 굉음
    // =====================================================================
    private boolean heldPipe(MinecraftClient c) {
        return c.player != null
            && (c.player.getMainHandStack().isOf(VoidHunt.NEON_PIPE)
             || c.player.getOffHandStack().isOf(VoidHunt.NEON_PIPE));
    }

    private void tickRoadKit(MinecraftClient c) {
        boolean held = heldPipe(c);
        long win = c.getWindow().getHandle();
        boolean ns = c.currentScreen == null;
        boolean pN = held && ns && InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_P);
        boolean lN = held && ns && InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_L);
        boolean iN = held && ns && InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_I);
        boolean hN = held && ns && InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_PERIOD);
        if (pN && !lastRp && roadTimer <= 0) startRoad(c);
        if (lN && !lastRl && dashTimer <= 0) startDash(c);
        if (iN && !lastRi) summonBikes(c);
        if (hN && !lastRh && hornTimer <= 0) startHorn(c);
        lastRp = pN; lastRl = lN; lastRi = iN; lastRh = hN;
        if (!held) bikes.clear();
        if (roadTimer > 0) { tickRoadDomain(c); roadTimer--; }
        if (dashTimer > 0) { tickDash(c); dashTimer--; }
        if (hornTimer > 0) { tickHorn(c); hornTimer--; }
        if (!bikes.isEmpty()) tickBikes(c);
    }

    private void startRoad(MinecraftClient c) { roadCenter = c.player.getPos(); roadTimer = ROAD_TICKS; buffRoadCaster(c); }
    private void buffRoadCaster(MinecraftClient c) {
        playerEffect(c, StatusEffects.SPEED, 130, 2);
        playerEffect(c, StatusEffects.FIRE_RESISTANCE, 130, 0);
        playerEffect(c, StatusEffects.STRENGTH, 130, 1);
        playerEffect(c, StatusEffects.JUMP_BOOST, 130, 1);
    }

    private void tickRoadDomain(MinecraftClient c) {
        ClientPlayerEntity p = c.player; ClientWorld w = c.world;
        if (roadCenter == null) roadCenter = p.getPos();
        Vec3d ctr = roadCenter; double R = ROAD_RADIUS; long t = p.age;
        int elapsed = ROAD_TICKS - roadTimer; double form = Math.min(1.0, elapsed / 16.0); double spin = t * 0.02;
        DustParticleEffect pink = new DustParticleEffect(0xFF46B4, 1.5f);
        DustParticleEffect cyan = new DustParticleEffect(0x3CE6F0, 1.5f);
        // neon dome shell
        int MER = 12, SEG = 8;
        for (int m = 0; m < MER; m++) {
            double th = m * (Math.PI * 2 / MER) + spin;
            for (int s = 0; s <= SEG; s++) {
                double elev = (Math.PI / 2) * s / SEG * form; double hr = R * Math.cos(elev);
                w.addParticle((m % 2 == 0) ? pink : cyan, ctr.x + Math.cos(th) * hr, ctr.y + R * Math.sin(elev), ctr.z + Math.sin(th) * hr, 0, 0, 0);
            }
        }
        // glowing road lane lines on the floor + exhaust smoke
        if ((t & 1) == 0) {
            for (int lane = -2; lane <= 2; lane++) {
                for (int d = -14; d <= 14; d += 2) {
                    double off = ((d + t) % 6 == 0) ? 1 : 0;
                    if (off == 0) continue;
                    w.addParticle(new DustParticleEffect(0xF0D23C, 1.3f), ctr.x + lane * 5, ctr.y + 0.05, ctr.z + d, 0, 0, 0);
                }
            }
            for (int rr = 1; rr <= 3; rr++) {
                double rad = R * rr / 3.0 * form;
                for (int s = 0; s < 24; s++) { double a = s * (Math.PI * 2 / 24) + spin * 0.3; w.addParticle(ParticleTypes.LARGE_SMOKE, ctr.x + Math.cos(a) * rad, ctr.y + 0.4, ctr.z + Math.sin(a) * rad, 0, 0.01, 0); }
            }
        }
        // streaking headlights
        for (int i = 0; i < 6; i++) { double a = Math.random() * Math.PI * 2, d2 = Math.random() * R * form; w.addParticle(ParticleTypes.END_ROD, ctr.x + Math.cos(a) * d2, ctr.y + 1 + Math.random() * 2, ctr.z + Math.sin(a) * d2, 0, 0, 0); }
        if ((t % 40) == 0) buffRoadCaster(c);
        // run over every hostile in the road
        Box box = new Box(ctr.subtract(R, R, R), ctr.add(R, R, R));
        List<MobEntity> mobs = w.getEntitiesByClass(MobEntity.class, box,
            e -> e.isAlive() && (e instanceof HostileEntity) && e.getPos().distanceTo(ctr) <= R + 1.0);
        for (MobEntity m : mobs) {
            Vec3d mc2 = m.getBoundingBox().getCenter();
            if ((t % 2) == 0) w.addParticle(ParticleTypes.FLAME, mc2.x, mc2.y, mc2.z, 0, 0, 0);
            if ((t % 4) == 0) roadHit(c, m, ROAD_DMG);
        }
    }
    private void roadHit(MinecraftClient c, MobEntity mob, float amt) {
        if (renderingRemote) return;
        MinecraftServer server = c.getServer(); if (server == null) return; UUID id = mob.getUuid();
        server.execute(() -> {
            ServerWorld sw = server.getWorld(c.world.getRegistryKey()); if (sw == null) return;
            Entity se = sw.getEntity(id);
            if (se instanceof LivingEntity le) {
                le.damage(sw, sw.getDamageSources().magic(), amt);
                le.setOnFireForTicks(50);
                le.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 2));
            }
        });
    }

    // ---- 폭주 질주 (Nitro Dash) ----
    private void startDash(MinecraftClient c) {
        dashTimer = DASH_TICKS;
        playerEffect(c, StatusEffects.FIRE_RESISTANCE, 60, 0);
        playerEffect(c, StatusEffects.SPEED, 40, 3);
    }
    private void tickDash(MinecraftClient c) {
        ClientPlayerEntity p = c.player; ClientWorld w = c.world;
        Vec3d look = p.getRotationVec(1.0f); Vec3d flat = new Vec3d(look.x, 0, look.z);
        if (flat.lengthSquared() > 1e-4) { flat = flat.normalize(); p.setVelocity(flat.x * 1.5, p.getVelocity().y + 0.02, flat.z * 1.5); }
        for (int i = 0; i < 3; i++) w.addParticle(ParticleTypes.FLAME, p.getX() + (Math.random() - 0.5), p.getY() + 0.3 + Math.random(), p.getZ() + (Math.random() - 0.5), 0, 0, 0);
        w.addParticle(new DustParticleEffect(0xFF46B4, 1.6f), p.getX(), p.getY() + 0.2, p.getZ(), 0, 0, 0);
        w.addParticle(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 0.2, p.getZ(), 0, 0, 0);
        // ram nearby enemies
        List<MobEntity> mobs = w.getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(2.6), e -> e.isAlive() && (e instanceof HostileEntity));
        for (MobEntity m : mobs) { damage(c, m, 12f); knockback(c, m, flat); roadHit(c, m, 0.1f); }
    }

    // ---- 폭주족 소환 (Ghost Gang) ----
    private void summonBikes(MinecraftClient c) {
        bikes.clear(); Vec3d base = c.player.getEyePos();
        for (int i = 0; i < MAX_BIKES; i++) { double a = i * (Math.PI * 2 / MAX_BIKES); bikes.add(new Crow(base.add(Math.cos(a) * 4, -0.5, Math.sin(a) * 4), i)); }
    }
    private void tickBikes(MinecraftClient c) {
        ClientPlayerEntity p = c.player; ClientWorld w = c.world; long t = p.age;
        double step = Math.PI * 2 / Math.max(1, bikes.size());
        List<MobEntity> host = w.getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(20), e -> e.isAlive() && (e instanceof HostileEntity));
        for (Crow bk : bikes) {
            MobEntity tgt = host.stream().min(Comparator.comparingDouble(e -> e.squaredDistanceTo(bk.pos.x, bk.pos.y, bk.pos.z))).orElse(null);
            Vec3d goal;
            if (tgt != null) goal = tgt.getBoundingBox().getCenter().add(0, -0.5, 0);
            else { double a = bk.idx * step + t * 0.06; goal = p.getEyePos().add(Math.cos(a) * 4.5, -0.5, Math.sin(a) * 4.5); }
            Vec3d prev = bk.pos;
            bk.pos = bk.pos.add(goal.subtract(bk.pos).multiply(0.2));
            double mvx = bk.pos.x - prev.x, mvz = bk.pos.z - prev.z;
            if (mvx * mvx + mvz * mvz > 1e-4) bk.face = Math.toDegrees(Math.atan2(-mvx, mvz));
            if ((t % 2) == 0) { w.addParticle(ParticleTypes.FLAME, bk.pos.x, bk.pos.y - 0.2, bk.pos.z, 0, 0, 0); w.addParticle(ParticleTypes.LARGE_SMOKE, bk.pos.x, bk.pos.y - 0.2, bk.pos.z, 0, 0, 0); }
            if (bk.cd > 0) bk.cd--;
            if (tgt != null && bk.pos.distanceTo(tgt.getBoundingBox().getCenter()) < 3.0 && bk.cd <= 0) {
                damage(c, tgt, 11f); knockback(c, tgt, tgt.getPos().subtract(p.getPos())); roadHit(c, tgt, 0.1f); bk.cd = 14;
            }
        }
    }

    // ---- 클락션 굉음 (Horn Blast) ----
    private void startHorn(MinecraftClient c) {
        ClientPlayerEntity p = c.player; ClientWorld w = c.world;
        hornTimer = HORN_TICKS; hornPos = p.getEyePos();
        Vec3d look = p.getRotationVec(1.0f); Vec3d flat = new Vec3d(look.x, 0, look.z);
        hornDir = flat.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : flat.normalize();
        List<MobEntity> mobs = w.getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(9), e -> e.isAlive());
        for (MobEntity m : mobs) {
            Vec3d to = m.getBoundingBox().getCenter().subtract(hornPos);
            if (to.lengthSquared() > 81) continue;
            Vec3d fl = new Vec3d(to.x, 0, to.z);
            if (fl.normalize().dotProduct(hornDir) < 0.35) continue;
            damage(c, m, 8f); knockback(c, m, hornDir); stun(c, m);
        }
    }
    private void tickHorn(MinecraftClient c) {
        if (hornPos == null || hornDir == null) return; ClientWorld w = c.world;
        int el = HORN_TICKS - hornTimer; double travel = el * 1.2;
        Vec3d ctr = hornPos.add(hornDir.multiply(travel));
        Vec3d right = hornDir.crossProduct(new Vec3d(0, 1, 0)).normalize();
        DustParticleEffect cyan = new DustParticleEffect(0x3CE6F0, 1.8f);
        for (int i = -7; i <= 7; i++) {
            Vec3d base = ctr.add(right.multiply(i * 0.8));
            for (int h = 0; h < 3; h++) { w.addParticle(cyan, base.x, base.y + h * 0.7, base.z, 0, 0, 0); if (h == 0) w.addParticle(ParticleTypes.NOTE, base.x, base.y + 1, base.z, 0, 0, 0); }
        }
    }
    private void stun(MinecraftClient c, MobEntity mob) {
        if (renderingRemote) return;
        MinecraftServer server = c.getServer(); if (server == null) return; UUID id = mob.getUuid();
        server.execute(() -> {
            ServerWorld sw = server.getWorld(c.world.getRegistryKey()); if (sw == null) return;
            Entity se = sw.getEntity(id);
            if (se instanceof LivingEntity le) { le.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 50, 5)); le.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 50, 2)); }
        });
    }

    private void renderRoadWorld(MatrixStack ms, VertexConsumerProvider vcp, MinecraftClient mc, Vec3d camPos, int light) {
        Vec3d ctr = roadCenter; float age = mc.player.age;
        int elapsed = ROAD_TICKS - roadTimer; float rise = Math.min(1f, elapsed / 18f);
        float gs = 3.0f * (0.25f + 0.75f * rise);
        double gLift = (23.0 / 16.0) * gs;
        renderModel(NGATE_STACK, ms, vcp, mc, camPos, light, ctr.x, ctr.y + gLift, ctr.z, gs, 0f, 0f, false);
        int LN = 8;
        for (int i = 0; i < LN; i++) {
            double a = i * (Math.PI * 2 / LN) + 0.2; float sc = 2.2f * (0.3f + 0.7f * rise); double lift = (16.0 / 16.0) * sc;
            double px = ctr.x + Math.cos(a) * ROAD_RADIUS * 0.85, pz = ctr.z + Math.sin(a) * ROAD_RADIUS * 0.85;
            renderModel(LIGHT_STACK, ms, vcp, mc, camPos, light, px, ctr.y + lift, pz, sc, (float) Math.toDegrees(a) + 90, 0f, false);
        }
        int BC = 4;
        for (int i = 0; i < BC; i++) {
            double a = i * (Math.PI * 2 / BC) + age * 0.03; double rad = ROAD_RADIUS * 0.55;
            double cx = ctr.x + Math.cos(a) * rad, cz = ctr.z + Math.sin(a) * rad;
            renderModel(BIKE_STACK, ms, vcp, mc, camPos, light, cx, ctr.y + 0.6, cz, 1.2f, (float) Math.toDegrees(a) + 90, 0f, false);
        }
    }

    private void drawRoadOverlay(DrawContext ctx, MinecraftClient c) {
        TextRenderer tr = c.textRenderer;
        int W = ctx.getScaledWindowWidth(), H = ctx.getScaledWindowHeight();
        int elapsed = ROAD_TICKS - roadTimer; long t = c.player.age;
        float dk = Math.min(1f, elapsed / 16f); if (roadTimer < 20) dk *= roadTimer / 20f;
        ctx.fill(0, 0, W, H, ((int) (70 * dk) << 24) | 0x18061C);
        int N = 60, maxIn = Math.min(W, H) * 3 / 5, band = Math.max(1, maxIn / N) + 1;
        for (int i = 0; i < N; i++) {
            int inset = i * maxIn / N;
            int a = (int) (200 * dk * Math.pow(1 - (double) i / N, 2.2));
            if (a <= 2) continue;
            int col = (a << 24) | ((i % 2 == 0) ? 0x2A0A22 : 0x061E24);
            ctx.fill(inset, inset, W - inset, inset + band, col);
            ctx.fill(inset, H - inset - band, W - inset, H - inset, col);
            ctx.fill(inset, inset, inset + band, H - inset, col);
            ctx.fill(W - inset - band, inset, W - inset, H - inset, col);
        }
        ctx.fill(0, 0, W, 22, 0xAA000000);
        ctx.fill(0, H - 22, W, H, 0xAA000000);
        if (elapsed < 50) {
            String jp = "暴走族の道";
            boolean blink = (elapsed < 26) && ((t / 3) % 2 == 0);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(W / 2f, H / 2f - 48f, 0f);
            ctx.getMatrices().scale(2.9f, 2.9f, 1f);
            ctx.drawText(tr, Text.literal(jp), -tr.getWidth(jp) / 2, 0, blink ? 0xFFFFFFFF : NPINK, true);
            ctx.getMatrices().pop();
            String kr = "폭주족들의 도로";
            ctx.getMatrices().push();
            ctx.getMatrices().translate(W / 2f, H / 2f - 16f, 0f);
            ctx.getMatrices().scale(1.9f, 1.9f, 1f);
            ctx.drawText(tr, Text.literal(kr), -tr.getWidth(kr) / 2, 0, NCYAN, true);
            ctx.getMatrices().pop();
            String en = "N I G H T   H I G H W A Y   ·   ROADKILL";
            ctx.drawText(tr, Text.literal(en), W / 2 - tr.getWidth(en) / 2, H / 2 + 16, BON, true);
        } else {
            String b = "暴走 · 폭주족들의 도로";
            ctx.getMatrices().push();
            ctx.getMatrices().translate(W / 2f, 4f, 0f);
            ctx.getMatrices().scale(1.3f, 1.3f, 1f);
            ctx.drawText(tr, Text.literal(b), -tr.getWidth(b) / 2, 0, NPINK, true);
            ctx.getMatrices().pop();
        }
        int barW = 180, bx = W / 2 - barW / 2, by = H - 30;
        ctx.fill(bx - 1, by - 1, bx + barW + 1, by + 4, 0xFF1A0820);
        int fillW = (int) (barW * roadTimer / (double) ROAD_TICKS);
        ctx.fill(bx, by, bx + fillW, by + 3, NPINK);
        String sec = (roadTimer / 20 + 1) + "s";
        ctx.drawText(tr, Text.literal(sec), bx + barW + 6, by - 3, NPINK, true);
    }
    private void drawPipePanel(DrawContext ctx, MinecraftClient c) {
        TextRenderer tr = c.textRenderer;
        int H = ctx.getScaledWindowHeight();
        int y0 = H - 120;
        ctx.drawText(tr, Text.literal("NEON PIPE · 폭주족의 쇠파이프"), 8, y0, NPINK, true);
        ctx.drawText(tr, Text.literal(roadTimer > 0 ? ">> 도로 " + (roadTimer / 20 + 1) + "s" : "= 폭주족들의 도로 (P)"), 8, y0 + 12, roadTimer > 0 ? NCYAN : DIM, true);
        ctx.drawText(tr, Text.literal(dashTimer > 0 ? ">> 폭주 질주" : "= 폭주 질주 (L)"), 8, y0 + 22, dashTimer > 0 ? NPINK : DIM, true);
        ctx.drawText(tr, Text.literal("폭주족 소환 (I)  x" + bikes.size()), 8, y0 + 32, bikes.isEmpty() ? DIM : NCYAN, true);
        ctx.drawText(tr, Text.literal(hornTimer > 0 ? ">> 클락션 굉음" : "= 클락션 굉음 (.)"), 8, y0 + 42, hornTimer > 0 ? NPINK : DIM, true);
    }

    // =====================================================================
    //  KING'S WORLD — 왕의 세계 / 오라 / 왕국의 수호자 / 조공  (+ 기사의 대쉬)
    // =====================================================================
    private boolean heldScepter(MinecraftClient c) {
        return c.player != null && (c.player.getMainHandStack().isOf(VoidHunt.KING_SCEPTER) || c.player.getOffHandStack().isOf(VoidHunt.KING_SCEPTER));
    }
    private boolean heldKnightBlade(MinecraftClient c) {
        return c.player != null && (c.player.getMainHandStack().isOf(VoidHunt.KNIGHT_BLADE) || c.player.getOffHandStack().isOf(VoidHunt.KNIGHT_BLADE));
    }

    private void tickKingKit(MinecraftClient c) {
        boolean held = heldScepter(c);
        long win = c.getWindow().getHandle();
        boolean ns = c.currentScreen == null;
        boolean kd = held && ns && InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_SEMICOLON);
        boolean au = held && ns && InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_LEFT_BRACKET);
        boolean gu = held && ns && InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_RIGHT_BRACKET);
        boolean tr = held && ns && InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_APOSTROPHE);
        if (kd && !lastKd && kingTimer <= 0) startKing(c);
        if (au && !lastAu && auraTimer <= 0) castAura(c);
        if (gu && !lastGu && guardTimer <= 0) guardTimer = GUARD_TICKS;
        if (tr && !lastTr) castTribute(c);
        lastKd = kd; lastAu = au; lastGu = gu; lastTr = tr;
        if (kingTimer > 0) { tickKingDomain(c); kingTimer--; if (kingTimer == 0) { knightId = null; kingHpPaid = false; } }
        if (auraTimer > 0) { tickAura(c); auraTimer--; }
        if (guardTimer > 0) { tickGuardian(c); guardTimer--; }
    }

    private UUID pickKnight(MinecraftClient c) {
        double bd = 1e9; PlayerEntity best = null;
        for (PlayerEntity pl : c.world.getPlayers()) {
            if (pl == c.player) continue;
            double d = pl.squaredDistanceTo(c.player);
            if (d < bd && d < KING_RADIUS * KING_RADIUS) { bd = d; best = pl; }
        }
        if (best != null) { knightName = best.getName().getString(); return best.getUuid(); }
        knightName = "—"; return null;
    }
    private void startKing(MinecraftClient c) {
        kingCenter = c.player.getPos(); kingTimer = KING_TICKS; kingHpPaid = false;
        knightId = pickKnight(c);
        MinecraftServer server = c.getServer(); if (server == null) return;
        UUID kingU = c.player.getUuid(); UUID knU = knightId;
        server.execute(() -> {
            var king = server.getPlayerManager().getPlayer(kingU);
            if (king != null) king.setHealth(Math.max(1f, king.getMaxHealth() * 0.5f));  // 왕의 피는 반으로
            if (knU != null) {
                var kn = server.getPlayerManager().getPlayer(knU);
                if (kn != null) {
                    kn.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, KING_TICKS, 2));
                    kn.addStatusEffect(new StatusEffectInstance(StatusEffects.HEALTH_BOOST, KING_TICKS, 3));
                    kn.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, KING_TICKS, 1));
                    kn.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, KING_TICKS, 1));
                    kn.getInventory().offerOrDrop(new ItemStack(VoidHunt.KNIGHT_BLADE));  // 기사의 검 하사
                }
            }
        });
    }

    private void tickKingDomain(MinecraftClient c) {
        ClientPlayerEntity p = c.player; ClientWorld w = c.world;
        if (kingCenter == null) kingCenter = p.getPos();
        Vec3d ctr = kingCenter; double R = KING_RADIUS; long t = p.age;
        int elapsed = KING_TICKS - kingTimer; double form = Math.min(1.0, elapsed / 16.0); double spin = t * 0.02;
        DustParticleEffect gold = new DustParticleEffect(0xF2C044, 1.5f);
        DustParticleEffect roy = new DustParticleEffect(0xB07CF0, 1.4f);
        int MER = 12, SEG = 8;
        for (int m = 0; m < MER; m++) {
            double th = m * (Math.PI * 2 / MER) + spin;
            for (int s = 0; s <= SEG; s++) { double elev = (Math.PI / 2) * s / SEG * form; double hr = R * Math.cos(elev); w.addParticle((m % 2 == 0) ? gold : roy, ctr.x + Math.cos(th) * hr, ctr.y + R * Math.sin(elev), ctr.z + Math.sin(th) * hr, 0, 0, 0); }
        }
        for (int i = 0; i < 10; i++) { double a = Math.random() * Math.PI * 2, d2 = Math.random() * R * form; w.addParticle(ParticleTypes.END_ROD, ctr.x + Math.cos(a) * d2, ctr.y + 8 + Math.random() * 8, ctr.z + Math.sin(a) * d2, 0, -0.03, 0); }
        if ((t & 1) == 0) for (int rr = 1; rr <= 3; rr++) { double rad = R * rr / 3.0 * form; for (int s = 0; s < 24; s++) { double a = s * (Math.PI * 2 / 24) + spin * 0.3; w.addParticle(gold, ctr.x + Math.cos(a) * rad, ctr.y + 0.05, ctr.z + Math.sin(a) * rad, 0, 0, 0); } }
        if ((t % 40) == 0 && knightId != null && !renderingRemote) {
            MinecraftServer server = c.getServer();
            if (server != null) { UUID knU = knightId; server.execute(() -> { var kn = server.getPlayerManager().getPlayer(knU); if (kn != null) { kn.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 60, 2)); kn.addStatusEffect(new StatusEffectInstance(StatusEffects.HEALTH_BOOST, 60, 3)); kn.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 1)); } }); }
        }
        Box box = new Box(ctr.subtract(R, R, R), ctr.add(R, R, R));
        List<MobEntity> mobs = w.getEntitiesByClass(MobEntity.class, box, e -> e.isAlive() && (e instanceof HostileEntity) && e.getPos().distanceTo(ctr) <= R + 1.0);
        for (MobEntity m : mobs) { if ((t % 4) == 0) damage(c, m, KING_DMG); Vec3d mc2 = m.getBoundingBox().getCenter(); if ((t % 3) == 0) w.addParticle(gold, mc2.x, mc2.y, mc2.z, 0, 0, 0); }
    }

    // ---- 오라 (Aura — 1s stun) ----
    private void castAura(MinecraftClient c) {
        ClientPlayerEntity p = c.player; ClientWorld w = c.world; auraTimer = AURA_TICKS;
        List<LivingEntity> foes = w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(7),
            e -> e.isAlive() && e != p && ((e instanceof HostileEntity) || (e instanceof PlayerEntity)));
        for (LivingEntity le : foes) { if (knightId != null && le.getUuid().equals(knightId)) continue; stunEntity(c, le); }
    }
    private void tickAura(MinecraftClient c) {
        ClientPlayerEntity p = c.player; ClientWorld w = c.world; Vec3d ctr = p.getPos();
        int el = AURA_TICKS - auraTimer; double rad = el * 0.7;
        DustParticleEffect gold = new DustParticleEffect(0xF2C044, 1.8f);
        for (int i = 0; i < 40; i++) { double a = i * (Math.PI * 2 / 40); w.addParticle(gold, ctr.x + Math.cos(a) * rad, ctr.y + 0.3, ctr.z + Math.sin(a) * rad, 0, 0, 0); }
    }
    private void stunEntity(MinecraftClient c, LivingEntity target) {
        if (renderingRemote) return;
        MinecraftServer server = c.getServer(); if (server == null) return; UUID id = target.getUuid();
        server.execute(() -> {
            ServerWorld sw = server.getWorld(c.world.getRegistryKey()); if (sw == null) return;
            Entity se = sw.getEntity(id);
            if (se instanceof LivingEntity le) { le.setVelocity(0, 0, 0); le.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20, 250)); le.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 20, 4)); le.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 20, 128)); }
        });
    }

    // ---- 왕국의 수호자 (Guardian hands) ----
    private void tickGuardian(MinecraftClient c) {
        ClientPlayerEntity p = c.player; ClientWorld w = c.world; long t = p.age; Vec3d ctr = p.getPos();
        List<MobEntity> mobs = w.getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(4.5), e -> e.isAlive() && (e instanceof HostileEntity));
        for (MobEntity m : mobs) { knockback(c, m, m.getPos().subtract(ctr)); if ((t % 6) == 0) damage(c, m, 8f); }
        for (int i = 0; i < 3; i++) w.addParticle(new DustParticleEffect(0xF2C044, 1.6f), ctr.x + (Math.random() - 0.5) * 3, ctr.y + 1 + Math.random() * 2, ctr.z + (Math.random() - 0.5) * 3, 0, 0, 0);
    }
    private void renderGuardianHands(MatrixStack ms, VertexConsumerProvider vcp, MinecraftClient mc, Vec3d camPos, int light, Vec3d kingPos, float age) {
        for (int s = 0; s < 2; s++) {
            double side = (s == 0) ? 1 : -1; double a = age * 0.05;
            double hx = kingPos.x + Math.cos(a) * 3.0 * side, hz = kingPos.z + Math.sin(a) * 3.0 * side, hy = kingPos.y + 3.5 + Math.sin(age * 0.06) * 0.4;
            renderModel(HAND_STACK, ms, vcp, mc, camPos, light, hx, hy, hz, 3.2f, (float) (Math.toDegrees(a) + (s == 0 ? 0 : 180)), 0f, false);
        }
    }

    // ---- 조공 (Tribute — buffs scale with your minerals) ----
    private void castTribute(MinecraftClient c) {
        ClientWorld w = c.world;
        for (int i = 0; i < 30; i++) w.addParticle(new DustParticleEffect(0xF2C044, 1.6f), c.player.getX() + (Math.random() - 0.5) * 1.5, c.player.getY() + Math.random() * 2, c.player.getZ() + (Math.random() - 0.5) * 1.5, 0, 0.02, 0);
        MinecraftServer server = c.getServer(); if (server == null) return; UUID kingU = c.player.getUuid();
        server.execute(() -> {
            var king = server.getPlayerManager().getPlayer(kingU); if (king == null) return;
            int ore = 0;
            for (int i = 0; i < king.getInventory().size(); i++) { ItemStack st = king.getInventory().getStack(i); if (!st.isEmpty() && isMineral(st)) ore += st.getCount(); }
            int amp = Math.min(4, ore / 16); int dur = 600;
            king.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, dur, amp));
            king.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, dur, Math.min(3, amp)));
            king.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, dur, Math.min(2, amp)));
            king.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, dur, Math.min(4, amp + 1)));
        });
    }
    private boolean isMineral(ItemStack st) {
        return st.isOf(Items.DIAMOND) || st.isOf(Items.EMERALD) || st.isOf(Items.GOLD_INGOT) || st.isOf(Items.IRON_INGOT)
            || st.isOf(Items.NETHERITE_INGOT) || st.isOf(Items.COPPER_INGOT) || st.isOf(Items.LAPIS_LAZULI) || st.isOf(Items.REDSTONE)
            || st.isOf(Items.COAL) || st.isOf(Items.AMETHYST_SHARD) || st.isOf(Items.QUARTZ)
            || st.isOf(Items.RAW_IRON) || st.isOf(Items.RAW_GOLD) || st.isOf(Items.RAW_COPPER);
    }

    // ---- 기사의 대쉬 (Knight's Dash) ----
    private void tickKnightKit(MinecraftClient c) {
        boolean held = heldKnightBlade(c);
        long win = c.getWindow().getHandle(); boolean ns = c.currentScreen == null;
        boolean kn = held && ns && InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_COMMA);
        if (kn && !lastKn && knightDashTimer <= 0) knightDash(c);
        lastKn = kn;
        if (knightDashTimer > 0) { tickKnightDash(c); knightDashTimer--; }
    }
    private void knightDash(MinecraftClient c) {
        knightDashTimer = KDASH_TICKS; playerEffect(c, StatusEffects.SPEED, 30, 3);
        ClientPlayerEntity p = c.player; Vec3d look = p.getRotationVec(1.0f); Vec3d flat = new Vec3d(look.x, 0, look.z);
        if (flat.lengthSquared() > 1e-4) { flat = flat.normalize(); p.setVelocity(flat.x * 1.6, p.getVelocity().y + 0.1, flat.z * 1.6); }
    }
    private void tickKnightDash(MinecraftClient c) {
        ClientPlayerEntity p = c.player; ClientWorld w = c.world; Vec3d look = p.getRotationVec(1.0f); Vec3d flat = new Vec3d(look.x, 0, look.z);
        if (flat.lengthSquared() > 1e-4) { flat = flat.normalize(); p.setVelocity(flat.x * 1.5, p.getVelocity().y, flat.z * 1.5); }
        for (int i = 0; i < 3; i++) w.addParticle(new DustParticleEffect(0x64AAFF, 1.4f), p.getX() + (Math.random() - 0.5), p.getY() + 0.5 + Math.random(), p.getZ() + (Math.random() - 0.5), 0, 0, 0);
        List<MobEntity> mobs = w.getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(2.4), e -> e.isAlive() && (e instanceof HostileEntity));
        for (MobEntity m : mobs) damage(c, m, 10f);
    }

    private void renderKingWorld(MatrixStack ms, VertexConsumerProvider vcp, MinecraftClient mc, Vec3d camPos, int light) {
        Vec3d ctr = kingCenter; float age = mc.player.age; int elapsed = KING_TICKS - kingTimer; float rise = Math.min(1f, elapsed / 18f);
        float ts = 3.0f * (0.25f + 0.75f * rise); double tLift = (23.0 / 16.0) * ts;
        renderModel(THRONE_STACK, ms, vcp, mc, camPos, light, ctr.x, ctr.y + tLift, ctr.z, ts, 0f, 0f, false);
        int PN = 8;
        for (int i = 0; i < PN; i++) {
            double a = i * (Math.PI * 2 / PN) + 0.2; float sc = 2.2f * (0.3f + 0.7f * rise); double lift = (18.0 / 16.0) * sc;
            double px = ctr.x + Math.cos(a) * KING_RADIUS * 0.85, pz = ctr.z + Math.sin(a) * KING_RADIUS * 0.85;
            renderModel(RPILLAR_STACK, ms, vcp, mc, camPos, light, px, ctr.y + lift, pz, sc, (float) Math.toDegrees(a) + 90, 0f, false);
        }
    }

    private void drawKingOverlay(DrawContext ctx, MinecraftClient c) {
        TextRenderer tr = c.textRenderer;
        int W = ctx.getScaledWindowWidth(), H = ctx.getScaledWindowHeight();
        int elapsed = KING_TICKS - kingTimer; long t = c.player.age;
        float dk = Math.min(1f, elapsed / 16f); if (kingTimer < 20) dk *= kingTimer / 20f;
        ctx.fill(0, 0, W, H, ((int) (55 * dk) << 24) | 0x140E02);
        int N = 60, maxIn = Math.min(W, H) * 3 / 5, band = Math.max(1, maxIn / N) + 1;
        for (int i = 0; i < N; i++) {
            int inset = i * maxIn / N; int a = (int) (190 * dk * Math.pow(1 - (double) i / N, 2.2)); if (a <= 2) continue;
            int col = (a << 24) | 0x1C1404;
            ctx.fill(inset, inset, W - inset, inset + band, col); ctx.fill(inset, H - inset - band, W - inset, H - inset, col);
            ctx.fill(inset, inset, inset + band, H - inset, col); ctx.fill(W - inset - band, inset, W - inset, H - inset, col);
        }
        ctx.fill(0, 0, W, 22, 0xAA000000); ctx.fill(0, H - 22, W, H, 0xAA000000);
        if (elapsed < 50) {
            String jp = "王の世界"; boolean blink = (elapsed < 26) && ((t / 3) % 2 == 0);
            ctx.getMatrices().push(); ctx.getMatrices().translate(W / 2f, H / 2f - 48f, 0f); ctx.getMatrices().scale(3.1f, 3.1f, 1f);
            ctx.drawText(tr, Text.literal(jp), -tr.getWidth(jp) / 2, 0, blink ? 0xFFFFFFFF : GLD, true); ctx.getMatrices().pop();
            String kr = "왕의 세계";
            ctx.getMatrices().push(); ctx.getMatrices().translate(W / 2f, H / 2f - 16f, 0f); ctx.getMatrices().scale(1.9f, 1.9f, 1f);
            ctx.drawText(tr, Text.literal(kr), -tr.getWidth(kr) / 2, 0, ROYP, true); ctx.getMatrices().pop();
            String en = "K I N G ' S   R E A L M   ·   기사: " + knightName;
            ctx.drawText(tr, Text.literal(en), W / 2 - tr.getWidth(en) / 2, H / 2 + 16, BON, true);
        } else {
            String b = "王 · 왕의 세계   (기사: " + knightName + ")";
            ctx.getMatrices().push(); ctx.getMatrices().translate(W / 2f, 4f, 0f); ctx.getMatrices().scale(1.3f, 1.3f, 1f);
            ctx.drawText(tr, Text.literal(b), -tr.getWidth(b) / 2, 0, GLD, true); ctx.getMatrices().pop();
        }
        int barW = 180, bx = W / 2 - barW / 2, by = H - 30;
        ctx.fill(bx - 1, by - 1, bx + barW + 1, by + 4, 0xFF201804);
        int fillW = (int) (barW * kingTimer / (double) KING_TICKS); ctx.fill(bx, by, bx + fillW, by + 3, GLD);
        String sec = (kingTimer / 20 + 1) + "s"; ctx.drawText(tr, Text.literal(sec), bx + barW + 6, by - 3, GLD, true);
    }
    private void drawScepterPanel(DrawContext ctx, MinecraftClient c) {
        TextRenderer tr = c.textRenderer; int H = ctx.getScaledWindowHeight(); int y0 = H - 120;
        ctx.drawText(tr, Text.literal("KING SCEPTER · 왕의 지팡이"), 8, y0, GLD, true);
        ctx.drawText(tr, Text.literal(kingTimer > 0 ? ">> 왕의 세계 " + (kingTimer / 20 + 1) + "s  기사:" + knightName : "= 왕의 세계 (;)"), 8, y0 + 12, kingTimer > 0 ? ROYP : DIM, true);
        ctx.drawText(tr, Text.literal(auraTimer > 0 ? ">> 오라" : "= 오라 스턴 ([)"), 8, y0 + 22, auraTimer > 0 ? GLD : DIM, true);
        ctx.drawText(tr, Text.literal(guardTimer > 0 ? ">> 수호자 " + (guardTimer / 20 + 1) + "s" : "= 왕국의 수호자 (])"), 8, y0 + 32, guardTimer > 0 ? GLD : DIM, true);
        ctx.drawText(tr, Text.literal("조공 (')  — 광물로 버프"), 8, y0 + 42, DIM, true);
    }
    private void drawKnightPanel(DrawContext ctx, MinecraftClient c) {
        TextRenderer tr = c.textRenderer; int H = ctx.getScaledWindowHeight();
        ctx.drawText(tr, Text.literal("KNIGHT BLADE · 기사의 검"), 8, H - 60, 0xFF9AC0FF, true);
        ctx.drawText(tr, Text.literal(knightDashTimer > 0 ? ">> 대쉬" : "= 대쉬 (,)"), 8, H - 48, knightDashTimer > 0 ? 0xFF9AC0FF : DIM, true);
    }

    private void fireLaser(ClientWorld w, Vec3d from, Vec3d to) {
        int steps = (int) (from.distanceTo(to) * 3) + 4;
        for (int i = 0; i <= steps; i++) {
            double f = i / (double) steps;
            w.addParticle(ParticleTypes.END_ROD,
                from.x + (to.x - from.x) * f,
                from.y + (to.y - from.y) * f,
                from.z + (to.z - from.z) * f, 0, 0, 0);
        }
    }

    // Deal real damage in singleplayer (integrated server). Multiplayer = visual only.
    private void damage(MinecraftClient c, MobEntity mob, float amt) {
        if (renderingRemote) return;
        MinecraftServer server = c.getServer();
        if (server == null) return;
        UUID id = mob.getUuid();
        server.execute(() -> {
            ServerWorld sw = server.getWorld(c.world.getRegistryKey());
            if (sw == null) return;
            Entity se = sw.getEntity(id);
            if (se instanceof LivingEntity le) {
                le.damage(sw, sw.getDamageSources().magic(), amt);
            }
        });
    }

    private void aimAt(ClientPlayerEntity p, Entity t) {
        Vec3d eye = p.getEyePos();
        double dx = t.getX() - eye.x;
        double dy = (t.getY() + t.getHeight() * 0.5) - eye.y;
        double dz = t.getZ() - eye.z;
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        float wantYaw   = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float wantPitch = (float) (-Math.toDegrees(Math.atan2(dy, distXZ)));
        p.setYaw(p.getYaw() + MathHelper.wrapDegrees(wantYaw - p.getYaw()) * AIM);
        p.setPitch(p.getPitch() + (wantPitch - p.getPitch()) * AIM);
    }

    // ---- tiny draw helpers (DrawContext only has fill/drawText) ----
    private static void dot(DrawContext g,int x,int y,int col){ g.fill(x,y,x+1,y+1,col); }
    private static void hLine(DrawContext g,int x1,int x2,int y,int col){ g.fill(Math.min(x1,x2),y,Math.max(x1,x2)+1,y+1,col); }
    private static void vLine(DrawContext g,int x,int y1,int y2,int col){ g.fill(x,Math.min(y1,y2),x+1,Math.max(y1,y2)+1,col); }
    private static void line(DrawContext g,int x0,int y0,int x1,int y1,int col){
        int dx=Math.abs(x1-x0), dy=Math.abs(y1-y0), sx=x0<x1?1:-1, sy=y0<y1?1:-1, err=dx-dy;
        for(int n=0;n<400;n++){ dot(g,x0,y0,col); if(x0==x1&&y0==y1)break; int e2=2*err; if(e2>-dy){err-=dy;x0+=sx;} if(e2<dx){err+=dx;y0+=sy;} }
    }
    private static void ring(DrawContext g,int cx,int cy,int r,int col){
        int seg=Math.max(28,r*3); for(int i=0;i<seg;i++){ double a=i*2*Math.PI/seg; dot(g,cx+(int)Math.round(Math.cos(a)*r),cy+(int)Math.round(Math.sin(a)*r),col); }
    }

    // ---- DOMAIN EXPANSION full-screen overlay (領域展開 · 기계의 세계) ----
    private void drawDomainOverlay(DrawContext ctx, MinecraftClient c) {
        TextRenderer tr = c.textRenderer;
        int W = ctx.getScaledWindowWidth();
        int H = ctx.getScaledWindowHeight();
        int elapsed = DOMAIN_TICKS - domainTimer;
        long t = c.player.age;

        // ===== VOID DARKNESS: everything outside the domain sinks into black =====
        float dk = Math.min(1f, elapsed / 16f);              // darkness forms with the domain
        if (domainTimer < 20) dk *= domainTimer / 20f;       // eases out as it ends
        // faint deep-navy void tint over the whole view
        ctx.fill(0, 0, W, H, ((int) (70 * dk) << 24) | 0x0A0E1E);
        // radial vignette: nested dark frames, strong at the edges, clear in the center
        int N = 64;
        int maxIn = Math.min(W, H) * 3 / 5;
        int band = Math.max(1, maxIn / N) + 1;
        for (int i = 0; i < N; i++) {
            int inset = i * maxIn / N;
            int a = (int) (225 * dk * Math.pow(1 - (double) i / N, 2.3));
            if (a <= 2) continue;
            int col = (a << 24) | 0x02040A;                  // near-black, faint blue
            ctx.fill(inset, inset, W - inset, inset + band, col);            // top
            ctx.fill(inset, H - inset - band, W - inset, H - inset, col);    // bottom
            ctx.fill(inset, inset, inset + band, H - inset, col);            // left
            ctx.fill(W - inset - band, inset, W - inset, H - inset, col);    // right
        }

        // activation flash (cyan-white burst), first 10 ticks
        if (elapsed < 10) {
            int a = (int) (210 * (1 - elapsed / 10.0));
            ctx.fill(0, 0, W, H, (a << 24) | 0x8FF6FF);
        }
        // cinematic letterbox + moving scanline
        ctx.fill(0, 0, W, 22, 0x99000000);
        ctx.fill(0, H - 22, W, H, 0x99000000);
        int sy = (int) ((t * 5) % H);
        hLine(ctx, 0, W, sy, 0x2241E9FF);

        if (elapsed < 48) {
            // ---- dramatic reveal: 領域展開 / 기계의 세계 ----
            String jp = "領域展開";
            boolean blink = (elapsed < 26) && ((t / 3) % 2 == 0);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(W / 2f, H / 2f - 48f, 0f);
            ctx.getMatrices().scale(3.0f, 3.0f, 1f);
            ctx.drawText(tr, Text.literal(jp), -tr.getWidth(jp) / 2, 0, blink ? 0xFFFFFFFF : CY, true);
            ctx.getMatrices().pop();

            String kr = "기 계 의  세 계";
            ctx.getMatrices().push();
            ctx.getMatrices().translate(W / 2f, H / 2f - 18f, 0f);
            ctx.getMatrices().scale(1.7f, 1.7f, 1f);
            ctx.drawText(tr, Text.literal(kr), -tr.getWidth(kr) / 2, 0, VIO, true);
            ctx.getMatrices().pop();

            String en = "M A C H I N E   W O R L D   ·   SURE-HIT";
            ctx.drawText(tr, Text.literal(en), W / 2 - tr.getWidth(en) / 2, H / 2 + 14, DIM, true);
        } else {
            // ---- compact banner once the name has faded ----
            String b = "領域 · 기계의 세계";
            ctx.getMatrices().push();
            ctx.getMatrices().translate(W / 2f, 4f, 0f);
            ctx.getMatrices().scale(1.3f, 1.3f, 1f);
            ctx.drawText(tr, Text.literal(b), -tr.getWidth(b) / 2, 0, CY, true);
            ctx.getMatrices().pop();
        }

        // remaining-time bar
        int barW = 170, bx = W / 2 - barW / 2, by = H - 30;
        ctx.fill(bx - 1, by - 1, bx + barW + 1, by + 4, 0xFF10202A);
        int fillW = (int) (barW * domainTimer / (double) DOMAIN_TICKS);
        ctx.fill(bx, by, bx + fillW, by + 3, CY);
        String sec = (domainTimer / 20 + 1) + "s";
        ctx.drawText(tr, Text.literal(sec), bx + barW + 6, by - 3, CY, true);
    }

    // ---- CROW GRAVE overlay (領域展開 · 까마귀들의 무덤) — darker & fouler ----
    private void drawCrowOverlay(DrawContext ctx, MinecraftClient c) {
        TextRenderer tr = c.textRenderer;
        int W = ctx.getScaledWindowWidth();
        int H = ctx.getScaledWindowHeight();
        int elapsed = CROW_DOMAIN_TICKS - crowDomainTimer;
        long t = c.player.age;

        // void darkness swallowing the world (heavier than the machine domain)
        float dk = Math.min(1f, elapsed / 16f);
        if (crowDomainTimer < 20) dk *= crowDomainTimer / 20f;
        ctx.fill(0, 0, W, H, ((int) (95 * dk) << 24) | 0x060A06);   // sickly dark tint
        int N = 64, maxIn = Math.min(W, H) * 3 / 5, band = Math.max(1, maxIn / N) + 1;
        for (int i = 0; i < N; i++) {
            int inset = i * maxIn / N;
            int a = (int) (235 * dk * Math.pow(1 - (double) i / N, 2.2));
            if (a <= 2) continue;
            int col = (a << 24) | 0x030603;
            ctx.fill(inset, inset, W - inset, inset + band, col);
            ctx.fill(inset, H - inset - band, W - inset, H - inset, col);
            ctx.fill(inset, inset, inset + band, H - inset, col);
            ctx.fill(W - inset - band, inset, W - inset, H - inset, col);
        }
        // faint green poison haze at the very bottom
        ctx.fill(0, H - 40, W, H, (((int) (40 * dk)) << 24) | 0x2E5A18);

        // HEARTBEAT blood pulse — the edges throb red like a dying heart
        double beat = Math.max(0, Math.sin(t * 0.18)) * Math.max(0, Math.sin(t * 0.18));
        int pulse = (int) (150 * dk * beat);
        if (pulse > 3) {
            int bandp = Math.max(1, maxIn / N) + 1;
            for (int i = 0; i < N; i++) {
                int inset = i * maxIn / N;
                int a = (int) (pulse * Math.pow(1 - (double) i / N, 2.6));
                if (a <= 2) continue;
                int col = (a << 24) | 0x6A0202;
                ctx.fill(inset, inset, W - inset, inset + bandp, col);
                ctx.fill(inset, H - inset - bandp, W - inset, H - inset, col);
                ctx.fill(inset, inset, inset + bandp, H - inset, col);
                ctx.fill(W - inset - bandp, inset, W - inset, H - inset, col);
            }
        }
        // blood dripping down from the top edge
        for (int i = 0; i < 26; i++) {
            int dx2 = (i * 61 + 13) % W;
            int len = (int) (6 + 10 * (0.5 + 0.5 * Math.sin(i * 1.7 + t * 0.05)));
            ctx.fill(dx2, 22, dx2 + 2, 22 + len, (((int) (170 * dk)) << 24) | 0x6A0202);
        }

        ctx.fill(0, 0, W, 22, 0xB0000000);
        ctx.fill(0, H - 22, W, H, 0xB0000000);

        if (elapsed < 50) {
            String jp = "領域展開";
            boolean blink = (elapsed < 28) && ((t / 3) % 2 == 0);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(W / 2f, H / 2f - 48f, 0f);
            ctx.getMatrices().scale(3.0f, 3.0f, 1f);
            ctx.drawText(tr, Text.literal(jp), -tr.getWidth(jp) / 2, 0, blink ? 0xFFFFFFFF : BON, true);
            ctx.getMatrices().pop();
            String kr = "까마귀들의 무덤";
            ctx.getMatrices().push();
            ctx.getMatrices().translate(W / 2f, H / 2f - 16f, 0f);
            ctx.getMatrices().scale(1.9f, 1.9f, 1f);
            ctx.drawText(tr, Text.literal(kr), -tr.getWidth(kr) / 2, 0, RED, true);
            ctx.getMatrices().pop();
            String en = "G R A V E   O F   C R O W S   ·   POISON";
            ctx.drawText(tr, Text.literal(en), W / 2 - tr.getWidth(en) / 2, H / 2 + 16, GRN, true);
        } else {
            String b = "領域 · 까마귀들의 무덤";
            ctx.getMatrices().push();
            ctx.getMatrices().translate(W / 2f, 4f, 0f);
            ctx.getMatrices().scale(1.3f, 1.3f, 1f);
            ctx.drawText(tr, Text.literal(b), -tr.getWidth(b) / 2, 0, BON, true);
            ctx.getMatrices().pop();
        }
        int barW = 170, bx = W / 2 - barW / 2, by = H - 30;
        ctx.fill(bx - 1, by - 1, bx + barW + 1, by + 4, 0xFF201810);
        int fillW = (int) (barW * crowDomainTimer / (double) CROW_DOMAIN_TICKS);
        ctx.fill(bx, by, bx + fillW, by + 3, GRN);
        String sec = (crowDomainTimer / 20 + 1) + "s";
        ctx.drawText(tr, Text.literal(sec), bx + barW + 6, by - 3, GRN, true);
    }

    // ---- small skill panel while the Crow Fan is held ----
    private void drawFanPanel(DrawContext ctx, MinecraftClient c) {
        TextRenderer tr = c.textRenderer;
        int y0 = shadesOn(c) ? 96 : 8;
        ctx.drawText(tr, Text.literal("CROW FAN · 까마귀의 부채"), 8, y0, BON, true);
        ctx.drawText(tr, Text.literal((crowDomainTimer > 0 ? ">> 세계 ACTIVE " + (crowDomainTimer / 20 + 1) + "s" : "= 세계 (R)")), 8, y0 + 12, crowDomainTimer > 0 ? RED : DIM, true);
        ctx.drawText(tr, Text.literal("까마귀 소환 (C)  x" + crows.size()), 8, y0 + 22, crows.isEmpty() ? DIM : PUR, true);
        ctx.drawText(tr, Text.literal((wingsTimer > 0 ? ">> 날개 " + (wingsTimer / 20 + 1) + "s" : "= 까마귀의 날개 (V)")), 8, y0 + 32, wingsTimer > 0 ? GRN : DIM, true);
        ctx.drawText(tr, Text.literal((sealTimer > 0 ? ">> 봉인 " + (sealTimer / 20 + 1) + "s" : "= 망자 봉인 (X)")), 8, y0 + 42, sealTimer > 0 ? PUR : DIM, true);
    }

    // ---- DUEL ARENA overlay (決闘場 · 전사들의 결투장) — golden & grand ----
    private void drawArenaOverlay(DrawContext ctx, MinecraftClient c) {
        TextRenderer tr = c.textRenderer;
        int W = ctx.getScaledWindowWidth();
        int H = ctx.getScaledWindowHeight();
        int elapsed = ARENA_TICKS - arenaTimer;
        long t = c.player.age;

        float dk = Math.min(1f, elapsed / 16f);
        if (arenaTimer < 20) dk *= arenaTimer / 20f;
        ctx.fill(0, 0, W, H, ((int) (55 * dk) << 24) | 0x140C04);   // warm dusk tint
        int N = 60, maxIn = Math.min(W, H) * 3 / 5, band = Math.max(1, maxIn / N) + 1;
        for (int i = 0; i < N; i++) {
            int inset = i * maxIn / N;
            int a = (int) (200 * dk * Math.pow(1 - (double) i / N, 2.2));
            if (a <= 2) continue;
            int col = (a << 24) | 0x1A1206;
            ctx.fill(inset, inset, W - inset, inset + band, col);
            ctx.fill(inset, H - inset - band, W - inset, H - inset, col);
            ctx.fill(inset, inset, inset + band, H - inset, col);
            ctx.fill(W - inset - band, inset, W - inset, H - inset, col);
        }
        ctx.fill(0, 0, W, 22, 0xAA000000);
        ctx.fill(0, H - 22, W, H, 0xAA000000);

        if (elapsed < 52) {
            String jp = "決闘場";
            boolean blink = (elapsed < 26) && ((t / 3) % 2 == 0);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(W / 2f, H / 2f - 50f, 0f);
            ctx.getMatrices().scale(3.2f, 3.2f, 1f);
            ctx.drawText(tr, Text.literal(jp), -tr.getWidth(jp) / 2, 0, blink ? 0xFFFFFFFF : GLD, true);
            ctx.getMatrices().pop();
            String kr = "전사들의 결투장";
            ctx.getMatrices().push();
            ctx.getMatrices().translate(W / 2f, H / 2f - 16f, 0f);
            ctx.getMatrices().scale(1.9f, 1.9f, 1f);
            ctx.drawText(tr, Text.literal(kr), -tr.getWidth(kr) / 2, 0, CRM, true);
            ctx.getMatrices().pop();
            String en = "D U E L   A R E N A   ·   1  vs  1";
            ctx.drawText(tr, Text.literal(en), W / 2 - tr.getWidth(en) / 2, H / 2 + 16, BON, true);
        } else {
            String b = "決闘場 · 전사들의 결투장";
            ctx.getMatrices().push();
            ctx.getMatrices().translate(W / 2f, 4f, 0f);
            ctx.getMatrices().scale(1.3f, 1.3f, 1f);
            ctx.drawText(tr, Text.literal(b), -tr.getWidth(b) / 2, 0, GLD, true);
            ctx.getMatrices().pop();
        }
        // versus line + buff/debuff readout
        String vs = "VS  " + opponentName;
        ctx.drawText(tr, Text.literal(vs), W / 2 - tr.getWidth(vs) / 2, H / 2 + 30, 0xFFFFFFFF, true);
        String me = "나 : 힘+ 속도+ 채굴+ 방어+";
        String foe = "상대 : 나약함- 둔화-";
        ctx.drawText(tr, Text.literal(me), 10, H - 44, GLD, true);
        ctx.drawText(tr, Text.literal(foe), W - 10 - tr.getWidth(foe), H - 44, CRM, true);

        int barW = 180, bx = W / 2 - barW / 2, by = H - 30;
        ctx.fill(bx - 1, by - 1, bx + barW + 1, by + 4, 0xFF241A08);
        int fillW = (int) (barW * arenaTimer / (double) ARENA_TICKS);
        ctx.fill(bx, by, bx + fillW, by + 3, GLD);
        String sec = (arenaTimer / 20 + 1) + "s";
        ctx.drawText(tr, Text.literal(sec), bx + barW + 6, by - 3, GLD, true);
    }

    private void drawSwordPanel(DrawContext ctx, MinecraftClient c) {
        TextRenderer tr = c.textRenderer;
        int H = ctx.getScaledWindowHeight();
        int y0 = H - 120;
        ctx.drawText(tr, Text.literal("DUEL BLADE · 결투장의 대검"), 8, y0, GLD, true);
        ctx.drawText(tr, Text.literal(arenaTimer > 0 ? ">> 결투장 " + (arenaTimer / 20 + 1) + "s  VS " + opponentName : "= 전사들의 결투장 (B)"),
            8, y0 + 12, arenaTimer > 0 ? CRM : DIM, true);
        ctx.drawText(tr, Text.literal(berserkTimer > 0 ? ">> 광폭화 " + (berserkTimer / 20 + 1) + "s" : "= 광폭화 (Z)"),
            8, y0 + 22, berserkTimer > 0 ? CRM : DIM, true);
        ctx.drawText(tr, Text.literal("혈검술 (N)  — 피를 깎아 참격"), 8, y0 + 32, slashTimer > 0 ? CRM : DIM, true);
        ctx.drawText(tr, Text.literal("전사의 심판 — 원거리 상대 즉사"), 8, y0 + 42, judgmentTimer > 0 ? CRM : DIM, true);
    }

    // full-screen effects for the sword skills (berserk aura, judgment banner)
    private void drawSwordFx(DrawContext ctx, MinecraftClient c) {
        TextRenderer tr = c.textRenderer;
        int W = ctx.getScaledWindowWidth(), H = ctx.getScaledWindowHeight();
        long t = c.player.age;
        if (berserkTimer > 0) {
            double beat = 0.55 + 0.45 * Math.sin(t * 0.3);
            double fade = Math.min(1.0, berserkTimer / 20.0);
            int N = 42, maxIn = Math.min(W, H) / 2, band = Math.max(1, maxIn / N) + 1;
            for (int i = 0; i < N; i++) {
                int inset = i * maxIn / N;
                int a = (int) (95 * fade * beat * Math.pow(1 - (double) i / N, 2.4));
                if (a <= 2) continue;
                int col = (a << 24) | 0x8A0410;
                ctx.fill(inset, inset, W - inset, inset + band, col);
                ctx.fill(inset, H - inset - band, W - inset, H - inset, col);
                ctx.fill(inset, inset, inset + band, H - inset, col);
                ctx.fill(W - inset - band, inset, W - inset, H - inset, col);
            }
            String bz = "光 · 광폭화 BERSERK";
            ctx.drawText(tr, Text.literal(bz), W / 2 - tr.getWidth(bz) / 2, 30, CRM, true);
        }
        if (judgmentTimer > 0) {
            int a = (int) (140 * Math.min(1.0, judgmentTimer / 12.0));
            ctx.fill(0, 0, W, H, (a << 24) | 0x2A0006);
            String jp = "戦士の審判";
            boolean blink = ((t / 2) % 2 == 0);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(W / 2f, H / 2f - 46f, 0f);
            ctx.getMatrices().scale(3.4f, 3.4f, 1f);
            ctx.drawText(tr, Text.literal(jp), -tr.getWidth(jp) / 2, 0, blink ? 0xFFFFFFFF : CRM, true);
            ctx.getMatrices().pop();
            String kr = "전사의 심판";
            ctx.getMatrices().push();
            ctx.getMatrices().translate(W / 2f, H / 2f - 12f, 0f);
            ctx.getMatrices().scale(2.0f, 2.0f, 1f);
            ctx.drawText(tr, Text.literal(kr), -tr.getWidth(kr) / 2, 0, GLD, true);
            ctx.getMatrices().pop();
            String en = "W A R R I O R ' S   J U D G M E N T   ·   즉사";
            ctx.drawText(tr, Text.literal(en), W / 2 - tr.getWidth(en) / 2, H / 2 + 18, BON, true);
        }
    }

    private void onHud(DrawContext ctx, RenderTickCounter counter) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || c.world == null) return;
        // Domain overlay renders whenever the domain is up + shades on (even in standby).
        if (domainTimer > 0 && shadesOn(c)) drawDomainOverlay(ctx, c);
        // Crow-grave overlay + fan HUD render whenever the fan is held.
        if (crowDomainTimer > 0 && heldFan(c)) drawCrowOverlay(ctx, c);
        if (heldFan(c)) drawFanPanel(ctx, c);
        // Duel arena overlay + sword HUD render whenever the greatsword is held.
        if (arenaTimer > 0 && heldSword(c)) drawArenaOverlay(ctx, c);
        if (heldSword(c)) { drawSwordPanel(ctx, c); drawSwordFx(ctx, c); }
        // Ocean world overlay + trident HUD whenever the trident is held.
        if (seaTimer > 0 && heldTrident(c)) drawSeaOverlay(ctx, c);
        if (heldTrident(c)) drawTridentPanel(ctx, c);
        // Highway overlay + pipe HUD whenever the neon pipe is held.
        if (roadTimer > 0 && heldPipe(c)) drawRoadOverlay(ctx, c);
        if (heldPipe(c)) drawPipePanel(ctx, c);
        // King overlay + scepter/knight HUD
        if (kingTimer > 0 && heldScepter(c)) drawKingOverlay(ctx, c);
        if (heldScepter(c)) drawScepterPanel(ctx, c);
        if (heldKnightBlade(c)) drawKnightPanel(ctx, c);
        if (!active(c)) return;
        ClientPlayerEntity p = c.player;
        TextRenderer tr = c.textRenderer;
        int W = ctx.getScaledWindowWidth();
        int Hh = ctx.getScaledWindowHeight();
        long t = p.age;

        List<LivingEntity> hostiles = c.world.getEntitiesByClass(LivingEntity.class,
            p.getBoundingBox().expand(RANGE),
            e -> e.isAlive() && e != p && ((e instanceof HostileEntity) || (e instanceof PlayerEntity)));

        // ---------- top-left system panel ----------
        ctx.drawText(tr, Text.literal("J.A.R.V.I.S"), 8, 8, CY, true);
        ctx.drawText(tr, Text.literal("VOID HUNT PROTOCOL"), 8, 18, DIM, true);
        String clock = String.format("%02d:%02d", java.time.LocalTime.now().getHour(), java.time.LocalTime.now().getMinute());
        ctx.drawText(tr, Text.literal((huntMode ? "> HUNTING" : "= STANDBY") + "   " + clock), 8, 30, huntMode ? AMB : DIM, true);
        ctx.drawText(tr, Text.literal("TARGETS " + hostiles.size() + "   RANGE " + (int) RANGE + "m"), 8, 42, CY, true);
        ctx.drawText(tr, Text.literal(target != null ? "LOCK  >> LOCKED" : "LOCK  -- SEARCHING"), 8, 54, target != null ? AMB : DIM, true);
        ctx.drawText(tr, Text.literal("DRONES " + drones.size() + "/" + MAX_DRONES + "  (K)   ULT (O)"), 8, 66, drones.isEmpty() ? DIM : VIO, true);
        ctx.drawText(tr, Text.literal(domainTimer > 0 ? "DOMAIN >> ACTIVE (G)" : "DOMAIN -- READY (G)"), 8, 78, domainTimer > 0 ? RED : DIM, true);

        // ---------- ULTIMATE banner ----------
        if (ultTimer > 0) {
            String u = ">> ORBITAL STRIKE <<   " + (ultTimer / 20 + 1) + "s";
            int uw = tr.getWidth(u);
            ctx.fill(W / 2 - uw / 2 - 12, 82, W / 2 + uw / 2 + 12, 100, 0xAA000000);
            hLine(ctx, W / 2 - uw / 2 - 12, W / 2 + uw / 2 + 12, 82, 0xFFFF3050);
            hLine(ctx, W / 2 - uw / 2 - 12, W / 2 + uw / 2 + 12, 100, 0xFFFF3050);
            ctx.drawText(tr, Text.literal(u), W / 2 - uw / 2, 87, 0xFFFF5070, true);
        }

        // ---------- top-right radar ----------
        int rcx = W - 58, rcy = 60, R = 44;
        ctx.fill(rcx - R - 6, rcy - R - 6, rcx + R + 6, rcy + R + 6, 0x66000000);
        ring(ctx, rcx, rcy, R, 0x8841E9FF);
        ring(ctx, rcx, rcy, R * 2 / 3, 0x5541E9FF);
        ring(ctx, rcx, rcy, R / 3, 0x5541E9FF);
        hLine(ctx, rcx - R, rcx + R, rcy, 0x4441E9FF);
        vLine(ctx, rcx, rcy - R, rcy + R, 0x4441E9FF);
        double sweep = (t * 0.06) % (2 * Math.PI);
        line(ctx, rcx, rcy, rcx + (int) (Math.cos(sweep) * R), rcy + (int) (Math.sin(sweep) * R), 0xAA41E9FF);
        float yawR = (float) Math.toRadians(p.getYaw());
        double fwx = -Math.sin(yawR), fwz = Math.cos(yawR);   // forward
        double rgx = Math.cos(yawR),  rgz = Math.sin(yawR);   // right
        for (LivingEntity m : hostiles) {
            double relX = m.getX() - p.getX(), relZ = m.getZ() - p.getZ();
            double forward = relX * fwx + relZ * fwz;
            double rightd  = relX * rgx + relZ * rgz;
            int bx = rcx + (int) (rightd / RANGE * R);
            int by = rcy - (int) (forward / RANGE * R);
            int col = (target != null && m == target) ? AMB : ((m instanceof PlayerEntity) ? PUR : (m.getMaxHealth() >= 40 ? RED : CY));
            ctx.fill(bx - 1, by - 1, bx + 2, by + 2, col);
        }
        ctx.fill(rcx - 1, rcy - 1, rcx + 2, rcy + 2, 0xFF8FF6FF);

        // ---------- center lock reticle ----------
        int mx = W / 2, my = Hh / 2;
        if (target != null) {
            int L = 11, T = 2, col = AMB;
            ctx.fill(mx - L, my - L, mx - T, my - L + 1, col); ctx.fill(mx - L, my - L, mx - L + 1, my - T, col);
            ctx.fill(mx + T, my - L, mx + L, my - L + 1, col); ctx.fill(mx + L - 1, my - L, mx + L, my - T, col);
            ctx.fill(mx - L, my + L - 1, mx - T, my + L, col); ctx.fill(mx - L, my + T, mx - L + 1, my + L, col);
            ctx.fill(mx + T, my + L - 1, mx + L, my + L, col); ctx.fill(mx + L - 1, my + T, mx + L, my + L, col);
        }

        // ---------- bottom-center target card ----------
        if (target != null) {
            String nm = target.getName().getString();
            float hp = target.getHealth(), mhp = Math.max(1f, target.getMaxHealth());
            float dist = p.distanceTo(target);
            String threat = mhp >= 40 ? "HIGH" : (mhp >= 20 ? "MED" : "LOW");
            boolean exec = hp / mhp <= 0.2f;
            String meta = "DIST " + String.format("%.1fm", dist) + "  |  THREAT " + threat;
            int tw = Math.max(96, Math.max(tr.getWidth(nm), tr.getWidth(meta)));
            int cx = W / 2, ty = Hh - 58;
            ctx.fill(cx - tw / 2 - 8, ty - 6, cx + tw / 2 + 8, ty + 30, 0xB0000000);
            hLine(ctx, cx - tw / 2 - 8, cx + tw / 2 + 8, ty - 6, 0x8841E9FF);
            ctx.drawText(tr, Text.literal(nm), cx - tr.getWidth(nm) / 2, ty, 0xFFFFFFFF, true);
            ctx.drawText(tr, Text.literal(meta), cx - tr.getWidth(meta) / 2, ty + 11, DIM, true);
            int bx = cx - tw / 2, by = ty + 23, bw = tw;
            ctx.fill(bx - 1, by - 1, bx + bw + 1, by + 4, 0xFF20323A);
            ctx.fill(bx, by, bx + (int) (bw * Math.max(0f, hp / mhp)), by + 3, exec ? 0xFFFF2040 : RED);
            String hptxt = (int) Math.ceil(hp) + "/" + (int) mhp;
            ctx.drawText(tr, Text.literal(hptxt), cx + tw / 2 - tr.getWidth(hptxt), by - 9, 0xFFFFFFFF, true);
            if (exec) {
                String ex = "!! EXECUTE";
                ctx.drawText(tr, Text.literal(ex), cx - tr.getWidth(ex) / 2, ty - 16, 0xFFFF3050, true);
            }
        }

        // ---------- bottom-right ELIMINATED counter ----------
        String kb = String.valueOf(kills);
        ctx.drawText(tr, Text.literal(kb), W - 16 - tr.getWidth(kb), Hh - 40, 0xFFFFFFFF, true);
        ctx.drawText(tr, Text.literal("ELIMINATED"), W - 16 - tr.getWidth("ELIMINATED"), Hh - 28, DIM, true);
        if (combo > 1) {
            String cb = "COMBO x" + combo;
            ctx.drawText(tr, Text.literal(cb), W - 16 - tr.getWidth(cb), Hh - 52, AMB, true);
        }
    }
}
