package ch.so.agi.dsmocclusion.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ch.so.agi.dsmocclusion.config.AlgorithmMode;
import ch.so.agi.dsmocclusion.config.BBox;
import ch.so.agi.dsmocclusion.config.HorizonParameters;
import ch.so.agi.dsmocclusion.config.LightingParameters;
import ch.so.agi.dsmocclusion.config.OutputMode;
import ch.so.agi.dsmocclusion.config.RunConfig;
import ch.so.agi.dsmocclusion.raster.RasterMetadata;
import ch.so.agi.dsmocclusion.testsupport.BlockingRasterSource;
import ch.so.agi.dsmocclusion.testsupport.CapturedCommandOutput;
import ch.so.agi.dsmocclusion.testsupport.ManualHeartbeatScheduler;
import ch.so.agi.dsmocclusion.util.ConsoleLogger;

class OcclusionPipelineLoggingTest {
    @TempDir
    Path tempDir;

    @Test
    void emitsHeartbeatBeforeFirstTileCompletes() throws Exception {
        CapturedCommandOutput output = new CapturedCommandOutput();
        ConsoleLogger logger = new ConsoleLogger(output.stdoutWriter(), output.stderrWriter(), fixedClock(), false);
        ManualHeartbeatScheduler heartbeatScheduler = new ManualHeartbeatScheduler();
        OcclusionPipeline pipeline = new OcclusionPipeline(
                new ch.so.agi.dsmocclusion.tiling.TilePlanner(),
                new ch.so.agi.dsmocclusion.core.TileProcessor(),
                logger,
                fixedClock(),
                () -> heartbeatScheduler);

        RasterMetadata metadata = new RasterMetadata(
                2,
                2,
                new ReferencedEnvelope(0.0, 2.0, 0.0, 2.0, CRS.decode("EPSG:2056", true)),
                CRS.decode("EPSG:2056", true),
                1.0,
                1.0,
                -9999.0,
                1,
                "blocking-test");
        BlockingRasterSource rasterSource = new BlockingRasterSource(metadata, new float[] {
                1.0f, 2.0f,
                3.0f, 4.0f
        });
        RunConfig runConfig = new RunConfig(
                "blocking-test",
                new BBox(0.0, 0.0, 2.0, 2.0),
                AlgorithmMode.EXACT,
                OutputMode.TILE_FILES,
                tempDir.resolve("unused.tif"),
                tempDir.resolve("tiles"),
                2,
                0,
                null,
                0,
                1,
                false,
                false,
                false,
                1.0,
                new LightingParameters(0, 4, 1.0, 0.0, 1.0, 0.0, 0.0, 45.0, 11.4, 1.0),
                HorizonParameters.defaults());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> future = executor.submit(() -> {
                try {
                    pipeline.run(rasterSource, runConfig);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            assertThat(rasterSource.awaitReadStarted(5, TimeUnit.SECONDS)).isTrue();
            heartbeatScheduler.trigger();
            assertThat(output.stdout()).contains("Heartbeat:");

            rasterSource.releaseRead();
            future.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(output.stdout()).contains("Completed tile 1/1:").contains("Finished run:");
        assertThat(output.stderr()).isBlank();
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-03-09T12:00:00Z"), ZoneId.of("Europe/Zurich"));
    }
}
