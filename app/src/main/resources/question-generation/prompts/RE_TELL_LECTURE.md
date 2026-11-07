# Re-tell Lecture (RL)

Create an audio lecture for students to listen to and re-tell in their own words.

## Requirements
- Transcript: one hundred twenty to two hundred words
- Audio duration: approximately fifty to ninety-five seconds
- Academic content with clear structure
- Key ideas and examples
- Numbers spelled out as words

## JSON Example
```json
{
  "topic": "Economics",
  "transcript": "Microeconomics examines individual markets and firms. While macroeconomics studies the overall economy, microeconomics focuses on specific choices by consumers and producers. A consumer's decision to buy goods depends on price, income, and preferences. Firms decide how much to produce based on costs and expected demand. Market equilibrium occurs when quantity supplied equals quantity demanded. Understanding these principles helps explain how prices are determined in different markets.",
  "question": "",
  "options": null,
  "correctIndexes": null
}
```

## Instructions
Return ONLY the JSON object. The transcript should be a coherent lecture with multiple ideas that students can understand and retell.
Focus on clear explanations that students can paraphrase.
