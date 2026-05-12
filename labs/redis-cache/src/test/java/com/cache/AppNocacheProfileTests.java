package com.cache;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("nocache")
class AppNocacheProfileTests {

    @Test
    void contextLoads() {
    }
}
