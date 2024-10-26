package com.renomad.minum.scheduler;

import com.renomad.minum.database.DbData;

import java.time.LocalTime;
import java.util.Objects;

import static com.renomad.minum.utils.SerializationUtils.deserializeHelper;
import static com.renomad.minum.utils.SerializationUtils.serializeHelper;

/**
 * This class represents the data that is needed to be persisted to disk
 * for the {@link Scheduler} program.
 */
public class Schedule extends DbData<Schedule> {

    public static Schedule EMPTY = new Schedule(0, 0, LocalTime.MIN);
    private long index;
    private final int scheduledItemIndex;
    private final LocalTime time;

    public Schedule(long index, int scheduledItemIndex, LocalTime time) {
        super();

        this.index = index;
        this.scheduledItemIndex = scheduledItemIndex;
        this.time = time;
    }

    @Override
    protected String serialize() {
        return serializeHelper(index, scheduledItemIndex, time);
    }

    @Override
    protected Schedule deserialize(String serializedText) {
        final var tokens = deserializeHelper(serializedText);

        return new Schedule(
                Long.parseLong(tokens.get(0)),
                Integer.parseInt(tokens.get(1)),
                LocalTime.parse(tokens.get(2)));
    }

    @Override
    protected long getIndex() {
        return index;
    }

    @Override
    protected void setIndex(long index) {
        this.index = index;
    }

    public LocalTime getTime() {
        return time;
    }

    public int getScheduledItemIndex() {
        return scheduledItemIndex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Schedule schedule = (Schedule) o;
        return index == schedule.index && scheduledItemIndex == schedule.scheduledItemIndex && Objects.equals(time, schedule.time);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, scheduledItemIndex, time);
    }

    @Override
    public String toString() {
        return "Schedule{" +
                "index=" + index +
                ", scheduledItemIndex=" + scheduledItemIndex +
                ", time=" + time +
                '}';
    }
}
