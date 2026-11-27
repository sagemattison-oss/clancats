package com.example.clancats.world;

import net.minecraft.util.math.BlockPos;

/** Anything a clan can claim: the cave camp or the surface camp. */
public interface CampSite {
    /** Floor level at the centre, directly under the Great Rock. */
    BlockPos center();

    int rot();

    /** Height of the rock's top above {@link #center()} (where the clan wool goes). */
    int markerY();

    /** {@link CampSpots#CAVE} or {@link CampSpots#SURFACE}. */
    int kind();
}
