package com.blanoir.accessory.module.inventory.ui;

import java.util.*;

/** Request identity prevents delayed loads from reopening a replaced viewer/owner session. */
public final class MenuRequests {
    public static final class Request {
        private final UUID viewer, owner;
        private Request(UUID viewer, UUID owner) { this.viewer = viewer; this.owner = owner; }
    }
    private final Map<UUID, Request> viewers = new HashMap<>(), owners = new HashMap<>();
    public Request begin(UUID viewer, UUID owner) {
        cancelViewer(viewer); cancelOwner(owner);
        Request request = new Request(viewer, owner);
        viewers.put(viewer, request); owners.put(owner, request); return request;
    }
    public boolean complete(Request request) {
        boolean viewer = viewers.remove(request.viewer, request);
        boolean owner = owners.remove(request.owner, request);
        return viewer && owner;
    }
    public void cancelViewer(UUID viewer) { Request request = viewers.get(viewer); if (request != null) complete(request); }
    public void cancelOwner(UUID owner) { Request request = owners.get(owner); if (request != null) complete(request); }
    public void clear() { viewers.clear(); owners.clear(); }
    public int pendingCount() { return viewers.size(); }
}
