package com.voidhunt;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
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
    private static KeyBinding toggleKey;   // H : hunt mode on/off
    private static KeyBinding droneKey;     // Q : summon/recall drones

    private static boolean huntMode  = true;
    private static boolean aimAssist = true;
    private static boolean lastUse   = false;   // edge-detect right click
    private static MobEntity target;

    private static final double RANGE = 20.0;   // detection radius
    private static final double REACH = 3.0;    // melee reach
    private static final float  AIM   = 0.20f;  // aim-assist strength

    // ---- drones ----
    private static final List<Drone> drones = new ArrayList<>();
    private static final int    MAX_DRONES = 2;
    private static final double DRONE_RANGE = 14.0;
    private static final float  DRONE_DMG   = 4.0f;

    private static final int CY=0xFF41E9FF, AMB=0xFFFFB638, DIM=0xFF5B7C8A, RED=0xFFFF4D6D, VIO=0xFFB98CFF;

    static final class Drone {
        Vec3d pos; Vec3d goal; int side; int cd = 0;
        Drone(Vec3d p, int side){ this.pos = p; this.side = side; }
    }

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.voidhunt.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "category.voidhunt"));
        droneKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.voidhunt.drone", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P, "category.voidhunt"));
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        HudRenderCallback.EVENT.register(this::onHud);
    }

    private boolean shadesOn(MinecraftClient c) {
        return c.player != null
            && c.player.getEquippedStack(EquipmentSlot.HEAD).isOf(VoidHunt.VOID_SHADES);
    }
    private boolean active(MinecraftClient c) { return huntMode && shadesOn(c); }

    private void onTick(MinecraftClient c) {
        while (toggleKey.wasPressed()) huntMode = !huntMode;
        while (droneKey.wasPressed())  toggleDrones(c);
        target = null;

        if (c.player == null || c.world == null || c.interactionManager == null) return;
        if (!shadesOn(c)) { drones.clear(); return; }

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
                }
            }
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
    }

    private void toggleDrones(MinecraftClient c) {
        if (c.player == null || !shadesOn(c)) return;
        if (drones.isEmpty()) {
            Vec3d base = c.player.getEyePos();
            drones.add(new Drone(base.add(-1.5, 0.6, -1.5), -1));
            drones.add(new Drone(base.add( 1.5, 0.6, -1.5),  1));
        } else {
            drones.clear();
        }
    }

    private void tickDrones(MinecraftClient c) {
        if (drones.isEmpty()) return;
        ClientPlayerEntity p = c.player;
        ClientWorld w = c.world;
        for (Drone d : drones) {
            Vec3d follow = p.getEyePos()
                .add(p.getRotationVec(1.0f).multiply(2.0))
                .add(d.side * 1.6, 0.7, 0.0);
            Vec3d goal = (d.goal != null) ? d.goal.add(0.0, 1.0, 0.0) : follow;
            d.pos = d.pos.add(goal.subtract(d.pos).multiply(0.15));

            // drone body
            w.addParticle(ParticleTypes.END_ROD, d.pos.x, d.pos.y, d.pos.z, 0, 0, 0);
            if ((p.age % 4) == 0)
                w.addParticle(ParticleTypes.SOUL_FIRE_FLAME, d.pos.x, d.pos.y, d.pos.z, 0, 0.01, 0);

            // nearest hostile near the drone
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

    private void onHud(DrawContext ctx, RenderTickCounter counter) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (!active(c)) return;
        TextRenderer tr = c.textRenderer;
        int W = ctx.getScaledWindowWidth();
        int Hh = ctx.getScaledWindowHeight();

        ctx.drawText(tr, Text.literal(">> VOID HUNT"), 8, 8, CY, true);
        ctx.drawText(tr, Text.literal(target != null ? "[ LOCKED ]" : "[ SEARCHING ]"),
            8, 20, target != null ? AMB : DIM, true);
        ctx.drawText(tr, Text.literal("DRONES " + drones.size() + "/" + MAX_DRONES + "  (Q)"),
            8, 32, drones.isEmpty() ? DIM : VIO, true);

        if (target != null) {
            String nm = target.getName().getString();
            float hp = target.getHealth(), mhp = Math.max(1f, target.getMaxHealth());
            float dist = c.player.distanceTo(target);
            String meta = "HP " + (int) Math.ceil(hp) + "/" + (int) mhp + "   " + String.format("%.1fm", dist);
            int tw = Math.max(tr.getWidth(nm), tr.getWidth(meta));
            int cx = W / 2, ty = 26;
            ctx.fill(cx - tw / 2 - 8, ty - 6, cx + tw / 2 + 8, ty + 28, 0xB0000000);
            ctx.drawText(tr, Text.literal(nm), cx - tw / 2, ty, 0xFFFFFFFF, true);
            ctx.drawText(tr, Text.literal(meta), cx - tw / 2, ty + 11, DIM, true);
            int bx = cx - tw / 2, by = ty + 22, bw = tw;
            ctx.fill(bx - 1, by - 1, bx + bw + 1, by + 4, 0xFF20323A);
            ctx.fill(bx, by, bx + (int) (bw * Math.max(0f, hp / mhp)), by + 3, RED);

            int mx = W / 2, my = Hh / 2, L = 11, T = 2;
            ctx.fill(mx - L, my - L, mx - T, my - L + 1, AMB); ctx.fill(mx - L, my - L, mx - L + 1, my - T, AMB);
            ctx.fill(mx + T, my - L, mx + L, my - L + 1, AMB); ctx.fill(mx + L - 1, my - L, mx + L, my - T, AMB);
            ctx.fill(mx - L, my + L - 1, mx - T, my + L, AMB); ctx.fill(mx - L, my + T, mx - L + 1, my + L, AMB);
            ctx.fill(mx + T, my + L - 1, mx + L, my + L, AMB); ctx.fill(mx + L - 1, my + T, mx + L, my + L, AMB);
        }
    }
}
