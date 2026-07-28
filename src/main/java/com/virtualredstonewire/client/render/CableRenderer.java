package com.virtualredstonewire.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.virtualredstonewire.VirtualRedstoneWire;
import com.virtualredstonewire.client.ClientCableCache;
import com.virtualredstonewire.config.ModConfig;
import com.virtualredstonewire.data.CableLink;
import com.virtualredstonewire.item.CableCutterItem;
import com.virtualredstonewire.item.CableMagnifierItem;
import com.virtualredstonewire.item.VirtualCableItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@Mod.EventBusSubscriber(modid = VirtualRedstoneWire.MOD_ID, value = Dist.CLIENT)
public class CableRenderer
{
    private static final float[] COLOR_INPUT_BLUE   = {0.0f, 0.59f, 1.0f, 0.5f};
    private static final float[] COLOR_OUTPUT_YELLOW = {1.0f, 1.0f, 0.39f, 0.5f};
    private static final float[] COLOR_LINE_RED      = {0.86f, 0.31f, 0.31f, 0.4f};
    private static final float[] COLOR_INPUT_DIM_BLUE = {0.3f, 0.4f, 0.7f, 0.25f};

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onRenderLevel(RenderLevelStageEvent event)
    {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPLANE_BLOCKS) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        ItemStack mainHand = player.getMainHandItem();
        boolean holdingCable = mainHand.getItem() instanceof VirtualCableItem;
        boolean holdingCutter = mainHand.getItem() instanceof CableCutterItem;
        boolean holdingMagnifier = mainHand.getItem() instanceof CableMagnifierItem;

        if (!holdingCable && !holdingCutter && !holdingMagnifier) return;

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource =
            Minecraft.getInstance().renderBuffers().bufferSource();
        Vec3 cameraPos = event.getCamera().getPosition();

        double renderDistSq = ModConfig.magnifierRenderDistance.get()
            * (double) ModConfig.magnifierRenderDistance.get();

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        for (CableLink link : ClientCableCache.getLinks())
        {
            Vec3 fromCenter = new Vec3(
                link.getFromCenterX(), link.getFromCenterY(), link.getFromCenterZ());
            if (fromCenter.distanceToSqr(cameraPos) > renderDistSq) continue;

            if (holdingMagnifier)
            {
                // 输入端淡蓝色边框
                renderBlockOutline(poseStack, bufferSource, link.getFrom(), COLOR_INPUT_BLUE);
                // 输出端接收面淡黄色边框
                renderFaceOutline(poseStack, bufferSource,
                    link.getTo(), link.getToFace(), COLOR_OUTPUT_YELLOW);
                // 连接线
                renderLine(poseStack, bufferSource,
                    new Vec3(link.getFromCenterX(), link.getFromCenterY(), link.getFromCenterZ()),
                    new Vec3(link.getFaceCenterX(), link.getFaceCenterY(), link.getFaceCenterZ()),
                    COLOR_LINE_RED);
            }
            else if (holdingCable || holdingCutter)
            {
                // 淡暗蓝色高亮渲染输入端
                renderBlockOutline(poseStack, bufferSource, link.getFrom(), COLOR_INPUT_DIM_BLUE);
            }
        }

        // 如果手持线缆且已选中输入端，高亮选中的方块
        if (holdingCable)
        {
            BlockPos selected = VirtualCableItem.getSelectedInput(player);
            if (selected != null)
            {
                renderBlockOutline(poseStack, bufferSource, selected,
                    new float[]{0.0f, 0.59f, 1.0f, 0.7f});
            }
        }

        poseStack.popPose();
        bufferSource.endBatch();
    }

    private static void renderBlockOutline(PoseStack poseStack,
                                            MultiBufferSource bufferSource,
                                            BlockPos pos, float[] color)
    {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.LINES);
        LevelRenderer.renderLineBox(
            poseStack, consumer,
            pos.getX(), pos.getY(), pos.getZ(),
            pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1,
            color[0], color[1], color[2], color[3]);
    }

    private static void renderFaceOutline(PoseStack poseStack,
                                           MultiBufferSource bufferSource,
                                           BlockPos pos, Direction face, float[] color)
    {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.LINES);
        double x = pos.getX(), y = pos.getY(), z = pos.getZ();
        double x1 = x, y1 = y, z1 = z;
        double x2 = x + 1, y2 = y + 1, z2 = z + 1;

        switch (face)
        {
            case DOWN:  y1 = y; y2 = y; break;
            case UP:    y1 = y + 1; y2 = y + 1; break;
            case NORTH: z1 = z; z2 = z; break;
            case SOUTH: z1 = z + 1; z2 = z + 1; break;
            case WEST:  x1 = x; x2 = x; break;
            case EAST:  x1 = x + 1; x2 = x + 1; break;
        }

        // 绘制四边形边框（4条边）
        LevelRenderer.renderLineBox(poseStack, consumer,
            x1, y1, z1, x2, y2, z2,
            color[0], color[1], color[2], color[3]);
    }

    private static void renderLine(PoseStack poseStack,
                                    MultiBufferSource bufferSource,
                                    Vec3 from, Vec3 to, float[] color)
    {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.LINES);
        Matrix4f matrix = poseStack.last().pose();

        consumer.vertex(matrix, (float) from.x, (float) from.y, (float) from.z)
            .color(color[0], color[1], color[2], color[3])
            .endVertex();
        consumer.vertex(matrix, (float) to.x, (float) to.y, (float) to.z)
            .color(color[0], color[1], color[2], color[3])
            .endVertex();
    }
}
