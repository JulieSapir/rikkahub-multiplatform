package androidx.sqlite.db;

import java.io.Closeable;
import java.util.List;

/** Detached query results with Android Cursor's nullable Java string contract. */
public final class SQLiteCursor implements Closeable {
    private final List<? extends List<?>> rows;
    private int position = -1;

    public SQLiteCursor(List<? extends List<?>> rows) {
        this.rows = rows;
    }

    public boolean moveToFirst() {
        position = 0;
        return !rows.isEmpty();
    }

    public boolean moveToNext() {
        return ++position < rows.size();
    }

    public String getString(int index) {
        Object value = rows.get(position).get(index);
        return value == null ? null : value.toString();
    }

    public long getLong(int index) {
        Object value = rows.get(position).get(index);
        return value == null ? 0 : ((Number) value).longValue();
    }

    public int getInt(int index) {
        return (int) getLong(index);
    }

    @Override
    public void close() {}
}
