package dev.ringlab.application.validation;

import static org.junit.jupiter.api.Assertions.*;

import dev.ringlab.application.ValidationException;
import org.junit.jupiter.api.Test;

class ProfanityPolicyTest {
  @Test
  void delegatesEnglishTextToTheConfiguredFilter() {
    var calls = new java.util.ArrayList<String>();
    var policy = new ProfanityPolicy((language, text) -> {
      calls.add(language + ":" + text);
      return text.equals("blocked");
    });

    assertTrue(policy.containsProfanity("blocked"));
    assertFalse(policy.containsProfanity("clean"));
    assertEquals(java.util.List.of("en:blocked", "en:clean"), calls);
  }

  @Test
  void returnsCleanAndBlockedResultsForRepresentativeInputs() {
    var policy = new ProfanityPolicy();

    assertFalse(policy.containsProfanity("A helpful racing setup"));
    var error = assertThrows(ValidationException.class, () -> policy.requireClean("This is crap"));
    assertEquals("Text contains inappropriate language", error.getMessage());
  }
}
