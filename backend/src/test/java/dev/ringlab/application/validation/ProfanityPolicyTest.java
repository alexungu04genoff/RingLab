package dev.ringlab.application.validation;

import static org.junit.jupiter.api.Assertions.*;

import dev.ringlab.application.ValidationException;
import org.junit.jupiter.api.Test;

class ProfanityPolicyTest {
  private final ProfanityPolicy policy = new ProfanityPolicy();

  @Test
  void acceptsCleanTextAndInnocentLargerWords() {
    assertFalse(policy.containsProfanity("A helpful racing setup"));
    assertFalse(policy.containsProfanity("Scunthorpe and shitake mushrooms"));
    assertDoesNotThrow(() -> policy.requireClean("A clean comment"));
  }

  @Test
  void rejectsCaseAndSimpleSeparatorVariations() {
    for (String text : new String[] {"fuck", "FUCK", "f.u.c.k", "f u c k"}) {
      var error = assertThrows(ValidationException.class, () -> policy.requireClean(text));
      assertEquals("Text contains inappropriate language", error.getMessage());
    }
  }
}
