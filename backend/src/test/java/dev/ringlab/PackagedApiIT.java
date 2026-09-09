package dev.ringlab;

import io.quarkus.test.junit.QuarkusIntegrationTest;

/** Repeats the same acceptance contract against the packaged application. */
@QuarkusIntegrationTest
public class PackagedApiIT extends ApiContract {}
