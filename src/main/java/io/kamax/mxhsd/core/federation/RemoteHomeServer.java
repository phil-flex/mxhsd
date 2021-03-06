/*
 * mxhsd - Corporate Matrix Homeserver
 * Copyright (C) 2017 Kamax Sarl
 *
 * https://www.kamax.io/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.kamax.mxhsd.core.federation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.kamax.matrix._MatrixID;
import io.kamax.mxhsd.GsonUtil;
import io.kamax.mxhsd.api.event.IEvent;
import io.kamax.mxhsd.api.event.INakedEvent;
import io.kamax.mxhsd.api.federation.FederationException;
import io.kamax.mxhsd.api.federation.IRemoteHomeServer;
import io.kamax.mxhsd.api.federation.ITransaction;
import io.kamax.mxhsd.api.room.IRoomStateSnapshot;
import io.kamax.mxhsd.api.room.IRoomStateSnapshotIds;
import io.kamax.mxhsd.api.room.directory.IFederatedRoomAliasLookup;
import io.kamax.mxhsd.core.GlobalStateHolder;
import io.kamax.mxhsd.core.event.Event;
import io.kamax.mxhsd.core.room.RoomStateSnapshot;
import io.kamax.mxhsd.core.room.RoomStateSnapshotIds;
import io.kamax.mxhsd.core.room.directory.FederatedRoomAliasLookup;
import io.kamax.mxhsd.spring.federation.controller.v1.transaction.TransactionJson;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class RemoteHomeServer implements IRemoteHomeServer {

    private final Logger log = LoggerFactory.getLogger(RemoteHomeServer.class);

    private GlobalStateHolder global;
    private String domain;
    private volatile Instant nextRetry;

    private <T> T withHealthCheck(Supplier<T> r) {
        if (nextRetry.isAfter(Instant.now())) {
            throw new RuntimeException("Host is not available at this time");
        }

        try {
            T v = r.get();
            setAvailable();
            return v;
        } catch (FederationException e) {
            throw e;
        } catch (Throwable t) {
            nextRetry = Instant.now().plus(1, ChronoUnit.MINUTES); // FIXME make it smart
            throw t;
        }
    }

    public RemoteHomeServer(GlobalStateHolder global, String domain) {
        this.global = global;
        this.domain = domain;
        setAvailable();
    }

    @Override
    public String getDomain() {
        return domain;
    }

    @Override
    public Instant lastAvailable() {
        return nextRetry;
    }

    @Override
    public void setAvailable() {
        nextRetry = Instant.EPOCH;
    }

    @Override
    public String getImplementationName() {
        throw new NotImplementedException("");
    }

    @Override
    public String getImplementationVersion() {
        throw new NotImplementedException("");
    }

    @Override
    public Optional<IFederatedRoomAliasLookup> lookup(String roomAlias) {
        Map<String, String> parms = new HashMap<>();
        parms.put("room_alias", roomAlias);
        JsonObject obj = global.getFedClient().query(domain, "directory", parms);
        String roomId = GsonUtil.getOrThrow(obj, "room_id");
        List<String> servers = GsonUtil.asList(GsonUtil.getArrayOrThrow(obj, "servers"), String.class);
        return Optional.of(new FederatedRoomAliasLookup(domain, roomId, roomAlias, servers));
    }

    @Override
    public JsonObject makeJoin(String roomId, _MatrixID joiner) {
        return withHealthCheck(() -> global.getFedClient().makeJoin(domain, roomId, joiner));
    }

    @Override
    public JsonObject sendJoin(IEvent ev) {
        return withHealthCheck(() -> global.getFedClient().sendJoin(domain, ev));
    }

    @Override
    public void pushTransaction(ITransaction t) {
        try {
            TransactionJson json = new TransactionJson();
            json.setOrigin(t.getOrigin());
            json.setOriginServerTs(t.getOriginTimestamp().toEpochMilli());
            json.setPdus(t.getPdus().stream().map(INakedEvent::getJson).collect(Collectors.toList()));
            JsonObject answer = global.getFedClient().sendTransaction(domain, t.getId(), GsonUtil.makeObj(json));
            log.info("HS {} response: {}", domain, GsonUtil.getPrettyForLog(answer));
        } catch (RuntimeException e) {
            Throwable tr = e;
            if (Objects.nonNull(e.getCause()) && e.getCause() instanceof IOException) {
                tr = e.getCause();
            }
            log.warn("Outbound transaction {} failed: {}", t.getId(), tr.getMessage());
        }
    }

    @Override
    public JsonObject send(String method, String path, Map<String, String> parameters, JsonElement payload) {
        return withHealthCheck(() -> global.getFedClient().send(domain, method, path, parameters, payload));
    }

    @Override
    public IRoomStateSnapshot getState(String roomId, String eventId) {
        return withHealthCheck(() -> {
            List<IEvent> authChain = new ArrayList<>();
            List<IEvent> pdus = new ArrayList<>();

            JsonObject obj = global.getFedClient().getRoomState(domain, roomId, eventId);
            GsonUtil.findArray(obj, "auth_chain").orElseGet(JsonArray::new).forEach(el -> authChain.add(new Event(el.getAsJsonObject())));
            GsonUtil.getArrayOrThrow(obj, "pdus").forEach(el -> pdus.add(new Event(el.getAsJsonObject())));

            return new RoomStateSnapshot(authChain, pdus);
        });
    }

    @Override
    public IRoomStateSnapshotIds getStateIds(String roomId, String eventId) {
        return withHealthCheck(() -> {
            JsonObject stateIds = global.getFedClient().getRoomStateIds(domain, roomId, eventId);
            List<String> chainIds = GsonUtil.asList(GsonUtil.findArray(stateIds, "auth_chain_ids").orElseGet(JsonArray::new), String.class);
            List<String> pduIds = GsonUtil.asList(GsonUtil.findArray(stateIds, "pdu_ids").orElseGet(JsonArray::new), String.class);
            return new RoomStateSnapshotIds(pduIds, chainIds);
        });
    }

}
