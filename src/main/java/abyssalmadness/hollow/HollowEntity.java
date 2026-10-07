package abyssalmadness.hollow;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

// A body with nobody in it.
//
// Deliberately empty: no goals, no sounds, no drops. Everything a hollow does is
// driven from {@link Hollows}, one tick at a time, because a mob left to its own AI
// would swing at people and wander after animals - and none of that is what this is.
public class HollowEntity extends Mob {
    public HollowEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        setNoAi(true);
        setSilent(true);
        setInvulnerable(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                // They walk dead straight with no pathfinding, so without a step height
                // the first dune stops them where they stand.
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void registerGoals() {
        // None. On purpose.
    }

    @Override
    public boolean isPushable() {
        return false;                 // shoving one aside would break the spell
    }

    // Silent in every sense. setSilent covers what the entity plays for itself, but not
    // footsteps, and a hollow crunching across the sand gives the whole thing away.
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
