package com.weaxme.wicket.cluster.session;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.wicket.Application;
import org.apache.wicket.Session;
import org.apache.wicket.protocol.http.IRequestLogger;
import org.apache.wicket.request.Request;
import org.apache.wicket.session.ISessionStore;

import javax.servlet.http.*;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class HazelcastSessionStore2 implements ISessionStore {

    public static final String STORE_NAME = "wicket-sessions";

    public static final String ID_PREFIX        = "jsessionid-";

    public static final String JSESSIONID = "JSESSIONID";

    public static final String GRAPH = "session-graph";

    private final Set<UnboundListener> unboundListeners = new CopyOnWriteArraySet<UnboundListener>();
    private final Set<BindListener> bindListeners = new CopyOnWriteArraySet<BindListener>();


    private final IMap<String, Serializable> store;
    private final String node;


    public HazelcastSessionStore2(HazelcastInstance hazelcast, String node) {
        this.store = hazelcast.getMap(STORE_NAME);
        this.node = node;
        store.put(GRAPH, new Graph());
    }


    @Override
    public void bind(Request request, Session newSession)
    {
        if (getAttribute(request, Session.SESSION_ATTRIBUTE_NAME) != newSession)
        {
            // call template method
            onBind(request, newSession);
            for (BindListener listener : getBindListeners())
            {
                listener.bindingSession(request, newSession);
            }

            HttpSession httpSession = getHttpSession(request, false);

            if (httpSession != null)
            {
                // register an unbinding listener for cleaning up
                String applicationKey = Application.get().getName();
                httpSession.setAttribute("Wicket:SessionUnbindingListener-" + applicationKey,
                        new SessionBindingListener(applicationKey, newSession));
            }
            // register the session object itself
            setAttribute(request, Session.SESSION_ATTRIBUTE_NAME, newSession);
        }
    }

    protected void onBind(final Request request, final Session newSession)
    {
    }
    protected void onUnbind(final String sessionId)
    {
    }

    @Override
    public void destroy() {
    }

    @Override
    public Serializable getAttribute(Request request, String name) {
        String key = getKey(request, name);
        Serializable value = null;

        if (key != null) {
            value = store.get(key);
        }
        return value;
    }


    @Override
    public List<String> getAttributeNames(Request request) {
        List<String> attributeNames = store.keySet().stream()
                .map(key -> key.substring(key.indexOf("-") + 1))
                .collect(Collectors.toList());
        log.info("Attribute names: {}", attributeNames);
        return attributeNames;
    }

    @Override
    public String getSessionId(Request request, boolean create) {
        HttpSession httpSession = getHttpSession(request, false);
        String id = null;
        if (httpSession != null) {
            id = httpSession.getId();
        }
        if (id == null) {
            String jsessionid = getJsessionId(request);

            if (jsessionid != null && store.containsKey(ID_PREFIX + jsessionid)) {
                Graph graph = (Graph) store.get(GRAPH);
                SessionVertex vertex = findVertex(jsessionid, node, graph);
                log.info("Vertex");
                printVertex(vertex);
                log.info("Graph before update");
                printGraph(graph);


                if (vertex != null) {
                    id = vertex.id;
                } else {
                    httpSession = getHttpSession(request, true);
                    id = httpSession.getId();


                    log.info("New Http id {} for jsessionid {}", id, jsessionid);

                    createVertex(id, jsessionid, node, graph);

                    log.info("Graph after update");
                    printGraph(graph);

                    store.put(ID_PREFIX + id, store.get(ID_PREFIX + jsessionid));
                    store.put(GRAPH, graph);
                    log.info("Store state after update {}", ID_PREFIX + id);
                    printStore();
                }
            } else if (create) {
                httpSession = getHttpSession(request, true);
                id = httpSession.getId();

                log.info("New SessionId: " + id);
                IRequestLogger logger = Application.get().getRequestLogger();
                if (logger != null) {
                    logger.sessionCreated(id);
                }
                Graph graph = (Graph) store.get(GRAPH);
                createVertex(id, id, node, graph);
                store.put(ID_PREFIX + id, id);
                store.put(GRAPH, graph);
                log.info("Store state after update {}", ID_PREFIX + id);
                printGraph(graph);
                printStore();
            }
        }


        return id;
    }

    private String getJsessionId(Request request) {
        String jsessionid = null;
        HttpServletRequest servletRequest = getHttpServletRequest(request);
        Cookie[] cookies = servletRequest.getCookies();
        if (cookies != null && cookies.length > 0) {
            jsessionid = Stream.of(cookies)
                    .filter(c -> c.getName().equals(JSESSIONID))
                    .map(Cookie::getValue)
                    .filter(Objects::nonNull)
                    .map(value -> value.split("\\.")[0])
                    .findFirst()
                    .orElse(null);
            if (jsessionid != null) log.info("jsessionid from cookies {}", jsessionid);
        }
        if (jsessionid == null) {
            String[] split = servletRequest.getRequestURI().split(";");
            if (split.length > 1 && split[1].contains("jsessionid=")) {
                //session exists, first check if it's already mapped:
                jsessionid = split[1].replace("jsessionid=", "");
                if (jsessionid.contains(".")) {
                    jsessionid = jsessionid.split("\\.")[0];
                }
                if (jsessionid != null) log.info("jsessionid from url {}", jsessionid);
            }
        }
        return jsessionid;
    }

    final HttpSession getHttpSession(final Request request, final boolean create) {
        return getHttpServletRequest(request).getSession(create);
    }

    protected final HttpServletRequest getHttpServletRequest(final Request request) {
        Object containerRequest = request.getContainerRequest();
        if (containerRequest instanceof HttpServletRequest) {
            return (HttpServletRequest)containerRequest;
        }
        throw new IllegalArgumentException("Request must be ServletWebRequest");
    }

    @Override
    public void invalidate(Request request) {
        HttpSession httpSession = getHttpSession(request, false);
        if (httpSession != null) {
            // tell the app server the session is no longer valid
            httpSession.invalidate();
        }
    }

    @Override
    public Session lookup(Request request) {
        String sessionId = getSessionId(request, false);
        if (sessionId != null) {
            return (Session)getAttribute(request, Session.SESSION_ATTRIBUTE_NAME);
        }
        return null;
    }

    @Override
    public void registerUnboundListener(UnboundListener listener)
    {
        unboundListeners.add(listener);
    }

    @Override
    public void removeAttribute(Request request, String name) {
        String key = getKey(request, name);
        if(key != null) {
            store.remove(key);
        }
    }

    @Override
    public final Set<UnboundListener> getUnboundListener()
    {
        return Collections.unmodifiableSet(unboundListeners);
    }

    @Override
    public void setAttribute(Request request, String name, Serializable value) {
        String key = getKey(request, name);
        if(key != null) {
            store.put(key, value);
            log.info("Store state after set attribute {}", key);
            printStore();
        } else {
            log.info("Key {} doesn't exists. name = {}, value = {}, jsessionId = {}", key, name, value, getJsessionId(request));
        }
    }

    @Override
    public void unregisterUnboundListener(UnboundListener listener)
    {
        unboundListeners.remove(listener);
    }

    @Override
    public void registerBindListener(BindListener listener)
    {
        bindListeners.add(listener);
    }

    @Override
    public void unregisterBindListener(BindListener listener)
    {
        bindListeners.remove(listener);
    }

    @Override
    public Set<BindListener> getBindListeners()
    {
        return Collections.unmodifiableSet(bindListeners);
    }

    @Override
    public void flushSession(Request request, Session session)
    {
        if (getAttribute(request, Session.SESSION_ATTRIBUTE_NAME) != session)
        {
            // this session is not yet bound, bind it
            bind(request, session);
        }
        else
        {
            setAttribute(request, Session.SESSION_ATTRIBUTE_NAME, session);
        }
    }

    private String getKey(Request request, String name) {
        String sessionId = getSessionId(request, false);

        if (sessionId != null) {
            String masterId = (String) store.get(ID_PREFIX + sessionId);
            return masterId + "-" + name;
        }
        return null;
    }

    private void printStore() {
        log.info("");
        store.forEach((k, v) -> log.info("{} -> {}", k, v));
        log.info("");
    }

    private void printGraph(Graph graph) {
        graph.getVertices().forEach(this::printVertex);
    }

    private void printVertex(SessionVertex vertex) {
        if (vertex == null) {
            log.info("Given vertex is null");
            return;
        }
        String outEdges = vertex.getEdges()
                .stream()
                .filter(edge -> edge.out.equals(vertex.id))
                .map(edge -> edge.out + " -> " + edge.in)
                .reduce((acc, edge) -> acc + ", " + edge)
                .orElse(null);
        String inEdges = vertex.getEdges()
                .stream()
                .filter(edge -> !edge.out.equals(vertex.id) || (edge.out.equals(vertex.id) && edge.in.equals(vertex.id)))
                .map(edge -> edge.in + " <- " + edge.out)
                .reduce((acc, edge) -> acc + ", " + edge)
                .orElse(null);

        log.info("id = {}, node = {}, in edges = {}, out edges = {}", vertex.getId(), vertex.getNode(), inEdges, outEdges);
    }

    private SessionVertex createVertex(String id, String creatorId, String node, Graph graph) {
        SessionEdge edge = new SessionEdge(creatorId, id);
        SessionVertex vertex = new SessionVertex(id, node, Collections.emptyList());
        graph.addVertex(vertex);
        graph.addEdge(edge);
        return vertex;
    }

    private SessionVertex findVertex(String inputId, String currentNode, Graph graph) {
        Set<SessionVertex> vertices = graph.getVertices();

        SessionVertex vertex = vertices.stream()
                .filter(v -> v.id.equals(inputId))
                .findFirst().orElse(null);

        SessionVertex result = null;

        if (vertex != null && vertex.getId().equals(inputId) && vertex.getNode().equals(currentNode)) {
            result = vertex;
        }

        if (vertex != null && result == null) {
            Set<SessionVertex> visited = new HashSet<>();
            List<SessionVertex> queue = new LinkedList<>(convertToVerticles(vertex.getEdges(), graph));

            log.info("");

            while (queue.size() > 0) {
                SessionVertex v = queue.remove(0);
                if (!visited.contains(v)) {
                    if (v.node.equals(currentNode)) {
                        result = v;
                        break;
                    }
                    visited.add(v);
                    queue.addAll(convertToVerticles(v.getEdges(), graph));
                }
            }
        }
        return result;
    }

    private List<SessionVertex> convertToVerticles(List<SessionEdge> edges, Graph graph) {
        return edges.stream()
                .flatMap(edge -> Stream.of(graph.getVertex(edge.in), graph.getVertex(edge.out)))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Value
    private static class Graph implements Serializable {
        private Set<SessionVertex> vertices;

        public Graph() {
            this.vertices = new HashSet<>();
        }

        public synchronized void addEdge(SessionEdge edge) {
            SessionVertex in = getVertexById(edge.in).orElse(null);
            SessionVertex out = getVertexById(edge.out).orElse(null);

            if (in != null && out != null) {
                removeVertex(in);
                removeVertex(out);

                List<SessionEdge> edges = new LinkedList<>(in.getEdges());
                edges.add(edge);
                addVertex(new SessionVertex(in.id, in.node, edges));

                edges = new LinkedList<>(out.getEdges());
                edges.add(edge);

                addVertex(new SessionVertex(out.id, out.node, edges));
            }
        }

        public Optional<SessionVertex> getVertexById(String id) {
            return vertices.stream()
                    .filter(v -> v.getId().equals(id))
                    .findFirst();
        }

        public SessionVertex getVertex(String id) {
            return getVertexById(id).orElse(null);
        }

        public Set<SessionVertex> getVertices() {
            return Collections.unmodifiableSet(vertices);
        }

        public synchronized void addVertex(SessionVertex vertex) {
            vertices.add(vertex);
        }

        public synchronized void removeVertex(SessionVertex vertex) {
            vertices.remove(vertex);
        }

        public synchronized void clear() {
            vertices.clear();
        }
    }

    @Value
    private static class SessionVertex implements Serializable {
        private String id;
        private String node;
        private List<SessionEdge> edges;

        public List<SessionEdge> getEdges() {
            return Collections.unmodifiableList(edges);
        }
    }

    @Value
    private static class SessionEdge implements Serializable {
        private String out;
        private String in;
    }

    /**
     * Reacts on unbinding from the session by cleaning up the session related data.
     */
    protected static final class SessionBindingListener
            implements
            HttpSessionBindingListener,
            Serializable
    {
        private static final long serialVersionUID = 1L;

        /** The unique key of the application within this web application. */
        private final String applicationKey;

        /**
         * The Wicket Session associated with the expiring HttpSession
         */
        private final Session wicketSession;

        /**
         * Constructor.
         *
         * @param applicationKey
         *          The unique key of the application within this web application
         * @deprecated Use #SessionBindingListener(String, Session) instead
         */
        @Deprecated
        public SessionBindingListener(final String applicationKey)
        {
            this(applicationKey, Session.get());
        }

        /**
         * Construct.
         *
         * @param applicationKey
         *            The unique key of the application within this web application
         * @param wicketSession
         *            The Wicket Session associated with the expiring http session
         */
        public SessionBindingListener(final String applicationKey, final Session wicketSession)
        {
            this.applicationKey = applicationKey;
            this.wicketSession = wicketSession;
        }

        /**
         * @see javax.servlet.http.HttpSessionBindingListener#valueBound(javax.servlet.http.HttpSessionBindingEvent)
         */
        @Override
        public void valueBound(final HttpSessionBindingEvent evg)
        {
        }

        /**
         * @see javax.servlet.http.HttpSessionBindingListener#valueUnbound(javax.servlet.http.HttpSessionBindingEvent)
         */
        @Override
        public void valueUnbound(final HttpSessionBindingEvent evt)
        {
            String sessionId = evt.getSession().getId();

            log.debug("Session unbound: {}", sessionId);

            if (wicketSession != null)
            {
                wicketSession.onInvalidate();
            }

            Application application = Application.get(applicationKey);
            if (application == null)
            {
                log.debug("Wicket application with name '{}' not found.", applicationKey);
                return;
            }

            ISessionStore sessionStore = application.getSessionStore();
            if (sessionStore != null)
            {
                if (sessionStore instanceof HazelcastSessionStore2)
                {
                    ((HazelcastSessionStore2) sessionStore).onUnbind(sessionId);
                }

                for (UnboundListener listener : sessionStore.getUnboundListener())
                {
                    listener.sessionUnbound(sessionId);
                }
            }
        }
    }
}
