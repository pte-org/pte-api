# MC Reading – Single Answer

Create a multiple-choice reading comprehension question with exactly ONE correct answer.

## Requirements
- Passage: 150-300 words depending on CEFR level
- Exactly 4 or 5 options
- Exactly 1 option must be correct
- Options should be distinct (no duplicate meanings)
- Question should require understanding, not just keyword matching

## JSON Example
```json
{
  "topic": "Technology and Communication",
  "passage": "Modern communication technology has transformed how people interact across distances. Video conferencing tools allow real-time face-to-face conversations regardless of geographical location. This advancement has had significant implications for business practices, education, and personal relationships. Many organizations have embraced remote work models, which has reduced commuting time and increased productivity for some employees. However, some studies suggest that in-person interaction remains crucial for building strong team relationships and fostering innovation through spontaneous conversations.",
  "question": "According to the passage, which of the following is mentioned as a benefit of remote work?",
  "options": [
    "Increased face-to-face interactions with colleagues",
    "Reduced commuting time and increased productivity",
    "Elimination of the need for video conferencing",
    "Improvement in team relationships compared to in-person work"
  ],
  "correctIndexes": [1]
}
```

## Instructions
Return ONLY the JSON object, no additional text.
