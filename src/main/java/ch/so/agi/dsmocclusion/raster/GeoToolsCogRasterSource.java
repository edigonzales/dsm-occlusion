package ch.so.agi.dsmocclusion.raster;

import java.awt.Rectangle;
import java.awt.image.RenderedImage;
import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.geotools.api.coverage.grid.GridEnvelope;
import org.geotools.api.coverage.grid.GridGeometry;
import org.geotools.api.parameter.GeneralParameterValue;
import org.geotools.api.parameter.ParameterValue;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.datum.PixelInCell;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridEnvelope2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.OverviewPolicy;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.geotools.geometry.GeneralBounds;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.operation.transform.AffineTransform2D;
import org.geotools.util.factory.Hints;

import ch.so.agi.dsmocclusion.tiling.PixelWindow;
import it.geosolutions.imageio.core.BasicAuthURI;
import it.geosolutions.imageio.plugins.cog.CogImageReadParam;
import it.geosolutions.imageioimpl.plugins.cog.CogImageInputStreamSpi;
import it.geosolutions.imageioimpl.plugins.cog.CogImageReaderSpi;
import it.geosolutions.imageioimpl.plugins.cog.CogSourceSPIProvider;
import it.geosolutions.imageioimpl.plugins.cog.HttpRangeReader;
import it.geosolutions.imageioimpl.plugins.tiff.TIFFImageReaderSpi;
import ch.so.agi.dsmocclusion.util.ConsoleLogger;

public final class GeoToolsCogRasterSource implements RasterSource {
    private final String inputLocation;
    private final Hints hints;
    private final RasterMetadata metadata;
    private final ConsoleLogger logger;
    private final Queue<ReaderSession> allSessions = new ConcurrentLinkedQueue<>();
    private final ThreadLocal<ReaderSession> sessions;

    public GeoToolsCogRasterSource(String inputLocation) throws IOException {
        this(inputLocation, ConsoleLogger.system(false));
    }

    public GeoToolsCogRasterSource(String inputLocation, ConsoleLogger logger) throws IOException {
        this.inputLocation = inputLocation;
        this.logger = logger;
        this.hints = new Hints(Hints.FORCE_LONGITUDE_FIRST_AXIS_ORDER, Boolean.TRUE);
        this.logger.verbose("Initializing %s raster source: %s", isRemote(inputLocation) ? "remote" : "local", inputLocation);
        try (ReaderSession session = openSession()) {
            this.metadata = extractMetadata(session.reader());
        }
        this.sessions = ThreadLocal.withInitial(() -> {
            try {
                ReaderSession session = openSession();
                allSessions.add(session);
                return session;
            } catch (IOException e) {
                throw new IllegalStateException("Failed to open raster reader", e);
            }
        });
    }

    @Override
    public RasterMetadata metadata() {
        return metadata;
    }

    @Override
    public float[] readWindow(PixelWindow window) throws IOException {
        ReaderSession session = sessions.get();
        ImageReadParam readParam = session.imageReader().getDefaultReadParam();
        readParam.setSourceRegion(new Rectangle(window.x(), window.y(), window.width(), window.height()));
        Raster raster = session.imageReader().read(0, readParam).getRaster();
        Rectangle bounds = raster.getBounds();
        return raster.getSamples(bounds.x, bounds.y, bounds.width, bounds.height, 0, (float[]) null);
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        for (ReaderSession session : allSessions) {
            try {
                session.close();
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        sessions.remove();
        if (failure != null) {
            throw failure;
        }
    }

    private RasterMetadata extractMetadata(GeoTiffReader reader) throws IOException {
        GridEnvelope gridRange = reader.getOriginalGridRange();
        int width = gridRange.getSpan(0);
        int height = gridRange.getSpan(1);
        CoordinateReferenceSystem crs = reader.getCoordinateReferenceSystem();
        GeneralBounds originalEnvelope = reader.getOriginalEnvelope();
        ReferencedEnvelope envelope = new ReferencedEnvelope(originalEnvelope);

        AffineTransform2D transform = (AffineTransform2D) reader.getOriginalGridToWorld(PixelInCell.CELL_CORNER);
        validateTransform(transform);
        validateCrs(crs);

        double resolutionX = transform.getScaleX();
        double resolutionY = Math.abs(transform.getScaleY());

        double noData = extractNoData(reader, envelope, crs, resolutionX, resolutionY);
        int bandCount = extractBandCount(reader, envelope, crs, resolutionX, resolutionY);
        if (bandCount != 1) {
            throw new IllegalArgumentException("Only single-band DSM rasters are supported.");
        }
        return new RasterMetadata(width, height, envelope, crs, resolutionX, resolutionY, noData, bandCount, inputLocation);
    }

    private void validateTransform(AffineTransform2D transform) {
        if (transform.getShearX() != 0.0 || transform.getShearY() != 0.0) {
            throw new IllegalArgumentException("Rotated or sheared rasters are not supported.");
        }
        if (transform.getScaleX() <= 0.0 || transform.getScaleY() >= 0.0) {
            throw new IllegalArgumentException("Only north-up rasters with positive X and negative Y scale are supported.");
        }
    }

    private void validateCrs(CoordinateReferenceSystem crs) {
        if (crs == null || !(crs.getCoordinateSystem().getDimension() >= 2)) {
            throw new IllegalArgumentException("Raster CRS is missing or invalid.");
        }
        String unitX = crs.getCoordinateSystem().getAxis(0).getUnit().toString().toLowerCase();
        String unitY = crs.getCoordinateSystem().getAxis(1).getUnit().toString().toLowerCase();
        if (!unitX.equals("m") || !unitY.equals("m")) {
            throw new IllegalArgumentException("Only projected rasters with metre units are supported.");
        }
    }

    private int extractBandCount(
            GeoTiffReader reader,
            ReferencedEnvelope envelope,
            CoordinateReferenceSystem crs,
            double resolutionX,
            double resolutionY) throws IOException {
        GridCoverage2D coverage = reader.read(buildReadParameters(new PixelWindow(0, 0, 1, 1), envelope, crs, resolutionX, resolutionY));
        try {
            return coverage.getNumSampleDimensions();
        } finally {
            coverage.dispose(true);
        }
    }

    private double extractNoData(
            GeoTiffReader reader,
            ReferencedEnvelope envelope,
            CoordinateReferenceSystem crs,
            double resolutionX,
            double resolutionY) throws IOException {
        GridCoverage2D coverage = reader.read(buildReadParameters(new PixelWindow(0, 0, 1, 1), envelope, crs, resolutionX, resolutionY));
        try {
            double[] noDataValues = coverage.getSampleDimension(0).getNoDataValues();
            if (noDataValues != null && noDataValues.length > 0) {
                return noDataValues[0];
            }
            return Double.NaN;
        } finally {
            coverage.dispose(true);
        }
    }

    private GeneralParameterValue[] buildReadParameters(PixelWindow window) {
        return buildReadParameters(window, metadata.envelope(), metadata.crs(), metadata.resolutionX(), metadata.resolutionY());
    }

    private GeneralParameterValue[] buildReadParameters(
            PixelWindow window,
            ReferencedEnvelope fullEnvelope,
            CoordinateReferenceSystem crs,
            double resolutionX,
            double resolutionY) {
        GridEnvelope2D range = new GridEnvelope2D(0, 0, window.width(), window.height());
        ReferencedEnvelope envelope = toEnvelope(fullEnvelope, crs, resolutionX, resolutionY, window);
        GridGeometry geometry = new GridGeometry2D(range, envelope);

        ParameterValue<GridGeometry2D> gg = AbstractGridFormat.READ_GRIDGEOMETRY2D.createValue();
        gg.setValue((GridGeometry2D) geometry);
        ParameterValue<OverviewPolicy> overviewPolicy = AbstractGridFormat.OVERVIEW_POLICY.createValue();
        overviewPolicy.setValue(OverviewPolicy.IGNORE);
        return new GeneralParameterValue[] {gg, overviewPolicy};
    }

    private ReferencedEnvelope toEnvelope(
            ReferencedEnvelope fullEnvelope,
            CoordinateReferenceSystem crs,
            double resolutionX,
            double resolutionY,
            PixelWindow window) {
        double minX = fullEnvelope.getMinX() + window.x() * resolutionX;
        double maxX = fullEnvelope.getMinX() + window.endX() * resolutionX;
        double maxY = fullEnvelope.getMaxY() - window.y() * resolutionY;
        double minY = fullEnvelope.getMaxY() - window.endY() * resolutionY;
        return new ReferencedEnvelope(minX, maxX, minY, maxY, crs);
    }

    private ReaderSession openSession() throws IOException {
        if (isRemote(inputLocation)) {
            BasicAuthURI cogUri = new BasicAuthURI(inputLocation, false);
            HttpRangeReader rangeReader = new HttpRangeReader(cogUri.getUri(), CogImageReadParam.DEFAULT_HEADER_LENGTH);
            CogImageReaderSpi readerSpi = new CogImageReaderSpi();
            CogSourceSPIProvider input = new CogSourceSPIProvider(
                    cogUri,
                    readerSpi,
                    new CogImageInputStreamSpi(),
                    rangeReader.getClass().getName());
            logger.verbose("Opening remote GeoTiffReader session for %s", inputLocation);
            return new ReaderSession(new GeoTiffReader(input, hints), openImageReader(readerSpi, input.getStream()));
        }
        logger.verbose("Opening local GeoTiffReader session for %s", inputLocation);
        File file = new File(inputLocation);
        TIFFImageReaderSpi readerSpi = new TIFFImageReaderSpi();
        return new ReaderSession(new GeoTiffReader(file, hints), openImageReader(readerSpi, ImageIO.createImageInputStream(file)));
    }

    private boolean isRemote(String value) {
        URI uri = URI.create(value);
        return uri.getScheme() != null && (uri.getScheme().equalsIgnoreCase("http") || uri.getScheme().equalsIgnoreCase("https"));
    }

    private ReaderSession.ImageReaderHandle openImageReader(javax.imageio.spi.ImageReaderSpi readerSpi, ImageInputStream imageInputStream) throws IOException {
        ImageReader imageReader = readerSpi.createReaderInstance();
        imageReader.setInput(imageInputStream, true, true);
        return new ReaderSession.ImageReaderHandle(imageReader, imageInputStream);
    }

    private static final class ReaderSession implements AutoCloseable {
        private final GeoTiffReader reader;
        private final ImageReaderHandle imageReaderHandle;

        private ReaderSession(GeoTiffReader reader, ImageReaderHandle imageReaderHandle) {
            this.reader = reader;
            this.imageReaderHandle = imageReaderHandle;
        }

        private GeoTiffReader reader() {
            return reader;
        }

        private ImageReader imageReader() {
            return imageReaderHandle.imageReader();
        }

        @Override
        public void close() throws IOException {
            IOException failure = null;
            try {
                imageReaderHandle.close();
            } catch (IOException e) {
                failure = e;
            }
            reader.dispose();
            if (failure != null) {
                throw failure;
            }
        }

        private record ImageReaderHandle(ImageReader imageReader, ImageInputStream imageInputStream) implements AutoCloseable {
            @Override
            public void close() throws IOException {
                imageReader.dispose();
                imageInputStream.close();
            }
        }
    }
}
