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
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
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

    static final class Drone {
        Vec3d pos; Vec3d goal; int idx; int cd = 0;
        Drone(Vec3d p, int idx){ this.pos = p; this.idx = idx; }
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
        if (kNow && !lastK) toggleDrones(c);
        if (lNow && !lastL && shadesOn(c) && ultTimer <= 0) {  // ULTIMATE
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

    private void onHud(DrawContext ctx, RenderTickCounter counter) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || c.world == null) return;
        // Domain overlay renders whenever the domain is up + shades on (even in standby).
        if (domainTimer > 0 && shadesOn(c)) drawDomainOverlay(ctx, c);
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
