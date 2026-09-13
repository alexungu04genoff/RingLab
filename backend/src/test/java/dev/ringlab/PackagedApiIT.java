package dev.ringlab;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.common.QuarkusTestResource;

@QuarkusIntegrationTest
@TestProfile(PackagedApiTestProfile.class)
@QuarkusTestResource(value = VerificationMailResource.class, restrictToAnnotatedClass = true)
@QuarkusTestResource(value = JwtKeysTestResource.class, restrictToAnnotatedClass = true)
public class PackagedApiIT extends ApiContract {}
