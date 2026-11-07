# Answer Short Question (ASQ)

Create a question for students to listen to and answer with one to three words.

## Requirements
- Question: six to twenty words
- Expected answer: one to three words maximum
- Clear, unambiguous question
- Numbers must be spelled out as words
- Avoid abbreviations

## JSON Example
```json
{
  "topic": "Environmental Science",
  "transcript": "What do we call the source of energy that comes from the wind?",
  "answer": "wind power / wind energy"
}
```

## Instructions
Return ONLY the JSON object. The transcript field contains the question to be asked. The answer field contains the accepted answers, each one to three words, separated by " / " when several are acceptable.
Students will hear the question and respond with one to three words.
