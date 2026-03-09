package ch.so.agi.dsmocclusion.core;

import ch.so.agi.dsmocclusion.config.RunConfig;
import ch.so.agi.dsmocclusion.raster.RasterMetadata;
import ch.so.agi.dsmocclusion.tiling.TileRequest;

interface TileComputationStrategy {
    TileComputationResult process(
            TileRequest request,
            RasterMetadata metadata,
            float[] bufferedValues,
            RunConfig runConfig,
            int threads);
}
