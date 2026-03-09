package ch.so.agi.dsmocclusion.testsupport;

import java.io.IOException;
import java.nio.file.Path;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;

import ch.so.agi.dsmocclusion.output.GeoTiffSupport;

public final class TestRasterFactory {
    private TestRasterFactory() {
    }

    public static Path createRaster(
            Path output,
            float[] values,
            int width,
            int height,
            double minX,
            double minY,
            double pixelSize,
            String epsgCode,
            double noData) throws Exception {
        ReferencedEnvelope envelope = new ReferencedEnvelope(
                minX,
                minX + width * pixelSize,
                minY,
                minY + height * pixelSize,
                CRS.decode(epsgCode, true));
        GeoTiffSupport.writeFloatArray(output, values, width, height, envelope, noData, Math.min(128, Math.max(width, height)));
        return output;
    }

    public static float[] readRaster(Path rasterPath) throws IOException {
        try (TestRasterReader reader = new TestRasterReader(rasterPath.toString())) {
            return reader.readAll();
        }
    }
}
