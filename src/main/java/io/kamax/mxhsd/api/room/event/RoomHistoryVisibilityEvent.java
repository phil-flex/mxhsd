/*
 * mxhsd - Corporate Matrix Homeserver
 * Copyright (C) 2017 Maxime Dor
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

package io.kamax.mxhsd.api.room.event;

import io.kamax.mxhsd.api.room.RoomEventType;
import io.kamax.mxhsd.core.event.NakedContentEvent;

public class RoomHistoryVisibilityEvent extends NakedContentEvent {

    private String stateKey = "";

    public RoomHistoryVisibilityEvent(String sender, String visibility) {
        super(RoomEventType.HistoryVisibility.get(), sender);
        content.addProperty("history_visibility", visibility); // FIXME use enum
    }

}
