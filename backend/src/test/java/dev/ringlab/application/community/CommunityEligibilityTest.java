package dev.ringlab.application.community;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CommunityEligibilityTest {
  @Test void excludesFeatureNamedTeachingFixturesAndLegacyPrefixes() {
    for (String feature : new String[]{"Ranking", "Comments", "Recommendation", "Ownership", "Scenario", "Stats", "Maps", "Machines", "Remix"}) {
      assertTrue(CommunityEligibility.controlledDemo("[Demo · " + feature + "] A readable example"));
    }
    for (String prefix : new String[]{"[Wilson Demo]", "[Comment Demo]", "[Pagination Demo]", "[Optimizer Demo]", "[DEMO]", "[Demo]"}) {
      assertTrue(CommunityEligibility.controlledDemo(prefix + " Existing example"));
    }
  }

  @Test void ordinaryTitlesAndIncidentalDemoWordsRemainEligible() {
    for (String title : new String[]{"My weekend setup", "Trying the demo racer", "A [Demo · Scenario] idea", "[Demo · ] Empty feature", "[Demo · Scenario]"}) {
      assertFalse(CommunityEligibility.controlledDemo(title));
    }
  }
}
