package com.virtualredstonewire.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.virtualredstonewire.VirtualRedstoneWire;
import com.virtualredstonewire.client.ClientCableCache;
import com.virtualredstonewire.config.ClientConfig;
import com.virtualredstonewire.config.ServerConfig;
import com.virtualredstonewire.data.CableLink;
import com.virtualredstonewire.item.CableCutterItem;
import com.virtualredstonewire.item.CableMagnifierItem;
import com.virtualredstonewire.item.VirtualCableItem;
import com.virtualredstonewire.network.CableNetworkChannel;
import com.virtualredstonewire.network.CableRequestSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid = VirtualRedstoneWire.MOD_ID, value = Dist.CLIENT)
public class CableRenderer
{
    private static final float OUTLINE_EXPAND = 0.001f;

    private static float[] getColorInput()
    {
        return new float[]{
            ClientConfig.colorInputR.get().floatValue(),
            ClientConfig.colorInputG.get().floatValue(),
            ClientConfig.colorInputB.get().floatValue(),
            ClientConfig.colorInputA.get().floatValue()};
    }

    private static float[] getColorOutput()
    {
        return new float[]{
            ClientConfig.colorOutputR.get().floatValue(),
            ClientConfig.colorOutputG.get().floatValue(),
            ClientConfig.colorOutputB.get().floatValue(),
            ClientConfig.colorOutputA.get().floatValue()};
    }

    private static float[] getColorLine()
    {
        return new float[]{
            ClientConfig.colorLineR.get().floatValue(),
            ClientConfig.colorLineG.get().floatValue(),
            ClientConfig.colorLineB.get().floatValue(),
            ClientConfig.colorLineA.get().floatValue()};
    }

    private static float[] getColorDim()
    {
        return new float[]{
            ClientConfig.colorDimR.get().floatValue(),
            ClientConfig.colorDimG.get().floatValue(),
            ClientConfig.colorDimB.get().floatValue(),
            ClientConfig.colorDimA.get().floatValue()};
    }

    private static float[] getColorSelected()
    {
        return new float[]{
            ClientConfig.colorSelectedR.get().floatValue(),
            ClientConfig.colorSelectedG.get().floatValue(),
            ClientConfig.colorSelectedB.get().floatValue(),
            ClientConfig.colorSelectedA.get().floatValue()};
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onRenderLevel(RenderLevelStageEvent event)
    {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        boolean holdingCable = mainHand.getItem() instanceof VirtualCableItem
            || offHand.getItem() instanceof VirtualCableItem;
        boolean holdingCutter = mainHand.getItem() instanceof CableCutterItem;
        boolean holdingMagnifier = mainHand.getItem() instanceof CableMagnifierItem
            || offHand.getItem() instanceof CableMagnifierItem;

        if (!holdingCable && !holdingCutter && !holdingMagnifier) return;

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource =
            Minecraft.getInstance().renderBuffers().bufferSource();
        Vec3 cameraPos = event.getCamera().getPosition();

        double renderDistSq = ServerConfig.magnifierRenderDistance.get()
            * (double) ServerConfig.magnifierRenderDistance.get();

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        BlockPos selectedInput = null;
        if (holdingCable)
        {
            selectedInput = VirtualCableItem.getSelectedInput(player);
        }

        for (CableLink link : ClientCableCache.getLinks())
        {
            Vec3 fromCenter = new Vec3(
                link.getFromCenterX(), link.getFromCenterY(), link.getFromCenterZ());
            if (fromCenter.distanceToSqr(cameraPos) > renderDistSq) continue;

            boolean isFromSelected = selectedInput != null
                && selectedInput.equals(link.getFrom());

            if (holdingMagnifier)
            {
                renderBlockOutline(poseStack, bufferSource, link.getFrom(), getColorInput());
                renderFaceOutline(poseStack, bufferSource,
                    link.getTo(), link.getToFace(), getColorOutput());
                renderCableQuad(poseStack, bufferSource,
                    new Vec3(link.getFromCenterX(), link.getFromCenterY(), link.getFromCenterZ()),
                    new Vec3(link.getFaceCenterX(), link.getFaceCenterY(), link.getFaceCenterZ()),
                    getColorLine());
            }
            else if (holdingCable && isFromSelected)
            {
                renderBlockOutline(poseStack, bufferSource, link.getFrom(), getColorInput());
                renderFaceOutline(poseStack, bufferSource,
                    link.getTo(), link.getToFace(), getColorOutput());
                renderCableQuad(poseStack, bufferSource,
                    new Vec3(link.getFromCenterX(), link.getFromCenterY(), link.getFromCenterZ()),
                    new Vec3(link.getFaceCenterX(), link.getFaceCenterY(), link.getFaceCenterZ()),
                    getColorLine());
            }
            else if (holdingCable || holdingCutter)
            {
                renderBlockOutline(poseStack, bufferSource, link.getFrom(), getColorDim());
            }
        }

        if (selectedInput != null)
        {
            renderBlockOutline(poseStack, bufferSource, selectedInput, getColorSelected());
        }

        bufferSource.endBatch(RenderType.LINES);
        bufferSource.endBatch(RenderType.debugQuads());
        bufferSource.endBatch();

        poseStack.popPose();
    }

    private static boolean wasHoldingCable = false;
    private static int syncCooldown = 0;

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        boolean holdingCable = player.getMainHandItem().getItem() instanceof VirtualCableItem
            || player.getOffhandItem().getItem() instanceof VirtualCableItem;
        boolean holdingMagnifier = player.getMainHandItem().getItem() instanceof CableMagnifierItem
            || player.getOffhandItem().getItem() instanceof CableMagnifierItem;

        if (wasHoldingCable && !holdingCable)
        {
            VirtualCableItem.clearSelectedInput(player);
        }
        wasHoldingCable = holdingCable;

        if (holdingCable || holdingMagnifier)
        {
            syncCooldown--;
            if (syncCooldown <= 0)
            {
                CableNetworkChannel.sendToServer(new CableRequestSyncPacket());
                syncCooldown = 20;
            }
        }
        else
        {
            syncCooldown = 0;
        }

        if (holdingCable)
        {
            BlockPos sel = VirtualCableItem.getSelectedInput(player);
            if (sel != null && player.level().isEmptyBlock(sel))
            {
                VirtualCableItem.clearSelectedInput(player);
            }
        }
    }

    private static void renderBlockOutline(PoseStack poseStack,
                                            MultiBufferSource bufferSource,
                                            BlockPos pos, float[] color)
    {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.LINES);
        float expand = OUTLINE_EXPAND;
        for (int i = 0; i < 3; i++)
        {
            float dx = (i == 0) ? -expand : (i == 1) ? expand : 0;
            float dy = (i == 0) ? 0 : (i == 1) ? -expand : expand;
            float dz = (i == 2) ? -expand : (i == 2) ? expand : (i == 1) ? expand : -expand;
            LevelRenderer.renderLineBox(
                poseStack, consumer,
                pos.getX() - expand + dx, pos.getY() - expand + dy, pos.getZ() - expand + dz,
                pos.getX() + 1 + expand + dx, pos.getY() + 1 + expand + dy, pos.getZ() + 1 + expand + dz,
                color[0], color[1], color[2], color[3]);
        }
    }

    private static void renderFaceOutline(PoseStack poseStack,
                                           MultiBufferSource bufferSource,
                                           BlockPos pos, Direction face, float[] color)
    {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.LINES);
        double x = pos.getX(), y = pos.getY(), z = pos.getZ();
        double x1 = x, y1 = y, z1 = z;
        double x2 = x + 1, y2 = y + 1, z2 = z + 1;

        float expand = OUTLINE_EXPAND;
        switch (face)
        {
            case DOWN:  y1 = y - expand; y2 = y - expand; x1 -= expand; x2 += expand; z1 -= expand; z2 += expand; break;
            case UP:    y1 = y + 1 + expand; y2 = y + 1 + expand; x1 -= expand; x2 += expand; z1 -= expand; z2 += expand; break;
            case NORTH: z1 = z - expand; z2 = z - expand; x1 -= expand; x2 += expand; y1 -= expand; y2 += expand; break;
            case SOUTH: z1 = z + 1 + expand; z2 = z + 1 + expand; x1 -= expand; x2 += expand; y1 -= expand; y2 += expand; break;
            case WEST:  x1 = x - expand; x2 = x - expand; z1 -= expand; z2 += expand; y1 -= expand; y2 += expand; break;
            case EAST:  x1 = x + 1 + expand; x2 = x + 1 + expand; z1 -= expand; z2 += expand; y1 -= expand; y2 += expand; break;
        }

        for (int i = 0; i < 2; i++)
        {
            float offset = (i == 0) ? -0.0005f : 0.0005f;
            LevelRenderer.renderLineBox(poseStack, consumer,
                x1 + offset, y1 + offset, z1 + offset,
                x2 + offset, y2 + offset, z2 + offset,
                color[0], color[1], color[2], color[3]);
        }
    }

    private static void renderCableQuad(PoseStack poseStack,
                                         MultiBufferSource bufferSource,
                                         Vec3 from, Vec3 to, float[] color)
    {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.debugQuads());
        Matrix4f matrix = poseStack.last().pose();

        Vec3 dir = to.subtract(from).normalize();
        Vec3 perp = new Vec3(-dir.z, 0, dir.x);
        if (perp.lengthSqr() < 0.01)
        {
            perp = new Vec3(1, 0, 0);
        }
        perp = perp.normalize();

        float halfWidth = 0.0625f;
        float r = color[0], g = color[1], b = color[2], a = color[3];

        float fx = (float) from.x, fy = (float) from.y, fz = (float) from.z;
        float tx = (float) to.x, ty = (float) to.y, tz = (float) to.z;
        float px = (float) perp.x * halfWidth, py = (float) perp.y * halfWidth, pz = (float) perp.z * halfWidth;

        consumer.vertex(matrix, fx - px, fy - py, fz - pz).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, fx + px, fy + py, fz + pz).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, tx + px, ty + py, tz + pz).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, tx - px, ty - py, tz - pz).color(r, g, b, a).endVertex();
    }
}
