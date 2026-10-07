package abyssalmadness.client;

import abyssalmadness.hollow.HollowEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

// Draws a hollow with the player's own geometry.
//
// The model layer is vanilla's PLAYER, so the silhouette is exactly a player's - which
// matters more than it sounds, because the infection shader cuts by that silhouette
// and anything else reads as "some mob painted white" rather than as a person.
//
// HumanoidModel is used rather than PlayerModel on purpose. PlayerModel is bound to
// AvatarRenderState, which carries skin data a mob has no way to supply; HumanoidModel
// takes the same baked layer and simply leaves the outer jacket and sleeves unused. A
// hollow has no business wearing a second layer anyway.
public class HollowRenderer extends HumanoidMobRenderer<HollowEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
    // Never actually seen: the shader paints the body over. It only has to be opaque so
    // the silhouette lands in the outline buffer.
    private static final Identifier SKIN =
            Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");

    public HollowRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState state) {
        return SKIN;
    }
}
