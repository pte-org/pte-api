# Multiple Choice - Listening Multiple Answers

Create an audio listening comprehension task with multiple correct answers.

## Requirements
- Transcript: sixty to one hundred sixty words
- Exactly five options
- One to three correct answers
- Question requires careful listening
- Numbers spelled out as words

## JSON Example
```json
{
  "topic": "Business Leadership",
  "transcript": "Effective leaders demonstrate several key qualities. They possess strong integrity and maintain ethical standards. Empathy allows leaders to understand their team members' needs. Decisiveness helps leaders make timely choices. Additionally, adaptability and continuous learning are crucial in today's changing business environment.",
  "question": "Which qualities are mentioned as important for effective leaders?",
  "options": [
    "Integrity",
    "Empathy",
    "Decisiveness",
    "Physical strength",
    "Adaptability"
  ],
  "correctIndexes": [0, 1, 2, 4]
}
```

## Instructions
Return ONLY the JSON object with one to three correct answer indices.
