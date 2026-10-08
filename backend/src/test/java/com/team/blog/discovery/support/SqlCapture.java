package com.team.blog.discovery.support;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * 실행한 SQL 문자열을 모으는 DataSource 감싸개 (005 테스트 전용). "본문 컬럼을 읽지 않는다"처럼 SQL 내용을 봐야 할 때 저장소를 이것으로 감싼
 * {@code JdbcClient}로 직접 만든다. 개수만 볼 때는 001 {@code support.SqlCounter}를 쓴다.
 */
public final class SqlCapture extends DelegatingDataSource {

    private final List<String> statements = new CopyOnWriteArrayList<>();

    public SqlCapture(DataSource target) {
        super(target);
    }

    public List<String> statements() {
        return List.copyOf(statements);
    }

    public void clear() {
        statements.clear();
    }

    @Override
    public Connection getConnection() throws SQLException {
        return wrap(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return wrap(super.getConnection(username, password));
    }

    private Connection wrap(Connection target) {
        InvocationHandler handler =
                (proxy, method, args) -> {
                    if (("prepareStatement".equals(method.getName())
                                    || "prepareCall".equals(method.getName()))
                            && args != null
                            && args.length > 0
                            && args[0] instanceof String sql) {
                        statements.add(sql);
                    }
                    try {
                        return method.invoke(target, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                };
        return (Connection)
                Proxy.newProxyInstance(
                        SqlCapture.class.getClassLoader(),
                        new Class<?>[] {Connection.class},
                        handler);
    }
}
