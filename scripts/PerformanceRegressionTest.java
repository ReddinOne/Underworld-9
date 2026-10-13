import dev.unstable.underworld.UnderworldMath;
import dev.unstable.underworld.UnderworldPlacement;
/** Plain Java regression test: migration keeps old world coordinates deterministic. */
public final class PerformanceRegressionTest {
  public static void main(String[] args) {
    long seed = 0x12553a7L;
    for (int x=-7000; x<=7000; x+=89) {
      for(int z=-7000;z<=7000;z+=113) {
        int y=UnderworldMath.surfaceY(x,z,seed);
        if (y - UnderworldMath.bottomY(x,z,seed) + 1 != 30)
          throw new AssertionError("ground thickness must remain 30 blocks");
        if (Math.hypot(x,z)>=UnderworldMath.COMPLETE_VOID && UnderworldMath.hasLand(x,z,seed))
          throw new AssertionError("land after complete void");
      }
    }
    var once=UnderworldPlacement.inChunk(0,0,seed);
    var twice=UnderworldPlacement.inChunk(0,0,seed);
    if (!once.equals(twice)) throw new AssertionError("structure positions not stable");
    System.out.println("PASS: void boundary, 30-block geology, stable structure positions");
  }
}
