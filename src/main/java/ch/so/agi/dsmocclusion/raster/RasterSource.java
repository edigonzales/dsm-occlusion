package ch.so.agi.dsmocclusion.raster;

import java.io.Closeable;
import java.io.IOException;

import ch.so.agi.dsmocclusion.tiling.PixelWindow;

public interface RasterSource extends Closeable {
    RasterMetadata metadata();

    float[] readWindow(PixelWindow window) throws IOException;
}
