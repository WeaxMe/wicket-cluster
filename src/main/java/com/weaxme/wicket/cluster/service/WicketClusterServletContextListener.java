package com.weaxme.wicket.cluster.service;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.servlet.GuiceServletContextListener;
import com.weaxme.wicket.cluster.InitModule;

public class WicketClusterServletContextListener extends GuiceServletContextListener {

    private Injector injector;

    @Override
    protected Injector getInjector() {
        return injector != null ? injector : (injector = Guice.createInjector(new InitModule()));
    }
}
