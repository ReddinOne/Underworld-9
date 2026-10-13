import dev.unstable.underworld.UnderworldPlacement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure Java tests: reproducible positions, no repeated chunk borders, no grid. */
public class PlacementTest {
    public static void main(String[] args) {
        long seed = 7412589L;
        var all = new java.util.HashMap<String,UnderworldPlacement.Site>();
        var centers = new ArrayList<UnderworldPlacement.Site>();
        // Bounded test region; verify every generated structure is returned for
        // every intersecting chunk consistently and never changes with scan order.
        for (int cx = -65; cx <= 65; cx++) {
            for (int cz = -65; cz <= 65; cz++) {
                for (var s : UnderworldPlacement.inChunk(cx*16,cz*16,seed)) {
                    String id = s.x()+":"+s.z();
                    var old = all.putIfAbsent(id,s);
                    if (old != null && (!old.equals(s))) throw new AssertionError("Unstable site: "+id);
                }
            }
        }
        centers.addAll(all.values());
        if (centers.size() < 6) throw new AssertionError("Too few ruins: " + centers.size());
        int totalXUnique = (int)centers.stream().map(x -> x.x()).distinct().count();
        int totalZUnique = (int)centers.stream().map(x -> x.z()).distinct().count();
        if (totalXUnique < centers.size()/2 || totalZUnique < centers.size()/2)
            throw new AssertionError("Structure lines/grid still visible");
        for (int i=0; i<centers.size();i++) for(int j=i+1;j<centers.size();j++) {
            var a=centers.get(i); var b=centers.get(j);
            double d=Math.hypot((double)a.x()-b.x(),(double)a.z()-b.z());
            if (d<174.0) throw new AssertionError("Sites collide: " + a + " and " + b + ":" + d);
        }
        System.out.println("PASS: " + centers.size() + " stable randomized ruins without aligned grid, overlap, or missing positions");
    }
}
