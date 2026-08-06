package com.prediction.config;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteDataSource;

@Configuration
public class SqliteConfig {

    @Bean
    DataSource dataSource(AppProperties props) {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + props.dbPath().toAbsolutePath());
        ds.setBusyTimeout(5000);
        return ds;
    }
}
