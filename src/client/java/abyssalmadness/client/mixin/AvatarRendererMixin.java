package abyssalmadness.client.mixin;

import abyssalmadness.client.InfectionArm;
import abyssalmadness.client.InfectionClientState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The infection on the first-person arm. The world pass cannot reach it - the arm is
// drawn with its own projection - so it gets an additive overlay instead.
@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {
    @Inject(method = "renderRightHand", at = @At("TAIL"))
    private void abyssalmadness$rightHandGlow(PoseStack pose, SubmitNodeCollector collector,
                                              int light, Identifier skin, boolean showSleeve,
                                              CallbackInfo ci) {
        abyssalmadness$handGlow(pose, collector, showSleeve, true);
    }

    @Inject(method = "renderLeftHand", at = @At("TAIL"))
    private void abyssalmadness$leftHandGlow(PoseStack pose, SubmitNodeCollector collector,
                                             int light, Identifier skin, boolean showSleeve,
                                             CallbackInfo ci) {
        abyssalmadness$handGlow(pose, collector, showSleeve, false);
    }

    private static void abyssalmadness$handGlow(PoseStack pose, SubmitNodeCollector collector,
                                                boolean showSleeve, boolean right) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        int level = InfectionClientState.get(player.getUUID());
        if (level <= 0) {
            return;
        }
        Identifier overlay = InfectionArm.get(level);
        if (overlay == null) {
            return;
        }
        boolean slim = player.getSkin().model() == PlayerModelType.SLIM;
        PlayerModel model = InfectionArm.model(slim);
        ModelPart arm = abyssalmadness$poseArm(model, showSleeve, right);
        // energySwirl is the additive type, so this adds light rather than painting
        // over: the arm glows white the way the body does under the world shader.
        collector.submitModelPart(arm, pose,
                RenderTypes.energySwirl(overlay, 0.0F, 0.0F),
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null);
    }

    // Same arm setup vanilla does in AvatarRenderer#renderHand. The sleeve is a child
    // of the arm, so submitting the arm draws both.
    private static ModelPart abyssalmadness$poseArm(PlayerModel model, boolean showSleeve,
                                                    boolean right) {
        ModelPart arm = right ? model.rightArm : model.leftArm;
        arm.resetPose();
        arm.visible = true;
        model.leftSleeve.visible = showSleeve;
        model.rightSleeve.visible = showSleeve;
        model.leftArm.zRot = -0.1F;
        model.rightArm.zRot = 0.1F;
        return arm;
    }
}
