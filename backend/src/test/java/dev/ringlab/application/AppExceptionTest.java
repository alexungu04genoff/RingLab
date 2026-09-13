package dev.ringlab.application;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class AppExceptionTest {
  @Test
  void baseExceptionCarriesSemanticsWithoutTransportStatus() {
    assertTrue(Modifier.isAbstract(AppException.class.getModifiers()));
    assertTrue(AppException.class.isSealed());
    assertEquals(0, AppException.class.getDeclaredFields().length);
    assertArrayEquals(
        new Class<?>[] {String.class},
        AppException.class.getDeclaredConstructors()[0].getParameterTypes());
  }
}
