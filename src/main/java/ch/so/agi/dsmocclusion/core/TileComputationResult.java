package ch.so.agi.dsmocclusion.core;

import ch.so.agi.dsmocclusion.tiling.TileRequest;

public record TileComputationResult(TileRequest tileRequest, float[] data, boolean skipped, int validPixelCount) {
    public TileComputationResult {
        if (tileRequest == null || data == null) {
            throw new IllegalArgumentException("TileComputationResult requires tile metadata and data");
        }
    }
}
