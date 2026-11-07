# MC Reading – Multiple Answers

Create a multiple-choice reading comprehension question with 2-3 correct answers.

## Requirements
- Passage: 150-300 words
- 5-7 options total
- Exactly 2 or 3 correct answers
- Options must be clearly distinct
- Question must require understanding of multiple concepts

## JSON Example
```json
{
  "topic": "Environmental Conservation",
  "passage": "Coral reefs are among the most biodiverse ecosystems on Earth, supporting thousands of marine species. These underwater structures face unprecedented threats from rising ocean temperatures, ocean acidification, and overfishing. Climate change causes coral bleaching, where corals expel the algae that provide their color and nutrients. Many countries have established marine protected areas to reduce human impact. International agreements aim to limit greenhouse gas emissions to preserve these vital ecosystems for future generations.",
  "question": "Which of the following are mentioned as threats to coral reefs?",
  "options": [
    "Rising ocean temperatures",
    "Increased marine protected areas",
    "Ocean acidification",
    "International climate agreements",
    "Overfishing"
  ],
  "correctIndexes": [0, 2, 4]
}
```

## Instructions
Return ONLY the JSON object, no additional text.
