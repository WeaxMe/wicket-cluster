package com.weaxme.wicket.cluster.web;

import com.weaxme.wicket.cluster.component.CounterPanel;
import de.agilecoders.wicket.webjars.request.resource.WebjarsCssResourceReference;
import de.agilecoders.wicket.webjars.request.resource.WebjarsJavaScriptResourceReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.model.IModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.request.resource.CssResourceReference;
import org.apache.wicket.request.resource.JavaScriptResourceReference;

@Slf4j
public class HomePage extends WebPage {

    public static final CssResourceReference BOOTSTRAP_CSS        = new WebjarsCssResourceReference("bootstrap/current/css/bootstrap.min.css");
    public static final JavaScriptResourceReference BOOTSTRAP_JS = new WebjarsJavaScriptResourceReference("bootstrap/current/js/bootstrap.bundle.min.js");

    public HomePage() {
        super();
    }

    public HomePage(IModel<?> model) {
        super(model);
    }

    public HomePage(PageParameters parameters) {
        super(parameters);
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();
        add(new CounterPanel("counterPanel"));
    }

    @Override
    public void renderHead(IHeaderResponse response) {
        response.render(CssHeaderItem.forReference(BOOTSTRAP_CSS));
        response.render(JavaScriptHeaderItem.forReference(BOOTSTRAP_JS));

        super.renderHead(response);
    }
}
