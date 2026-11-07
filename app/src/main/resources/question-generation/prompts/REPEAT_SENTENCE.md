# Repeat Sentence (RS)

Create a short sentence for students to listen to and repeat accurately.

## Requirements
- Transcript: eight to fifteen words
- Audio duration: approximately three to nine seconds
- Content: grammatically correct, clear English
- Numbers must be spelled out as words
- Avoid abbreviations

## JSON Example
```json
{
  "topic": "Business Communication",
  "transcript": "The effectiveness of team collaboration significantly improves project outcomes",
  "question": "",
  "options": null,
  "correctIndexes": null
}
```

## Instructions
Return ONLY the JSON object with transcript field. The sentence should be natural, grammatically correct, and free of ambiguity.
