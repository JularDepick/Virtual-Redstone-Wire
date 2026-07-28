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
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
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
    private static final float BEAM_THICKNESS = 0.02f;
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

        double cx = cameraPos.x, cy = cameraPos.y, cz = cameraPos.z;

        BlockPos selectedInput = null;
        if (holdingCable)
        {
            selectedInput = VirtualCableItem.getSelectedInput(player);
        }

        var level = player.level();

        for (CableLink link : ClientCableCache.getLinks())
        {
            double fromX = link.getFrom().getX(), fromY = link.getFrom().getY(), fromZ = link.getFrom().getZ();
            double dx = fromX + 0.5 - cx, dy = fromY + 0.5 - cy, dz = fromZ + 0.5 - cz;
            if (dx*dx + dy*dy + dz*dz > renderDistSq) continue;

            boolean isFromSelected = selectedInput != null
                && selectedInput.equals(link.getFrom());

            Vec3 fromCenter = getShapeCenter(link.getFrom(), level, cx, cy, cz);
            Vec3 cableEnd = new Vec3(
                link.getFaceCenterX() - cx, link.getFaceCenterY() - cy, link.getFaceCenterZ() - cz);

            if (holdingMagnifier)
            {
                renderBlockOutlineBeams(poseStack, bufferSource, link.getFrom(), getColorInput(), level, cx, cy, cz);
                renderFaceOutline(poseStack, bufferSource,
                    link.getTo(), link.getToFace(), getColorOutput(), cx, cy, cz);
                renderCableBeam(poseStack, bufferSource, fromCenter, cableEnd, getColorLine());
            }
            else if (holdingCable && isFromSelected)
            {
                renderBlockOutlineBeams(poseStack, bufferSource, link.getFrom(), getColorInput(), level, cx, cy, cz);
                renderFaceOutline(poseStack, bufferSource,
                    link.getTo(), link.getToFace(), getColorOutput(), cx, cy, cz);
                renderCableBeam(poseStack, bufferSource, fromCenter, cableEnd, getColorLine());
            }
            else if (holdingCable || holdingCutter)
            {
                renderBlockOutlineBeams(poseStack, bufferSource, link.getFrom(), getColorDim(), level, cx, cy, cz);
            }
        }

        if (selectedInput != null)
        {
            renderBlockOutlineBeams(poseStack, bufferSource, selectedInput, getColorSelected(), level, cx, cy, cz);
        }

        bufferSource.endBatch(RenderType.debugQuads());
        bufferSource.endBatch(RenderType.LINES);
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

    private static void renderBlockOutlineBeams(PoseStack poseStack,
                                                 MultiBufferSource bufferSource,
                                                 BlockPos pos, float[] color,
                                                 net.minecraft.world.level.Level level,
                                                 double cx, double cy, double cz)
    {
        VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
        AABB bounds;
        if (shape.isEmpty())
        {
            bounds = new AABB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
        }
        else
        {
            bounds = shape.bounds().move(pos.getX(), pos.getY(), pos.getZ());
        }

        double x0 = bounds.minX - cx, y0 = bounds.minY - cy, z0 = bounds.minZ - cz;
        double x1 = bounds.maxX - cx, y1 = bounds.maxY - cy, z1 = bounds.maxZ - cz;

        Vec3[] corners = {
            new Vec3(x0, y0, z0), new Vec3(x1, y0, z0), new Vec3(x1, y0, z1), new Vec3(x0, y0, z1),
            new Vec3(x0, y1, z0), new Vec3(x1, y1, z0), new Vec3(x1, y1, z1), new Vec3(x0, y1, z1)
        };

        int[][] edges = {
            {0,1},{1,2},{2,3},{3,0},
            {4,5},{5,6},{6,7},{7,4},
            {0,4},{1,5},{2,6},{3,7}
        };

        for (int[] edge : edges)
        {
            renderBeam(poseStack, bufferSource,
                corners[edge[0]], corners[edge[1]], color);
        }
    }

    private static void renderFaceOutline(PoseStack poseStack,
                                           MultiBufferSource bufferSource,
                                           BlockPos pos, Direction face, float[] color,
                                           double cx, double cy, double cz)
    {
        double x = pos.getX() - cx, y = pos.getY() - cy, z = pos.getZ() - cz;
        double expand = OUTLINE_EXPAND;

        Vec3[] corners = new Vec3[4];
        switch (face)
        {
            case DOWN:
                corners[0] = new Vec3(x - expand, y - expand, z - expand);
                corners[1] = new Vec3(x + 1 + expand, y - expand, z - expand);
                corners[2] = new Vec3(x + 1 + expand, y - expand, z + 1 + expand);
                corners[3] = new Vec3(x - expand, y - expand, z + 1 + expand);
                break;
            case UP:
                corners[0] = new Vec3(x - expand, y + 1 + expand, z - expand);
                corners[1] = new Vec3(x + 1 + expand, y + 1 + expand, z - expand);
                corners[2] = new Vec3(x + 1 + expand, y + 1 + expand, z + 1 + expand);
                corners[3] = new Vec3(x - expand, y + 1 + expand, z + 1 + expand);
                break;
            case NORTH:
                corners[0] = new Vec3(x - expand, y - expand, z - expand);
                corners[1] = new Vec3(x + 1 + expand, y - expand, z - expand);
                corners[2] = new Vec3(x + 1 + expand, y + 1 + expand, z - expand);
                corners[3] = new Vec3(x - expand, y + 1 + expand, z - expand);
                break;
            case SOUTH:
                corners[0] = new Vec3(x - expand, y - expand, z + 1 + expand);
                corners[1] = new Vec3(x + 1 + expand, y - expand, z + 1 + expand);
                corners[2] = new Vec3(x + 1 + expand, y + 1 + expand, z + 1 + expand);
                corners[3] = new Vec3(x - expand, y + 1 + expand, z + 1 + expand);
                break;
            case WEST:
                corners[0] = new Vec3(x - expand, y - expand, z - expand);
                corners[1] = new Vec3(x - expand, y - expand, z + 1 + expand);
                corners[2] = new Vec3(x - expand, y + 1 + expand, z + 1 + expand);
                corners[3] = new Vec3(x - expand, y + 1 + expand, z - expand);
                break;
            case EAST:
                corners[0] = new Vec3(x + 1 + expand, y - expand, z - expand);
                corners[1] = new Vec3(x + 1 + expand, y - expand, z + 1 + expand);
                corners[2] = new Vec3(x + 1 + expand, y + 1 + expand, z + 1 + expand);
                corners[3] = new Vec3(x + 1 + expand, y + 1 + expand, z - expand);
                break;
        }

        for (int i = 0; i < 4; i++)
        {
            renderBeam(poseStack, bufferSource, corners[i], corners[(i + 1) % 4], color);
        }

        Vec3 center = new Vec3(
            (corners[0].x + corners[2].x) * 0.5,
            (corners[0].y + corners[2].y) * 0.5,
            (corners[0].z + corners[2].z) * 0.5
        );

        float dotRadius = BEAM_THICKNESS * 3.0f;
        Vec3 faceNormal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        renderFaceDot(poseStack, bufferSource, center, faceNormal, dotRadius, color);
    }

    private static void renderCableBeam(PoseStack poseStack,
                                         MultiBufferSource bufferSource,
                                         Vec3 from, Vec3 to, float[] color)
    {
        renderBeam(poseStack, bufferSource, from, to, color);
    }

    private static Vec3 getShapeCenter(BlockPos pos, net.minecraft.world.level.Level level,
                                        double cx, double cy, double cz)
    {
        VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
        if (shape.isEmpty())
        {
            return new Vec3(pos.getX() + 0.5 - cx, pos.getY() + 0.5 - cy, pos.getZ() + 0.5 - cz);
        }
        AABB bounds = shape.bounds();
        return new Vec3(
            pos.getX() + (bounds.minX + bounds.maxX) * 0.5 - cx,
            pos.getY() + (bounds.minY + bounds.maxY) * 0.5 - cy,
            pos.getZ() + (bounds.minZ + bounds.maxZ) * 0.5 - cz);
    }

    private static void renderBeam(PoseStack poseStack,
                                    MultiBufferSource bufferSource,
                                    Vec3 from, Vec3 to, float[] color)
    {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.debugQuads());
        Matrix4f matrix = poseStack.last().pose();

        Vec3 dir = to.subtract(from);
        double length = dir.length();
        if (length < 0.0001) return;
        dir = dir.scale(1.0 / length);

        Vec3 perp1 = new Vec3(-dir.z, 0, dir.x);
        if (perp1.lengthSqr() < 0.01) { perp1 = new Vec3(1, 0, 0); }
        perp1 = perp1.normalize();
        Vec3 perp2 = dir.cross(perp1).normalize();

        float t = BEAM_THICKNESS;
        Vec3 ext = dir.scale(t);
        Vec3 fromExt = from.subtract(ext);
        Vec3 toExt = to.add(ext);
        float r = color[0], g = color[1], bl = color[2], alpha = color[3];

        float fx = (float) fromExt.x, fy = (float) fromExt.y, fz = (float) fromExt.z;
        float tx = (float) toExt.x, ty = (float) toExt.y, tz = (float) toExt.z;
        float p1x = (float) perp1.x, p1y = (float) perp1.y, p1z = (float) perp1.z;
        float p2x = (float) perp2.x, p2y = (float) perp2.y, p2z = (float) perp2.z;

        float a1x = fx + p1x * t + p2x * t;
        float a1y = fy + p1y * t + p2y * t;
        float a1z = fz + p1z * t + p2z * t;

        float a2x = fx - p1x * t + p2x * t;
        float a2y = fy - p1y * t + p2y * t;
        float a2z = fz - p1z * t + p2z * t;

        float a3x = fx - p1x * t - p2x * t;
        float a3y = fy - p1y * t - p2y * t;
        float a3z = fz - p1z * t - p2z * t;

        float a4x = fx + p1x * t - p2x * t;
        float a4y = fy + p1y * t - p2y * t;
        float a4z = fz + p1z * t - p2z * t;

        float b1x = tx + p1x * t + p2x * t;
        float b1y = ty + p1y * t + p2y * t;
        float b1z = tz + p1z * t + p2z * t;

        float b2x = tx - p1x * t + p2x * t;
        float b2y = ty - p1y * t + p2y * t;
        float b2z = tz - p1z * t + p2z * t;

        float b3x = tx - p1x * t - p2x * t;
        float b3y = ty - p1y * t - p2y * t;
        float b3z = tz - p1z * t - p2z * t;

        float b4x = tx + p1x * t - p2x * t;
        float b4y = ty + p1y * t - p2y * t;
        float b4z = tz + p1z * t - p2z * t;

        consumer.vertex(matrix, a1x, a1y, a1z).color(r, g, bl, alpha).endVertex();
        consumer.vertex(matrix, a2x, a2y, a2z).color(r, g, bl, alpha).endVertex();
        consumer.vertex(matrix, b2x, b2y, b2z).color(r, g, bl, alpha).endVertex();
        consumer.vertex(matrix, b1x, b1y, b1z).color(r, g, bl, alpha).endVertex();

        consumer.vertex(matrix, a2x, a2y, a2z).color(r, g, bl, alpha).endVertex();
        consumer.vertex(matrix, a3x, a3y, a3z).color(r, g, bl, alpha).endVertex();
        consumer.vertex(matrix, b3x, b3y, b3z).color(r, g, bl, alpha).endVertex();
        consumer.vertex(matrix, b2x, b2y, b2z).color(r, g, bl, alpha).endVertex();

        consumer.vertex(matrix, a3x, a3y, a3z).color(r, g, bl, alpha).endVertex();
        consumer.vertex(matrix, a4x, a4y, a4z).color(r, g, bl, alpha).endVertex();
        consumer.vertex(matrix, b4x, b4y, b4z).color(r, g, bl, alpha).endVertex();
        consumer.vertex(matrix, b3x, b3y, b3z).color(r, g, bl, alpha).endVertex();

        consumer.vertex(matrix, a4x, a4y, a4z).color(r, g, bl, alpha).endVertex();
        consumer.vertex(matrix, a1x, a1y, a1z).color(r, g, bl, alpha).endVertex();
        consumer.vertex(matrix, b1x, b1y, b1z).color(r, g, bl, alpha).endVertex();
        consumer.vertex(matrix, b4x, b4y, b4z).color(r, g, bl, alpha).endVertex();

        consumer.vertex(matrix, a1x, a1y, a1z).color(r, g, bl, alpha).endVertex();
        consumer.vertex(matrix, a4x, a4y, a4z).color(r, g, bl, alpha).endVertex();
        consumer.vertex(matrix, a3x, a3y, a3z).color(r, g, bl, alpha).endVertex();
        consumer.vertex(matrix, a2x, a2y, a2z).color(r, g, bl, alpha).endVertex();

        consumer.vertex(matrix, b1x, b1y, b1z).color(r, g, bl, alpha).endVertex();
        consumer.vertex(matrix, b2x, b2y, b2z).color(r, g, bl, alpha).endVertex();
        consumer.vertex(matrix, b3x, b3y, b3z).color(r, g, bl, alpha).endVertex();
        consumer.vertex(matrix, b4x, b4y, b4z).color(r, g, bl, alpha).endVertex();
    }

    private static void renderFaceDot(PoseStack poseStack,
                                       MultiBufferSource bufferSource,
                                       Vec3 center, Vec3 normal, float radius, float[] color)
    {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.debugQuads());
        Matrix4f matrix = poseStack.last().pose();

        Vec3 u, v;
        if (Math.abs(normal.x) < 0.9) {
            u = new Vec3(1, 0, 0).cross(normal).normalize();
        } else {
            u = new Vec3(0, 1, 0).cross(normal).normalize();
        }
        v = normal.cross(u).normalize();

        float r = color[0], g = color[1], b = color[2], a = color[3];
        float cx = (float) center.x, cy = (float) center.y, cz = (float) center.z;
        float ux = (float) u.x, uy = (float) u.y, uz = (float) u.z;
        float vx = (float) v.x, vy = (float) v.y, vz = (float) v.z;

        consumer.vertex(matrix, cx + ux * radius + vx * radius, cy + uy * radius + vy * radius, cz + uz * radius + vz * radius).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, cx - ux * radius + vx * radius, cy - uy * radius + vy * radius, cz - uz * radius + vz * radius).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, cx - ux * radius - vx * radius, cy - uy * radius - vy * radius, cz - uz * radius - vz * radius).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, cx + ux * radius - vx * radius, cy + uy * radius - vy * radius, cz + uz * radius - vz * radius).color(r, g, b, a).endVertex();
    }
}
