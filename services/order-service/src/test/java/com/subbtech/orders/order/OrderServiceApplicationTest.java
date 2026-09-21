package com.subbtech.orders.order;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@Import(TestcontainersConfig.class)
class OrderServiceApplicationTest {

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcClient jdbc;

    @Test
    void contextLoads_andTalksToPostgres16() {
        String version = jdbc.sql("SHOW server_version").query(String.class).single();

        assertThat(dataSource).isNotNull();
        assertThat(version).startsWith("16");
    }
}
