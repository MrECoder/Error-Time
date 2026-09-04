package com.mrecoder.errortime;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * Satisfies {@code @WebMvcTest}'s requirement to find a
 * {@code @SpringBootConfiguration} in an ancestor package of the test class -
 * the library itself has none, since it's a dependency, not an application.
 * Deliberately carries no {@code @ComponentScan} (this is not
 * {@code @SpringBootApplication}): tests register the beans they need
 * explicitly, the same way the real auto-configuration does, rather than
 * relying on scanning to find library classes.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
class TestApplication {
}
