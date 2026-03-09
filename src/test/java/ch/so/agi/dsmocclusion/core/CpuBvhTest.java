package ch.so.agi.dsmocclusion.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CpuBvhTest {
    @Test
    void findsNearestHitAgainstNeighbourColumn() {
        float[] values = new float[] {1.0f, 1.0f};
        CpuBvh bvh = new CpuBvh(values, 2, 1, 1.0, 1.0, 1.0, -9999.0);

        Hit hit = bvh.findNearestHit(new Vector3(0.0, 0.0, 1.0), new Vector3(1.0, 0.0, 0.0), bvh.columnIndexForPixel(0, 0));

        assertThat(hit).isNotNull();
        assertThat(hit.distance()).isGreaterThan(0.0);
        assertThat(hit.normal()).isEqualTo(new Vector3(-1.0, 0.0, 0.0));
    }

    @Test
    void reusableTraversalContextProducesSameHit() {
        float[] values = new float[] {1.0f, 1.0f};
        CpuBvh bvh = new CpuBvh(values, 2, 1, 1.0, 1.0, 1.0, -9999.0);
        CpuBvh.TraversalContext traversalContext = bvh.newTraversalContext();

        Hit hit = bvh.findNearestHit(0.0, 0.0, 1.0, 1.0, 0.0, 0.0, bvh.columnIndexForPixel(0, 0), traversalContext);

        assertThat(hit).isNotNull();
        assertThat(hit.distance()).isGreaterThan(0.0);
        assertThat(hit.normal()).isEqualTo(new Vector3(-1.0, 0.0, 0.0));
    }
}
