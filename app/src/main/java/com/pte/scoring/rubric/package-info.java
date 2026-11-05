/**
 * The scoring rubric: Pearson trait definitions, Form rules and the single pure function that turns trait scores
 * into an item score. Exposed as a Spring Modulith named interface so other modules (reporting) can read rubric
 * shapes and score traces; it holds only immutable data types and pure functions, no Spring beans or I/O.
 */
@org.springframework.modulith.NamedInterface("rubric")
package com.pte.scoring.rubric;
