package org.dkf.jed2k.kad;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.dkf.jed2k.Time;
import org.dkf.jed2k.exception.JED2KException;
import org.dkf.jed2k.kad.traversal.algorithm.Bootstrap;
import org.dkf.jed2k.kad.traversal.algorithm.Direct;
import org.dkf.jed2k.kad.traversal.algorithm.Refresh;
import org.dkf.jed2k.kad.traversal.algorithm.Traversal;
import org.dkf.jed2k.kad.traversal.observer.NullObserver;
import org.dkf.jed2k.kad.traversal.observer.Observer;
import org.dkf.jed2k.protocol.Endpoint;
import org.dkf.jed2k.protocol.Hash;
import org.dkf.jed2k.protocol.kad.Kad2Ping;
import org.dkf.jed2k.protocol.kad.KadId;
import org.dkf.jed2k.protocol.kad.Transaction;
import org.dkf.jed2k.util.EndpointSerializer;
import org.dkf.jed2k.util.HashSerializer;
import org.dkf.jed2k.util.KadIdSerializer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by inkpot on 22.11.2016.
 */
@Slf4j
@Getter
public class NodeImpl {

    private final static int SEARCH_BRANCHING = 5;
    private final static int BUCKET_SIZE = 10;
    private final RpcManager rpc;
    private DhtTracker tracker = null;
    private RoutingTable table = null;
    private Set<Traversal> runningRequests = new HashSet<>();
    private final KadId self;
    private int port;

    public NodeImpl(final DhtTracker tracker, final KadId id, int port) {
        this.tracker = tracker;
        this.rpc = new RpcManager();
        this.self = id;
        this.table = new RoutingTable(id, BUCKET_SIZE);
        this.port = port;
    }

    public void addNode(final Endpoint ep, final KadId id) {
        invoke(new Kad2Ping(), ep, new NullObserver(new Direct(this, id), ep, id));
    }

    public void addTraversalAlgorithm(final Traversal ta) {
        assert !runningRequests.contains(ta);
        runningRequests.add(ta);
    }

    public void removeTraversalAlgorithm(final Traversal ta) {
        assert runningRequests.contains(ta);
        runningRequests.remove(ta);
    }

    public RoutingTable getTable() {
        return table;
    }

    public void tick() {
        rpc.tick();
        KadId target = table.needRefresh();
        if (target != null) refresh(target);
    }

    public void searchSources(final KadId id) {
        log.debug("[node] search sources {}", id);
    }

    public void searchKeywords(final KadId id) {
        log.debug("[node] search keywords {}", id);
    }

    // not available now
    public void searchNotes(final KadId id) {
        log.debug("[node] search notes {}", id);
    }

    public void refresh(final KadId id) {
        assert id != null;
        log.debug("[node] refresh on target {}", id);
        Traversal t = new Refresh(this, self);
        t.start();
    }

    public void abort() {
        tracker = null;
    }

    public void bootstrap(final List<Endpoint> nodes) {
        log.debug("[node] bootstrap with {} nodes", nodes.size());
        Traversal t = new Bootstrap(this, self);
        for(Endpoint ep: nodes) {
            t.addEntry(new KadId(), ep, Observer.FLAG_INITIAL);
        }

        t.start();
    }

    public int getSearchBranching() {
        return SEARCH_BRANCHING;
    }

    public void incoming(final Transaction t, final Endpoint ep) {
        Observer o = rpc.incoming(t, ep);

        if (o != null) {
            o.reply(t, ep);
            // if we have endpoint's KAD id in packet - use it
            // else use KAD id from observer
            Traversal ta = o.getAlgorithm();
            assert ta != null;
            // for all real traversers update routing table
            if (ta.containsNewNodes()) {
                KadId originId = t.getSelfId().equals(new KadId()) ? o.getId() : t.getSelfId();
                table.nodeSeen(originId, ep);
            }
        }
    }

    public boolean invoke(final Transaction t, final Endpoint ep, final Observer o) {
        try {
            if (tracker.write(t, ep.toInetSocketAddress())) {
                // register transaction if packet was sent
                rpc.invoke(o);
                o.setWasSent(true);
                o.setFlags(o.getFlags() | Observer.FLAG_QUERIED);
                o.setSentTime(Time.currentTime());
                log.debug("[node] invoked {}", o);
                return true;
            } else {
                log.debug("[node] invoke failed without error {}", o);
            }
        } catch(final JED2KException e) {
            log.error("[node] invoke failed {} with error {}", o, e);
        }

        return false;
    }

    public void logStatus() {
        for(Traversal ta: runningRequests) {
            log.info(ta.toString());
        }
    }

    public String getRoutingTableStatus() {
        Gson gson = new GsonBuilder().setPrettyPrinting()
                .registerTypeAdapter(KadId.class, new KadIdSerializer())
                .registerTypeAdapter(Hash.class, new HashSerializer())
                .registerTypeAdapter(Endpoint.class, new EndpointSerializer())
                .create();

        return gson.toJson(table);
    }
}
