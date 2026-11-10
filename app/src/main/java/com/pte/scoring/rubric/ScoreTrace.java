package com.pte.scoring.rubric;

import java.math.BigDecimal;
import java.util.List;

/**
 * How an item score was reached: the unrounded percentage, the rounded 0-100 item score, the gate trait that zeroed
 * the item (null when no gate fired) and each trait's contribution.
 */
public record ScoreTrace(int itemScore, BigDecimal rawPercent, String gateTriggeredBy,
                         List<TraitContribution> contributions) {

    public ScoreTrace {
        contributions = List.copyOf(contributions);
    }
}
