package io.kamax.mxhsd.api.session.server;

import io.kamax.mxhsd.api.event.IEvent;

import java.util.Collection;
import java.util.List;

public interface IServerEventManager {

    List<? extends IEvent> getEvents(Collection<String> ids);

}
