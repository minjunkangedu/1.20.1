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
    private static MobEntity target;
    private static MobEntity attackedTarget = null; // for kill counting
    private static int kills = 0, combo = 0, comboTimer = 0;

    private static final double RANGE = 20.0;   // detection radius
    private static final double REACH = 3.0;    // melee reach
    private static final float  AIM   = 0.20f;  // aim-assist strength

    // ---- drones ----
    private static final List<Drone> drones = new ArrayList<>();
    private static final int    MAX_DRONES = 2;
    private static final double DRONE_RANGE = 14.0;
    private static final float  DRONE_DMG   = 12.0f;  // laser damage (was 4)

    private static final int CY=0xFF41E9FF, AMB=0xFFFFB638, DIM=0xFF5B7C8A, RED=0xFFFF4D6D, VIO=0xFFB98CFF;

    private static final ItemStack DRONE_STACK = new ItemStack(VoidHunt.VOID_DRONE);

    static final class Drone {
        Vec3d pos; Vec3d goal; int side; int cd = 0;
        Drone(Vec3d p, int side){ this.pos = p; this.side = side; }
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
        if (drones.isEmpty()) return;
        MatrixStack ms = ctx.matrixStack();
        if (ms == null) return;
        VertexConsumerProvider vcp = ctx.consumers();
        Camera cam = ctx.camera();
        net.minecraft.util.math.Vec3d camPos = cam.getPos();
        MinecraftClient mc = MinecraftClient.getInstance();
        float spin = (mc.player != null ? mc.player.age : 0) * 2.0f;
        int light = LightmapTextureManager.pack(15, 15);
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
        if (hNow && !lastH) huntMode = !huntMode;
        if (kNow && !lastK) toggleDrones(c);
        lastH = hNow; lastK = kNow;
        target = null;

        if (c.player == null || c.world == null || c.interactionManager == null) return;

        // kill tracking: a target we hit has died
        if (attackedTarget != null && (attackedTarget.isRemoved() || !attackedTarget.isAlive())) {
            kills++; combo++; comboTimer = 200; attackedTarget = null;
        }
        if (comboTimer > 0) { comboTimer--; if (comboTimer == 0) combo = 0; }

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
                .add(d.side * 1.8, 0.7, 0.0);
            // each drone aims for a spread-out point so they don't stack
            Vec3d spread = new Vec3d(d.side * 1.8, 0.0, 0.0);
            Vec3d goal = (d.goal != null) ? d.goal.add(0.0, 1.0, 0.0).add(spread) : follow;
            d.pos = d.pos.add(goal.subtract(d.pos).multiply(0.15));

            // separation: push apart from any other drone that is too close
            for (Drone o : drones) {
                if (o == d) continue;
                Vec3d diff = d.pos.subtract(o.pos);
                double dist = diff.length();
                if (dist < 2.2 && dist > 0.0001)
                    d.pos = d.pos.add(diff.normalize().multiply((2.2 - dist) * 0.5));
            }

            // small thruster spark under the drone (subtle; body is the 3D model)
            if ((p.age % 6) == 0)
                w.addParticle(ParticleTypes.SOUL_FIRE_FLAME, d.pos.x, d.pos.y - 0.35, d.pos.z, 0, -0.01, 0);

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

    private void onHud(DrawContext ctx, RenderTickCounter counter) {
        MinecraftClient c = MinecraftClient.getInstance();
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
        ctx.drawText(tr, Text.literal("DRONES " + drones.size() + "/" + MAX_DRONES + "  (K)"), 8, 66, drones.isEmpty() ? DIM : VIO, true);

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
