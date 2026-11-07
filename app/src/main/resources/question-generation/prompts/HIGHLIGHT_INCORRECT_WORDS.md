# Highlight Incorrect Words (HIW)

Create an audio listening task where students identify words that differ from the text.

## Requirements
- Transcript: the correct sentence (sixty to one hundred sixty words for longer passages)
- DisplayedText: same sentence with exactly four to seven words changed
- Changed words must differ by at least two characters and have different first two letters
- Numbers must be spelled out as words
- Avoid abbreviations

## JSON Example
```json
{
  "topic": "Environmental Science",
  "transcript": "Climate change presents unprecedented challenges for sustainable development worldwide",
  "displayedText": "Climate change presents uncommon challenges for maintainable development globally",
  "question": "",
  "options": null,
  "correctIndexes": null,
  "changedIndexes": [2, 5, 6]
}
```

## Instructions
Return ONLY the JSON object. The displayedText must have same word count as transcript with four to seven words changed.
Each changed word must be distinctly different in spelling and first letters to avoid near-homophones.
