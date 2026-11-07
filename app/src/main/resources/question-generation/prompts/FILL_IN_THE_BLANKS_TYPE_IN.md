# Fill in the Blanks - Listening (Type In)

Create an audio passage with blanks to fill from words contained in the transcript.

## Requirements
- Transcript: sixty to one hundred sixty words with key words identified
- Multiple blanks (two to four)
- Each answer must be a word from the transcript
- List the blanks in the order their words appear in the transcript; use each word once
- Numbers spelled out as words

## JSON Example
```json
{
  "topic": "Environmental Science",
  "transcript": "Plants require sunlight, water, and nutrients to grow and photosynthesize effectively. Photosynthesis converts light energy into chemical energy. The roots absorb water from the soil and transport it to leaves. Chlorophyll in leaves captures light energy for the process.",
  "blanks": [
    {"answer": "sunlight"},
    {"answer": "photosynthesize"}
  ]
}
```

## Instructions
Return ONLY the JSON object. Each blank answer must be one exact word from the transcript, and the blanks must follow the order of the transcript.
