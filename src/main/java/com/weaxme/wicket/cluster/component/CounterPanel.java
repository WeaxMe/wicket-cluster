package com.weaxme.wicket.cluster.component;

import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IAtomicLong;
import com.hazelcast.core.IMap;
import com.weaxme.wicket.cluster.WicketClusterApplication;
import lombok.extern.slf4j.Slf4j;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.request.Request;

import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class CounterPanel extends Panel {

    public static final String COUNTER_NAME = "counter";

    @Inject
    @Named("node.name")
    private String node;


    public CounterPanel(String id) {
        super(id);
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();
        add(new Label("host", Model.of(node)));
        add(createCounterLabel("counter"));
        add(createIncreaseLink("increaseCounter"));
        add(createDecreaseLink("decreaseCounter"));
        setOutputMarkupPlaceholderTag(true);
    }

    private Label createCounterLabel(String id) {
        return new Label(id, Model.of()) {
            @Override
            protected void onConfigure() {
                super.onConfigure();
                IAtomicLong counter = getCounter();
//                log.info("counter: {}", counter.get());
                setDefaultModelObject(counter.get());
            }

            @Override
            protected void onInitialize() {
                super.onInitialize();
                setOutputMarkupPlaceholderTag(true);
            }
        };
    }

    public AjaxLink<Void> createIncreaseLink(String id) {
        return new AjaxLink<Void>(id) {
            @Override
            public void onClick(AjaxRequestTarget target) {
                IAtomicLong counter = getCounter();
                counter.alter(l -> l + 1);
                target.add(CounterPanel.this);
            }
        };
    }

    public AjaxLink<Void> createDecreaseLink(String id) {
        return new AjaxLink<Void>(id) {
            @Override
            public void onClick(AjaxRequestTarget target) {
                IAtomicLong counter = getCounter();
                if (counter.get() > 0) counter.alter(l -> l - 1);
                target.add(CounterPanel.this);
            }
        };
    }


    private IAtomicLong getCounter() {
        WicketClusterApplication app = (WicketClusterApplication) WicketClusterApplication.get();
        return app.getHazelcast().getAtomicLong(COUNTER_NAME);
    }
}
