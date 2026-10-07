package com.team.blog.support;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * 현재 스레드가 준비한 SQL 문 수를 센다("판정은 SQL 1번" 같은 확인용).
 *
 * <pre>{@code
 * try (SqlCounter.Scope scope = SqlCounter.start()) {
 *     service.call();
 *     assertThat(scope.count()).isEqualTo(1);
 * }
 * }</pre>
 *
 * <p>{@link #dataSourceWrapper()}가 테스트 컨텍스트의 {@link DataSource}를 감싸 {@code prepareStatement}·{@code
 * prepareCall}·{@code createStatement} 호출을 센다.
 */
public final class SqlCounter {

    private static final ThreadLocal<int[]> COUNTER = new ThreadLocal<>();
    private static final Set<String> STATEMENT_METHODS =
            Set.of("prepareStatement", "prepareCall", "createStatement");

    private SqlCounter() {}

    public static Scope start() {
        int[] holder = new int[1];
        COUNTER.set(holder);
        return new Scope(holder);
    }

    public static final class Scope implements AutoCloseable {
        private final int[] holder;

        private Scope(int[] holder) {
            this.holder = holder;
        }

        public int count() {
            return holder[0];
        }

        @Override
        public void close() {
            COUNTER.remove();
        }
    }

    static BeanPostProcessor dataSourceWrapper() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSource ds && !(bean instanceof CountingDataSource)) {
                    return new CountingDataSource(ds);
                }
                return bean;
            }
        };
    }

    static final class CountingDataSource extends DelegatingDataSource {
        CountingDataSource(DataSource target) {
            super(target);
        }

        @Override
        public Connection getConnection() throws java.sql.SQLException {
            return wrap(super.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password)
                throws java.sql.SQLException {
            return wrap(super.getConnection(username, password));
        }

        private static Connection wrap(Connection target) {
            InvocationHandler handler =
                    (proxy, method, args) -> {
                        if (STATEMENT_METHODS.contains(method.getName())) {
                            int[] holder = COUNTER.get();
                            if (holder != null) {
                                holder[0]++;
                            }
                        }
                        try {
                            return method.invoke(target, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    };
            return (Connection)
                    Proxy.newProxyInstance(
                            SqlCounter.class.getClassLoader(),
                            new Class<?>[] {Connection.class},
                            handler);
        }
    }
}
