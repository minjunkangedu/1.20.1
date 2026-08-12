package com.voidhunt;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
    private static MobEntity target;
    private static MobEntity attackedTarget = null; // for kill counting
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
            List<MobEntity> mobs = c.world.getEntitiesByClass(MobEntity.class, box,
                e -> e.isAlive() && (e instanceof HostileEntity) && c.player.canSee(e)); // canSee = no through-terrain
            target = mobs.stream().min(Comparator.comparingDouble(c.player::squaredDistanceTo)).orElse(null);

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
        if (!active(c)) return;
        ClientPlayerEntity p = c.player;
        TextRenderer tr = c.textRenderer;
        int W = ctx.getScaledWindowWidth();
        int Hh = ctx.getScaledWindowHeight();
        long t = p.age;

        List<MobEntity> hostiles = c.world.getEntitiesByClass(MobEntity.class,
            p.getBoundingBox().expand(RANGE), e -> e.isAlive() && (e instanceof HostileEntity));

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
        for (MobEntity m : hostiles) {
            double relX = m.getX() - p.getX(), relZ = m.getZ() - p.getZ();
            double forward = relX * fwx + relZ * fwz;
            double rightd  = relX * rgx + relZ * rgz;
            int bx = rcx + (int) (rightd / RANGE * R);
            int by = rcy - (int) (forward / RANGE * R);
            int col = (target != null && m == target) ? AMB : (m.getMaxHealth() >= 40 ? RED : CY);
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
