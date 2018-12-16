package com.weaxme.wicket.cluster;

import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.servlet.ServletModule;
import com.hazelcast.config.Config;
import com.hazelcast.config.FileSystemXmlConfig;
import com.hazelcast.config.XmlConfigLocator;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import lombok.extern.slf4j.Slf4j;
import org.apache.wicket.guice.GuiceWebApplicationFactory;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.protocol.http.WicketFilter;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Slf4j
public class InitModule extends ServletModule {

    public static final String PROPETIES_FILE = "wicket-cluster.properties";

    @Override
    protected void configureServlets() {
        bindFilter();
        bindWicketClusterProperties();
    }

    private void bindFilter() {
        Map<String, String> params = new HashMap<String, String>();
        params.put(WicketFilter.FILTER_MAPPING_PARAM, "/*");
        params.put("applicationFactoryClassName", GuiceWebApplicationFactory.class.getName());
        params.put("injectorContextAttribute", Injector.class.getName());

        bind(WicketFilter.class).in(Singleton.class);
        filter("/*").through(WicketFilter.class, params);

        bind(WebApplication.class).to(WicketClusterApplication.class);
    }

    private void bindWicketClusterProperties() {
        Properties properties = new Properties();
        log.info("properties = {} ", properties);
        try {
            properties.load(new FileInputStream(PROPETIES_FILE));
        } catch (Exception ex) {
            log.warn("Can't load properties from file {} use system properties", PROPETIES_FILE);
        }
        properties.putAll(System.getProperties());
        Names.bindProperties(binder(), properties);
    }

    @Provides
    @Singleton
    public HazelcastInstance provideHazelcast(@Named("hazelcast.config") String configFile) {
        Config config;
        try {
            config = new FileSystemXmlConfig(configFile);
        } catch (IOException ex) {
            log.warn("Can't load Hazelcast config from file {} se default", configFile);
            config = new Config();
            config.setInstanceName("wicket-cluster-hazelcast");
        }
        return Hazelcast.getOrCreateHazelcastInstance(config);
    }
}
