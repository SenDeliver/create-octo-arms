package dev.tentaworks.compat.curios;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.equipment.armor.BacktankBlock;
import com.simibubi.create.content.equipment.armor.BacktankRenderer;
import dev.tentaworks.client.TentacleLayer;
import dev.tentaworks.registry.TentaworksItems;
import dev.tentaworks.rig.TentacleRigItem;
import dev.tentaworks.rig.WornRig;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * Client side of the Curios Back Slot: a Back Slot Rig is drawn on the body over the chest armor, tank as Create's
 * worn-Backtank layer draws it plus the Tentacles and harness of {@link TentacleLayer}. Loaded only when Curios is.
 */
public final class CuriosClient implements ICurioRenderer {
    private CuriosClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener((FMLClientSetupEvent e) -> {
            CuriosRendererRegistry.register(TentaworksItems.COPPER_TENTACLE_RIG.get(), CuriosClient::new);
            CuriosRendererRegistry.register(TentaworksItems.NETHERITE_TENTACLE_RIG.get(), CuriosClient::new);
        });
    }

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext ctx, PoseStack ps, RenderLayerParent<T, M> parent,
                                                                          MultiBufferSource buffers, int light, float limbSwing, float limbSwingAmount,
                                                                          float pt, float ageInTicks, float netHeadYaw, float headPitch) {
        if (ctx.cosmetic() || !(ctx.entity() instanceof Player player) || !(parent.getModel() instanceof HumanoidModel<?> model)) return;
        WornRig worn = WornRig.of(player);
        TentacleRigItem rig = worn.item();
        if (!worn.inBack() || worn.stack() != stack || rig == null || player.getPose() == Pose.SLEEPING) return;
        drawTank(ps, buffers, light, player, rig, stack.hasFoil(), model);
        TentacleLayer.drawOnBody(ps, buffers, light, player, rig, pt, model.body);
    }

    /** Create's BacktankArmorLayer, for a Backtank that is not in the chest slot. */
    private static void drawTank(PoseStack ps, MultiBufferSource buffers, int light, Player player, TentacleRigItem rig, boolean foil, HumanoidModel<?> model) {
        VertexConsumer vc = ItemRenderer.getFoilBuffer(buffers, Sheets.cutoutBlockSheet(), false, foil);
        BlockState state = rig.getBlock().defaultBlockState().setValue(BacktankBlock.HORIZONTAL_FACING, Direction.SOUTH);
        SuperByteBuffer tank = CachedBuffers.block(state);
        SuperByteBuffer cogs = CachedBuffers.partial(BacktankRenderer.getCogsModel(state), state);
        SuperByteBuffer shaft = CachedBuffers.partial(BacktankRenderer.getShaftModel(state), state);
        ps.pushPose();
        model.body.translateAndRotate(ps);
        ps.translate(-0.5F, 0.625F, 1.0F);
        ps.scale(1.0F, -1.0F, -1.0F);
        tank.disableDiffuse().light(light).renderInto(ps, vc);
        shaft.disableDiffuse().translate(0.0F, -0.1875F, 0.0F).light(light).renderInto(ps, vc);
        cogs.center().rotateYDegrees(180.0F).uncenter().translate(0.0F, 0.40625F, 0.6875F)
                .rotate(AngleHelper.rad(2.0F * AnimationTickHolder.getRenderTime(player.level()) % 360.0F), Direction.EAST)
                .translate(0.0F, -0.40625F, -0.6875F);
        cogs.disableDiffuse().light(light).renderInto(ps, vc);
        ps.popPose();
    }
}
