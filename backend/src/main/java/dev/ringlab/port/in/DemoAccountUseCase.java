package dev.ringlab.port.in;

import dev.ringlab.domain.auth.User;
import java.util.List;

/** Development fixture capability; its implementation is absent from production builds. */
public interface DemoAccountUseCase {
  record Account(String key, String username, String email) {}
  List<User> bootstrap(List<Account> accounts, String password);
}
