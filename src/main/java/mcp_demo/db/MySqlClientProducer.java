package mcp_demo.db;

import io.vertx.core.Vertx;
import io.vertx.mysqlclient.MySQLBuilder;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@ApplicationScoped
public class MySqlClientProducer {

    @Produces
    @Singleton
    public Pool createMySqlPool() {
        MySQLConnectOptions connectOptions = new MySQLConnectOptions()
            .setHost("127.0.0.1")
            .setPort(3306)
            .setDatabase("mcp_demo")
            .setUser("cubeguerrero")
            .setPassword("password");

        PoolOptions poolOptions = new PoolOptions()
            .setMaxSize(5);

        return MySQLBuilder.pool()
            .with(poolOptions)
            .connectingTo(connectOptions)
            .using(Vertx.vertx())
            .build();
    }

    public void closeMySqlPool(@Disposes Pool pool) {
        pool.close();
    }
}

