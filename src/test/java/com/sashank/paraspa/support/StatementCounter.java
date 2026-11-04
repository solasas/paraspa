package com.sashank.paraspa.support;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Counts every SQL statement that reaches the database, whoever sends it (Hibernate or JdbcTemplate), by wrapping
 * the DataSource. Used to prove that a page of posts costs a fixed number of statements however many posts it holds.
 */
public class StatementCounter {

    private final List<String> statements = Collections.synchronizedList(new ArrayList<>());

    public void reset() {
        statements.clear();
    }

    public int count() {
        return statements.size();
    }

    public List<String> statements() {
        return new ArrayList<>(statements);
    }

    /** Wraps a DataSource so its connections report every prepared statement to this counter. */
    public DataSource wrap(DataSource target) {
        InvocationHandler dataSourceHandler = (proxy, method, args) -> {
            Object result = invoke(method, target, args);
            if (method.getName().equals("getConnection") && result instanceof Connection connection) {
                return wrapConnection(connection);
            }
            return result;
        };
        return (DataSource) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{DataSource.class}, dataSourceHandler);
    }

    private Connection wrapConnection(Connection target) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getName().equals("prepareStatement") && args != null && args.length > 0 && args[0] instanceof String sql) {
                statements.add(sql);
            }
            return invoke(method, target, args);
        };
        return (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{Connection.class}, handler);
    }

    private static Object invoke(java.lang.reflect.Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
