package ch.so.agi.dsmocclusion.raster;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ch.so.agi.dsmocclusion.testsupport.RangeAwareHttpServer;
import ch.so.agi.dsmocclusion.testsupport.TestRasterFactory;
import ch.so.agi.dsmocclusion.tiling.PixelWindow;

class GeoToolsCogRasterSourceIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void readsRemoteSubsetViaRangeRequests() throws Exception {
        Path raster = TestRasterFactory.createRaster(
                tempDir.resolve("remote.tif"),
                new float[] {
                        1, 2, 3, 4,
                        5, 6, 7, 8,
                        9, 10, 11, 12,
                        13, 14, 15, 16
                },
                4,
                4,
                0.0,
                0.0,
                1.0,
                "EPSG:2056",
                -9999.0);
        assertThat(Files.exists(raster)).isTrue();

        try (RangeAwareHttpServer server = new RangeAwareHttpServer(raster, "/remote.tif");
                GeoToolsCogRasterSource source = new GeoToolsCogRasterSource(server.uri("/remote.tif").toString())) {
            float[] subset = source.readWindow(new PixelWindow(1, 1, 2, 2));

            assertThat(subset).containsExactly(6.0f, 7.0f, 10.0f, 11.0f);
            assertThat(server.rangeRequests()).isGreaterThan(0);
            assertThat(server.seenRanges()).allMatch(range -> range.startsWith("bytes="));
        }
    }
}
