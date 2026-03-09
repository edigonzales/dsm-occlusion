package ch.so.agi.dsmocclusion.output;

import java.awt.Point;
import java.awt.image.BandedSampleModel;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferFloat;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.nio.file.Path;

import javax.imageio.ImageWriteParam;

import org.eclipse.imagen.PlanarImage;
import org.eclipse.imagen.TiledImage;
import org.eclipse.imagen.media.range.NoDataContainer;
import org.geotools.api.parameter.GeneralParameterValue;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.imageio.GeoToolsWriteParams;
import org.geotools.gce.geotiff.GeoTiffFormat;
import org.geotools.gce.geotiff.GeoTiffWriteParams;
import org.geotools.gce.geotiff.GeoTiffWriter;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.api.parameter.ParameterValueGroup;

public final class GeoTiffSupport {
    private GeoTiffSupport() {
    }

    public static void writeFloatArray(
            Path output,
            float[] data,
            int width,
            int height,
            ReferencedEnvelope envelope,
            double noData,
            int tileSizePixels) throws IOException {
        BandedSampleModel sampleModel = new BandedSampleModel(DataBuffer.TYPE_FLOAT, width, height, 1);
        DataBufferFloat buffer = new DataBufferFloat(data, data.length);
        WritableRaster raster = WritableRaster.createWritableRaster(sampleModel, buffer, new Point(0, 0));
        TiledImage image = createImage(width, height, sampleModel);
        image.setData(raster);
        image.setProperty(NoDataContainer.GC_NODATA, new NoDataContainer(noData));
        writeRenderedImage(output, image, envelope, tileSizePixels);
    }

    public static void writeByteArray(
            Path output,
            byte[] data,
            int width,
            int height,
            ReferencedEnvelope envelope,
            double noData,
            int tileSizePixels) throws IOException {
        BandedSampleModel sampleModel = new BandedSampleModel(DataBuffer.TYPE_BYTE, width, height, 1);
        DataBufferByte buffer = new DataBufferByte(data, data.length);
        WritableRaster raster = WritableRaster.createWritableRaster(sampleModel, buffer, new Point(0, 0));
        TiledImage image = createImage(width, height, sampleModel);
        image.setData(raster);
        image.setProperty(NoDataContainer.GC_NODATA, new NoDataContainer(noData));
        writeRenderedImage(output, image, envelope, tileSizePixels);
    }

    public static void writeRenderedImage(
            Path output,
            RenderedImage image,
            ReferencedEnvelope envelope,
            int tileSizePixels) throws IOException {
        GridCoverageFactory coverageFactory = new GridCoverageFactory();
        GridCoverage2D coverage = coverageFactory.create("dsm-occlusion", image, envelope);
        GeoTiffFormat format = new GeoTiffFormat();
        GeoTiffWriteParams writeParams = new GeoTiffWriteParams();
        writeParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        writeParams.setCompressionType("Deflate");
        writeParams.setTilingMode(ImageWriteParam.MODE_EXPLICIT);
        writeParams.setTiling(tileSizePixels, tileSizePixels);

        ParameterValueGroup parameters = format.getWriteParameters();
        parameters.parameter(AbstractGridFormat.GEOTOOLS_WRITE_PARAMS.getName().toString()).setValue(writeParams);
        parameters.parameter(GeoTiffFormat.WRITE_NODATA.getName().toString()).setValue(Boolean.TRUE);

        GeoTiffWriter writer = new GeoTiffWriter(output.toFile());
        try {
            writer.write(coverage, parameters.values().toArray(GeneralParameterValue[]::new));
        } finally {
            writer.dispose();
            coverage.dispose(true);
        }
    }

    private static TiledImage createImage(int width, int height, BandedSampleModel sampleModel) {
        ColorModel colorModel = PlanarImage.createColorModel(sampleModel);
        return new TiledImage(0, 0, width, height, 0, 0, sampleModel, colorModel);
    }
}
