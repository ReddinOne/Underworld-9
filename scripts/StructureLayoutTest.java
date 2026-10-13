import dev.unstable.underworld.UnderworldLayout;
import dev.unstable.underworld.UnderworldMath;
import java.util.*;
/** Exercises the real chest layout and its carving rule, without Minecraft. */
public final class StructureLayoutTest {
 public static void main(String[] args) {
  int available=0, spawned=0, treasuries=0;
  for(int kind=0;kind<8;kind++) {
   for (int i=0;i<UnderworldLayout.CHESTS_PER_RUIN;i++) {
    int x=UnderworldLayout.chestX(kind,i), z=UnderworldLayout.chestZ(kind,i);
    if (!UnderworldLayout.insideFloor(kind,x,z)) throw new AssertionError("chest outside floor");
    if (!UnderworldLayout.insideFloor(kind,x+UnderworldLayout.facingX(kind,i),z))
      throw new AssertionError("entrance outside floor");
    for(int j=0;j<i;j++) {
     int x2=UnderworldLayout.chestX(kind,j), z2=UnderworldLayout.chestZ(kind,j);
     if(Math.abs(x-x2)<=2 && Math.abs(z-z2)<=2) throw new AssertionError("overlapping chest clearances");
    }
   }
   for(int sample=0;sample<300;sample++) {
     long hash=(((long)kind<<32) ^ ((long)sample * 0x9E3779B97F4A7C15L));
     for (int i=0;i<UnderworldLayout.CHESTS_PER_RUIN;i++) {
       available++;
       if (!UnderworldLayout.chestRoll(hash,kind,i)) continue;
       spawned++;
       if(i==5) treasuries++;
       int x=UnderworldLayout.chestX(kind,i),z=UnderworldLayout.chestZ(kind,i);
       if (!UnderworldLayout.reserved(kind,x,z,1,hash) ||
           !UnderworldLayout.reserved(kind,x,z,3,hash) ||
           !UnderworldLayout.reserved(kind,x+UnderworldLayout.facingX(kind,i),z,2,hash))
           throw new AssertionError("blocked chest space");
     }
   }
  }
  if (spawned < available / 3 || treasuries < 400) throw new AssertionError("loot too sparse");
  System.out.println("PASS: 8 ruin families, six separated chest alcoves each, headroom, approach orientation, "+spawned+" chest rolls, "+treasuries+" treasuries");
 }
}
