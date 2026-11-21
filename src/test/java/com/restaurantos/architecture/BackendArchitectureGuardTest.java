package com.restaurantos.architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Enforces the repository's controller -> service -> DAO layering at build time. */
class BackendArchitectureGuardTest {
    private static final Path SOURCE = Path.of("src/main/java/com/restaurantos");

    @Test void controllersDoNotAccessJdbcOrContainSql() throws Exception {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCE)) {
            for (Path file : files.filter(p -> p.toString().endsWith("Controller.java")).toList()) {
                String source = Files.readString(file);
                if (source.contains("JdbcTemplate") || source.contains("EntityManager")
                    || source.contains("java.sql.") || source.contains("SELECT ")
                    || source.contains("INSERT INTO ") || source.contains("UPDATE ")
                    || source.contains("DELETE FROM "))
                    violations.add(file.toString());
            }
        }
        assertTrue(violations.isEmpty(), "Controllers contain direct persistence logic: " + violations);
    }

    @Test void serviceImplementationsDoNotInjectJdbc() throws Exception {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCE)) {
            for (Path file : files.filter(p -> p.toString().endsWith("ServiceImpl.java")).toList()) {
                String source = Files.readString(file);
                if (source.contains("JdbcTemplate") || source.contains("EntityManager")
                    || source.contains("java.sql."))
                    violations.add(file.toString());
            }
        }
        assertTrue(violations.isEmpty(), "Service implementations bypass their DAOs: " + violations);
    }
}
