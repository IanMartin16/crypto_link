package com.evilink.crypto_link.bootstrap;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayForceRunner {

    @Bean
      ApplicationRunner flywayForce(
        ObjectProvider<Flyway> flywayProvider,
        @Value("${cryptolink.flyway.force:false}") boolean force
    ) {
      return args -> {
        Flyway flyway = flywayProvider.getIfAvailable();
        if (force && flyway != null) {
           flyway.migrate();
        }
      };
    }
}
