package it.necst.android.reservator.model.platformcalendar;

import it.necst.android.reservator.model.Room;

public class PlatformCalendarRoom extends Room {
    private static final long serialVersionUID = 1L;
    private long id;
    private String location;

    public PlatformCalendarRoom(String name, String email, long id, String location) {
        super(name, email);
        this.id = id;
        this.location = location;
    }

    public long getId() {
        return id;
    }

    public boolean equals(Room room) {
        if (room instanceof PlatformCalendarRoom && ((PlatformCalendarRoom) room).getId() == id) {
            return true;
        }
        return false;
    }

    public String getLocation() {
        return this.location;
    }
}
