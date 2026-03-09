package ch.so.agi.dsmocclusion.testsupport;

import java.io.IOException;

import ch.so.agi.dsmocclusion.raster.GeoToolsCogRasterSource;
import ch.so.agi.dsmocclusion.tiling.PixelWindow;

public final class TestRasterReader implements AutoCloseable {
    private final GeoToolsCogRasterSource delegate;

    public TestRasterReader(String inputLocation) throws IOException {
        this.delegate = new GeoToolsCogRasterSource(inputLocation);
    }

    public float[] readAll() throws IOException {
        return delegate.readWindow(new PixelWindow(0, 0, delegate.metadata().width(), delegate.metadata().height()));
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
