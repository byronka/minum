package database;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.util.logging.Logger;

/**
 * This class only exists because we want no nulls in our
 * system.  In order to do this, we have to be able to create
 * "empty" versions of our classes.  To create an empty String, for
 * example, is simply "".  But an empty DataSource would look like this.
 */
class EmptyDataSource implements DataSource {
    @Override
    public Connection getConnection() {
        throw new NotImplementedException();
    }

    @Override
    public Connection getConnection(String username, String password) {
        throw new NotImplementedException();
    }

    @Override
    public <T> T unwrap(Class<T> iface) {
        throw new NotImplementedException();
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        throw new NotImplementedException();
    }

    @Override
    public PrintWriter getLogWriter() {
        throw new NotImplementedException();
    }

    @Override
    public void setLogWriter(PrintWriter out) {
        throw new NotImplementedException();
    }

    @Override
    public void setLoginTimeout(int seconds) {
        throw new NotImplementedException();
    }

    @Override
    public int getLoginTimeout() {
        throw new NotImplementedException();
    }

    @Override
    public Logger getParentLogger() {
        throw new NotImplementedException();
    }

}
