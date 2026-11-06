package com.javafarmer.progress;

import com.javafarmer.runtime.ProgramFeature;
import com.javafarmer.world.CropType;

import java.util.EnumSet;
import java.util.Set;

/** What one program execution actually achieved (only successful actions are counted). */
public final class RunReport {
    public final Set<ProgramFeature> features = EnumSet.noneOf(ProgramFeature.class);
    public final int[] planted = new int[CropType.values().length];
    public final int[] harvested = new int[CropType.values().length];
    public int moves;
    public int tills;
    public int waters;
    public int coinsEarned;
    public int itemsSold;

    public RunReport(Set<ProgramFeature> features) {
        if (features != null) this.features.addAll(features);
    }

    public int plants() { int t = 0; for (int v : planted) t += v; return t; }
    public int harvests() { int t = 0; for (int v : harvested) t += v; return t; }

    public int plantedTypes() {
        int types = 0;
        for (int v : planted) if (v > 0) types++;
        return types;
    }

    public boolean has(ProgramFeature feature) {
        return feature == null || features.contains(feature);
    }
}
