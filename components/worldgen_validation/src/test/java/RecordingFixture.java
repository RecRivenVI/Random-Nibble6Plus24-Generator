import java.nio.file.Files;
import java.nio.file.Path;
import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Recording;

/** Both diagnostic marker schemas, without Minecraft, game worlds or dependencies. */
class RecordingFixture {
    @Name("mosaic.ChunkPhase")
    static class WithClock extends Event { public String phase,key,detail; public long elapsedNanos; }
    @Name("mosaic.ChunkPhase")
    static class WithoutClock extends Event { public String phase,key,detail; }
    @Name("jdk.CPULoad")
    static class CpuLoadFixture extends Event { public float machineTotal,jvmUser,jvmSystem; }

    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);Files.createDirectories(root);
        try(Recording recording=new Recording()) {
            recording.enable(WithClock.class);
            recording.enable(CpuLoadFixture.class);
            recording.start();
            var start=new WithClock();start.phase="WORKLOAD_START_unit";start.key="WORKLOAD";start.elapsedNanos=100;start.commit();
            // Fixed reducer inputs, not a hardware sampling or performance assertion.
            var load=new CpuLoadFixture();load.machineTotal=.25f;load.jvmUser=.125f;load.jvmSystem=.0625f;load.commit();
            Thread.sleep(5);
            var end=new WithClock();end.phase="WORKLOAD_END";end.key="WORKLOAD";end.elapsedNanos=200;end.commit();
            recording.stop();recording.dump(root.resolve("with-clock.jfr"));
        }
        try(Recording recording=new Recording()) {
            recording.enable(WithoutClock.class);recording.start();
            var start=new WithoutClock();start.phase="WORKLOAD_START_unit";start.key="WORKLOAD";start.commit();
            Thread.sleep(5);
            var end=new WithoutClock();end.phase="WORKLOAD_END";end.key="WORKLOAD";end.commit();
            recording.stop();recording.dump(root.resolve("without-clock.jfr"));
        }
    }
}
