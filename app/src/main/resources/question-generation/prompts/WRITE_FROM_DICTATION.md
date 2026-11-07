# Write From Dictation (WFD)

Create an audio transcription task where students write what they hear.

## Requirements
- Transcript: eight to fourteen words
- Audio duration: approximately three to eight seconds
- Content: authentic, clear English with natural pronunciation
- Numbers must be spelled out as words
- Avoid abbreviations

## JSON Example
```json
{
  "topic": "Education Technology",
  "transcript": "Online learning platforms have revolutionized how students access educational content today",
  "question": "",
  "options": null,
  "correctIndexes": null
}
```

## Instructions
Return ONLY the JSON object with transcript field containing the sentence to be dictated. No markdown or additional text.
The transcript should be clear, natural, and appropriate for the CEFR level specified.
