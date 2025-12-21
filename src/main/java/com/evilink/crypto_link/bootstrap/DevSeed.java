package com.evilink.crypto_link.bootstrap;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DevSeed implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    public DevSeed(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
       jdbc.execute("""
            create table if not exists cryptolink_api_keys (
                api_key text primary key,
                plan text not null,
                status text not null,
                expires_at timestamptz null
            )
         """);

        jdbc.update("""
             insert into cryptolink_api_keys (api_key, plan, status)
             values ('free_123', 'FREE', 'ACTIVE')
             on conflict (api_key) do nothing
        """);
     }

}
