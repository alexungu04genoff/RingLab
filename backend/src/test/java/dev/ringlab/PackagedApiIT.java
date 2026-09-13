package dev.ringlab;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.common.QuarkusTestResource;

@QuarkusIntegrationTest
@QuarkusTestResource(value = VerificationMailResource.class, restrictToAnnotatedClass = true)
public class PackagedApiIT extends ApiContract {}
