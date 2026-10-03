package net.ochibo.wishlist.core.material;

import net.ochibo.wishlist.core.model.IngredientChoice;
import net.ochibo.wishlist.core.model.ResourceIdentity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public final class InventoryAllocator {
    private record Base(String kind, String id) {
        static Base of(ResourceIdentity identity) { return new Base(identity.kind(), identity.registryId()); }
    }
    public record Request(int index, IngredientChoice ingredient, long required) {}
    public record Result(Map<Integer, Long> allocatedByRequest, Map<String, Long> usedByItem) {}

    public Result allocate(List<Request> requests, Map<String, Long> available) {
        List<String> items = available.entrySet().stream().filter(e -> e.getValue() > 0).map(Map.Entry::getKey).sorted().toList();
        int source = 0;
        int itemStart = 1;
        int reqStart = itemStart + items.size();
        int sink = reqStart + requests.size();
        Dinic dinic = new Dinic(sink + 1);
        Map<Base, List<Integer>> itemIndex = new HashMap<>();
        List<ResourceIdentity> identities = new ArrayList<>();
        Map<String, ResourceIdentity> requirements = new HashMap<>();
        for (int i = 0; i < items.size(); i++) {
            ResourceIdentity identity = ResourceIdentity.parse(items.get(i));
            identities.add(identity);
            itemIndex.computeIfAbsent(Base.of(identity), ignored -> new ArrayList<>()).add(i);
            dinic.addEdge(source, itemStart + i, available.getOrDefault(items.get(i), 0L));
        }
        List<Dinic.Edge> sinkEdges = new ArrayList<>();
        Map<String, List<Dinic.Edge>> itemEdges = new LinkedHashMap<>();
        for (int i = 0; i < requests.size(); i++) {
            Request req = requests.get(i);
            int rn = reqStart + i;
            // Preserve the sorted edge order of the original allocator, including alternatives.
            TreeSet<Integer> matching = new TreeSet<>();
            if (req.required() > 0) {
                for (String candidate : req.ingredient().candidates()) {
                    ResourceIdentity wanted = requirements.computeIfAbsent(candidate, ResourceIdentity::parse);
                    for (int item : itemIndex.getOrDefault(Base.of(wanted), List.of())) {
                        if (wanted.tag() == null || wanted.tag().equals(identities.get(item).tag())) matching.add(item);
                    }
                }
            }
            for (int item : matching) {
                Dinic.Edge edge = dinic.addEdge(itemStart + item, rn, Long.MAX_VALUE / 4);
                itemEdges.computeIfAbsent(items.get(item), k -> new ArrayList<>()).add(edge);
            }
            sinkEdges.add(dinic.addEdge(rn, sink, req.required()));
        }
        dinic.maxFlow(source, sink);

        Map<Integer, Long> allocated = new LinkedHashMap<>();
        for (int i = 0; i < requests.size(); i++) {
            Dinic.Edge e = sinkEdges.get(i);
            allocated.put(requests.get(i).index(), e.originalCapacity - e.capacity);
        }
        Map<String, Long> used = new LinkedHashMap<>();
        for (String item : items) {
            long sum = 0;
            for (Dinic.Edge e : itemEdges.getOrDefault(item, List.of())) sum += e.originalCapacity - e.capacity;
            if (sum > 0) used.put(item, sum);
        }
        return new Result(Map.copyOf(allocated), Map.copyOf(used));
    }

    static final class Dinic {
        static final class Edge {
            final int to, rev;
            final long originalCapacity;
            long capacity;
            Edge(int to, int rev, long capacity) { this.to=to; this.rev=rev; this.capacity=capacity; this.originalCapacity=capacity; }
        }
        final List<List<Edge>> graph;
        int[] level, it;
        Dinic(int n) { graph = new ArrayList<>(n); for(int i=0;i<n;i++) graph.add(new ArrayList<>()); level=new int[n]; it=new int[n]; }
        Edge addEdge(int from, int to, long cap) {
            Edge f = new Edge(to, graph.get(to).size(), cap);
            Edge r = new Edge(from, graph.get(from).size(), 0);
            graph.get(from).add(f); graph.get(to).add(r); return f;
        }
        long maxFlow(int s, int t) {
            long flow=0, f;
            while (bfs(s,t)) { java.util.Arrays.fill(it,0); while((f=dfs(s,t,Long.MAX_VALUE/4))>0) flow+=f; }
            return flow;
        }
        boolean bfs(int s,int t) {
            java.util.Arrays.fill(level,-1); level[s]=0; ArrayDeque<Integer> q=new ArrayDeque<>(); q.add(s);
            while(!q.isEmpty()) { int v=q.removeFirst(); for(Edge e:graph.get(v)) if(e.capacity>0 && level[e.to]<0){level[e.to]=level[v]+1;q.add(e.to);} }
            return level[t]>=0;
        }
        long dfs(int v,int t,long f) {
            if(v==t) return f;
            for(int i=it[v];i<graph.get(v).size();i=++it[v]) { Edge e=graph.get(v).get(i); if(e.capacity>0 && level[v]+1==level[e.to]) { long d=dfs(e.to,t,Math.min(f,e.capacity)); if(d>0){e.capacity-=d; graph.get(e.to).get(e.rev).capacity+=d; return d;} } }
            return 0;
        }
    }
}
