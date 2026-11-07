# Select Missing Word (SMW)

Create an audio passage where the final word is missing and students choose from options.

## Requirements
- Transcript: sixty to one hundred sixty words (final word is the answer)
- Multiple choice options that complete the passage
- Only one correct answer
- Meaningful context that leads to the answer word

## JSON Example
```json
{
  "topic": "Literature Analysis",
  "transcript": "The author explores themes of identity and belonging throughout the narrative. The protagonist's transformation mirrors the reader's own personal growth. This universal connection is what makes the novel such a profound and meaningful",
  "question": "",
  "options": [
    "entertainment",
    "experience",
    "challenge",
    "disappointment"
  ],
  "correctIndexes": [1]
}
```

## Instructions
Return ONLY the JSON object. The transcript should end with a natural stopping point where students must infer the missing final word from context.
