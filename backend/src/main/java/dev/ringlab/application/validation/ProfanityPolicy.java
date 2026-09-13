package dev.ringlab.application.validation;

import dev.ringlab.application.ValidationException;
import com.modernmt.text.profanity.ProfanityFilter;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.function.BiPredicate;

@ApplicationScoped
public class ProfanityPolicy {
  private static final String MESSAGE = "Text contains inappropriate language";
  private static final String LANGUAGE = "en";
  private final BiPredicate<String, String> profanityTest;

  public ProfanityPolicy() {
    this(new ProfanityFilter()::test);
  }

  protected ProfanityPolicy(BiPredicate<String, String> profanityTest) {
    this.profanityTest = profanityTest;
  }

  public boolean containsProfanity(String text) {
    return text != null && !text.isEmpty() && profanityTest.test(LANGUAGE, text);
  }

  public void requireClean(String text) {
    if (containsProfanity(text)) throw new ValidationException(MESSAGE);
  }

  public void requireClean(String text, String field) {
    if (containsProfanity(text)) throw new ValidationException(MESSAGE, field);
  }
}
