package ru.sbtqa.tag.api.context;

import ru.sbtqa.tag.api.EndpointEntry;

public class EndpointContext {

    private static final ThreadLocal<String> currentEndpointTitle = new ThreadLocal<>();
    private static final ThreadLocal<EndpointEntry> currentEndpoint = new ThreadLocal<>();

    private EndpointContext() {
    }

    public static String getCurrentEndpointTitle() {
        return currentEndpointTitle.get();
    }

    private static void setCurrentEndpointTitle(String currentEndpointTitle) {
        EndpointContext.currentEndpointTitle.set(currentEndpointTitle);
    }

    public static EndpointEntry getCurrentEndpoint() {
        return currentEndpoint.get();
    }

    public static void setCurrentEndpoint(EndpointEntry currentEndpoint) {
        EndpointContext.currentEndpoint.set(currentEndpoint);
        EndpointContext.setCurrentEndpointTitle(currentEndpoint.getTitle());
    }

    public static void clear() {
        EndpointContext.currentEndpoint.remove();
        EndpointContext.currentEndpointTitle.remove();
    }
}
