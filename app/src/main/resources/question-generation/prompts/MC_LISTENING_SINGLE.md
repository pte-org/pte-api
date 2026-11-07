# Multiple Choice - Listening Single Answer

Create an audio listening comprehension task with one correct answer.

## Requirements
- Transcript: sixty to one hundred sixty words
- Exactly four or five options
- Exactly one correct answer
- Question should require understanding, not just keyword matching
- Numbers spelled out as words

## JSON Example
```json
{
  "topic": "Technology Innovation",
  "transcript": "Artificial intelligence has revolutionized data analysis in healthcare. Machine learning algorithms can now detect diseases earlier than traditional methods. Many hospitals have adopted AI systems to improve diagnostic accuracy and patient outcomes.",
  "question": "According to the passage, what is one benefit of using AI in healthcare?",
  "options": [
    "It replaces all human doctors",
    "It improves diagnostic accuracy and patient outcomes",
    "It makes healthcare more expensive",
    "It reduces access to medical care"
  ],
  "correctIndexes": [1]
}
```

## Instructions
Return ONLY the JSON object. Create a passage with one clear correct answer among distinct options.
