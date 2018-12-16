package com.weaxme.wicket.cluster;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Named;
import com.hazelcast.core.HazelcastInstance;
import com.weaxme.wicket.cluster.pageStore.HazelcastDataStore;
import com.weaxme.wicket.cluster.session.HazelcastSessionStore2;
import com.weaxme.wicket.cluster.web.HomePage;
import de.agilecoders.wicket.webjars.WicketWebjars;
import de.agilecoders.wicket.webjars.request.resource.WebjarsJavaScriptResourceReference;
import org.apache.wicket.DefaultPageManagerProvider;
import org.apache.wicket.Page;
import org.apache.wicket.guice.GuiceInjectorHolder;
import org.apache.wicket.pageStore.IDataStore;
import org.apache.wicket.protocol.http.WebApplication;

public class WicketClusterApplication extends WebApplication {

    @Inject
    private HazelcastInstance hazelcast;

    @Inject
    @Named("node.name")
    private String node;

    @Override
    public Class<? extends Page> getHomePage() {
        return HomePage.class;
    }


    @Override
    protected void init() {
        super.init();
        WicketWebjars.install(this);
        getJavaScriptLibrarySettings().setJQueryReference(new WebjarsJavaScriptResourceReference("jquery/current/jquery.min.js"));

        mountPage("/", HomePage.class);

        setPageManagerProvider(new DefaultPageManagerProvider(this) {
            @Override
            protected IDataStore newDataStore() {
                return new HazelcastDataStore(hazelcast);
            }
        });
        setSessionStoreProvider(() -> new HazelcastSessionStore2(hazelcast, node));
    }

    public Injector getInjector() {
        return getMetaData(GuiceInjectorHolder.INJECTOR_KEY).getInjector();
    }

    public HazelcastInstance getHazelcast() {
        return hazelcast;
    }
}
