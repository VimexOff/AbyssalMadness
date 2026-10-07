package abyssalmadness.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;

// Terrain for the whole world, picked by distance from spawn. Registered as a
// density-function type and referenced from noise_settings.noise_router.final_density.
public record AbyssTerrain() implements DensityFunction.SimpleFunction {
    public static final KeyDispatchDataCodec<AbyssTerrain> CODEC =
            KeyDispatchDataCodec.of(MapCodec.unit(new AbyssTerrain()));

    private static final double LEVEL1_MAX = 5000.0;
    private static final double LEVEL2_MAX = 10000.0;
    private static final double LEVEL3_MAX = 15000.0;
    private static final double LEVEL12_BLEND = 240.0;

    // Level 1: gorges
    private static final double PLATEAU_BASE = 196.0;
    private static final double PLATEAU_VARY = 44.0;
    private static final double PLATEAU_VARY_SCALE = 620.0;
    private static final double PLATEAU_ROLL = 3.0;
    private static final double WALL_DROP = 128.0;
    private static final double VALLEY_DEPTH = 62.0;
    private static final double CELL = 448.0;
    private static final double HALF_WIDTH = 84.0;
    private static final double WALL_BAND = 8.0;
    private static final double WARP = 95.0;
    private static final double WARP_SCALE = 240.0;

    // Level 2: desert of dunes and monoliths
    private static final double FIELD_BASE = 7.0;
    private static final double FIELD_HILLS = 46.0;
    private static final double FIELD_HILLS_SCALE = 360.0;
    private static final double FIELD_ROLL = 4.0;
    private static final double MONO_CELL = 330.0;
    private static final double MONO_CHANCE = 0.62;
    private static final double MONO_MIN_HALF = 8.0;
    private static final double MONO_HALF_RANGE = 11.0;    // 8..19 to a side
    private static final double MONO_MIN_TOP = 215.0;
    private static final double MONO_TOP_RANGE = 95.0;     // tops at 215..310, ceiling is 320
    private static final double MONO_SKIRT = 54.0;         // how high the dune swells at the foot
    private static final double MONO_SKIRT_REACH = 95.0;
    private static final double MONO_FADE = 420.0;
    private static final double MONO_END = 7600.0;      // last of the towers, just short of the wood
    private static final double MONO_END_FADE = 700.0;
    // Glyphs cut into the faces: a glyph is 3x5 cells of CARVE_CELL blocks, repeating
    // every GLYPH_BAND up the tower.
    private static final double CARVE_CELL = 2.0;
    private static final double GLYPH_BAND = 26.0;
    private static final double GLYPH_DEPTH = 1.6;
    private static final int GLYPH_W = 3;
    private static final int GLYPH_H = 5;
    // 3x5 bitmasks, top row first, high bit left. Few on purpose - marks that recur
    // read as writing, a new shape every time reads as noise.
    private static final int[][] GLYPHS = {
            {0b010, 0b101, 0b101, 0b111, 0b101},
            {0b010, 0b101, 0b101, 0b101, 0b010},
            {0b111, 0b010, 0b010, 0b010, 0b111},
            {0b101, 0b010, 0b111, 0b010, 0b101},
            {0b111, 0b010, 0b010, 0b010, 0b010},
    };
    // One tower in ODD_ONE is different: taller, twice as broad, bored through at
    // mid-height. The hole is what carries at distance - it cannot be walked into and
    // cannot be mistaken for terrain.
    private static final int ODD_ONE = 13;
    private static final double ODD_EXTRA_TOP = 46.0;
    private static final double ODD_GROW = 2.0;
    /** Hole radius, as a share of the half-width of the face it is cut into. */
    private static final double ODD_HOLE_R = 0.55;
    /** Height of the hole as a share of the tower's top: the sand covers the rest. */
    private static final double ODD_HOLE_AT = 0.60;
    private static final double ODD_HOLE_AT_RANGE = 0.10;
    // Shafts straight through the world. Cut in the density function, not a feature:
    // features may only write ~16 blocks out before writes are dropped.
    private static final double PIT_CELL = 470.0;
    private static final double PIT_CHANCE = 0.30;
    private static final double PIT_MIN_R = 16.0;
    private static final double PIT_R_RANGE = 26.0;      // 16..42 across, no small ones
    private static final double PIT_WARP = 0.30;         // ragged edge, not a drawn circle
    private static final double PIT_MONO_CLEAR = 24.0;   // gap kept between a pit and a tower

    // Far half of Level 2: the wood. The trees are a feature, but where one may stand
    // belongs to the world, so the shape lives here and the feature only asks.
    private static final double FOREST_START = 7500.0;   // the halfway line of Level 2
    private static final double FOREST_FULL = 8000.0;
    private static final double FOREST_END_FADE = 460.0; // thinned out before Level 3 closes in
    private static final double FOREST_PATCH_SCALE = 300.0;
    // Clearings only in the approach: groves with sand between them, closing to a solid
    // roof past FOREST_CLOSE_TO.
    private static final double FOREST_CLEARING = 0.36;
    private static final double FOREST_EDGE = 0.16;      // width of the thinning rim of a clearing
    private static final double FOREST_CLOSE_FROM = 7700.0;
    private static final double FOREST_CLOSE_TO = 8000.0;
    private static final double FOREST_MONO_CLEAR = 1.4; // how hard a tower pushes the wood back

    // Where the trunks stand. A hash, not a roll: the canopy is drawn chunk by chunk
    // and has to work out the same trunks the tree feature used.
    private static final double TREE_CHANCE = 0.52;
    private static final int TREE_JITTER = 4;

    // The leaf layer is an altitude, not a height above ground: trunks grow to whatever
    // length reaches it, so crowns arrive level with each other and merge.
    private static final double CANOPY_BASE = 130.0;
    private static final double CANOPY_ROLL = 14.0;
    private static final double CANOPY_SCALE = 560.0;
    private static final double DAPPLE_SCALE = 22.0;

    // Level 3: caves
    private static final double CAVE_RAMP = 80.0;
    private static final double CAVE_FILL_TOP = 340.0;
    private static final double CAVE_SEAL_Y = 300.0;   // no tunnels above this
    private static final double CAVE_PEAKS = 84.0;
    private static final double CAVE_PEAKS_SCALE = 80.0;
    private static final double CAVE_CARVE_STEEP = 14.0;

    // Level 4: lattice of stone girders
    private static final double BEAM_CELL = 72.0;
    private static final double BEAM_DECK = 96.0;
    private static final double BEAM_HALF = 8.0;         // half-thickness, 16-block girders
    private static final double BEAM_WARP = 26.0;
    private static final double BEAM_WARP_SCALE = 260.0;
    private static final double BEAM_TILT = 0.42;        // rise per block along a girder
    private static final double SQRT3_2 = 0.8660254037844386;
    private static final double L34_BLEND = 250.0;

    // Level 5: floating islands
    private static final double LEVEL4_MAX = 20000.0;
    private static final double L45_BLEND = 300.0;
    private static final double ISLE_CELL = 120.0;
    private static final double ISLE_CHANCE = 0.72;
    private static final double ISLE_MIN_R = 24.0;
    private static final double ISLE_R_RANGE = 42.0;
    private static final double ISLE_MIN_Y = 60.0;
    private static final double ISLE_Y_RANGE = 140.0;
    private static final double ISLE_FLATTEN = 2.2;      // >1 gives discs, not spheres
    private static final double ISLE_CONE = 2.4;         // spike length below, in radii
    private static final int ISLE_MEDIUM_MIN = 2;
    private static final int ISLE_MEDIUM_EXTRA = 3;      // 2..4 medium satellites
    private static final int ISLE_SMALL_PER_MEDIUM = 2;
    private static final double ISLE_CHAOS = 0.70;
    private static final int ISLE_DEBRIS_MIN = 6;
    private static final int ISLE_DEBRIS_EXTRA = 8;      // 6..13 chips

    // Level 0: flat spawn hub
    private static final double SPAWN_MAX = 1500.0;
    private static final double SPAWN_BLEND = 220.0;
    private static final double SPAWN_BASE = 64.0;
    private static final double SPAWN_RELIEF = 12.0;
    private static final double SPAWN_RELIEF_SCALE = 240.0;

    // Outer barrier wall
    private static final double WALL_CENTER = 25000.0;
    private static final double WALL_HALF = 12.0;
    private static final int WALL_FLOOR_Y = -52;

    private static final long SALT_TIER = 0x51ED2701L;
    private static final long SALT_ROLL = 0x1B9F44A3L;
    private static final long SALT_WARP_X = 0x2A15D9E1L;
    private static final long SALT_WARP_Z = 0x6C4B71F5L;
    private static final long SALT_FIELD = 0x3D9A6C11L;
    private static final long SALT_FIELD_ROLL = 0x71E4B8D3L;
    private static final long SALT_MONO = 0x6A3C58D7L;
    private static final long SALT_PIT = 0x2F71B4E9L;
    private static final long SALT_T1A = 0x11A6C3E9L;
    private static final long SALT_T1B = 0x59D0F17BL;
    private static final long SALT_T2A = 0x7E22B4C1L;
    private static final long SALT_T2B = 0x3A9155EDL;
    private static final long SALT_CHEESE = 0x64BD2F09L;
    private static final long SALT_PEAK = 0x5AC31D77L;
    private static final long SALT_BEAM_X = 0x3F19C6A5L;
    private static final long SALT_BEAM_Y = 0x77E1B053L;
    private static final long SALT_BEAM_Z = 0x2B84DAC9L;
    private static final long SALT_DISSOLVE = 0x6D2F91BBL;
    private static final long SALT_FRACTURE = 0x1CE7A93DL;
    private static final long SALT_ISLE = 0x4B8D2F61L;
    private static final long SALT_GIRDER = 0x2E9A47C3L;
    private static final long SALT_COLUMN = 0x58B1D06FL;
    private static final long SALT_SPAWN = 0x3B7E1A05L;
    private static final long SALT_CHAOS = 0x7719C4EDL;
    private static final long SALT_FOREST = 0x4D71E2C5L;
    private static final long SALT_TREE = 0x1A6F93B7L;
    private static final long SALT_CANOPY = 0x62C8054FL;
    private static final long SALT_DAPPLE = 0x35E7A16BL;

    private static volatile long worldSeed = 0L;

    public static void setWorldSeed(long seed) {
        worldSeed = seed;
    }

    @Override
    public double compute(DensityFunction.FunctionContext ctx) {
        int x = ctx.blockX();
        int y = ctx.blockY();
        int z = ctx.blockZ();
        double dist = Math.max(Math.abs((double) x), Math.abs((double) z));

        // Level 5 and the shatter band, where the lattice breaks into floating chunks.
        if (dist >= LEVEL4_MAX - L45_BLEND) {
            double islands = islandDensity(x, y, z);
            if (dist >= LEVEL4_MAX + L45_BLEND) {
                return islands;
            }
            double t = smoothstep(LEVEL4_MAX - L45_BLEND, LEVEL4_MAX + L45_BLEND, dist);
            double fracture = fbm3(x, y, z, 30.0, 2, SALT_FRACTURE);
            double cut = (fracture - Mth.lerp(t, 0.26, 0.64)) * 10.0;
            double shattered = Math.min(beamDensity(x, y, z), cut);
            return Mth.lerp(t, shattered, islands);
        }

        // Level 4 and its dissolve band, where girders emerge from the Level 3 rock.
        if (dist >= LEVEL3_MAX - L34_BLEND) {
            double beams = beamDensity(x, y, z);
            if (dist >= LEVEL3_MAX + L34_BLEND) {
                return beams;
            }
            double tRaw = smoothstep(LEVEL3_MAX - L34_BLEND, LEVEL3_MAX + L34_BLEND, dist);
            // noisy threshold so the rock face crumbles raggedly instead of on a clean plane
            double jitter = (fbm3(x, y, z, 44.0, 2, SALT_DISSOLVE) - 0.5) * 0.55;
            double t = Mth.clamp(tRaw + jitter, 0.0, 1.0);
            return Mth.lerp(t, cavesDensity(x, y, z, dist), beams);
        }

        if (dist >= LEVEL2_MAX) {
            return cavesDensity(x, y, z, dist);
        }

        // Level 0: flat plains hub around spawn.
        if (dist <= SPAWN_MAX + SPAWN_BLEND) {
            double spawnY = spawnSurfaceY(x, z);
            if (dist <= SPAWN_MAX - SPAWN_BLEND) {
                return Mth.clamp(spawnY - y, -1.0, 1.0);
            }
            double t = smoothstep(SPAWN_MAX - SPAWN_BLEND, SPAWN_MAX + SPAWN_BLEND, dist);
            return Mth.clamp(Mth.lerp(t, spawnY, gorgeSurfaceY(x, z)) - y, -1.0, 1.0);
        }

        double surfaceY;
        if (dist <= LEVEL1_MAX - LEVEL12_BLEND) {
            surfaceY = gorgeSurfaceY(x, z);
        } else if (dist >= LEVEL1_MAX + LEVEL12_BLEND) {
            surfaceY = fieldSurfaceY(x, z, dist);
        } else {
            double t = smoothstep(LEVEL1_MAX - LEVEL12_BLEND, LEVEL1_MAX + LEVEL12_BLEND, dist);
            surfaceY = Mth.lerp(t, gorgeSurfaceY(x, z), fieldSurfaceY(x, z, dist));
        }

        // A pit wins over everything - anything left hanging in it would look carved.
        if (inPit(x, z, dist)) {
            return -1.0;
        }

        // Unioned with the ground, not blended: a tower has to keep its hard edge.
        double ground = Mth.clamp(surfaceY - y, -1.0, 1.0);
        return Math.max(ground, Mth.clamp(monolithDensity(x, y, z, dist), -1.0, 1.0));
    }

    private static double cavesDensity(int x, int y, int z, double dist) {
        // Rises past the build ceiling, so the only way through is a tunnel mouth.
        double t = smoothstep(LEVEL2_MAX, LEVEL2_MAX + CAVE_RAMP, dist);
        double ridge = 1.0 - Math.abs(fbm(x, z, CAVE_PEAKS_SCALE, 2, SALT_PEAK) * 2.0 - 1.0);
        double peaks = Math.pow(ridge, 3.0) * CAVE_PEAKS;
        double ceilingY = Mth.lerp(t, fieldSurfaceY(x, z, dist), CAVE_FILL_TOP) + peaks;
        double solid = ceilingY - y;

        // Never carve near the ceiling, so no tunnel breaches the roof.
        if (y > CAVE_SEAL_Y) {
            return Mth.clamp(solid, -1.0, 1.0);
        }

        double tube1 = tube(x, y, z, 46.0, SALT_T1A, SALT_T1B, 0.15);
        double tube2 = tube(x, y, z, 28.0, SALT_T2A, SALT_T2B, 0.13);
        double cheese = fbm3(x, y, z, 60.0, 2, SALT_CHEESE) - 0.66;
        double carve = Math.max(tube1, Math.max(tube2, cheese));

        return Mth.clamp(Math.min(solid, -carve * CAVE_CARVE_STEEP), -1.0, 1.0);
    }

    // One island: shallow dome on top, cone below ending in a sharp tip.
    private static double islandShape(double ax, double ay, double az,
                                      double cx, double cy, double cz, double radius) {
        double dx = ax - cx;
        double dy = ay - cy;
        double dz = az - cz;

        // cheap bounding reject before the expensive noise below
        if (dy > radius * 0.5 || dy < -radius * 2.75) {
            return -1.0e9;
        }
        double horiz2 = dx * dx + dz * dz;
        double bound = radius * 1.45;
        if (horiz2 > bound * bound) {
            return -1.0e9;
        }

        // warp the radius by 3-D noise so the outline is jagged, plus a vertical wobble
        double warp = (fbm3((int) ax, (int) ay, (int) az, 20.0, 2, SALT_CHAOS) - 0.5)
                * radius * ISLE_CHAOS;
        double vWarp = (fbm3((int) ax, (int) ay, (int) az, 34.0, 2, SALT_CHAOS + 3) - 0.5)
                * radius * 0.55 * ISLE_CHAOS;
        double distXZ = Math.sqrt(horiz2) + warp;
        double yy = dy + vWarp;

        double allowedR;
        if (yy >= 0.0) {
            double topH = radius / ISLE_FLATTEN * 0.6;
            if (yy >= topH) {
                return -1.0e9;
            }
            double f = yy / topH;
            allowedR = radius * Math.sqrt(Math.max(0.0, 1.0 - f * f));
        } else {
            double coneH = radius * ISLE_CONE;
            double depth = -yy;
            if (depth >= coneH) {
                return -1.0e9;
            }
            allowedR = radius * (1.0 - depth / coneH);
        }

        double bite = fbm3((int) (ax * 1.3), (int) (ay * 1.3), (int) (az * 1.3), 13.0, 2, SALT_CHAOS + 9);
        double carve = Math.max(0.0, bite - 0.62) * radius * 3.4 * ISLE_CHAOS;

        return allowedR - distXZ - carve;
    }

    private static double debrisBlob(double ax, double ay, double az,
                                     double cx, double cy, double cz, double r) {
        double dx = ax - cx;
        double dy = ay - cy;
        double dz = az - cz;
        if (Math.abs(dy) > r * 1.3) {
            return -1.0e9;
        }
        double horiz2 = dx * dx + dz * dz;
        double bound = r * 1.35;
        if (horiz2 > bound * bound) {
            return -1.0e9;
        }
        double vr = r * 0.72;
        double d = 1.0 - Math.sqrt(horiz2 / (r * r) + (dy * dy) / (vr * vr));
        double jag = (fbm3((int) ax, (int) ay, (int) az, 8.0, 2, SALT_CHAOS + 21) - 0.5) * 0.5;
        return (d + jag) * r;
    }

    private static double islandDensity(int x, int y, int z) {
        int cx = Mth.floor(x / ISLE_CELL);
        int cz = Mth.floor(z / ISLE_CELL);
        double wobble = 1.0 + (value2D(x, z, 30.0, SALT_ISLE + 5) - 0.5) * 0.38;
        double best = -1.0e9;

        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                int ix = cx + ox;
                int iz = cz + oz;
                if (hash01(ix, iz, SALT_ISLE) > ISLE_CHANCE) {
                    continue;
                }
                double centreX = (ix + 0.15 + hash01(ix, iz, SALT_ISLE + 1) * 0.7) * ISLE_CELL;
                double centreZ = (iz + 0.15 + hash01(ix, iz, SALT_ISLE + 2) * 0.7) * ISLE_CELL;
                double centreY = ISLE_MIN_Y + hash01(ix, iz, SALT_ISLE + 3) * ISLE_Y_RANGE;
                double radius = (ISLE_MIN_R + hash01(ix, iz, SALT_ISLE + 4) * ISLE_R_RANGE) * wobble;

                best = Math.max(best, islandShape(x, y, z, centreX, centreY, centreZ, radius));

                // ring of medium islands around the big one
                int mediumCount = ISLE_MEDIUM_MIN
                        + (int) (hash01(ix, iz, SALT_ISLE + 6) * (ISLE_MEDIUM_EXTRA + 0.999));
                for (int m = 0; m < mediumCount; m++) {
                    long s = SALT_ISLE + 100 + m * 17L;
                    double angle = hash01(ix, iz, s) * Math.PI * 2.0;
                    double away = Math.min(radius * (1.35 + hash01(ix, iz, s + 1) * 0.9),
                            ISLE_CELL * 0.55);
                    double mR = radius * (0.30 + hash01(ix, iz, s + 2) * 0.25) * wobble;
                    double mX = centreX + Math.cos(angle) * away;
                    double mZ = centreZ + Math.sin(angle) * away;
                    double mY = centreY + (hash01(ix, iz, s + 3) - 0.5) * radius * 1.2;
                    best = Math.max(best, islandShape(x, y, z, mX, mY, mZ, mR));

                    // small islands around each medium
                    for (int k = 0; k < ISLE_SMALL_PER_MEDIUM; k++) {
                        long s2 = s + 50 + k * 7L;
                        double a2 = hash01(ix, iz, s2) * Math.PI * 2.0;
                        double away2 = Math.min(mR * (1.4 + hash01(ix, iz, s2 + 1) * 1.0),
                                ISLE_CELL * 0.35);
                        double sR = mR * (0.30 + hash01(ix, iz, s2 + 2) * 0.30) * wobble;
                        double sX = mX + Math.cos(a2) * away2;
                        double sZ = mZ + Math.sin(a2) * away2;
                        double sY = mY + (hash01(ix, iz, s2 + 3) - 0.5) * mR * 1.4;
                        best = Math.max(best, islandShape(x, y, z, sX, sY, sZ, sR));
                    }
                }

                // free-floating chips scattered in the air around the island
                int chips = ISLE_DEBRIS_MIN
                        + (int) (hash01(ix, iz, SALT_ISLE + 7) * (ISLE_DEBRIS_EXTRA + 0.999));
                for (int c = 0; c < chips; c++) {
                    long s = SALT_ISLE + 300 + c * 13L;
                    double ang = hash01(ix, iz, s) * Math.PI * 2.0;
                    double away = radius * (1.15 + hash01(ix, iz, s + 1) * 1.8);
                    double chipR = 2.5 + hash01(ix, iz, s + 2) * (radius * 0.18);
                    double bX = centreX + Math.cos(ang) * away;
                    double bZ = centreZ + Math.sin(ang) * away;
                    double bY = centreY + (hash01(ix, iz, s + 3) - 0.5) * radius * 2.0;
                    best = Math.max(best, debrisBlob(x, y, z, bX, bY, bZ, chipR));
                }
            }
        }
        return Mth.clamp(best * 0.5, -1.0, 1.0);
    }

    // Level 4: hexagonal web of girders. Three horizontal line families 60 degrees
    // apart weave hex cells on stacked decks, with vertical columns at the nodes.
    private static double beamDensity(int x, int y, int z) {
        double gx = x + (fbm(x, z, BEAM_WARP_SCALE, 2, SALT_BEAM_X) - 0.5) * 2.0 * BEAM_WARP;
        double gz = z + (fbm(x, z, BEAM_WARP_SCALE, 2, SALT_BEAM_Z) - 0.5) * 2.0 * BEAM_WARP;
        // only a little vertical warp, a big one would shred the decks apart
        double gy = y + (fbm(x, z, BEAM_WARP_SCALE, 2, SALT_BEAM_Y) - 0.5) * 2.0 * 4.0;

        double d0 = distToLattice(gx, BEAM_CELL);
        double d1 = distToLattice(gx * 0.5 + gz * SQRT3_2, BEAM_CELL);
        double d2 = distToLattice(gx * 0.5 - gz * SQRT3_2, BEAM_CELL);

        double along0 = gz;
        double along1 = -gx * SQRT3_2 + gz * 0.5;
        double along2 = gx * SQRT3_2 + gz * 0.5;

        double best = -1.0e9;
        best = Math.max(best, girder(0, gx, gy, along0, d0));
        best = Math.max(best, girder(1, gx * 0.5 + gz * SQRT3_2, gy, along1, d1));
        best = Math.max(best, girder(2, gx * 0.5 - gz * SQRT3_2, gy, along2, d2));

        double lo = Math.min(d0, Math.min(d1, d2));
        double hi = Math.max(d0, Math.max(d1, d2));
        double vertical = d0 + d1 + d2 - lo - hi;
        int n0 = Mth.floor(gx / BEAM_CELL + 0.5);
        int n1 = Mth.floor((gx * 0.5 + gz * SQRT3_2) / BEAM_CELL + 0.5);
        int n2 = Mth.floor((gx * 0.5 - gz * SQRT3_2) / BEAM_CELL + 0.5);
        double colR = hash01(n0 * 7919 + n1, n2, SALT_COLUMN);
        if (colR <= 0.82) {
            double colThickness = BEAM_HALF * (0.45 + colR * 1.25);
            best = Math.max(best, colThickness - vertical);
        }

        return Mth.clamp(best * 0.5, -1.0, 1.0);
    }

    private static double girder(int family, double normalCoord, double gy, double along, double sideDist) {
        double deckCoord = gy - along * BEAM_TILT;
        int line = Mth.floor(normalCoord / BEAM_CELL + 0.5);
        int deck = Mth.floor(deckCoord / BEAM_DECK + 0.5);
        double r = hash01(line * 31 + family, deck, SALT_GIRDER);
        if (r > 0.86) {
            return -1.0e9; // this span is missing
        }
        double thickness = BEAM_HALF * (0.5 + r * 1.05);
        return thickness - Math.max(distToLattice(deckCoord, BEAM_DECK), sideDist);
    }

    private static double distToLattice(double v, double cell) {
        double half = cell * 0.5;
        return Math.abs(posmod(v + half, cell) - half);
    }

    private static double tube(int x, int y, int z, double scale, long saltA, long saltB, double radius) {
        double a = fbm3(x, y, z, scale, 2, saltA) * 2.0 - 1.0;
        double b = fbm3(x, y, z, scale, 2, saltB) * 2.0 - 1.0;
        return radius - Math.max(Math.abs(a), Math.abs(b));
    }

    private static double spawnSurfaceY(int x, int z) {
        return SPAWN_BASE + fbm(x, z, SPAWN_RELIEF_SCALE, 3, SALT_SPAWN) * SPAWN_RELIEF;
    }

    private static double fieldSurfaceY(int x, int z, double dist) {
        // noise only lifts, so lowlands stay near FIELD_BASE and hills read as relief
        return FIELD_BASE
                + fbm(x, z, FIELD_HILLS_SCALE, 3, SALT_FIELD) * FIELD_HILLS
                + (value2D(x, z, 40.0, SALT_FIELD_ROLL) - 0.5) * FIELD_ROLL
                + monolithSkirt(x, z, dist);
    }

    // First half of Level 2 only. They shrink rather than stop, so the towers get lower
    // until the trees take over. Pits ride on this too - a shaft under a closed canopy
    // would be a hole in the roof.
    private static double monolithGate(double dist) {
        return smoothstep(LEVEL1_MAX, LEVEL1_MAX + MONO_FADE, dist)
                * (1.0 - smoothstep(MONO_END - MONO_END_FADE, MONO_END, dist));
    }

    // Tower and the dune against it share a cell layout, so both read the same hashes.
    private static double monoCentreX(int ix, int iz) {
        return (ix + 0.2 + hash01(ix, iz, SALT_MONO + 1) * 0.6) * MONO_CELL;
    }

    private static double monoCentreZ(int ix, int iz) {
        return (iz + 0.2 + hash01(ix, iz, SALT_MONO + 2) * 0.6) * MONO_CELL;
    }

    private static double monoHalfX(int ix, int iz) {
        return MONO_MIN_HALF + hash01(ix, iz, SALT_MONO + 3) * MONO_HALF_RANGE;
    }

    private static double monoHalfZ(int ix, int iz) {
        return MONO_MIN_HALF + hash01(ix, iz, SALT_MONO + 4) * MONO_HALF_RANGE;
    }

    private static boolean monoAbsent(int ix, int iz) {
        return hash01(ix, iz, SALT_MONO) > MONO_CHANCE;
    }

    // Sand heaped at the foot, so the tower reads as grown out of the dunes.
    private static double monolithSkirt(int x, int z, double dist) {
        double gate = monolithGate(dist);
        if (gate <= 0.0) {
            return 0.0;
        }
        int cx = Mth.floor(x / MONO_CELL);
        int cz = Mth.floor(z / MONO_CELL);
        double lift = 0.0;

        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                int ix = cx + ox;
                int iz = cz + oz;
                if (monoAbsent(ix, iz)) {
                    continue;
                }
                double scale = oddScale(monoOdd(ix, iz));
                double dx = Math.abs(x - monoCentreX(ix, iz)) - monoHalfX(ix, iz) * scale;
                double dz = Math.abs(z - monoCentreZ(ix, iz)) - monoHalfZ(ix, iz) * scale;
                double away = Math.max(0.0, Math.max(dx, dz));
                if (away >= MONO_SKIRT_REACH) {
                    continue;
                }
                // Ragged edge, so the pile does not end on a perfect circle.
                double ragged = away * (0.78 + fbm(x, z, 34.0, 2, SALT_MONO + 7) * 0.5);
                double f = 1.0 - Mth.clamp(ragged / MONO_SKIRT_REACH, 0.0, 1.0);
                lift = Math.max(lift, MONO_SKIRT * f * f * (3.0 - 2.0 * f));
            }
        }
        return lift * gate;
    }

    private static final double PROX_REACH = 120.0;

    /** 1 at the foot of a tower, 0 further than PROX_REACH from any. */
    public static double monolithProximity(int x, int z) {
        double dist = Math.max(Math.abs((double) x), Math.abs((double) z));
        if (monolithGate(dist) <= 0.0) {
            return 0.0;
        }
        int cx = Mth.floor(x / MONO_CELL);
        int cz = Mth.floor(z / MONO_CELL);
        double best = 0.0;

        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                int ix = cx + ox;
                int iz = cz + oz;
                if (monoAbsent(ix, iz)) {
                    continue;
                }
                // Real footprint: the odd tower is twice as broad.
                double scale = oddScale(monoOdd(ix, iz));
                double dx = Math.abs(x - monoCentreX(ix, iz)) - monoHalfX(ix, iz) * scale;
                double dz = Math.abs(z - monoCentreZ(ix, iz)) - monoHalfZ(ix, iz) * scale;
                double away = Math.max(0.0, Math.max(dx, dz));
                best = Math.max(best, 1.0 - Mth.clamp(away / PROX_REACH, 0.0, 1.0));
            }
        }
        return best;
    }

    /** How thickly the wood stands here: 0 on open dune, 1 under closed canopy. */
    public static double forestDensity(int x, int z) {
        double dist = Math.max(Math.abs((double) x), Math.abs((double) z));
        double gate = woodGate(dist);
        if (gate <= 0.0) {
            return 0.0;
        }
        // Towers keep the wood off: a roof over one would hide the level's landmark.
        double open = 1.0 - Mth.clamp(monolithProximity(x, z) * FOREST_MONO_CLEAR, 0.0, 1.0);
        if (open <= 0.0) {
            return 0.0;
        }
        // Clearings are filled rather than removed as the distance passes FOREST_CLOSE_TO.
        double patch = fbm(x, z, FOREST_PATCH_SCALE, 3, SALT_FOREST);
        double grove = patch <= FOREST_CLEARING ? 0.0
                : Mth.clamp((patch - FOREST_CLEARING) / FOREST_EDGE, 0.0, 1.0);
        double closed = smoothstep(FOREST_CLOSE_FROM, FOREST_CLOSE_TO, dist);
        return gate * open * (grove + (1.0 - grove) * closed);
    }

    /**
     * The wood's claim from distance alone: 0 on the dunes, 1 under the deep roof.
     * Towers, ruins and crystals fade against this so the two halves hand over.
     */
    public static double woodGate(int x, int z) {
        return woodGate(Math.max(Math.abs((double) x), Math.abs((double) z)));
    }

    private static double woodGate(double dist) {
        return smoothstep(FOREST_START, FOREST_FULL, dist)
                * (1.0 - smoothstep(LEVEL2_MAX - FOREST_END_FADE, LEVEL2_MAX, dist));
    }

    /** True when a trunk stands in this chunk. Derived from the chunk, never from a roll. */
    public static boolean treeInChunk(int chunkX, int chunkZ) {
        double density = forestDensity(treeX(chunkX, chunkZ), treeZ(chunkX, chunkZ));
        return density > 0.0 && hash01(chunkX, chunkZ, SALT_TREE) < TREE_CHANCE * density;
    }

    public static int treeX(int chunkX, int chunkZ) {
        return (chunkX << 4) + 8 + jitter(chunkX, chunkZ, SALT_TREE + 1);
    }

    public static int treeZ(int chunkX, int chunkZ) {
        return (chunkZ << 4) + 8 + jitter(chunkX, chunkZ, SALT_TREE + 2);
    }

    private static int jitter(int chunkX, int chunkZ, long salt) {
        return (int) (hash01(chunkX, chunkZ, salt) * (TREE_JITTER * 2 + 1)) - TREE_JITTER;
    }

    /** Altitude of the leaf layer over this column - the height every trunk grows up to. */
    public static int canopyY(int x, int z) {
        return Mth.floor(CANOPY_BASE
                + (fbm(x, z, CANOPY_SCALE, 2, SALT_CANOPY) - 0.5) * 2.0 * CANOPY_ROLL);
    }

    /** Fine noise the canopy punches its pinholes with, 0..1. */
    public static double canopyDapple(int x, int z) {
        return value2D(x, z, DAPPLE_SCALE, SALT_DAPPLE);
    }

    /**
     * True when this block is monolith and not dune. Features ask instead of guessing
     * from height - the skirt terraces into cliffs that any height test reads as walls.
     */
    public static boolean isMonolith(int x, int y, int z) {
        double dist = Math.max(Math.abs((double) x), Math.abs((double) z));
        return monolithDensity(x, y, z, dist) > 0.0;
    }

    /**
     * Top of the odd monolith over this column, or {@link Integer#MIN_VALUE}. The
     * density function cannot say what stone is made of, so a feature paints it after.
     */
    public static int oddMonolithTop(int x, int z) {
        double dist = Math.max(Math.abs((double) x), Math.abs((double) z));
        double gate = monolithGate(dist);
        if (gate <= 0.0) {
            return Integer.MIN_VALUE;
        }
        int cx = Mth.floor(x / MONO_CELL);
        int cz = Mth.floor(z / MONO_CELL);

        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                int ix = cx + ox;
                int iz = cz + oz;
                if (monoAbsent(ix, iz) || !monoOdd(ix, iz)) {
                    continue;
                }
                double angle = hash01(ix, iz, SALT_MONO + 6) * Math.PI * 0.5;
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);
                double dx = x - monoCentreX(ix, iz);
                double dz = z - monoCentreZ(ix, iz);
                double rx = dx * cos + dz * sin;
                double rz = -dx * sin + dz * cos;

                if (Math.abs(rx) <= monoHalfX(ix, iz) * ODD_GROW
                        && Math.abs(rz) <= monoHalfZ(ix, iz) * ODD_GROW) {
                    double topY = (MONO_MIN_TOP + hash01(ix, iz, SALT_MONO + 5) * MONO_TOP_RANGE
                            + ODD_EXTRA_TOP) * gate;
                    return (int) topY;
                }
            }
        }
        return Integer.MIN_VALUE;
    }

    /** True inside a pit, where the world is cut away from the sky to the bedrock. */
    private static boolean inPit(int x, int z, double dist) {
        if (monolithGate(dist) <= 0.0) {
            return false;
        }
        int cx = Mth.floor(x / PIT_CELL);
        int cz = Mth.floor(z / PIT_CELL);

        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                int ix = cx + ox;
                int iz = cz + oz;
                if (hash01(ix, iz, SALT_PIT) > PIT_CHANCE) {
                    continue;
                }
                double centreX = (ix + 0.2 + hash01(ix, iz, SALT_PIT + 1) * 0.6) * PIT_CELL;
                double centreZ = (iz + 0.2 + hash01(ix, iz, SALT_PIT + 2) * 0.6) * PIT_CELL;
                double radius = PIT_MIN_R + hash01(ix, iz, SALT_PIT + 3) * PIT_R_RANGE;

                double dx = x - centreX;
                double dz = z - centreZ;
                double away = Math.sqrt(dx * dx + dz * dz);
                // Wobble the edge so it does not read as a circle drawn with a compass.
                double edge = radius * (1.0 + (fbm(x, z, 26.0, 2, SALT_PIT + 4) - 0.5) * PIT_WARP);
                if (away < edge && !pitHitsMonolith(centreX, centreZ, radius)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * True if this pit would swallow a tower, in which case there is no pit.
     *
     * Pits outrank monoliths in the density function, so the two grids eventually
     * collide. The skirt and the crystals key off the grid and would not notice the
     * tower had gone, leaving a hole ringed by a dune with nothing to stand around.
     * Called only for points already inside the circle - this runs per block.
     */
    private static boolean pitHitsMonolith(double centreX, double centreZ, double radius) {
        // Rim wobbles out by up to half of PIT_WARP; clearance keeps the pit off the
        // foot of the tower, not merely off its stone.
        double reach = radius * (1.0 + PIT_WARP * 0.5) + PIT_MONO_CLEAR;
        int cx = Mth.floor(centreX / MONO_CELL);
        int cz = Mth.floor(centreZ / MONO_CELL);

        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                int ix = cx + ox;
                int iz = cz + oz;
                if (monoAbsent(ix, iz)) {
                    continue;
                }
                // Half-diagonal: the tower is turned, so a circle meets its corner
                // first. Erring wide costs a pit, erring narrow costs a tower.
                double scale = oddScale(monoOdd(ix, iz));
                double halfX = monoHalfX(ix, iz) * scale;
                double halfZ = monoHalfZ(ix, iz) * scale;
                double corner = Math.sqrt(halfX * halfX + halfZ * halfZ);

                double dx = centreX - monoCentreX(ix, iz);
                double dz = centreZ - monoCentreZ(ix, iz);
                if (dx * dx + dz * dz < (reach + corner) * (reach + corner)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean monoOdd(int ix, int iz) {
        return hash01(ix, iz, SALT_MONO + 8) < 1.0 / ODD_ONE;
    }

    /** Half-width multiplier. Oddness is passed in, not re-hashed: this is per block. */
    private static double oddScale(boolean odd) {
        return odd ? ODD_GROW : 1.0;
    }

    /** Where up the tower the hole is bored, as a share of its top. */
    private static double holeFraction(int ix, int iz) {
        return ODD_HOLE_AT + hash01(ix, iz, SALT_MONO + 9) * ODD_HOLE_AT_RANGE;
    }

    // How deep the face is cut here, 0 where untouched. Carved into the density rather
    // than stamped on after, so the marks need no blocks of their own.
    private static double glyphCut(int ix, int iz, int y, double across) {
        int band = Mth.floor(y / GLYPH_BAND);
        double vy = y - band * GLYPH_BAND;
        int row = Mth.floor(vy / CARVE_CELL);
        if (row < 0 || row >= GLYPH_H) {
            return 0.0;                       // the gap between bands
        }
        double centred = across + GLYPH_W * CARVE_CELL * 0.5;
        int col = Mth.floor(centred / CARVE_CELL);
        if (col < 0 || col >= GLYPH_W) {
            return 0.0;
        }

        int pick = (int) (hash01(ix, iz, SALT_MONO + 40 + band) * GLYPHS.length);
        int[] glyph = GLYPHS[Math.min(pick, GLYPHS.length - 1)];
        // Rows are written top first, but y counts upwards.
        int bits = glyph[GLYPH_H - 1 - row];
        return ((bits >> (GLYPH_W - 1 - col)) & 1) != 0 ? GLYPH_DEPTH : 0.0;
    }

    // Rectangular towers on a jittered grid, each turned by its own angle so the
    // field does not read as a row of identical boxes.
    private static double monolithDensity(int x, int y, int z, double dist) {
        double gate = monolithGate(dist);
        if (gate <= 0.0) {
            return -1.0;
        }
        int cx = Mth.floor(x / MONO_CELL);
        int cz = Mth.floor(z / MONO_CELL);
        double best = -1.0;

        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                int ix = cx + ox;
                int iz = cz + oz;
                if (monoAbsent(ix, iz)) {
                    continue;
                }
                boolean odd = monoOdd(ix, iz);
                // Gated height, not density: at the edge towers sink into the sand
                // instead of fading into translucent stone.
                double topY = (MONO_MIN_TOP + hash01(ix, iz, SALT_MONO + 5) * MONO_TOP_RANGE) * gate;
                if (odd) {
                    topY += ODD_EXTRA_TOP * gate;
                }
                if (y > topY) {
                    continue;
                }

                double angle = hash01(ix, iz, SALT_MONO + 6) * Math.PI * 0.5;
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);
                double dx = x - monoCentreX(ix, iz);
                double dz = z - monoCentreZ(ix, iz);
                double rx = dx * cos + dz * sin;
                double rz = -dx * sin + dz * cos;

                double halfX = monoHalfX(ix, iz) * oddScale(odd);
                double halfZ = monoHalfZ(ix, iz) * oddScale(odd);
                double gapX = halfX - Math.abs(rx);
                double gapZ = halfZ - Math.abs(rz);
                double inside = Math.min(gapX, gapZ);

                double value;
                if (odd) {
                    // Solid stone minus one bore. No glyphs - the hole is the mark.
                    // The bore runs along the shorter side so the circle lands on the
                    // two widest faces, and its height is taken off the top, not off the
                    // sand: the skirt moves per column and would warp the circle.
                    double holeY = topY * holeFraction(ix, iz);
                    double across = halfX <= halfZ ? rz : rx;
                    double up = y - holeY;
                    double bore = Math.max(halfX, halfZ) * ODD_HOLE_R;
                    value = Math.min(inside, Math.sqrt(across * across + up * up) - bore);
                } else {
                    // Marks go on whichever face this point is nearest.
                    double across = gapX < gapZ ? rz : rx;
                    value = inside - glyphCut(ix, iz, y, across);
                }
                best = Math.max(best, Math.min(value, topY - y));
            }
        }
        return best;
    }

    private static double gorgeSurfaceY(int x, int z) {
        double plateauY = PLATEAU_BASE
                + (fbm(x, z, PLATEAU_VARY_SCALE, 3, SALT_TIER) - 0.5) * PLATEAU_VARY
                + (value2D(x, z, 30.0, SALT_ROLL) - 0.5) * PLATEAU_ROLL;

        double wx = (fbm(x, z, WARP_SCALE, 2, SALT_WARP_X) - 0.5) * 2.0 * WARP;
        double wz = (fbm(x, z, WARP_SCALE, 2, SALT_WARP_Z) - 0.5) * 2.0 * WARP;
        double gx = x + wx;
        double gz = z + wz;

        double du = Math.abs(posmod(gx, CELL) / CELL - 0.5) * CELL;
        double dv = Math.abs(posmod(gz, CELL) / CELL - 0.5) * CELL;
        double dist = Math.min(du, dv);

        double canyon = 1.0 - smoothstep(HALF_WIDTH - WALL_BAND, HALF_WIDTH, dist);
        double shelfY = plateauY - WALL_DROP;

        double innerSpan = HALF_WIDTH - WALL_BAND;
        double innerT = Mth.clamp((innerSpan - dist) / innerSpan, 0.0, 1.0);
        double floorY = shelfY - VALLEY_DEPTH * smoothstep(0.0, 1.0, innerT);

        return plateauY - canyon * (plateauY - floorY);
    }

    @Override
    public double minValue() {
        return -1.0;
    }

    @Override
    public double maxValue() {
        return 1.0;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    private static double posmod(double a, double n) {
        return a - Math.floor(a / n) * n;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private static double fbm(double x, double z, double baseScale, int oct, long salt) {
        double sum = 0.0;
        double amp = 0.5;
        double sc = baseScale;
        double norm = 0.0;
        for (int i = 0; i < oct; i++) {
            sum += amp * value2D(x, z, sc, salt + i * 7919L);
            norm += amp;
            amp *= 0.5;
            sc *= 0.5;
        }
        return sum / norm;
    }

    private static double value2D(double x, double z, double scale, long salt) {
        double sx = x / scale;
        double sz = z / scale;
        int x0 = Mth.floor(sx);
        int z0 = Mth.floor(sz);
        double u = fade(sx - x0);
        double v = fade(sz - z0);
        double n00 = hash01(x0, z0, salt);
        double n10 = hash01(x0 + 1, z0, salt);
        double n01 = hash01(x0, z0 + 1, salt);
        double n11 = hash01(x0 + 1, z0 + 1, salt);
        double nx0 = n00 + (n10 - n00) * u;
        double nx1 = n01 + (n11 - n01) * u;
        return nx0 + (nx1 - nx0) * v;
    }

    private static double hash01(int ix, int iz, long salt) {
        long h = worldSeed + salt;
        h ^= (long) ix * 0x9E3779B97F4A7C15L;
        h = Long.rotateLeft(h, 31) * 0xBF58476D1CE4E5B9L;
        h ^= (long) iz * 0xC2B2AE3D27D4EB4FL;
        h = (h ^ (h >>> 29)) * 0x94D049BB133111EBL;
        h ^= h >>> 32;
        return (h >>> 11) * 0x1.0p-53;
    }

    private static double fbm3(int x, int y, int z, double baseScale, int oct, long salt) {
        double sum = 0.0;
        double amp = 0.5;
        double sc = baseScale;
        double norm = 0.0;
        for (int i = 0; i < oct; i++) {
            sum += amp * value3D(x, y, z, sc, salt + i * 7919L);
            norm += amp;
            amp *= 0.5;
            sc *= 0.5;
        }
        return sum / norm;
    }

    private static double value3D(int x, int y, int z, double scale, long salt) {
        double sx = x / scale;
        double sy = y / scale;
        double sz = z / scale;
        int x0 = Mth.floor(sx);
        int y0 = Mth.floor(sy);
        int z0 = Mth.floor(sz);
        double u = fade(sx - x0);
        double v = fade(sy - y0);
        double w = fade(sz - z0);
        double c000 = hash01(x0, y0, z0, salt);
        double c100 = hash01(x0 + 1, y0, z0, salt);
        double c010 = hash01(x0, y0 + 1, z0, salt);
        double c110 = hash01(x0 + 1, y0 + 1, z0, salt);
        double c001 = hash01(x0, y0, z0 + 1, salt);
        double c101 = hash01(x0 + 1, y0, z0 + 1, salt);
        double c011 = hash01(x0, y0 + 1, z0 + 1, salt);
        double c111 = hash01(x0 + 1, y0 + 1, z0 + 1, salt);
        double x00 = c000 + (c100 - c000) * u;
        double x10 = c010 + (c110 - c010) * u;
        double x01 = c001 + (c101 - c001) * u;
        double x11 = c011 + (c111 - c011) * u;
        double y0v = x00 + (x10 - x00) * v;
        double y1v = x01 + (x11 - x01) * v;
        return y0v + (y1v - y0v) * w;
    }

    private static double hash01(int ix, int iy, int iz, long salt) {
        long h = worldSeed + salt;
        h ^= (long) ix * 0x9E3779B97F4A7C15L;
        h = Long.rotateLeft(h, 29) * 0xBF58476D1CE4E5B9L;
        h ^= (long) iy * 0xC2B2AE3D27D4EB4FL;
        h = Long.rotateLeft(h, 31) * 0x94D049BB133111EBL;
        h ^= (long) iz * 0xD6E8FEB86659FD93L;
        h = (h ^ (h >>> 29)) * 0x94D049BB133111EBL;
        h ^= h >>> 32;
        return (h >>> 11) * 0x1.0p-53;
    }
}
