package com.voidhunt;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;

public class VoidHuntClient implements ClientModInitializer {
    private static KeyBinding toggleKey;
    private static boolean huntMode = true;      // on by default (still needs shades worn)
    private static MobEntity target;
    private static final double RANGE = 16.0;    // detection radius
    private static final double REACH = 3.0;     // melee reach (blocks)

    private static final int CY  = 0xFF41E9FF;   // cyan
    private static final int AMB = 0xFFFFB638;   // amber
    private static final int DIM = 0xFF5B7C8A;
    private static final int RED = 0xFFFF4D6D;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.voidhunt.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "category.voidhunt"));
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
        target = null;
        if (c.player == null || c.world == null || c.interactionManager == null) return;
        if (!active(c)) return;

        // --- AUTO-TARGET: nearest living hostile in range ---
        Box box = c.player.getBoundingBox().expand(RANGE);
        List<MobEntity> mobs = c.world.getEntitiesByClass(MobEntity.class, box,
            e -> e.isAlive() && e != c.player && (e instanceof HostileEntity));
        target = mobs.stream()
            .min(Comparator.comparingDouble(c.player::squaredDistanceTo))
            .orElse(null);
        if (target == null) return;

        // --- AUTO-ATTACK: if in reach and attack fully charged ---
        boolean inReach = c.player.squaredDistanceTo(target) <= REACH * REACH;
        boolean charged = c.player.getAttackCooldownProgress(0.0f) >= 1.0f;
        boolean canSee   = c.player.canSee(target);
        if (inReach && charged && canSee) {
            c.interactionManager.attackEntity(c.player, target);
            c.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private void onHud(DrawContext ctx, RenderTickCounter counter) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (!active(c)) return;
        TextRenderer tr = c.textRenderer;
        int W = ctx.getScaledWindowWidth();
        int Hh = ctx.getScaledWindowHeight();

        // top-left status
        ctx.drawText(tr, Text.literal(">> VOID HUNT"), 8, 8, CY, true);
        ctx.drawText(tr, Text.literal(target != null ? "[ LOCKED ]" : "[ SEARCHING ]"),
            8, 20, target != null ? AMB : DIM, true);

        if (target != null) {
            String nm = target.getName().getString();
            float hp = target.getHealth(), mhp = Math.max(1f, target.getMaxHealth());
            float dist = c.player.distanceTo(target);
            String meta = "HP " + (int) Math.ceil(hp) + "/" + (int) mhp
                        + "   " + String.format("%.1fm", dist);
            int tw = Math.max(tr.getWidth(nm), tr.getWidth(meta));
            int cx = W / 2, ty = 26;
            ctx.fill(cx - tw / 2 - 8, ty - 6, cx + tw / 2 + 8, ty + 28, 0xB0000000);
            ctx.drawText(tr, Text.literal(nm),   cx - tw / 2, ty, 0xFFFFFFFF, true);
            ctx.drawText(tr, Text.literal(meta), cx - tw / 2, ty + 11, DIM, true);
            // hp bar
            int bx = cx - tw / 2, by = ty + 22, bw = tw;
            ctx.fill(bx - 1, by - 1, bx + bw + 1, by + 4, 0xFF20323A);
            ctx.fill(bx, by, bx + (int) (bw * Math.max(0f, hp / mhp)), by + 3, RED);

            // center lock reticle (amber corner brackets)
            int mx = W / 2, my = Hh / 2, L = 11, T = 2;
            ctx.fill(mx - L, my - L, mx - T, my - L + 1, AMB); ctx.fill(mx - L, my - L, mx - L + 1, my - T, AMB);
            ctx.fill(mx + T, my - L, mx + L, my - L + 1, AMB); ctx.fill(mx + L - 1, my - L, mx + L, my - T, AMB);
            ctx.fill(mx - L, my + L - 1, mx - T, my + L, AMB); ctx.fill(mx - L, my + T, mx - L + 1, my + L, AMB);
            ctx.fill(mx + T, my + L - 1, mx + L, my + L, AMB); ctx.fill(mx + L - 1, my + T, mx + L, my + L, AMB);
        }
    }
}
